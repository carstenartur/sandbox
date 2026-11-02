/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.LongConsumer;
import org.eclipse.jdt.core.dom.CompilationUnit;

/** Explicit deployment precondition, never inferred from source level or mutable JVM properties. */
public record ValueRuntimeEvidence(String contract, String bytecodeSha256, String auditedSourceSha256, @com.fasterxml.jackson.annotation.JsonIgnore List<de.regelsuche.sdk.optimization.VerificationEvidence> factoryProofs) {
    public ValueRuntimeEvidence {factoryProofs=List.copyOf(factoryProofs);}
    public ValueRuntimeEvidence(String contract,String bytecodeSha256,String auditedSourceSha256) {this(contract,bytecodeSha256,auditedSourceSha256,List.of());}
    ValueRuntimeEvidence withFactoryProofs(List<de.regelsuche.sdk.optimization.VerificationEvidence> proofs) {return new ValueRuntimeEvidence(contract,bytecodeSha256,auditedSourceSha256,proofs);}
    @com.fasterxml.jackson.annotation.JsonProperty("factoryProofs") public List<ValueInverseEvidence.LawReceipt> exportedFactoryProofs() {
        return factoryProofs.stream().map(ValueInverseEvidence.LawReceipt::of).toList();
    }
    static final String CONTRACT="AUDITED_OPENJDK_25_0_5_8";
    static final String BYTECODE="44c22b522dd865f70f0d14fea62ed1ebcaa472813a24447ab2cd4fe974d7d8c7";
    static final String SOURCE="bea1aec240aae5f19304cc95115ec7289ed39ae90b2ca59a7334c3ca40d23019";
    private static final List<String> CLASSES=List.of("BigInteger","BigInteger$RecursiveOp","BigInteger$RecursiveOp$RecursiveMultiply",
        "BigInteger$RecursiveOp$RecursiveSquare","BigInteger$UnsafeHolder","MutableBigInteger","SignedMutableBigInteger","BitSieve");
    static ValueRuntimeEvidence qualify(CompilationUnit ast,MathCleanUpOptions options,JavaValueSourceSummary.Work work) {
        if(options.valueRuntime()!=MathCleanUpOptions.ValueRuntime.AUDITED_OPENJDK_25_0_5_8)
            throw JavaValueSourceSummary.unavailable("VALUE_RUNTIME_UNSPECIFIED");
        var result=new ValueRuntimeEvidence(CONTRACT,BYTECODE,SOURCE);
        if(!BYTECODE.equals(fingerprint(work::charge)))throw JavaValueSourceSummary.unavailable("VALUE_RUNTIME_MISMATCH");
        JavaSourceContext.runtime(ast,result);return result;
    }
    public boolean current() {
        return CONTRACT.equals(contract) && BYTECODE.equals(bytecodeSha256) && SOURCE.equals(auditedSourceSha256)
            && BYTECODE.equals(fingerprint(ignored->{}));
    }
    void requireCurrent() {if(!current())throw JavaValueSourceSummary.unavailable("VALUE_RUNTIME_MISMATCH");}
    static String fingerprint(LongConsumer charge) {
        try {
            StringBuilder result=new StringBuilder();int total=0;
            for(String name:CLASSES)try(var in=BigInteger.class.getResourceAsStream(name+".class")) {
                if(in==null)return "unavailable";var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[4096];int read;
                while((read=in.read(buffer))!=-1) {total+=read;if(total>512_000)return "oversize";charge.accept(read);digest.update(buffer,0,read);}
                result.append(name).append(':').append(HexFormat.of().formatHex(digest.digest())).append('\n');
            }
            return JavaComputationTask.hash(result.toString());
        } catch(IOException | java.security.NoSuchAlgorithmException | SecurityException unavailable) {return "unavailable";}
    }
}
