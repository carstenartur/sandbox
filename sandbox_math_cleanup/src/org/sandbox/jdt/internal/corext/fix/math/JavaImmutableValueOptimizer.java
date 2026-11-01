/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.*;

/** Whole-method branch for direct numeric containers. Source effects and SDK algebra remain separate. */
final class JavaImmutableValueOptimizer {
    private JavaImmutableValueOptimizer() { }
    static long optimize(CompilationUnit ast,String source,MathCleanUpOptions options,IProgressMonitor monitor,
            int selectionOffset,int selectionLength,Map<String,String> compiler,MathematicalAnalysis.SourceEnvironment environment,
            List<MathematicalAnalysis.Replacement> replacements,List<MathematicalAnalysis.VerifiedRegion> evidence,
            List<MathematicalAnalysis.Diagnostic> diagnostics,JavaValueSourceSummary.Work work,MathematicalJavadoc documentation) {
        if(!options.mathematicalOptIn() || !options.kinds().contains(NumericKind.BIG_INTEGER))return 0;
        // Non-value files retain the established region allocation without paying for a whole-file value scan.
        boolean possible=false;
        for(Object item:ast.types())if(item instanceof TypeDeclaration type && Modifier.isFinal(type.getModifiers())
            && type.getSuperclassType()==null && type.typeParameters().isEmpty()) {
            var fields=Arrays.stream(type.getFields()).filter(field->!Modifier.isStatic(field.getModifiers())).toList();
            if(!fields.isEmpty() && fields.stream().allMatch(field->Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers()) && JavaValueSourceSummary.big(field.getType().resolveBinding())))possible=true;
        }
        if(!possible)return 0;
        try {
            work.charge(source.length());var methods=new ArrayList<MethodDeclaration>();
            ast.accept(new ASTVisitor(){
                @Override public void preVisit(ASTNode node){work.charge(1);}
                @Override public boolean visit(MethodDeclaration method) {
                    if(method.isConstructor() || Modifier.isStatic(method.getModifiers()) || method.getBody()==null
                        || method.getBody().statements().isEmpty())return false;
                    if(selectionOffset>=0 && !overlaps(method.getBody().getStartPosition(),method.getBody().getLength(),
                        selectionOffset,Math.max(1,selectionLength)))return false;
                    Object last=method.getBody().statements().getLast();
                    if(last instanceof ReturnStatement returned && returned.getExpression() instanceof ClassInstanceCreation creation
                        && creation.resolveTypeBinding()!=null && method.resolveBinding()!=null
                        && creation.resolveTypeBinding().isEqualTo(method.resolveBinding().getDeclaringClass()))methods.add(method);
                    return false;
                }
            });
            for(int index=0;index<methods.size();index++) {
                work.check();MethodDeclaration method=methods.get(index);Block body=method.getBody();int start=body.getStartPosition(),length=body.getLength();
                if(replacements.stream().anyMatch(edit->overlaps(start,length,edit.offset(),edit.length())))continue;
                try {
                    var session=new JavaValueSourceSummary.Session(ast,options,work);
                    if(session.shape(method.resolveBinding().getDeclaringClass()).components().stream().anyMatch(component->component.shape()!=null))continue;
                    var summary=bounded(session.method(method,body));
                    if(!session.onlyOwner(method.resolveBinding().getDeclaringClass()) || summary.staticValues().stream()
                        .anyMatch(value->!value.field().getDeclaringClass().isEqualTo(method.resolveBinding().getDeclaringClass())))continue;
                    long products=summary.trace().occurrences().stream().filter(o->JavaExpressions.operationOf(o.expression()).orElse(null)==NumericOperation.MULTIPLY).count();
                    if(products<4)continue;
                    Set<String> reserved=new HashSet<>();method.accept(new ASTVisitor(){@Override public boolean visit(SimpleName name){reserved.add(name.getIdentifier());return true;}});
                    var names=new LinkedHashMap<String,String>();var captures=new StringBuilder();
                    for(var input:summary.inputs()) {
                        String name="mathValue"+names.size();while(!reserved.add(name))name+="_";names.put(input.id(),name); //$NON-NLS-1$ //$NON-NLS-2$
                        captures.append("final java.math.BigInteger ").append(name).append(" = ").append(input.path()).append(";\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    }
                    long allowance=work.remaining()/(4L*(methods.size()-index));if(allowance<6000)continue;
                    var request=summary.request(options,work.allocate(allowance,options));var optimizer=new ComputationOptimizer();
                    var receiverGuards=new HashSet<>(names.values());summary.staticValues().forEach(value->receiverGuards.add(value.path()));
                    var rankingRegion=new JavaComputationRegion(summary.plan(),summary.trace(),List.of(),names,summary.assumptions(),reserved,receiverGuards,start,length);
                    var result=optimizer.optimize(request,work.token(),new JavaCandidateRanking(request,rankingRegion,options,source));work.check();
                    if(result instanceof OptimizationResult.BudgetExceeded exceeded && exceeded.work()>allowance)work.charge(exceeded.work()-allowance);
                    if(!(result instanceof OptimizationResult.Candidate candidate)) {
                        diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_NO_CANDIDATE",result.toString(),start,length,MathematicalAnalysis.Stage.SEARCH));continue; //$NON-NLS-1$
                    }
                    var emission=new JavaComputationEmitter().emit(candidate.prepared(),names,reserved,options.targetJava());
                    var arguments=new String[summary.value().shape().components().size()];
                    for(int i=0;i<arguments.length;i++)arguments[summary.value().shape().components().get(i).parameter()]=emission.outputValues().get("component"+i); //$NON-NLS-1$
                    String optimized=captures+emission.statements()+"return new "+method.resolveBinding().getDeclaringClass().getName()+"("+String.join(", ",arguments)+");\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    String original=source.substring(start,start+length);
                    String replacement="{\n// Mathematics value path: independently verified joint components.\nif ("+guard(summary)+") {\n"+optimized+"}\n// " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        +JavaComputationExtractor.ORIGINAL_FALLBACK_MARKER+"\n"+original.substring(1,original.length()-1)+"\n}"; //$NON-NLS-1$ //$NON-NLS-2$
                    String generated=source.substring(0,start)+replacement+source.substring(start+length);work.charge(generated.length());
                    var after=MathematicalAnalysis.checkGeneratedSource(ast,generated,compiler,options.targetJava(),monitor,environment);work.check();
                    MethodDeclaration replayMethod=(MethodDeclaration)after.findDeclaringNode(method.resolveBinding().getKey());
                    if(replayMethod==null || replayMethod.getBody().statements().isEmpty() || !(replayMethod.getBody().statements().getFirst() instanceof IfStatement condition)
                        || !(condition.getThenStatement() instanceof Block branch))throw JavaValueSourceSummary.unavailable("VALUE_REPLAY_SHAPE"); //$NON-NLS-1$
                    JavaValueSourceSummary.Summary replay;
                    try(var context=JavaSourceContext.open(after,generated,environment,monitor,options,work.remaining())) {
                        replay=new JavaValueSourceSummary.Session(after,options,work).method(replayMethod,branch);
                        context.checkpoint().requireCurrent();work.check();
                    }
                    if(!summary.inputs().equals(replay.inputs()))throw JavaValueSourceSummary.unavailable("VALUE_REPLAY_INPUTS"); //$NON-NLS-1$
                    var proofRequest=summary.request(options,work.allocate(allowance,options));
                    var verified=optimizer.verify(proofRequest,replay.plan(),work.token());work.check();
                    if(verified instanceof VerificationResult.BudgetExceeded exceeded && exceeded.work()>allowance)work.charge(exceeded.work()-allowance);
                    if(!(verified instanceof VerificationResult.Verified proof))continue;
                    var resolved=replay.plan().resolveOutputs(work::charge);
                    work.charge(resolved.work()+10L*resolved.expandedNodes()+2L*replay.plan().outputs().size());
                    var prepared=ComputationOptimizer.prepare(replay.plan());work.charge(prepared.cost().inspectionWork());
                    var realized=new OptimizationResult.Candidate(replay.plan(),prepared,proof.evidence(),proof.obligations(),candidate.cost(),
                        OptimizationResult.SearchCompletion.IMPROVEMENT_FOUND,0);
                    var explained=ComputationExplanations.describe(proofRequest,realized,work.allocate(allowance,options).maximumWork(),work.token());
                    if(explained.work()>allowance)work.charge(explained.work()-allowance);
                    if(explained.explanation().isEmpty())continue;
                    var inputNames=new LinkedHashMap<String,String>();summary.inputs().forEach(input->inputNames.put(input.id(),input.path()));
                    var outputNames=new LinkedHashMap<String,String>();for(int i=0;i<arguments.length;i++)
                        outputNames.put("component"+i,summary.value().shape().components().get(i).field().getName()); //$NON-NLS-1$
                    var explanation=new ComputationExplanation(inputNames,outputNames,new HashSet<>(inputNames.values()),explained.explanation().orElseThrow());
                    var contract="Explicit mathematical mode: BigInteger component reference identity may change; the result is a fresh wrapper. Exact bounded BigInteger guards retain the original fallback for other inputs."; //$NON-NLS-1$
                    var description="Verified immutable value components; "+contract; //$NON-NLS-1$
                    var plan=new JavaSourceRewritePlan("Source.java",JavaComputationTask.hash(source),source.length(),start,length,List.of(),List.of(),Map.of(), //$NON-NLS-1$
                        List.of(new JavaSourceRewritePlan.Change(start,length,JavaComputationTask.hash(original),replacement)));
                    work.check();documentation.add(ast,rankingRegion,contract);
                    replacements.add(new MathematicalAnalysis.Replacement(start,length,replacement,description));
                    evidence.add(new MathematicalAnalysis.VerifiedRegion(start,length,proof.evidence(),candidate.cost(),original,replacement,explanation,plan));
                } catch(IllegalArgumentException unsupported) {
                    diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_UNAVAILABLE",unsupported.getMessage(),start,length,MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
                }
            }
        } catch(IllegalArgumentException exhausted) {
            diagnostics.add(new MathematicalAnalysis.Diagnostic("VALUE_LIMIT",exhausted.getMessage(),0,source.length(),MathematicalAnalysis.Stage.SOURCE)); //$NON-NLS-1$
        } finally {JavaSourceContext.valueWork(ast,null);}
        return work.used;
    }
    private static String guard(JavaValueSourceSummary.Summary summary) {
        var guards=new LinkedHashSet<String>();
        for(var input:summary.inputs()) {
            String path=input.path();int dot=path.indexOf('.');
            if(dot>0 && !path.startsWith("this."))guards.add(path.substring(0,dot)+" != null"); //$NON-NLS-1$ //$NON-NLS-2$
            String bound=summary.assumptions().stream().filter(a->a.kind()==SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND && a.subject().equals(input.id())).findFirst().orElseThrow().parameter();
            guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class");guards.add(path+".abs().bitLength() <= "+bound); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        for(var value:summary.staticValues()) {
            String path=value.path();guards.add(path+" != null");guards.add(path+".getClass() == java.math.BigInteger.class"); //$NON-NLS-1$ //$NON-NLS-2$
            guards.add(path+".equals(new java.math.BigInteger(\""+JavaExpressions.literalValue(JavaValueSourceSummary.scalar(value.value()))+"\"))"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return String.join(" && ",guards); //$NON-NLS-1$
    }
    private static JavaValueSourceSummary.Summary bounded(JavaValueSourceSummary.Summary summary) {
        // Use a source modulus to define a narrower guarded execution domain. The generic safety ceiling is not a size prediction.
        int bits=summary.trace().occurrences().stream().filter(o->JavaExpressions.operationOf(o.expression()).orElse(null)==NumericOperation.MOD)
            .map(o->JavaExpressions.operands(o.expression()).get(1)).filter(JavaExpressions::isLiteral)
            .map(JavaExpressions::literalValue).filter(java.math.BigInteger.class::isInstance).map(java.math.BigInteger.class::cast)
            .filter(value->value.signum()>0).mapToInt(java.math.BigInteger::bitLength).min().orElse(4096);
        bits=Math.min(bits,4096);var assumptions=new HashSet<SemanticAssumption>();
        for(var assumption:summary.assumptions())assumptions.add(assumption.kind()==SemanticAssumption.Kind.BIG_INTEGER_BIT_LENGTH_BOUND
            ?new SemanticAssumption(assumption.kind(),assumption.subject(),Integer.toString(bits),"source modulus bit-length enforced by input guard"):assumption); //$NON-NLS-1$
        return new JavaValueSourceSummary.Summary(summary.value(),summary.plan(),summary.trace(),assumptions,summary.inputs(),summary.staticValues(),summary.sourceFingerprint());
    }
    private static boolean overlaps(int a,int n,int b,int m){return a<(long)b+m && b<(long)a+n;}
}
