/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import de.regelsuche.sdk.optimization.CancellationToken;
import de.regelsuche.sdk.optimization.CheckedPolicy;
import de.regelsuche.sdk.optimization.ComputationExplanations;
import de.regelsuche.sdk.optimization.ComputationOptimizer;
import de.regelsuche.sdk.optimization.OptimizationRequest;
import de.regelsuche.sdk.optimization.OptimizationResult.Candidate;
import de.regelsuche.sdk.optimization.RuntimeObligations.GuardKind;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.VerificationResult.Verified;

/** Numerical proof of a composed per-element kernel, separate from JDK topology admission. */
final class JavaNumericStreamOptimizer {
    private JavaNumericStreamOptimizer() { }

    /** Return unspent shared file work; independent emission proofs reserve their full bounds. */
    static long optimize(CompilationUnit ast, String source, MathCleanUpOptions options, IProgressMonitor monitor,
            int selectionOffset, int selectionLength, Map<String,String> compilerOptions,
            MathematicalAnalysis.SourceEnvironment environment, List<MathematicalAnalysis.Replacement> replacements,
            List<MathematicalAnalysis.VerifiedRegion> evidence, List<MathematicalAnalysis.Diagnostic> diagnostics,
            List<JavaNumericIterationContexts.Pair> accepted, long remaining) {
        if(options.safety()!=SafetyProfile.PRESERVE_JAVA || options.mathematicalOptIn() || remaining<12000) return remaining;
        CancellationToken token=() -> MathematicalAnalysis.isCancelled(monitor);
        var discovery=JavaNumericIterationContexts.discover(ast,source,remaining,token);
        remaining-=discovery.work();
        int pairs=discovery.contexts().stream().filter(JavaNumericIterationContexts.Context::standardMapChain)
                .mapToInt(context -> Math.max(0,context.stages().size()-1)).sum();
        for(var context:discovery.contexts()) for(int index=0;index<context.stages().size()-1;index++) {
            if(!context.standardMapChain()) continue;
            if(remaining<12000 || token.isCancelled()) return remaining;
            long allowance=remaining/Math.max(1,pairs--);
            if(allowance<12000) continue;
            var composition=JavaNumericIterationContexts.compose(ast,source,context,index,options,8192,token);
            remaining-=composition.work();
            var pair=composition.pair();
            if(pair==null) continue;
            var region=pair.region();
            if(selectionOffset>=0 && !overlaps(region.start(),region.length(),selectionOffset,Math.max(1,selectionLength))) continue;
            if(replacements.stream().anyMatch(edit -> overlaps(region.start(),region.length(),edit.offset(),edit.length()))) continue;
            try {
                // Reserve one share each for search and explanation, and three for the
                // independent emission proofs. Each uses the same bounded request.
                long proofShare=(allowance-8192)/5;
                var request=new OptimizationRequest(region.plan(),region.trace(),options.kinds(),"java25-numeric/v1", //$NON-NLS-1$
                        region.assumptions(),options.safety(),options.goal(),options.optimizationBudget(proofShare),CheckedPolicy.NONE);
                remaining-=proofShare;
                var result=new ComputationOptimizer().optimize(request,token);
                long searchWork=result instanceof Candidate candidate ? candidate.work() : MathematicalAnalysis.consumedWork(result,proofShare);
                remaining+=proofShare-searchWork;
                if(!(result instanceof Candidate candidate)) continue;
                if(candidate.work()>proofShare) continue;
                remaining-=proofShare;
                var explained=ComputationExplanations.describe(request,candidate,proofShare,token);
                remaining+=proofShare-(explained.work()>=0?explained.work():proofShare);
                if(!(explained.verification() instanceof Verified) || explained.explanation().isEmpty()
                        || candidate.obligations().guard()!=GuardKind.NONE || !candidate.cost().estimatedRuntimeImprovement()) continue;
                var emitted=new JavaComputationEmitter().emit(candidate.prepared(),region.inputNames(),region.reservedNames(),options.targetJava());
                emitted=JavaExpressionContexts.typedExpression(emitted,region);
                remaining-=3*proofShare;
                JavaEmissionVerifier.verify(request,candidate,emitted,region,options,token);
                String prefix=source.substring(pair.first().getExpression().getStartPosition(),
                        pair.first().getExpression().getStartPosition()+pair.first().getExpression().getLength());
                String replacement=prefix+".map("+pair.parameterName()+" -> "+emitted.outputValues().get(region.outputs().getFirst().id())+")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                String generated=source.substring(0,region.start())+replacement+source.substring(region.start()+region.length());
                var generatedAst=MathematicalAnalysis.checkGeneratedSource(ast,generated,compilerOptions,options.targetJava(),monitor,environment);
                var edit=new MathematicalAnalysis.Replacement(region.start(),region.length(),replacement,
                        "Mathematics PRESERVE_JAVA; independently verified composed per-element kernel; standard sequential JDK map chain; terminal retained; " //$NON-NLS-1$
                        +"estimated operation work "+candidate.cost().sourceCost().operationWork()+" -> "+candidate.cost().candidateCost().operationWork()); //$NON-NLS-1$ //$NON-NLS-2$
                verifyBoundary(ast,generatedAst,pair,List.of(edit));
                if(token.isCancelled()) return remaining;
                replacements.add(edit); accepted.add(pair);
                evidence.add(new MathematicalAnalysis.VerifiedRegion(region.start(),region.length(),candidate.evidence(),candidate.cost(),
                        source.substring(region.start(),region.start()+region.length()),replacement,
                        ComputationExplanation.from(region,explained.explanation().orElseThrow()),null));
            } catch(IllegalArgumentException | UnsupportedOperationException unavailable) {
                diagnostics.add(new MathematicalAnalysis.Diagnostic("STREAM_KERNEL_NOT_REALIZED",unavailable.getMessage(), //$NON-NLS-1$
                        region.start(),region.length(),MathematicalAnalysis.Stage.EMISSION));
            }
        }
        return remaining;
    }

    static void verifyBoundary(CompilationUnit before, CompilationUnit after, JavaNumericIterationContexts.Pair pair,
            List<MathematicalAnalysis.Replacement> replacements) {
        JavaExpressionContexts.verifyBoundary(before,after,pair.region(),replacements);
        int start=pair.region().start(),length=pair.region().length();
        for(var edit:replacements) {
            if(edit.offset()+edit.length()<=pair.region().start()) start+=edit.replacement().length()-edit.length();
            else if(edit.offset()>=pair.region().start() && edit.offset()+edit.length()<=pair.region().start()+pair.region().length())
                length+=edit.replacement().length()-edit.length();
        }
        var generated=NodeFinder.perform(after,start,length);
        if(!(generated instanceof MethodInvocation map) || map.arguments().size()!=1
                || !(map.arguments().getFirst() instanceof LambdaExpression lambda)
                || !JavaExpressionContexts.sameSam(pair.originalLambda(),lambda))
            throw new IllegalArgumentException("STREAM_KERNEL_SAM_CHANGED"); //$NON-NLS-1$
    }
    private static boolean overlaps(int a,int n,int b,int m) { return a<(long)b+m && b<(long)a+n; }
}
