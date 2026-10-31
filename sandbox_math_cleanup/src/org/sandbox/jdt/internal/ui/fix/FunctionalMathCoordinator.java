/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;

/** Bounded numerical phases followed by one style phase, entirely off the editor buffer. */
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
   var existingTemplates=templates(ast,budget);
   budget.phase("Verify original mathematics");
   var first=MathematicalAnalysis.analyze(ast,original,budget.allocate(options,3,4),budget,-1,0,null,existingTemplates);
   budget.check();if(!first.matches(original,compiler)) throw new IllegalArgumentException("STALE_MATHEMATICS_PHASE");
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
   protectedAll.addAll(templates(ast,budget));
   var snapshot=new FunctionalMathSnapshot(ast,document.get(),protectedAll,budget::charge);
   budget.phase("Convert remaining loops");
   var conversion=LoopConversionService.renderUnchanged(ast,profile.requested(),snapshot.exclusions(),budget::charge,budget);
   if(conversion!=null) {
    document.addDocumentListener(snapshot);
    try {conversion.getEdit().apply(document);} finally {document.removeDocumentListener(snapshot);}
    copy.getBuffer().setContents(document.get());
    snapshot.verify(parse(copy,compiler,budget),document);
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
 private static List<ISourceRange> templates(CompilationUnit ast,Budget budget) {
  var ranges=new LinkedHashMap<Integer,ISourceRange>();
  ast.accept(new ASTVisitor() {
   @Override public void preVisit(ASTNode node) {budget.charge(1);}
   @Override public boolean visit(VariableDeclarationFragment declaration) {
    if(declaration.getName().getIdentifier().startsWith("$series")) {
     ASTNode block=declaration;while(block!=null && !(block instanceof Block)) block=block.getParent();
     // The primitive distance guard emits an original fallback before the first
     // state declaration. Protect its complete wrapper, but never an arbitrary
     // enclosing user conditional or the rest of the containing method.
     if(block!=null && block.getParent() instanceof IfStatement guard && guard.getElseStatement()==block
       && guard.getParent() instanceof Block wrapper && wrapper.statements().size()==1
       && wrapper.statements().get(0)==guard && templateWrapper(wrapper)
       && primitiveGuard(guard.getExpression(),budget)) block=wrapper;
     if(block!=null) ranges.put(block.getStartPosition(),new SourceRange(block.getStartPosition(),block.getLength()));
    }return true;
   }
  });return List.copyOf(ranges.values());
 }
 private static boolean templateWrapper(Block wrapper) {
  if(wrapper.getParent() instanceof Statement) return true;
  if(!(wrapper.getParent() instanceof MethodDeclaration method) || !method.parameters().isEmpty()
    || !(method.getParent() instanceof AnonymousClassDeclaration anonymous)
    || !(anonymous.getParent() instanceof ClassInstanceCreation creation)) return false;
  var type=creation.getType().resolveBinding();if(type==null || type.isRecovered()) return false;
  return type.getQualifiedName().equals("java.util.function.IntSupplier") && method.getName().getIdentifier().equals("getAsInt")
    || type.getQualifiedName().equals("java.util.function.LongSupplier") && method.getName().getIdentifier().equals("getAsLong");
 }
 private static boolean primitiveGuard(Expression expression,Budget budget) {
  if(!(unparenthesized(expression,budget) instanceof InfixExpression comparison)
    || comparison.getOperator()!=InfixExpression.Operator.LESS || !comparison.extendedOperands().isEmpty()
    || !(unparenthesized(comparison.getRightOperand(),budget) instanceof NumberLiteral limit) || !limit.getToken().endsWith("L")) return false;
  return unparenthesized(comparison.getLeftOperand(),budget) instanceof InfixExpression distance
    && distance.getOperator()==InfixExpression.Operator.MINUS && distance.extendedOperands().isEmpty()
    && longCast(distance.getLeftOperand(),budget) && longCast(distance.getRightOperand(),budget);
 }
 private static boolean longCast(Expression expression,Budget budget) {
  return unparenthesized(expression,budget) instanceof CastExpression cast
    && cast.getType() instanceof PrimitiveType type && type.getPrimitiveTypeCode()==PrimitiveType.LONG;
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
