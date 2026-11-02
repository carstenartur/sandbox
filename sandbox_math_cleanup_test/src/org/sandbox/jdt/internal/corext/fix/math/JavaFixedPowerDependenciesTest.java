/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFixedPowerDependenciesTest {
    @TempDir Path directory;

    private static String independentCallers() {
        String first=JavaFixedPowerOptimizerTest.source();
        return first+first.replace("import java.math.BigInteger;","").replace("public final class Calculation","final class Other").replace("Calculation","Other");
    }
    private JavaImmutableValueTest fixture() {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;return fixture;
    }
    private MathematicalAnalysis.Analysis analyze(String source,int offset,int length)throws Exception {
        var fixture=fixture();return MathematicalAnalysis.analyze(fixture.parse(source),source,
            JavaValueAlgebraProofTest.options(),null,offset,length,fixture.environment());
    }
    private void replay(String source,MathematicalAnalysis.VerifiedRegion region)throws Exception {
        var options=JavaValueAlgebraProofTest.options();
        JavaFixedPowerOptimizer.verifyCombined(fixture().parse(source),source,region,options,
            new JavaValueSourceSummary.Work(options.workBudget(),options,()->false));
    }

    @Test void independentCallersApplyTogetherAndPreserveExecution()throws Exception {
        String source=independentCallers();var expected=execute(source,"Calculation","Other");
        var analysis=analyze(source,-1,0);
        assertEquals(2,analysis.evidence().stream().filter(region->region.fixedPower()!=null).count(),analysis.diagnostics().toString());
        String generated=fixture().apply(source,analysis);
        assertEquals(expected,execute(generated,"Calculation","Other"));
    }

    @Test void unrelatedSiblingBodyAndDocumentationDoNotInvalidatePowerReplay()throws Exception {
        String source=JavaFixedPowerOptimizerTest.source()+"final class Unrelated{/** Original documentation. */ static BigInteger scalar(BigInteger x){return x;}}";
        var analysis=analyze(source,-1,0);
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();
        String generated=fixture().apply(source,analysis);
        String changed=generated.replace("Original documentation.","Mathematics cleanup: independently verified scalar body.")
            .replace("scalar(BigInteger x){return x;}","scalar(BigInteger x){return x.add(BigInteger.ZERO);}");
        assertDoesNotThrow(()->replay(changed,region));
        assertEquals(execute(source,"Calculation"),execute(changed,"Calculation"));
    }

    @Test void valueShapeHelperAndProjectionSourcesRemainRequired()throws Exception {
        String declaration="static final BigInteger FIXED=new BigInteger(\"d201000000010000\",16);public static Calculation fixed(Calculation value){return value.raise(FIXED);}";
        String source=JavaFixedPowerOptimizerTest.source().replace(declaration,"").replace("private static final BigInteger P=","static final BigInteger P=")
            +"final class Caller{"+declaration+"}";
        String call="value.raise(FIXED)";var analysis=analyze(source,source.indexOf(call),call.length());
        var region=analysis.evidence().stream().filter(value->value.fixedPower()!=null).findFirst().orElseThrow();
        String generated=fixture().apply(source,analysis);assertDoesNotThrow(()->replay(generated,region));
        for(String changed:List.of(generated.replace("this.amber=a;","this.amber=a.add(BigInteger.ONE);"),
                generated.replace("return x.mod(P);","return x.add(BigInteger.ONE).mod(P);"),
                generated.replace("left(){return amber;}","left(){return blue;}"))) {
            assertNotEquals(generated,changed);
            assertEquals("VALUE_POWER_LOCAL_SOURCE_CHANGED",assertThrows(IllegalArgumentException.class,()->replay(changed,region)).getMessage());
        }
    }

    @Test void cachedTransitiveConstantSourceRemainsRequiredForEveryCaller()throws Exception {
        String original=independentCallers();
        var match=java.util.regex.Pattern.compile("private static final BigInteger P=([^;]+);").matcher(original);assertTrue(match.find());
        String source=original.replace(match.group(),"private static final BigInteger P=Modulus.P;")
            +"final class Modulus{static final BigInteger P=ConstantSource.P;}final class ConstantSource{static final BigInteger P="+match.group(1)+";}";
        var analysis=analyze(source,-1,0);var regions=analysis.evidence().stream().filter(region->region.fixedPower()!=null).toList();
        assertEquals(2,regions.size(),analysis.diagnostics().toString());String generated=fixture().apply(source,analysis);
        String changed=generated.replace("P="+match.group(1)+";","P="+match.group(1)+".add(BigInteger.ONE);");assertNotEquals(generated,changed);
        for(var region:regions)assertEquals("VALUE_POWER_LOCAL_SOURCE_CHANGED",
            assertThrows(IllegalArgumentException.class,()->replay(changed,region)).getMessage());
    }

    @Test void nestedConstantSourceDoesNotBindItsIndependentOuterCaller()throws Exception {
        String original=independentCallers();
        var match=java.util.regex.Pattern.compile("private static final BigInteger P=([^;]+);").matcher(original);assertTrue(match.find());
        String source=original.substring(0,match.start())+"private static final BigInteger P=Other.Modulus.P;"+original.substring(match.end());
        source=source.substring(0,source.length()-1)+"static final class Modulus{static final BigInteger P="+match.group(1)+";}}";
        var expected=execute(source,"Calculation","Other");var analysis=analyze(source,-1,0);
        assertEquals(2,analysis.evidence().stream().filter(region->region.fixedPower()!=null).count(),analysis.diagnostics().toString());
        assertEquals(expected,execute(fixture().apply(source,analysis),"Calculation","Other"));
    }

    private List<String> execute(String source,String... names)throws Exception {
        Path root=Files.createTempDirectory(directory,"dependencies");Path file=root.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",root.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{root.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var results=new ArrayList<String>();
            for(String name:names) {
                var type=loader.loadClass(name);var factory=type.getDeclaredMethod("of",BigInteger.class,BigInteger.class);
                var fixed=type.getDeclaredMethod("fixed",type);var parts=type.getDeclaredMethod("parts");
                factory.setAccessible(true);fixed.setAccessible(true);parts.setAccessible(true);
                for(long input:new long[]{0,2,-1}) {
                    Object value=factory.invoke(null,BigInteger.valueOf(input),BigInteger.valueOf(3)),result=fixed.invoke(null,value);
                    assertNotSame(value,result);results.add(name+Arrays.toString((Object[])parts.invoke(result)));
                }
            }
            return results;
        }
    }
}
