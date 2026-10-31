/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.iteration.*;

/** Runtime finite reductions: SDK proof plus independently reconstructed Java template/binding replay. */
final class JavaRuntimeIterationOptimizer {
    private static final Set<String> GENERATED_JDK_TYPES=Set.of("java.math.BigInteger","java.lang.Math","java.util.function.IntSupplier","java.util.function.LongSupplier");
    private JavaRuntimeIterationOptimizer() { }
    record Reservation(boolean admitted,long work) { }
    static Reservation preflight(CompilationUnit unit,MathCleanUpOptions options,long available,CancellationToken token) {
        if(available<20000 || options.goal()!=OptimizationGoal.LOWER_ESTIMATED_RUNTIME || options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn())return new Reservation(false,0);
        var work=new JavaLoopInvariantAnalysis.Work(Math.min(20000,available/100),token);
        var nodes=new ArrayList<ASTNode>();int[] visited={0};
        try {
            unit.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) {visited[0]++;return !token.isCancelled();}
                @Override public boolean visit(ForStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(WhileStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(DoStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(MethodInvocation node) {if(node.getName().getIdentifier().equals("sum"))nodes.add(node);return true;}
            });
            // Routing over the existing AST must not dilute established scalar-only allocations.
            // Once there is a candidate, charge its complete traversal before source admission.
            if(nodes.isEmpty())return new Reservation(false,0);
            work.charge(visited[0]);
            for(var node:nodes) try {
                var context=JavaRuntimeIterationAnalysis.inspect(unit,node,work);
                if(constant(context.header().start())==null || constant(context.header().bound())==null)return new Reservation(true,work.used);
            } catch(IllegalArgumentException | ArithmeticException unavailable) { /* A failed admission reserves nothing. */ }
        } catch(JavaLoopInvariantAnalysis.Limit exhausted) { /* Bound the optional preflight itself. */ }
        return new Reservation(false,work.used);
    }
    static long optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compilerOptions,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,long remaining) {
        if(options.goal()!=OptimizationGoal.LOWER_ESTIMATED_RUNTIME || options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || remaining<20000) return remaining;
        long started=System.nanoTime(),timeout=options.timeoutSeconds()*1000000000L;
        CancellationToken token=()->MathematicalAnalysis.isCancelled(monitor)||Thread.currentThread().isInterrupted()||System.nanoTime()-started>=timeout;
        var work=new JavaLoopInvariantAnalysis.Work(remaining,token);var nodes=new ArrayList<ASTNode>();
        try {
            ast.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) {work.tick();}
                @Override public boolean visit(ForStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(WhileStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(DoStatement node) {nodes.add(node);return true;}
                @Override public boolean visit(MethodInvocation node) {if(node.getName().getIdentifier().equals("sum")) nodes.add(node);return true;}
            });
            for(var node:nodes) {
                work.tick();int offset=node.getStartPosition(),length=node.getLength();
                if(selectionOffset>=0 && (selectionOffset>offset || (long)selectionOffset+selectionLength<(long)offset+length))continue;
                if(replacements.stream().anyMatch(edit->offset<(long)edit.offset()+edit.length() && edit.offset()<(long)offset+length))continue;
                try {
                    var context=JavaRuntimeIterationAnalysis.inspect(ast,node,work);
                    NumericKind kind=context.width().equals("int")?NumericKind.INT:context.width().equals("long")?NumericKind.LONG:NumericKind.BIG_INTEGER;
                    if(!options.kinds().contains(kind))continue;
                    var sums=new ArrayList<PolynomialSumPlan>();LinearIterationOptimizer.Candidate linear=null;PolynomialSeries.NestedPlan nested=null;
                    for(var model:context.sums()) {
                        var search=new PolynomialSumOptimizer().search(model,budget(options,remaining,work),token);work.charge(search.work());
                        sums.add(search.plan().orElseThrow(()->new IllegalArgumentException(search.reason())));
                    }
                    if(context.linear()!=null) {
                        var search=new LinearIterationOptimizer().search(context.linear(),budget(options,remaining,work),token);work.charge(search.work());
                        linear=search.candidate().orElseThrow(()->new IllegalArgumentException(search.reason()));
                    }
                    if(context.nested()!=null) {
                        // Nested factory does not expose work: reserve its entire allowance, including failures.
                        var allocation=budget(options,remaining,work);work.charge(allocation.maximumWork());
                        nested=PolynomialSeries.nested(context.nested(),allocation,token);
                    }
                    var proof=new RuntimeIterationEvidence(JavaFiniteSumOptimizer.hash(source),context.bindings(),sums,linear,nested,context.partition());
                    long proofAllowance=Math.min(proof.verificationLimit(),(remaining-work.used)/4);
                    if(proofAllowance<1)throw new JavaLoopInvariantAnalysis.Limit();
                    verifyReserved(work,proofAllowance,()->proof.verify(options.optimizationBudget(proofAllowance),token));
                    if(!profitableStaticRange(context,proof))continue;
                    String replacement=JavaRuntimeIterationEmitter.emit(ast,source,context,proof,work);
                    String original=source.substring(offset,offset+length);
                    var plan=new JavaSourceRewritePlan("Source.java",JavaFiniteSumOptimizer.hash(source),source.length(),offset,length,List.of(),List.of(),Map.of(),
                            List.of(new JavaSourceRewritePlan.Change(offset,length,JavaFiniteSumOptimizer.hash(original),replacement)));
                    String generated=source.substring(0,offset)+replacement+source.substring(offset+length);
                    work.charge(4L*generated.length());
                    var after=MathematicalAnalysis.checkGeneratedSource(ast,generated,compilerOptions,options.targetJava(),monitor,environment);
                    var receipt=new MathematicalAnalysis.VerifiedRegion(offset,length,null,proof.cost(options.goal()),original,replacement,null,plan,null,null,proof);
                    var edit=new MathematicalAnalysis.Replacement(offset,length,replacement,proof.description());
                    long replayAllowance=remaining-work.used;
                    verifyReserved(work,replayAllowance,()->verify(ast,after,source,generated,receipt,List.of(edit),options,replayAllowance,token));
                    if(token.isCancelled())break;replacements.add(edit);evidence.add(receipt);
                } catch(IllegalArgumentException | ArithmeticException invalid) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("RUNTIME_ITERATION_UNAVAILABLE",invalid.getMessage(),offset,length,MathematicalAnalysis.Stage.SOURCE));
                }
            }
        } catch(JavaLoopInvariantAnalysis.Limit | org.eclipse.core.runtime.OperationCanceledException exhausted) {return 0;}
        return remaining-work.used;
    }
    private static java.math.BigInteger constant(Expression expression) {
        Object value=expression.resolveConstantExpressionValue();
        return value instanceof Integer number?java.math.BigInteger.valueOf(number):value instanceof Long number?java.math.BigInteger.valueOf(number):null;
    }
    private static boolean profitableStaticRange(JavaRuntimeIterationAnalysis.Context context,RuntimeIterationEvidence proof) {
        var h=context.header();var start=constant(h.start());var bound=constant(h.bound());
        if(context.partition()!=null && context.partition().period()>0 && start!=null && start.signum()<0)return false;
        if(start==null || bound==null)return true;
        var distance=h.stride().signum()>0?bound.subtract(start):start.subtract(bound);
        if(!h.inclusive())distance=distance.subtract(java.math.BigInteger.ONE);
        var count=distance.signum()<0?java.math.BigInteger.ZERO:distance.divide(h.stride().abs()).add(java.math.BigInteger.ONE);
        if(h.once())count=count.max(java.math.BigInteger.ONE);
        if(count.compareTo(java.math.BigInteger.valueOf(proof.minimumIterations()))<0)return false;
        if(context.stream())return true;
        var minimum=java.math.BigInteger.ONE.shiftLeft(h.counter().getType().getName().equals("int")?31:63).negate();
        var after=start.add(count.multiply(h.stride()));return after.compareTo(minimum)>=0 && after.compareTo(minimum.negate().subtract(java.math.BigInteger.ONE))<=0;
    }
    private static OptimizationBudget budget(MathCleanUpOptions options,long remaining,JavaLoopInvariantAnalysis.Work work) {
        long available=Math.min(100000,(remaining-work.used)/4);if(available<1)throw new JavaLoopInvariantAnalysis.Limit();return options.optimizationBudget(available);
    }
    /** A throwing verifier cannot return its spent work: retain the whole reservation on failure. */
    private static void verifyReserved(JavaLoopInvariantAnalysis.Work work,long allowance,java.util.function.LongSupplier verification) {
        work.charge(allowance);
        long used=verification.getAsLong();
        if(used<0 || used>allowance)throw invalid();
        // Only a normal, validated return identifies known-unused work. Exceptions never refund.
        work.used-=allowance-used;
    }
    static long verify(CompilationUnit before,CompilationUnit after,String source,String generated,
            MathematicalAnalysis.VerifiedRegion receipt,List<MathematicalAnalysis.Replacement> edits,
            MathCleanUpOptions options,long allowance,CancellationToken parent) {
        long started=System.nanoTime(),timeout=options.timeoutSeconds()*1000000000L;
        CancellationToken token=()->parent.isCancelled() || System.nanoTime()-started>=timeout;
        var work=new JavaLoopInvariantAnalysis.Work(allowance,token);work.charge(2L*(source.length()+generated.length()));
        var proof=receipt.runtimeIteration();
        if(proof==null || receipt.proof()!=null || receipt.scheduling()!=null || receipt.finiteSum()!=null || receipt.sourcePlan()==null
                || !JavaFiniteSumOptimizer.hash(source).equals(proof.sourceSha256()))throw invalid();
        var expected=new StringBuilder(source);int end=source.length();
        for(var edit:edits.stream().sorted(Comparator.comparingInt(MathematicalAnalysis.Replacement::offset).reversed()).toList()) {
            work.tick();if(edit.offset()<0 || edit.length()<0 || (long)edit.offset()+edit.length()>end)throw invalid();
            expected.replace(edit.offset(),edit.offset()+edit.length(),edit.replacement());end=edit.offset();
        }
        if(!expected.toString().equals(generated))throw invalid();receipt.sourcePlan().resolve(before,source);
        var context=JavaRuntimeIterationAnalysis.inspect(before,NodeFinder.perform(before,receipt.offset(),receipt.length()),work);
        if(!proof.bindings().equals(context.bindings()) || !Objects.equals(proof.partition(),context.partition())
                || !proof.sums().stream().map(PolynomialSumPlan::source).toList().equals(context.sums())
                || !Objects.equals(proof.linear()==null?null:proof.linear().source(),context.linear())
                || !Objects.equals(proof.nested()==null?null:proof.nested().source(),context.nested()))throw invalid();
        if(allowance-work.used<1)throw new JavaLoopInvariantAnalysis.Limit();
        long proofAllowance=Math.min(options.workBudget(),allowance-work.used);
        verifyReserved(work,proofAllowance,()->proof.verify(options.optimizationBudget(proofAllowance),token));
        if(!proof.cost(options.goal()).equals(receipt.cost()))throw invalid();
        String rebuilt=JavaRuntimeIterationEmitter.emit(before,source,context,proof,work);
        int mapped=position(receipt.offset(),edits);
        if(!rebuilt.equals(receipt.replacement()) || mapped<0 || (long)mapped+rebuilt.length()>generated.length()
                || !generated.substring(mapped,mapped+rebuilt.length()).equals(rebuilt))throw invalid();
        var external=new HashMap<String,IVariableBinding>();
        for(var binding:proof.bindings()) {
            ASTNode declaration=NodeFinder.perform(before,binding.declarationOffset(),binding.name().length());
            if(!(declaration instanceof SimpleName name) || !(name.resolveBinding() instanceof IVariableBinding variable))throw invalid();
            external.put(binding.name(),mappedBinding(before,after,variable,edits));
        }
        // Every template read/write of an original local must resolve to its original declaration.
        after.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {work.tick();}
            @Override public boolean visit(SimpleType type) {
                if(type.getStartPosition()<mapped || type.getStartPosition()>=mapped+rebuilt.length())return true;
                String text=type.getName().getFullyQualifiedName();
                if(GENERATED_JDK_TYPES.contains(text))requireJdkTypePath(type.getName(),work);
                return true;
            }
            @Override public boolean visit(QualifiedName name) {
                if(name.getStartPosition()<mapped || name.getStartPosition()>=mapped+rebuilt.length())return true;
                if(GENERATED_JDK_TYPES.contains(name.getFullyQualifiedName()))requireJdkTypePath(name,work);
                String owner=name.getQualifier().getFullyQualifiedName();
                if(GENERATED_JDK_TYPES.contains(owner)) {
                    requireJdkTypePath(name.getQualifier(),work);
                    if(!(name.resolveBinding() instanceof IVariableBinding field) || field.isRecovered() || !field.isField()
                            || !java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getDeclaringClass()==null
                            || field.getDeclaringClass().isFromSource() || !owner.equals(field.getDeclaringClass().getQualifiedName()))throw invalid();
                }
                return true;
            }
            @Override public boolean visit(MethodInvocation invocation) {
                if(invocation.getStartPosition()>=mapped && invocation.getStartPosition()<mapped+rebuilt.length()
                        && invocation.getExpression() instanceof Name name && GENERATED_JDK_TYPES.contains(name.getFullyQualifiedName())) {
                    requireJdkTypePath(name,work);
                    var binding=invocation.resolveMethodBinding();
                    if(binding==null || binding.isRecovered() || !java.lang.reflect.Modifier.isStatic(binding.getModifiers())
                            || binding.getDeclaringClass()==null || binding.getDeclaringClass().isFromSource()
                            || !binding.getDeclaringClass().getQualifiedName().equals(name.getFullyQualifiedName()))throw invalid();
                }return true;
            }
            @Override public boolean visit(SimpleName name) {
                if(name.getStartPosition()<mapped || name.getStartPosition()>=mapped+rebuilt.length() || name.isDeclaration())return true;
                var expectedBinding=external.get(name.getIdentifier());
                if(expectedBinding!=null && name.resolveBinding() instanceof IVariableBinding actual && !expectedBinding.isEqualTo(actual))throw invalid();
                return true;
            }
        });
        before.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode node) {work.tick();}
            @Override public boolean visit(SimpleName name) {
                if(covered(name.getStartPosition(),edits) || !(name.resolveBinding() instanceof IVariableBinding binding))return false;
                ASTNode declaration=before.findDeclaringNode(binding);if(declaration!=null && covered(declaration.getStartPosition(),edits))return false;
                ASTNode moved=NodeFinder.perform(after,position(name.getStartPosition(),edits),name.getLength());
                if(!(moved instanceof SimpleName actual) || !(actual.resolveBinding() instanceof IVariableBinding actualBinding)
                        || !mappedBinding(before,after,binding,edits).isEqualTo(actualBinding))throw invalid();return false;
            }
        });
        return work.used;
    }
    /** Expression qualifiers can bind to source variables even when the terminal member is a JDK static. */
    private static void requireJdkTypePath(Name name,JavaLoopInvariantAnalysis.Work work) {
        if(!(name.resolveBinding() instanceof ITypeBinding type) || type.isRecovered() || type.isFromSource()
                || !type.getQualifiedName().equals(name.getFullyQualifiedName()))throw invalid();
        Name part=name;
        while(true) {
            work.tick();var binding=part.resolveBinding();
            if(binding==null || binding.isRecovered() || !(binding instanceof IPackageBinding || binding instanceof ITypeBinding))throw invalid();
            if(binding instanceof ITypeBinding qualifier && qualifier.isFromSource())throw invalid();
            if(!(part instanceof QualifiedName qualified))break;
            part=qualified.getQualifier();
        }
    }
    private static IVariableBinding mappedBinding(CompilationUnit before,CompilationUnit after,IVariableBinding binding,List<MathematicalAnalysis.Replacement> edits) {
        ASTNode node=before.findDeclaringNode(binding);if(node==null && binding.isField())return binding;
        if(!(node instanceof VariableDeclaration declaration) || covered(declaration.getName().getStartPosition(),edits))throw invalid();
        ASTNode moved=NodeFinder.perform(after,position(declaration.getName().getStartPosition(),edits),declaration.getName().getLength());
        if(!(moved instanceof SimpleName name) || !name.isDeclaration() || !(name.getParent() instanceof VariableDeclaration actual)
                || !name.getIdentifier().equals(declaration.getName().getIdentifier()) || actual.getNodeType()!=declaration.getNodeType()
                || actual.getParent().getNodeType()!=declaration.getParent().getNodeType())throw invalid();
        var result=actual.resolveBinding();if(result==null || result.isRecovered() || !binding.getType().isEqualTo(result.getType()) || binding.isField()!=result.isField() || binding.isParameter()!=result.isParameter())throw invalid();return result;
    }
    private static int position(int original,List<MathematicalAnalysis.Replacement> edits) {int mapped=original;for(var edit:edits)if(edit.offset()+edit.length()<=original)mapped+=edit.replacement().length()-edit.length();return mapped;}
    private static boolean covered(int position,List<MathematicalAnalysis.Replacement> edits) {return edits.stream().anyMatch(e->position>=e.offset()&&position<e.offset()+e.length());}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("RUNTIME_ITERATION_REPLAY_DIFFERS");}
}
