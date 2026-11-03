/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFixedPowerInitializationTest {
    @TempDir Path directory;
    private static final String PROPERTY="sandbox.fixedPower.initialization";

    @Test void retainsSuccessfulForeignInitializationInSourceOrder()throws Exception {verify(false);}
    @Test void retainsThrowingForeignInitializationAndSubsequentFailure()throws Exception {verify(true);}
    @Test void rejectsShadowedGuardOwnersBeforeForeignInitialization()throws Exception {verifyShadowed(false);}
    @Test void rejectsShadowedGuardOwnersBeforeForeignInitializationFailure()throws Exception {verifyShadowed(true);}

    private static String shadowedSource(boolean throwing) {
        return JavaFixedPowerOptimizerTest.source().replace("return value.raise(FIXED);","Other Calculation=null;return value.raise(FIXED);")
            +"final class Other{static{System.setProperty(\""+PROPERTY+"\",\"foreign\");"
            +(throwing?"if(System.getProperty(\""+PROPERTY+"\")!=null)throw new IllegalStateException(\"foreign\");":"")
            +"}static final Calculation ONE=Calculation.ONE;static final java.math.BigInteger P=java.math.BigInteger.ONE;}";
    }
    private void verifyShadowed(boolean throwing)throws Exception {
        String source=shadowedSource(throwing);var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
        var expected=List.of("null","returned:null","returned:null");
        assertEquals(expected,execute(source));assertEquals(expected,execute(fixture.apply(source,analysis)));
        assertTrue(analysis.evidence().stream().noneMatch(region->region.fixedPower()!=null));
    }
    @Test void rejectsLocallyReboundJdkGuardTypes()throws Exception {
        String source=JavaFixedPowerOptimizerTest.source().replace("return value.raise(FIXED);",
            "class java{static class math{static class BigInteger extends Scalar{BigInteger(String value){super(value);}}}}return value.raise(FIXED);")
            +"class Scalar extends java.math.BigInteger{Scalar(String value){super(value);}}";
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
        var expected=List.of("null","returned:null","returned:null");
        assertEquals(expected,execute(source));assertEquals(expected,execute(fixture.apply(source,analysis)));
        assertTrue(analysis.evidence().stream().noneMatch(region->region.fixedPower()!=null));
    }
    @Test void independentlyRejectsReboundGuardFieldsWithAnUpdatedEnvelopeHash()throws Exception {
        String source=shadowedSource(false).replace("Other Calculation=null;","Other harmless=null;System.identityHashCode(harmless);");
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var options=JavaValueAlgebraProofTest.options();
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,options,null,-1,0,fixture.environment());
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();var evidence=region.fixedPower();
        String generated=fixture.apply(source,analysis),changed=generated.replace("Other harmless=null;System.identityHashCode(harmless);","Other Calculation=null;System.identityHashCode(Calculation);");
        assertEquals(List.of("null","returned:null","returned:null"),execute(generated));
        assertEquals(List.of("null","returned:foreign","returned:foreign"),execute(changed));
        var after=fixture.parse(changed);var owner=after.findDeclaringNode(evidence.ownerBinding());
        var rebound=new FixedPowerEvidence(evidence.ownerBinding(),JavaComputationTask.hash(changed.substring(owner.getStartPosition(),owner.getStartPosition()+owner.getLength())),
            evidence.chains(),evidence.dependencies(),evidence.sourceWork(),evidence.scheduledWork(),evidence.localTypeHashes());
        var altered=new MathematicalAnalysis.VerifiedRegion(region.offset(),region.length(),region.proof(),region.cost(),region.original(),region.replacement(),null,region.sourcePlan(),null,null,null,null,rebound);
        assertThrows(IllegalArgumentException.class,()->JavaFixedPowerOptimizer.verifyCombined(after,changed,altered,options,new JavaValueSourceSummary.Work(options.workBudget(),options,()->false)));
    }

    private void verify(boolean throwing)throws Exception {
        String source=JavaFixedPowerOptimizerTest.source().replace("d201000000010000","10000000000000000")
            .replace("BigInteger a=amber.multiply","BigInteger ignored=FirstInit.touch(amber);BigInteger again=SecondInit.touch(amber);BigInteger a=amber.multiply")
            +"final class FirstInit{static{System.setProperty(\""+PROPERTY+"\",\"first\");}static java.math.BigInteger touch(java.math.BigInteger x){return x;}}"
            +"final class SecondInit{static{System.setProperty(\""+PROPERTY+"\",System.getProperty(\""+PROPERTY+"\")+\",second\");"
            +(throwing?"if(System.getProperty(\""+PROPERTY+"\")!=null)throw new IllegalStateException(\"initializer\");":"")
            +"}static java.math.BigInteger touch(java.math.BigInteger x){return x;}}";
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var analysis=MathematicalAnalysis.analyze(fixture.parse(source),source,JavaValueAlgebraProofTest.options(),null,-1,0,fixture.environment());
        String generated=fixture.apply(source,analysis);
        var expected=throwing?List.of("null","java.lang.ExceptionInInitializerError:first,second","java.lang.NoClassDefFoundError:first,second")
            :List.of("null","returned:first,second","returned:first,second");
        assertEquals(expected,execute(source));
        assertEquals(expected,execute(generated));
        assertTrue(analysis.evidence().stream().noneMatch(region->region.fixedPower()!=null));
        assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic->"VALUE_ALGEBRA_INITIALIZATION_OWNER".equals(diagnostic.message())));
    }

    private List<String> execute(String source)throws Exception {
        Path root=Files.createTempDirectory(directory,"initialization");Path file=root.resolve("Calculation.java");Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",root.toString(),file.toString()));
        String previous=System.getProperty(PROPERTY);System.clearProperty(PROPERTY);
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var type=loader.loadClass("Calculation");Object value=type.getMethod("of",BigInteger.class,BigInteger.class).invoke(null,BigInteger.TWO,BigInteger.valueOf(3));
            var fixed=type.getMethod("fixed",type);var results=new ArrayList<String>();results.add(String.valueOf(System.getProperty(PROPERTY)));
            for(int invocation=0;invocation<2;invocation++) {
                String outcome="returned";
                try{fixed.invoke(null,value);}catch(java.lang.reflect.InvocationTargetException failure){outcome=failure.getCause().getClass().getName();}
                results.add(outcome+":"+System.getProperty(PROPERTY));
            }
            return results;
        } finally {if(previous==null)System.clearProperty(PROPERTY);else System.setProperty(PROPERTY,previous);}
    }
}
