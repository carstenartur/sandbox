/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionService;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.JavaTailRecursionRewrite;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;

/** Explicit tail transfer, bounded numerical phases and optional style conversion off the editor buffer. */
final class FunctionalMathCoordinator {
 record Plan(String original,String result,Map<String,String> compilerOptions,List<Analysis> phases,long reservedWork) {
  Plan { compilerOptions=Map.copyOf(compilerOptions);phases=List.copyOf(phases); }
 }
 private FunctionalMathCoordinator() { }
 static Plan prepare(ICompilationUnit unit,String original,MathCleanUpOptions options,
   FunctionalMathOptions profile,IProgressMonitor parent) {
  var budget=new Budget(options,parent);var phases=new ArrayList<Analysis>();
  Map<String,String> compiler=Map.copyOf(unit.getJavaProject().getOptions(true));
  ICompilationUnit copy=null;
  try {
   budget.charge(4L*original.length());
   copy=unit.getWorkingCopy(new WorkingCopyOwner(){},budget);
   copy.getBuffer().setContents(original);
   var document=new Document(original);var ast=parse(copy,compiler,budget);
   var tail=tail(copy,ast,document,compiler,options,budget);ast=tail.ast();
   if(!tail.bodies().isEmpty() && options.safety()==de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA
     && !options.mathematicalOptIn() && options.goal()==de.regelsuche.sdk.optimization.OptimizationGoal.LOWER_ESTIMATED_RUNTIME) {
    budget.phase("Verify transferred tail iterations");
    var accelerated=MathematicalAnalysis.analyzeTailLoops(ast,document.get(),budget.allocate(options,3,4),budget,tailLoops(ast,tail.bodies(),budget));
    budget.check();if(!accelerated.matches(document.get(),compiler)) throw new IllegalArgumentException("STALE_TAIL_ITERATION_PHASE");
    phases.add(accelerated);applyMath(accelerated,document,tail.bodies());
    if(accelerated.changed()) {copy.getBuffer().setContents(document.get());ast=parse(copy,compiler,budget);}
   }
   var existingTemplates=templates(ast,document.get(),budget);
   budget.phase("Verify original mathematics");
   var first=MathematicalAnalysis.analyze(ast,document.get(),budget.allocate(options,3,4),budget,-1,0,null,existingTemplates);
   budget.check();if(!first.matches(document.get(),compiler)) throw new IllegalArgumentException("STALE_MATHEMATICS_PHASE");
   phases.add(first);
   var edits=edits(first);var sums=fusedSums(ast,first,edits);
   List<ISourceRange> protectedRegions=applyMath(first,document,existingTemplates);
   if(first.changed()) {copy.getBuffer().setContents(document.get());ast=parse(copy,compiler,budget);}
   if(!sums.isEmpty()) {
    budget.phase("Verify newly exposed sums");
    var second=MathematicalAnalysis.analyzeFreshSums(ast,document.get(),budget.allocate(options,3,4),budget,sums);
    budget.check();if(!second.matches(document.get(),compiler)) throw new IllegalArgumentException("STALE_MATHEMATICS_PHASE");
    phases.add(second);protectedRegions=applyMath(second,document,protectedRegions);
    if(second.changed()) {copy.getBuffer().setContents(document.get());ast=parse(copy,compiler,budget);}
   }
   var protectedAll=new ArrayList<ISourceRange>(protectedRegions);
   protectedAll.addAll(templates(ast,document.get(),budget));
   if(FunctionalMathOptions.enabled(profile.requested())) {
    var snapshot=new FunctionalMathSnapshot(ast,document.get(),protectedAll,budget::charge);
    budget.phase("Convert remaining loops");
    var conversion=LoopConversionService.renderUnchanged(ast,profile.requested(),snapshot.exclusions(),budget::charge,budget);
    if(conversion!=null) {
     document.addDocumentListener(snapshot);
     try {conversion.getEdit().apply(document);} finally {document.removeDocumentListener(snapshot);}
     copy.getBuffer().setContents(document.get());
     snapshot.verify(parse(copy,compiler,budget),document);
    }
   }
   budget.check();
   if(phases.stream().anyMatch(phase->!phase.sourceDependencies().current())) throw new IllegalArgumentException("STALE_SOURCE_DEPENDENCY");
   return new Plan(original,document.get(),compiler,phases,budget.used());
  } catch(CoreException | BadLocationException failure) {
   throw new IllegalArgumentException("COMBINED_CLEANUP_UNAVAILABLE: "+failure.getMessage(),failure);
  } finally {
   if(copy!=null) try {copy.discardWorkingCopy();}
   catch(CoreException failure) {throw new IllegalStateException("Cannot discard private cleanup working copy",failure);}
  }
 }
 private record TailPhase(CompilationUnit ast,List<ISourceRange> bodies) { }
 private static TailPhase tail(ICompilationUnit copy,CompilationUnit ast,Document document,Map<String,String> compiler,
   MathCleanUpOptions options,Budget budget) throws CoreException {
  if(!options.tailRecursion()) return new TailPhase(ast,List.of());
  for(String exclusion:options.exclusions()) {
   budget.charge(1L+exclusion.length());
   if(FileSystems.getDefault().getPathMatcher("glob:"+exclusion).matches(Path.of(copy.getPath().toPortableString())))
    return new TailPhase(ast,List.of());
  }
  var offsets=new ArrayList<Integer>();
  ast.accept(new ASTVisitor() {
   @Override public void preVisit(ASTNode node) {budget.charge(1);}
   @Override public boolean visit(MethodDeclaration method) {
    if(Modifier.isStatic(method.getModifiers()) && method.getBody()!=null && method.getBody().statements().size()==2
      && method.getBody().statements().getFirst() instanceof IfStatement
      && method.getBody().statements().get(1) instanceof ReturnStatement returned
      && returned.getExpression()!=null && unparenthesized(returned.getExpression(),budget) instanceof MethodInvocation call
      && call.getName().getIdentifier().equals(method.getName().getIdentifier())) {
     if(offsets.size()==8) throw new IllegalArgumentException("TOO_MANY_TAIL_METHODS");
     offsets.add(method.getStartPosition());
    }return true;
   }
  });
  offsets.sort(Comparator.reverseOrder());var bodies=new ArrayList<ISourceRange>();
  for(int offset:offsets) {
   budget.phase("Verify tail recursion");String before=document.get();
   var result=JavaTailRecursionRewrite.prepare(ast,before,offset,budget.allocate(options,1,4),budget);
   budget.check();
   if("CANCELLED".equals(result.reason())) throw new OperationCanceledException();
   if("WORK_LIMIT".equals(result.reason())) throw new IllegalArgumentException("TAIL_BUDGET_EXCEEDED: original buffer unchanged");
   if(result.plan().isEmpty()) continue;
   var plan=result.plan().orElseThrow();String after=plan.applyTo(before);int delta=after.length()-before.length();
   for(int i=0;i<bodies.size();i++) {
    var previous=bodies.get(i);bodies.set(i,new SourceRange(previous.getOffset()+delta,previous.getLength()));
   }
   document.set(after);copy.getBuffer().setContents(after);ast=parse(copy,compiler,budget);
   bodies.add(new SourceRange(plan.bodyOffset(),plan.bodyLength()));
  }
  return new TailPhase(ast,List.copyOf(bodies));
 }
 private static List<ISourceRange> tailLoops(CompilationUnit ast,List<ISourceRange> bodies,Budget budget) {
  var result=new ArrayList<ISourceRange>();
  for(var range:bodies) {
   var node=NodeFinder.perform(ast,range.getOffset(),range.getLength());
   if(!(node instanceof Block body)) throw new IllegalArgumentException("TAIL_BODY_CHANGED");
   int previous=result.size();
   body.accept(new ASTVisitor() {
    @Override public void preVisit(ASTNode child) {budget.charge(1);}
    @Override public boolean visit(ForStatement loop) {result.add(new SourceRange(loop.getStartPosition(),loop.getLength()));return false;}
   });
   if(result.size()!=previous+1) throw new IllegalArgumentException("TAIL_LOOP_CHANGED");
  }return result;
 }
 private static List<ISourceRange> templates(CompilationUnit ast,String source,Budget budget) {
  var ranges=new LinkedHashMap<Integer,ISourceRange>();
  boolean[] runtime={false};
  ast.accept(new ASTVisitor() {
   @Override public void preVisit(ASTNode node) {budget.charge(1);}
   @Override public boolean visit(MethodDeclaration method) {
    if(method.getBody()!=null) {
     int start=method.getStartPosition(),end=method.getBody().getStartPosition();budget.charge(end-start);
     if(source.substring(start,end).contains("ALLOW_FRAME_ELISION")) {
      var body=method.getBody();ranges.put(body.getStartPosition(),new SourceRange(body.getStartPosition(),body.getLength()));
      return false;
     }
    }return true;
   }
   @Override public boolean visit(VariableDeclarationFragment declaration) {
    runtime[0]|=declaration.getName().getIdentifier().startsWith("$series");return true;
   }
  });
  if(runtime[0])for(var range:org.sandbox.jdt.internal.corext.fix.math.JavaRuntimeIterationTemplates.ranges(ast,budget::charge))ranges.put(range.getOffset(),range);
  return List.copyOf(ranges.values());
 }
 private static Expression unparenthesized(Expression expression,Budget budget) {
  while(expression instanceof ParenthesizedExpression nested) {budget.charge(1);expression=nested.getExpression();}
  return expression;
 }
 private static CompilationUnit parse(ICompilationUnit copy,Map<String,String> compiler,Budget budget) throws CoreException {
  budget.charge(4L*copy.getSource().length());
  var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(copy);parser.setResolveBindings(true);parser.setCompilerOptions(compiler);
  var ast=(CompilationUnit)parser.createAST(budget);budget.check();
  for(var problem:ast.getProblems()) if(problem.isError()) throw new IllegalArgumentException("COMBINED_SOURCE_ERROR: "+problem.getMessage());
  return ast;
 }
 private static List<Replacement> edits(Analysis analysis) {
  var edits=new ArrayList<>(analysis.replacements());edits.addAll(analysis.documentation());
  edits.sort(Comparator.comparingInt(Replacement::offset));return edits;
 }
 private static List<ISourceRange> applyMath(Analysis analysis,Document document,List<ISourceRange> previous) throws BadLocationException {
  var edits=edits(analysis);var ranges=new ArrayList<ISourceRange>(previous);
  for(var edit:edits) ranges.add(new SourceRange(edit.offset(),edit.length()));
  ranges.sort(Comparator.comparingInt(ISourceRange::getOffset));
  var merged=new ArrayList<ISourceRange>();
  for(var range:ranges) {
   if(!merged.isEmpty()) {
    var last=merged.getLast();int end=last.getOffset()+last.getLength();
    if(range.getOffset()<=end) {
     merged.set(merged.size()-1,new SourceRange(last.getOffset(),Math.max(end,range.getOffset()+range.getLength())-last.getOffset()));continue;
    }
   }merged.add(range);
  }
  analysis.newEdit().apply(document);
  return merged.stream().map(range->(ISourceRange)new SourceRange(position(range.getOffset(),edits,false),
    position(range.getOffset()+range.getLength(),edits,true)-position(range.getOffset(),edits,false))).toList();
 }
 private static int position(int original,List<Replacement> edits,boolean end) {
  int mapped=original;
  for(var edit:edits) if(edit.offset()+edit.length()<original
    || edit.offset()+edit.length()==original && (end || edit.length()>0)) mapped+=edit.replacement().length()-edit.length();
  return mapped;
 }
 private static List<ISourceRange> fusedSums(CompilationUnit ast,Analysis first,List<Replacement> edits) {
  var result=new ArrayList<ISourceRange>();
  for(var receipt:first.evidence()) {
   if(receipt.proof()==null || receipt.sourcePlan()!=null) continue;
   var node=NodeFinder.perform(ast,receipt.offset(),receipt.length());
   if(node instanceof MethodInvocation second && second.getName().getIdentifier().equals("map")
     && second.getExpression() instanceof MethodInvocation initial && initial.getName().getIdentifier().equals("map")
     && second.getParent() instanceof MethodInvocation sum && sum.getExpression()==second
     && sum.getName().getIdentifier().equals("sum") && sum.arguments().isEmpty()) {
    int start=position(sum.getStartPosition(),edits,false),end=position(sum.getStartPosition()+sum.getLength(),edits,true);
    result.add(new SourceRange(start,end-start));if(result.size()==8) break;
   }
  }return result;
 }
 /** Reservations are never refunded, including unsuccessful mathematical searches. */
 static final class Budget extends NullProgressMonitor {
  private final IProgressMonitor parent;private final long total,started=System.nanoTime(),timeout;private long remaining;
  Budget(MathCleanUpOptions options,IProgressMonitor parent) {
   this.parent=parent==null?new NullProgressMonitor():parent;total=options.workBudget();remaining=total;timeout=options.timeoutSeconds()*1_000_000_000L;
  }
  @Override public boolean isCanceled() { return super.isCanceled() || parent.isCanceled() || Thread.currentThread().isInterrupted() || System.nanoTime()-started>=timeout; }
  void check() { if(isCanceled()) throw new OperationCanceledException("Combined cleanup cancelled or total deadline exceeded"); }
  void phase(String name) { parent.subTask(name);check(); }
  void charge(long work) {check();if(work<0 || work>remaining) throw new IllegalArgumentException("COMBINED_BUDGET_EXCEEDED: original buffer unchanged");remaining-=work;}
  MathCleanUpOptions allocate(MathCleanUpOptions options,int numerator,int denominator) {
   long allowance=remaining*numerator/denominator;charge(Math.max(1,allowance));
   var values=new HashMap<>(options.toMap());values.put(MathCleanUpOptions.WORK_BUDGET,Long.toString(Math.max(1,allowance)));
   return MathCleanUpOptions.parse(values,options.targetJava());
  }
  long used() { return total-remaining; }
 }
}
