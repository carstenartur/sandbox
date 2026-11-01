/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class JavaFixedPowerOptimizerTest {
    @TempDir Path directory;
    static String source(){return JavaFixedPowerSourceTest.source().replace("public BigInteger[] parts()","public BigInteger left(){return amber;} public BigInteger right(){return blue;} static final BigInteger FIXED=new BigInteger(\"d201000000010000\",16);public static Calculation fixed(Calculation value){return value.raise(FIXED);} public BigInteger[] parts()");}
    @Test void ordinaryApplyEmitsVerifiedFixedPowerAndExactUndo()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
        assertFalse(analysis.matches(source.replace("d201000000010000","d201000000010001"),analysis.compilerOptions()));
        String generated=fixture.apply(source,analysis);assertTrue(generated.contains("Mathematics fixed power"),analysis.diagnostics().toString());
        assertTrue(generated.contains("<strong>Mathematics cleanup:</strong>"),"The enclosing method must document its explicit mathematical contract");
        assertTrue(generated.contains("value.raise(FIXED)"));assertEquals(execute(source),execute(generated));
        assertTrue(MathReviewReport.ranked(analysis.evidence(),JavaValueAlgebraProofTest.options()).getFirst().assessment().cautions().stream().anyMatch(reason->reason.contains("fixed-power schedule")));
        String json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(analysis);assertTrue(json.contains("fixedPower"));assertTrue(json.contains("algebraFingerprint"));assertFalse(json.contains("originalTrace"));
    }
    @Test void repeatedCleanupPreservesTheVerifiedScheduleAndOriginalFallback()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();var options=JavaValueAlgebraProofTest.options();
        var first=MathematicalAnalysis.analyze(fixture.parse(source),source,options,null,-1,0,fixture.environment());
        String generated=fixture.apply(source,first);assertTrue(generated.contains("Mathematics fixed power"));
        var repeated=MathematicalAnalysis.analyze(fixture.parse(generated),generated,options,null,-1,0,fixture.environment());
        assertTrue(repeated.evidence().stream().noneMatch(region->region.fixedPower()!=null),"Repeated cleanup must not wrap the retained fallback in another power schedule");
    }
    @Test void rejectsPrivateProjectionsEffectsPartialOperationsAndWrongModulus()throws Exception {
        for(String source:List.of(source().replace("public BigInteger left()","private BigInteger left()"),source().replace("public BigInteger right(){return blue;}","public BigInteger right(){System.out.print(blue);return blue;}"),
                source().replace("BigInteger a=amber.multiply", "BigInteger unused=amber.divide(blue);BigInteger a=amber.multiply"),
                source().replace("reduce(c.add(d))","c.add(d).mod(BigInteger.valueOf(17))"),source().replace("private final BigInteger amber, blue","private BigInteger amber, blue"))) {
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
            assertFalse(fixture.apply(source,analysis).contains("Mathematics fixed power"),source);
        }
    }
    @Test void zeroOneNegativeAndChangedExponentKeepOriginalPowerCall()throws Exception {
        for(String literal:List.of("0","1","-1")) {
            String source=source().replace("new BigInteger(\"d201000000010000\",16)","new BigInteger(\""+literal+"\")");
            var fixture=new JavaImmutableValueTest();fixture.directory=directory;
            var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
            assertFalse(fixture.apply(source,analysis).contains("Mathematics fixed power"));
        }
    }
    @Test void sharedCancellationAndExhaustionPreventPowerEmission()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();var ast=fixture.parse(source);
        var options=JavaValueAlgebraProofTest.options();var edits=new ArrayList<MathematicalAnalysis.Replacement>();var evidence=new ArrayList<MathematicalAnalysis.VerifiedRegion>();var diagnostics=new ArrayList<MathematicalAnalysis.Diagnostic>();
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            for(var work:List.of(new JavaValueSourceSummary.Work(100,options,()->false),new JavaValueSourceSummary.Work(options.workBudget(),options,()->true),new JavaValueSourceSummary.Work(options.workBudget(),options,()->checks.incrementAndGet()>1000))) {
                JavaFixedPowerOptimizer.optimize(ast,source,options,null,-1,0,Map.of(),fixture.environment(),edits,evidence,diagnostics,work);assertTrue(edits.isEmpty());
            }
            assertTrue(checks.get()>1000);
        }
    }
    @Test void independentlyReplaysGeneratedOperationsEvenWithAReboundEnvelopeHash()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();var options=JavaValueAlgebraProofTest.options();
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,options,null,-1,0,fixture.environment());
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();
        String generated=fixture.apply(source,analysis),changed=generated.replaceAll("return mathPower[0-9]+;","return mathBase;");var after=fixture.parse(changed);
        var owner=(org.eclipse.jdt.core.dom.TypeDeclaration)after.findDeclaringNode(region.fixedPower().ownerBinding());
        var tampered=new FixedPowerEvidence(region.fixedPower().ownerBinding(),JavaComputationTask.hash(changed.substring(owner.getStartPosition(),owner.getStartPosition()+owner.getLength())),region.fixedPower().chains(),region.fixedPower().dependencies(),region.fixedPower().sourceWork(),region.fixedPower().scheduledWork(),region.fixedPower().localTypeHashes());
        var rebound=new MathematicalAnalysis.VerifiedRegion(region.offset(),region.length(),region.proof(),region.cost(),region.original(),region.replacement(),null,region.sourcePlan(),null,null,null,null,tampered);
        assertThrows(IllegalArgumentException.class,()->JavaFixedPowerOptimizer.verifyCombined(after,changed,rebound,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false)));
    }
    @Test void independentlyReplaysGuardFallbackAndReceiverAtTheOriginalCallSite()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();var options=JavaValueAlgebraProofTest.options();
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,options,null,-1,0,fixture.environment());
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();
        String generated=fixture.apply(source,analysis);var evidence=region.fixedPower();var chain=evidence.chains().getFirst();
        var probes=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(String changed:List.of(generated.replace(chain.guard(),"true"),
                generated.replace(" : "+chain.fallback()," : null"),
                generated.replace(chain.helper()+"(value)",chain.helper()+"(null)"),
                generated.substring(0,generated.lastIndexOf('}'))+"public static Calculation extra(Calculation value){return "+chain.helper()+"(value);}}")) {
            probes.add(()->{
                var after=fixture.parse(changed);var owner=(org.eclipse.jdt.core.dom.TypeDeclaration)after.findDeclaringNode(evidence.ownerBinding());
                var rebound=new FixedPowerEvidence(evidence.ownerBinding(),JavaComputationTask.hash(changed.substring(owner.getStartPosition(),owner.getStartPosition()+owner.getLength())),evidence.chains(),evidence.dependencies(),evidence.sourceWork(),evidence.scheduledWork(),evidence.localTypeHashes());
                var altered=new MathematicalAnalysis.VerifiedRegion(region.offset(),region.length(),region.proof(),region.cost(),region.original(),region.replacement(),null,region.sourcePlan(),null,null,null,null,rebound);
                assertThrows(IllegalArgumentException.class,()->JavaFixedPowerOptimizer.verifyCombined(after,changed,altered,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false)));
            });
        }
        assertAll(probes);
    }
    @Test void boundaryOnlySelectionsDoNotSelectThePowerCall()throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;String source=source();String call="value.raise(FIXED)";int start=source.indexOf(call);
        for(int[] selection:new int[][]{{start-1,1},{start+call.length(),0}}) {
            var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,selection[0],selection[1],fixture.environment());
            assertTrue(analysis.evidence().stream().noneMatch(region->region.fixedPower()!=null));
        }
    }
    @Test void combinedReplayRejectsChangedSameUnitConstantSource()throws Exception {
        String original=source();var match=java.util.regex.Pattern.compile("private static final BigInteger P=([^;]+);").matcher(original);assertTrue(match.find());String initializer=match.group(1);
        String source=original.replace(match.group(),"private static final BigInteger P=Modulus.P;")+"final class Modulus{static final java.math.BigInteger P="+initializer+";}";
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var options=JavaValueAlgebraProofTest.options();
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,options,null,-1,0,fixture.environment());
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();String generated=fixture.apply(source,analysis);
        String changed=generated.replace("static final java.math.BigInteger P="+initializer+";","static final java.math.BigInteger P="+initializer+".add(java.math.BigInteger.ONE);");
        assertThrows(IllegalArgumentException.class,()->JavaFixedPowerOptimizer.verifyCombined(fixture.parse(changed),changed,region,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false)));
    }
    private List<String> execute(String source)throws Exception {
        Path root=Files.createTempDirectory(directory,"execute");Path input=root.resolve("Calculation.java");Files.writeString(input,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",root.toString(),input.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var type=loader.loadClass("Calculation");var factory=type.getMethod("of",BigInteger.class,BigInteger.class);var fixed=type.getMethod("fixed",type);var parts=type.getMethod("parts");var modulusField=type.getDeclaredField("P");modulusField.setAccessible(true);BigInteger modulus=(BigInteger)modulusField.get(null);
            var results=new ArrayList<String>();
            for(BigInteger left:List.of(BigInteger.ZERO,BigInteger.ONE,BigInteger.TWO,BigInteger.valueOf(-1),modulus.subtract(BigInteger.ONE),modulus,modulus.add(BigInteger.ONE),BigInteger.ONE.shiftLeft(5000),new BigInteger("3"){})) {
                Object value=factory.invoke(null,left,BigInteger.valueOf(3));Object result=fixed.invoke(null,value);assertNotSame(value,result);results.add(Arrays.toString((Object[])parts.invoke(result)));
            }
            try{fixed.invoke(null,factory.invoke(null,new Object[]{null,BigInteger.ONE}));fail("null leaf must throw");}catch(java.lang.reflect.InvocationTargetException failure){results.add(failure.getCause().getClass().getName());}
            try{fixed.invoke(null,new Object[]{null});fail("null must throw");}catch(java.lang.reflect.InvocationTargetException failure){results.add(failure.getCause().getClass().getName());}
            return results;
        }
    }
}
