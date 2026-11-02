/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.FunctionExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.VerificationResult.Verified;
import de.regelsuche.search.program.JointComputationPlan;
import de.regelsuche.search.program.JointComputationPlan.Output;

/** Source-control simplification authorized only by a separate numerical equivalence proof. */
final class JavaSourceNumericBranches {
    private JavaSourceNumericBranches() { }
    private record Branch(IfStatement node, Assignment left, Assignment right, SourceNumericBranchEvidence receipt,
            OptimizationRequest request, JointComputationPlan other, ConditionSpan condition) { }
    private record ConditionSpan(int start, int end) {
        int length() { return end-start; }
    }
    private static final class Limit extends RuntimeException { private static final long serialVersionUID=1L; }
    private static final class Work {
        long left;
        final CancellationToken token;
        Work(long left,CancellationToken token) { this.left=left; this.token=token; }
        void charge(long value) { if(value>left || token.isCancelled()) throw new Limit(); left-=value; }
    }
    private static final class Fingerprint {
        final String source;
        String digest;
        Fingerprint(String source) { this.source=source; }
        String get(Work work) {
            if(digest==null) {
                work.charge(source.length());
                digest=MathematicalAnalysis.digest(source);
            }
            return digest;
        }
    }

    static long optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selected,int length,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<JavaComputationRegion> acceptedContexts,List<MathematicalAnalysis.Diagnostic> diagnostics,long remaining) {
        if(options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || remaining<12000) return remaining;
        var work=new Work(remaining,()->MathematicalAnalysis.isCancelled(monitor));
        var fingerprint=new Fingerprint(source);
        var nodes=new ArrayList<IfStatement>();
        try {
            ast.accept(new ASTVisitor() {
                @Override public boolean preVisit2(ASTNode node) { work.charge(1); return true; }
                @Override public boolean visit(IfStatement node) {
                    if(node.getElseStatement()!=null && (selected<0 || overlaps(node.getStartPosition(),node.getLength(),selected,Math.max(1,length)))) nodes.add(node);
                    return true;
                }
            });
            for(var node:nodes) {
                if(!canSubsume(node,replacements,evidence,work)) continue;
                long allowance=Math.min(20000,work.left/3);
                if(allowance<4000) break;
                var branch=decode(ast,source,node,options,allowance,work,fingerprint);
                if(branch==null) continue;
                if(options.goal()==OptimizationGoal.LOWER_ESTIMATED_RUNTIME && branch.receipt().conditionRetained()) continue;
                work.charge(2*allowance);
                var verified=new ComputationOptimizer().verify(branch.request(),branch.other(),work.token);
                if(!(verified instanceof Verified proof)) continue;
                // Fresh parsing/bindings and a second independent proof replay the exact receipt.
                work.charge(source.length());
                var fresh=MathematicalAnalysis.checkGeneratedSource(ast,source,compiler,options.targetJava(),monitor,environment);
                ASTNode anchor=NodeFinder.perform(fresh,node.getStartPosition(),node.getLength());
                var replay=anchor instanceof IfStatement condition?decode(fresh,source,condition,options,allowance,work,fingerprint):null;
                if(replay==null || !replay.receipt().equals(branch.receipt()) || !replay.condition().equals(branch.condition())
                        || !(new ComputationOptimizer().verify(replay.request(),replay.other(),work.token) instanceof Verified)) continue;
                Assignment chosen=branch.receipt().elseValueSelected()?branch.right():branch.left();
                // Admit both substring copies and the complete replacement before allocating them.
                // The retained wrapper adds 14 characters: "{ if (", ") {} ", and "; }".
                work.charge(2L*chosen.getLength()+(branch.receipt().conditionRetained()?2L*branch.condition().length()+14:1));
                String assignment=text(source,chosen);
                String replacement=branch.receipt().conditionRetained()
                        ? "{ if ("+source.substring(branch.condition().start(),branch.condition().end())+") {} "+assignment+"; }" : assignment+";"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                work.charge(source.length()+replacement.length());
                MathematicalAnalysis.checkGeneratedSource(ast,source.substring(0,node.getStartPosition())+replacement
                        +source.substring(node.getStartPosition()+node.getLength()),compiler,options.targetJava(),monitor,environment);
                work.charge(branch.left().getLength()+branch.right().getLength());
                var scalar=ComputationOptimizer.prepare(branch.request().plan()).cost();
                var alternative=ComputationOptimizer.prepare(branch.other()).cost();
                var before=withControl(scalar.operationWork()>=alternative.operationWork()?scalar:alternative,branch.receipt().sourceControlWork());
                var after=withControl(branch.receipt().elseValueSelected()?alternative:scalar,branch.receipt().candidateControlWork());
                var cost=new OptimizationResult.CostAssessment(before.operationWork(),after.operationWork(),0,0,before,after,
                        after.operationWork()<before.operationWork());
                work.charge((long)replacements.size()+evidence.size()+acceptedContexts.size());
                replacements.removeIf(edit->contained(node,edit.offset(),edit.length()));
                evidence.removeIf(edit->contained(node,edit.offset(),edit.length()));
                acceptedContexts.removeIf(region->contained(node,region.start(),region.length()));
                replacements.add(new MathematicalAnalysis.Replacement(node.getStartPosition(),node.getLength(),replacement,
                        "Source numeric branch: independently proved both primitive RHS values equal; condition " //$NON-NLS-1$
                        +(branch.receipt().conditionRetained()?"retained":"proved pure and total") //$NON-NLS-1$ //$NON-NLS-2$
                        +"; adapter source branch estimate 1 -> "+branch.receipt().candidateControlWork()+"; no measured runtime claim")); //$NON-NLS-1$ //$NON-NLS-2$
                evidence.add(new MathematicalAnalysis.VerifiedRegion(node.getStartPosition(),node.getLength(),proof.evidence(),cost,
                        text(source,node),replacement,null,null,null,null,null,branch.receipt()));
            }
        } catch(Limit exhausted) { /* Conservative no-edit for work not admitted by the shared file budget. */ }
        catch(IllegalArgumentException | UnsupportedOperationException unavailable) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("SOURCE_NUMERIC_BRANCH_NOT_REALIZED", unavailable.getMessage(), //$NON-NLS-1$
                    0, source.length(), MathematicalAnalysis.Stage.PROOF));
        }
        return work.left;
    }
    private static Branch decode(CompilationUnit ast,String source,IfStatement node,MathCleanUpOptions options,long allowance,Work work,Fingerprint fingerprint) {
        work.charge(1); Assignment left=assignment(node.getThenStatement()),right=assignment(node.getElseStatement());
        if(left==null || right==null || !(left.getLeftHandSide() instanceof SimpleName lhs)
                || !(right.getLeftHandSide() instanceof SimpleName rhs)
                || !(lhs.resolveBinding() instanceof IVariableBinding binding) || binding.isField() || binding.isRecovered()
                || !binding.isEqualTo(rhs.resolveBinding()) || !integral(binding.getType())
                || !binding.getType().isEqualTo(left.getRightHandSide().resolveTypeBinding())
                || !binding.getType().isEqualTo(right.getRightHandSide().resolveTypeBinding())
                || !total(left.getRightHandSide(),work) || !total(right.getRightHandSide(),work)) return null;
        boolean comments=false;
        int open=node.getStartPosition(), close=node.getThenStatement().getStartPosition()-1;
        int expressionStart=node.getExpression().getStartPosition(), expressionEnd=expressionStart+node.getExpression().getLength();
        // AST offsets refer to raw source; an escaped keyword can occupy more than two characters.
        while(open<expressionStart) {
            work.charge(1);
            if(source.charAt(open)=='(') break;
            open++;
        }
        while(close>=expressionEnd) {
            work.charge(1);
            if(!Character.isWhitespace(source.charAt(close))) break;
            close--;
        }
        if(open>=expressionStart || source.charAt(open)!='(' || close<expressionEnd || source.charAt(close)!=')') return null;
        for(Object value:ast.getCommentList()) {
            work.charge(1); ASTNode comment=(ASTNode)value;
            if(comment.getStartPosition()<node.getStartPosition() || comment.getStartPosition()>=node.getStartPosition()+node.getLength()) continue;
            if(comment.getStartPosition()<=open || comment.getStartPosition()+comment.getLength()>close) return null;
            comments=true;
        }
        // The existing extractor is reused without adding an adapter arithmetic catalogue.
        var extractor=new JavaComputationExtractor();
        var a=extractor.extractForSourceProof(ast,source,left.getRightHandSide(),options,work::charge);
        var b=extractor.extractForSourceProof(ast,source,right.getRightHandSide(),options,work::charge);
        if(!a.diagnostics().isEmpty() || !b.diagnostics().isEmpty() || a.regions().size()!=1 || b.regions().size()!=1) return null;
        var first=a.regions().getFirst(); var second=b.regions().getFirst();
        var inputs=new LinkedHashMap<>(first.plan().inputs());
        var mapping=new HashMap<String,String>();
        second.inputNames().forEach((id,name)-> {
            String existing=first.inputNames().entrySet().stream().filter(entry->entry.getValue().equals(name)).map(Map.Entry::getKey).findFirst().orElse(null);
            if(existing==null) { existing="branchInput"+inputs.size(); inputs.put(existing,second.plan().inputs().get(id)); } //$NON-NLS-1$
            mapping.put(id,existing);
        });
        var plan=new JointComputationPlan(inputs,Map.of(),first.plan().outputs());
        Expr other=rename(second.plan().outputExpressions().getFirst(),mapping,work,0);
        var target=new JointComputationPlan(inputs,Map.of(),List.of(new Output(plan.outputs().getFirst().name(),plan.outputs().getFirst().type(),other)));
        var request=new OptimizationRequest(plan,first.trace(),options.kinds(),ComputationOptimizer.SEMANTICS_REVISION,
                java.util.Set.of(),SafetyProfile.PRESERVE_JAVA,options.goal(),options.optimizationBudget(allowance),CheckedPolicy.NONE);
        var receipt=new SourceNumericBranchEvidence(fingerprint.get(work),node.getStartPosition(),node.getLength(),binding.getKey(),
                text(source,left.getRightHandSide()),text(source,right.getRightHandSide()),comments || !total(node.getExpression(),work),
                arithmetic(right.getRightHandSide(),work)<arithmetic(left.getRightHandSide(),work));
        return new Branch(node,left,right,receipt,request,target,new ConditionSpan(open+1,close));
    }
    private static Expr rename(Expr expression,Map<String,String> mapping,Work work,int depth) {
        work.charge(1); if(depth>128) throw new Limit();
        if(expression instanceof VariableExpr variable) return new VariableExpr(mapping.get(variable.name()));
        if(expression instanceof FunctionExpr function) return new FunctionExpr(function.name(),function.arguments().stream()
                .map(operand->rename(operand,mapping,work,depth+1)).toList());
        return expression;
    }
    private static Assignment assignment(Statement statement) {
        if(statement instanceof Block block && block.statements().size()==1) statement=(Statement)block.statements().getFirst();
        return statement instanceof ExpressionStatement expression && expression.getExpression() instanceof Assignment value
                && value.getOperator()==Assignment.Operator.ASSIGN?value:null;
    }
    private static boolean total(Expression expression,Work work) {
        boolean[] safe={true};
        expression.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) {
                work.charge(1); if(!safe[0]) return false;
                if(node instanceof SimpleName name) safe[0]=name.resolveBinding() instanceof IVariableBinding binding
                        && !binding.isRecovered() && !binding.isField() && binding.getType().isPrimitive();
                else if(node instanceof InfixExpression infix) safe[0]=infix.getOperator()!=InfixExpression.Operator.DIVIDE
                        && infix.getOperator()!=InfixExpression.Operator.REMAINDER;
                else if(node instanceof PrefixExpression prefix) safe[0]=prefix.getOperator()!=PrefixExpression.Operator.INCREMENT
                        && prefix.getOperator()!=PrefixExpression.Operator.DECREMENT;
                else safe[0]=node instanceof ParenthesizedExpression || node instanceof NumberLiteral || node instanceof CharacterLiteral
                        || node instanceof BooleanLiteral || node instanceof CastExpression || node instanceof PrimitiveType;
                return safe[0];
            }
        });
        return safe[0];
    }
    private static boolean integral(ITypeBinding type) {
        return type!=null && !type.isRecovered() && type.isPrimitive() && List.of("byte","short","char","int","long").contains(type.getName()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }
    private static int arithmetic(Expression expression,Work work) {
        int[] count={0};
        expression.accept(new ASTVisitor() {
            @Override public boolean preVisit2(ASTNode node) { work.charge(1); return true; }
            @Override public boolean visit(InfixExpression infix) { count[0]+=1+infix.extendedOperands().size(); return true; }
            @Override public boolean visit(PrefixExpression prefix) {
                if(!(prefix.getOperand() instanceof NumberLiteral)) count[0]++;
                return true;
            }
        });
        return count[0];
    }
    private static boolean canSubsume(IfStatement node,List<MathematicalAnalysis.Replacement> edits,
            List<MathematicalAnalysis.VerifiedRegion> evidence,Work work) {
        for(var edit:edits) {
            work.charge(1);
            if(!overlaps(node.getStartPosition(),node.getLength(),edit.offset(),edit.length())) continue;
            if(!contained(node,edit.offset(),edit.length())) return false;
            boolean scalar=false;
            for(var proof:evidence) {
                work.charge(1);
                if(proof.offset()==edit.offset() && proof.length()==edit.length() && proof.replacement().equals(edit.replacement())
                        && proof.proof()!=null && proof.proof().safetyProfile()==SafetyProfile.PRESERVE_JAVA && proof.sourcePlan()==null
                        && proof.scheduling()==null && proof.finiteSum()==null && proof.runtimeIteration()==null && proof.sourceNumericBranch()==null) scalar=true;
            }
            if(!scalar) return false;
        }
        return true;
    }
    private static boolean contained(ASTNode node,int start,int length) {
        return start>=node.getStartPosition() && (long)start+length<=(long)node.getStartPosition()+node.getLength();
    }
    private static de.regelsuche.search.program.PreparedJointComputation.Cost withControl(
            de.regelsuche.search.program.PreparedJointComputation.Cost cost,long control) {
        return new de.regelsuche.search.program.PreparedJointComputation.Cost(cost.operationWork()+control,cost.peakLiveStorage(),
                cost.retainedOutputStorage(),cost.outputBindings(),cost.operationCount(),cost.inspectionWork());
    }
    private static String text(String source,ASTNode node) { return source.substring(node.getStartPosition(),node.getStartPosition()+node.getLength()); }
    private static boolean overlaps(int a,int al,int b,int bl) { return a<(long)b+bl && b<(long)a+al; }
}
