/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.sdk.optimization.*;
import de.regelsuche.sdk.optimization.power.*;

/** Full internal proof inputs, with a compact source-bound export that never expands expression DAGs. */
public record ValueInverseEvidence(UnitInverseReusePlan.Premises premises, UnitInverseReusePlan plan,
        String algebraSourceFingerprint,@JsonIgnore List<VerificationEvidence> algebraLaws,@JsonIgnore List<VerificationEvidence> inverseLaws,
        @JsonIgnore SourceEvaluationTrace.Occurrence retainedScalarOccurrence,SemanticAssumption successfulOccurrence,
        ModularUnitVerifier.Verification unitRoot,@JsonIgnore OptimizationRequest unitRootRequest,@JsonIgnore Expr unitBase,
        String unitRootSourceCommitment,String unitBaseCommitment,String retainedJavaBinding,String generatedBlockDigest,
        @JsonIgnore MathematicalAnalysis.VerifiedRegion previousRegion) {
    public ValueInverseEvidence {
        Objects.requireNonNull(premises);Objects.requireNonNull(plan);Objects.requireNonNull(algebraSourceFingerprint);
        algebraLaws=List.copyOf(algebraLaws);inverseLaws=List.copyOf(inverseLaws);
        Objects.requireNonNull(retainedScalarOccurrence);Objects.requireNonNull(successfulOccurrence);Objects.requireNonNull(unitRoot);
        Objects.requireNonNull(unitRootRequest);Objects.requireNonNull(unitBase);
        Objects.requireNonNull(unitRootSourceCommitment);Objects.requireNonNull(unitBaseCommitment);
        Objects.requireNonNull(retainedJavaBinding);Objects.requireNonNull(generatedBlockDigest);
        if(!unitRoot.verified() || !premises.equals(plan.source()) || algebraLaws.size()!=4 || inverseLaws.size()!=2
            || !unitRootRequest.sourceTrace().occurrences().contains(retainedScalarOccurrence) || !unitRootRequest.assumptions().contains(successfulOccurrence))
            throw new IllegalArgumentException("INCOMPLETE_VALUE_INVERSE_EVIDENCE");
    }
    public record LawReceipt(String checkerRevision,String sourceHash,String candidateHash,String traceHash,String assumptionsHash,List<String> methods,long work) {
        static LawReceipt of(VerificationEvidence proof){return new LawReceipt(proof.checkerRevision(),proof.sourceHash(),proof.candidateHash(),proof.traceHash(),proof.assumptionsHash(),proof.proofMethods(),proof.verificationWork());}
    }
    public record PriorReceipt(int offset,int length,String originalHash,String replacementHash,LawReceipt proof) { }
    @JsonProperty("algebraLaws") public List<LawReceipt> exportedAlgebraLaws(){return algebraLaws.stream().map(LawReceipt::of).toList();}
    @JsonProperty("inverseLaws") public List<LawReceipt> exportedInverseLaws(){return inverseLaws.stream().map(LawReceipt::of).toList();}
    @JsonProperty("retainedScalarOccurrence") public String retainedSourceId(){return retainedScalarOccurrence.sourceId();}
    @JsonProperty("previousRegion") public PriorReceipt previousReceipt(){return previousRegion==null?null:new PriorReceipt(previousRegion.offset(),previousRegion.length(),
        JavaComputationTask.hash(previousRegion.original()),JavaComputationTask.hash(previousRegion.replacement()),LawReceipt.of(previousRegion.proof()));}
    /** Linear DAG commitments are metadata, never arithmetic proof or a replacement for the SDK verifier. */
    static List<String> commitments(OptimizationRequest request,Expr base,JavaValueSourceSummary.Work work) {
        var known=new IdentityHashMap<Expr,String>();var text=new StringBuilder(request.semanticsRevision());
        text.append(new TreeMap<>(request.plan().inputs()));
        for(var output:request.plan().outputs())text.append(output.name()).append(':').append(hash(output.expression(),known,work,0));
        for(var occurrence:request.sourceTrace().occurrences())text.append(occurrence.sourceId()).append(':').append(hash(occurrence.expression(),known,work,0))
            .append(':').append(occurrence.declaredKind()).append(':').append(occurrence.evaluatedKind());
        var assumptions=request.assumptions().stream().map(Object::toString).sorted().toList();text.append(assumptions);work.charge(text.length());
        return List.of(JavaComputationTask.hash(text.toString()),hash(base,known,work,0));
    }
    private static String hash(Expr expression,IdentityHashMap<Expr,String> known,JavaValueSourceSummary.Work work,int depth) {
        work.charge(1);if(depth>256)throw JavaValueSourceSummary.unavailable("VALUE_INVERSE_COMMITMENT_DEPTH");
        var cached=known.get(expression);if(cached!=null)return cached;String text;
        if(expression instanceof VariableExpr variable)text="input:"+variable.name();
        else if(JavaExpressions.isLiteral(expression)){Object value=JavaExpressions.literalValue(expression);text="literal:"+value.getClass().getName()+":"+value;}
        else {var builder=new StringBuilder(JavaExpressions.operationOf(expression).orElseThrow().name());
            builder.append(':').append(JavaExpressions.resultKind(expression));for(var operand:JavaExpressions.operands(expression))builder.append(':').append(hash(operand,known,work,depth+1));text=builder.toString();}
        work.charge(text.length());var result=JavaComputationTask.hash(text);known.put(expression,result);return result;
    }
}
