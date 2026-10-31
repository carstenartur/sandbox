/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult.CostAssessment;
import de.regelsuche.sdk.optimization.RuntimeObligations.GuardKind;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.VerificationResult.Verified;

/** Numerical identity proof and independently replayed adapter scheduling. No optimizer candidate is fabricated. */
final class JavaLoopInvariantOptimizer {
    private JavaLoopInvariantOptimizer() { }

    static long optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compilerOptions,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,long remaining) {
        if(options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || remaining<12000) return remaining;
        CancellationToken token=()->MathematicalAnalysis.isCancelled(monitor);
        var discovery=JavaLoopInvariantAnalysis.discover(ast,remaining,token);
        remaining-=discovery.work();
        if(discovery.contexts().isEmpty()) return remaining;
        var names=new HashSet<String>();
        var nameWork=new JavaLoopInvariantAnalysis.Work(remaining,token);
        try {
            ast.accept(new ASTVisitor() {
                @Override public void preVisit(ASTNode node) {nameWork.tick();}
                @Override public boolean visit(SimpleName name) {names.add(name.getIdentifier());return true;}
            });
        } catch(JavaLoopInvariantAnalysis.Limit exceeded) {return remaining-nameWork.used;}
        remaining-=nameWork.used;
        var extractor=new JavaComputationExtractor();
        for(var context:discovery.contexts()) {
            int start=context.envelope().getStartPosition(), length=context.envelope().getLength();
            if(remaining<12000L+source.length()*4L || token.isCancelled()) break;
            if(selectionOffset>=0 && (selectionOffset>start || (long)selectionOffset+selectionLength<(long)start+length)) continue;
            if(replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length()))) continue;
            long share=Math.min(50000,(remaining-source.length()*4L)/3);
            // Two bounded SDK verifications, one schedule replay, and complete extraction scans.
            remaining-=3*share+source.length()*4L;
            try {
                var extraction=extractor.extractRuntimeExpression(ast,source,context.expression(),options);
                if(extraction.regions().size()!=1 || !extraction.diagnostics().isEmpty()) continue;
                var region=extraction.regions().getFirst();
                if(!region.arrayReads().isEmpty() || !region.receiverGuards().isEmpty()) continue;
                long scalarWork=region.trace().occurrences().size();
                long originalWork=Math.multiplyExact(context.control().tripCount(),scalarWork);
                long scheduledWork=Math.addExact(scalarWork+1,context.control().tripCount());
                CostAssessment cost=LoopSchedulingEvidence.cost(originalWork,scheduledWork,options.goal());
                if(scalarWork<1 || !cost.estimatedRuntimeImprovement() || cost.candidateScore()>=cost.sourceScore()) continue;
                String name; int index=0;
                do {name="_mathInvariant"+index++;} while(!names.add(name)); //$NON-NLS-1$
                String type=context.expression().resolveTypeBinding().getName();
                String expression=source.substring(context.expression().getStartPosition(),context.expression().getStartPosition()+context.expression().getLength());
                String declaration="final "+type+" "+name+" = "+expression+";\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                var plan=JavaSourceRewritePlan.hoist(ast,source,context.envelope(),context.expression(),declaration,name);
                String replacement=plan.replacement(source);
                String generated=source.substring(0,start)+replacement+source.substring(start+length);
                CompilationUnit generatedAst=MathematicalAnalysis.checkGeneratedSource(ast,generated,compilerOptions,options.targetJava(),monitor,environment);
                var temporary=temporary(generatedAst,start,declaration,name);
                var decoded=extractor.extractRuntimeExpression(generatedAst,generated,temporary.getInitializer(),options);
                if(decoded.regions().size()!=1 || !decoded.diagnostics().isEmpty()) throw new IllegalArgumentException("LOOP_INITIALIZER_NOT_EXTRACTED"); //$NON-NLS-1$
                var actual=decoded.regions().getFirst();
                if(!region.inputNames().equals(actual.inputNames()) || !region.plan().inputs().equals(actual.plan().inputs()))
                    throw new IllegalArgumentException("LOOP_INITIALIZER_INPUTS_CHANGED"); //$NON-NLS-1$
                var request=request(region,options,share);
                var proof=new ComputationOptimizer().verify(request,actual.plan(),token);
                remaining-=verificationOvershoot(proof,share);
                if(remaining<0 || token.isCancelled()) break;
                var reverse=new ComputationOptimizer().verify(request(actual,options,share),region.plan(),token);
                remaining-=verificationOvershoot(reverse,share);
                if(remaining<0 || token.isCancelled()) break;
                if(!(proof instanceof Verified verified) || !(reverse instanceof Verified)
                        || verified.obligations().guard()!=GuardKind.NONE) continue;
                var schedule=new LoopSchedulingEvidence(context.control(),context.normalCompletionOnly()
                        ?LoopSchedulingEvidence.EvaluationBasis.NORMAL_LOOP_COMPLETION:LoopSchedulingEvidence.EvaluationBasis.TOTAL_BODY,
                        context.inputs(),name,type,context.loop().getStartPosition(),context.expression().getStartPosition(),
                        context.expression().getLength(),scalarWork,originalWork,scheduledWork,1);
                var receipt=new MathematicalAnalysis.VerifiedRegion(start,length,verified.evidence(),cost,
                        source.substring(start,start+length),replacement,null,plan,schedule);
                var edit=new MathematicalAnalysis.Replacement(start,length,replacement,schedule.description());
                verifySchedule(ast,generatedAst,source,generated,receipt,List.of(edit),options,new JavaLoopInvariantAnalysis.Work(share,token));
                if(token.isCancelled()) break;
                replacements.add(edit); evidence.add(receipt);
            } catch(IllegalArgumentException | UnsupportedOperationException | JavaLoopInvariantAnalysis.Limit unavailable) {
                diagnostics.add(new MathematicalAnalysis.Diagnostic("LOOP_SCHEDULE_NOT_REALIZED",unavailable.getMessage(),start,length,MathematicalAnalysis.Stage.EMISSION)); //$NON-NLS-1$
            }
        }
        return remaining;
    }

    private static OptimizationRequest request(JavaComputationRegion region,MathCleanUpOptions options,long share) {
        return new OptimizationRequest(region.plan(),region.trace(),options.kinds(),"java25-numeric/v1",region.assumptions(), //$NON-NLS-1$
                SafetyProfile.PRESERVE_JAVA,options.goal(),options.optimizationBudget(share),CheckedPolicy.NONE);
    }
    private static long verificationOvershoot(de.regelsuche.sdk.optimization.VerificationResult result,long share) {
        long work=result instanceof Verified verified?verified.evidence().verificationWork()
                :result instanceof de.regelsuche.sdk.optimization.VerificationResult.BudgetExceeded exceeded?exceeded.work():share;
        return Math.max(0,work-share);
    }
    /** Replay control/admission, exact unchanged loop text, temp type/use, and all original variable bindings. */
    static void verifySchedule(CompilationUnit before,CompilationUnit after,String source,String generated,
            MathematicalAnalysis.VerifiedRegion receipt,List<MathematicalAnalysis.Replacement> edits,MathCleanUpOptions options,
            JavaLoopInvariantAnalysis.Work work) {
        var schedule=receipt.scheduling();
        if(schedule==null || receipt.sourcePlan()==null) throw new IllegalArgumentException("MISSING_LOOP_SCHEDULE"); //$NON-NLS-1$
        receipt.sourcePlan().resolve(before,source);
        ASTNode node=NodeFinder.perform(before,schedule.expressionOffset(),schedule.expressionLength());
        ASTNode ancestor=node;
        while(ancestor!=null && ancestor.getStartPosition()!=schedule.loopOffset()) ancestor=ancestor.getParent();
        if(!(ancestor instanceof Statement loop)) throw new IllegalArgumentException("LOOP_ANCHOR_CHANGED"); //$NON-NLS-1$
        var contexts=JavaLoopInvariantAnalysis.inspect(before,loop,work);
        var context=contexts.stream().filter(part->part.expression()==node).findFirst().orElseThrow(()->new IllegalArgumentException("LOOP_ADMISSION_CHANGED")); //$NON-NLS-1$
        if(!context.control().equals(schedule.control()) || !context.inputs().equals(schedule.invariantBindings())
                || context.normalCompletionOnly()!=(schedule.evaluationBasis()==LoopSchedulingEvidence.EvaluationBasis.NORMAL_LOOP_COMPLETION))
            throw new IllegalArgumentException("LOOP_CONTROL_OR_INPUTS_CHANGED"); //$NON-NLS-1$
        // Precharge complete source binding scans and expression decoding from this replay's allowance.
        // Compare the actual trace, rather than trusting a second caller-supplied work count or hash.
        work.charge(2L*source.length());
        var extracted=new JavaComputationExtractor().extractRuntimeExpression(before,source,context.expression(),options);
        work.tick();
        if(extracted.regions().size()!=1 || !extracted.diagnostics().isEmpty() || receipt.proof()==null
                || !extracted.regions().getFirst().trace().equals(receipt.proof().obligations().originalTrace()))
            throw new IllegalArgumentException("LOOP_VERIFIED_TRACE_DIFFERS"); //$NON-NLS-1$
        schedule.requireCost(receipt.proof(),receipt.cost(),options.goal());
        int preceding=0;
        for(var edit:edits) if(edit.offset()+edit.length()<=receipt.offset()) preceding+=edit.replacement().length()-edit.length();
        String declaration=receipt.sourcePlan().changes().getFirst().replacement();
        int insertion=receipt.offset()+preceding;
        if(insertion<0 || (long)insertion+receipt.replacement().length()>generated.length()
                || !generated.substring(insertion,insertion+receipt.replacement().length()).equals(receipt.replacement()))
            throw new IllegalArgumentException("LOOP_ENVELOPE_CHANGED"); //$NON-NLS-1$
        var temporary=temporary(after,insertion,declaration,schedule.temporaryName());
        if(!Modifier.isFinal(temporary.resolveBinding().getModifiers())
                || !temporary.resolveBinding().getType().getName().equals(schedule.primitiveType())
                || !temporary.getInitializer().resolveTypeBinding().getName().equals(schedule.primitiveType()))
            throw new IllegalArgumentException("LOOP_TEMPORARY_TYPE_CHANGED"); //$NON-NLS-1$
        int shift=preceding+declaration.length();
        ASTNode use=NodeFinder.perform(after,schedule.expressionOffset()+shift,schedule.temporaryName().length());
        if(!(use instanceof SimpleName name) || !temporary.resolveBinding().isEqualTo(name.resolveBinding()))
            throw new IllegalArgumentException("LOOP_TEMPORARY_USE_CHANGED"); //$NON-NLS-1$
        int useDelta=schedule.temporaryName().length()-schedule.expressionLength();
        String originalLoop=source.substring(loop.getStartPosition(),loop.getStartPosition()+loop.getLength());
        int from=schedule.expressionOffset()-loop.getStartPosition();
        String expected=originalLoop.substring(0,from)+schedule.temporaryName()+originalLoop.substring(from+schedule.expressionLength());
        if(!generated.substring(loop.getStartPosition()+shift,loop.getStartPosition()+shift+expected.length()).equals(expected))
            throw new IllegalArgumentException("ORIGINAL_LOOP_WRITES_OR_CONTROLS_CHANGED"); //$NON-NLS-1$
        if(loop instanceof WhileStatement || loop instanceof DoStatement) {
            var parent=(Block)context.envelope().getParent();
            var counterDeclaration=(VariableDeclarationStatement)parent.statements().get(parent.statements().indexOf(context.envelope())-1);
            var original=(VariableDeclarationFragment)counterDeclaration.fragments().getFirst();
            var mapped=mappedBinding(before,after,original.resolveBinding(),receipt,edits);
            if(!(after.findDeclaringNode(mapped) instanceof VariableDeclarationFragment actual)
                    || actual.getInitializer()==null || !(actual.getInitializer().resolveConstantExpressionValue() instanceof Integer value)
                    || value.longValue()!=schedule.control().initial())
                throw new IllegalArgumentException("LOOP_COUNTER_INITIALIZER_CHANGED"); //$NON-NLS-1$
        } else if(loop instanceof EnhancedForStatement) {
            ASTNode mapped=NodeFinder.perform(after,loop.getStartPosition()+shift,expected.length());
            if(!(mapped instanceof EnhancedForStatement enhanced)
                    || JavaLoopInvariantAnalysis.arrayLength(after,enhanced.getExpression(),work)!=schedule.control().tripCount())
                throw new IllegalArgumentException("LOOP_ARRAY_LENGTH_CHANGED"); //$NON-NLS-1$
        }
        loop.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode child) {work.tick();}
            @Override public boolean visit(SimpleName original) {
                if(original.getStartPosition()>=schedule.expressionOffset()
                        && original.getStartPosition()<schedule.expressionOffset()+schedule.expressionLength()) return false;
                int offset=original.getStartPosition()+shift+(original.getStartPosition()>schedule.expressionOffset()?useDelta:0);
                ASTNode mapped=NodeFinder.perform(after,offset,original.getLength());
                if(original.resolveBinding() instanceof IVariableBinding binding
                        && (!(mapped instanceof SimpleName actual) || !(actual.resolveBinding() instanceof IVariableBinding actualBinding)
                                || !mappedBinding(before,after,binding,receipt,edits).isEqualTo(actualBinding)))
                    throw new IllegalArgumentException("LOOP_ORIGINAL_BINDING_CHANGED"); //$NON-NLS-1$
                return false;
            }
        });
        // Generated initializer resolves to the same original input bindings at its new scope.
        var bindings=new java.util.LinkedHashSet<String>();
        temporary.getInitializer().accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode child) {work.tick();}
            @Override public boolean visit(SimpleName name) {
                if(name.resolveBinding() instanceof IVariableBinding binding) bindings.add(JavaLoopInvariantAnalysis.key(binding));
                return false;
            }
        });
        var expectedBindings=new java.util.LinkedHashSet<String>();
        node.accept(new ASTVisitor() {
            @Override public void preVisit(ASTNode child) {work.tick();}
            @Override public boolean visit(SimpleName name) {
                if(name.resolveBinding() instanceof IVariableBinding binding)
                    expectedBindings.add(JavaLoopInvariantAnalysis.key(mappedBinding(before,after,binding,receipt,edits)));
                return false;
            }
        });
        if(!bindings.equals(expectedBindings)) throw new IllegalArgumentException("LOOP_MOVED_INPUT_BINDING_CHANGED"); //$NON-NLS-1$
    }
    /** Local binding keys can renumber after insertion; replay their original declaration anchors instead. */
    private static IVariableBinding mappedBinding(CompilationUnit before,CompilationUnit after,IVariableBinding binding,
            MathematicalAnalysis.VerifiedRegion receipt,List<MathematicalAnalysis.Replacement> edits) {
        // Canonical headers may spell constant bounds through external primitive constant fields.
        if(binding.isField() && binding.getConstantValue()!=null) return binding;
        if(!(before.findDeclaringNode(binding) instanceof VariableDeclaration declaration))
            throw new IllegalArgumentException("LOOP_BINDING_DECLARATION_MISSING"); //$NON-NLS-1$
        int mapped=mappedPosition(declaration.getName().getStartPosition(),receipt,edits);
        ASTNode actual=NodeFinder.perform(after,mapped,declaration.getName().getLength());
        if(!(actual instanceof SimpleName name) || !name.isDeclaration()
                || !(name.getParent() instanceof VariableDeclaration actualDeclaration) || actualDeclaration.getName()!=name
                || actualDeclaration.getNodeType()!=declaration.getNodeType()
                || actualDeclaration.getParent().getNodeType()!=declaration.getParent().getNodeType()
                || actualDeclaration.getStartPosition()!=mappedPosition(declaration.getStartPosition(),receipt,edits)
                || !name.getIdentifier().equals(declaration.getName().getIdentifier()))
            throw new IllegalArgumentException("LOOP_BINDING_DECLARATION_CHANGED"); //$NON-NLS-1$
        IVariableBinding result=actualDeclaration.resolveBinding();
        if(result==null || result.isRecovered() || after.findDeclaringNode(result)!=actualDeclaration
                || !result.isEqualTo(name.resolveBinding()) || binding.isField()!=result.isField()
                || binding.isParameter()!=result.isParameter() || binding.isEnumConstant()!=result.isEnumConstant()
                || !binding.getType().isEqualTo(result.getType()))
            throw new IllegalArgumentException("LOOP_BINDING_CATEGORY_OR_TYPE_CHANGED"); //$NON-NLS-1$
        return result;
    }
    private static int mappedPosition(int offset,MathematicalAnalysis.VerifiedRegion receipt,
            List<MathematicalAnalysis.Replacement> edits) {
        int mapped=offset;
        for(var edit:edits) if(edit.offset()+edit.length()<=offset) mapped+=edit.replacement().length()-edit.length();
        if(offset>=receipt.offset() && offset<receipt.offset()+receipt.length()) {
            mapped+=receipt.sourcePlan().changes().getFirst().replacement().length();
            if(offset>receipt.scheduling().expressionOffset()) mapped+=receipt.scheduling().temporaryName().length()-receipt.scheduling().expressionLength();
        }
        return mapped;
    }
    private static VariableDeclarationFragment temporary(CompilationUnit ast,int start,String declaration,String name) {
        ASTNode node=NodeFinder.perform(ast,start+declaration.indexOf(name),name.length());
        if(!(node instanceof SimpleName identifier) || !(identifier.getParent() instanceof VariableDeclarationFragment fragment)
                || fragment.resolveBinding()==null || fragment.resolveBinding().isRecovered())
            throw new IllegalArgumentException("LOOP_TEMPORARY_NOT_RESOLVED"); //$NON-NLS-1$
        return fragment;
    }
    private static boolean overlaps(int a,int n,int b,int m) {return a<(long)b+m && b<(long)a+n;}
}
