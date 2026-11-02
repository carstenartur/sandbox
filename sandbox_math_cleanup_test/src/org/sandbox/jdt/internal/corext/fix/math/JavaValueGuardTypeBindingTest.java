/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueGuardTypeBindingTest {
    @TempDir Path directory;

    @Test void constantSpecializationCannotCallAbsThroughReboundJdkType() throws Exception {
        String source=rebound(JavaValueCallTest.fixture(),"scale(Leaf x){");
        verify(source,false,List.of("97","18","0"));
    }

    @Test void residuePropagationCannotCallAbsThroughReboundJdkType() throws Exception {
        String source=JavaValueCallTest.fixture()
            .replace("BigInteger.valueOf(101)","new BigInteger(\"4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787\")")
            .replace("public static Leaf scale(Leaf x){return x.combine(TWIST);}",
                "public static Leaf scale(BigInteger x,BigInteger y){BigInteger a=x.mod(Leaf.P);BigInteger b=y.mod(Leaf.P);return Leaf.of(a,b);}");
        verify(rebound(source,"scale(BigInteger x,BigInteger y){"),true,List.of("7","11","0"));
    }

    private static String rebound(String source,String signature) {
        return source.replace("public final class Calculation {","public final class Calculation {public static int absCalls;")
            .replace(signature,signature+"class java{static class math{static class BigInteger extends Scalar{BigInteger(String value){super(value);}}}}")
            +"class Scalar extends java.math.BigInteger{Scalar(String value){super(value);}"
            +"@Override public java.math.BigInteger abs(){Calculation.absCalls++;return super.abs();}}";
    }

    private void verify(String source,boolean residue,List<String> expected) throws Exception {
        var helper=new JavaImmutableValueTest();helper.directory=directory;
        var analysis=MathematicalAnalysis.analyze(helper.parse(source),source,JavaValueCallTest.options(),null,-1,0,helper.environment());
        String generated=helper.apply(source,analysis);
        Files.writeString(directory.resolve("original.java.txt"), source, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("generated.java.txt"), generated, StandardCharsets.UTF_8);
        String marker=residue?"Mathematics factory residue":"Mathematics constant value";
        boolean accepted=generated.contains(marker);
        var original=execute(source,residue);var actual=execute(generated,residue);
        System.out.println("VALUE_GUARD_TYPE "+(residue?"residue":"constant")+" accepted="+accepted+" original="+original+" generated="+actual+" diagnostics="+analysis.diagnostics());
        assertEquals(expected,original,"The original program must not invoke the observable subclass abs()");
        assertEquals(original,actual,"A rebound class literal must not admit subclass calls before the original fallback");
        assertFalse(accepted,"A guard that resolves java.math.BigInteger to a local subclass is not a JDK exact-type guard");
    }

    private List<String> execute(String source,boolean residue) throws Exception {
        Path output=Files.createTempDirectory(directory,"compiled");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var owner=loader.loadClass("Calculation");var leaf=loader.loadClass("Leaf");
            var scalar=loader.loadClass("Calculation$1java$math$BigInteger").getDeclaredConstructor(String.class);scalar.setAccessible(true);
            Object a=scalar.newInstance("7"),b=scalar.newInstance("11"),result;
            if(residue)result=owner.getMethod("scale",BigInteger.class,BigInteger.class).invoke(null,a,b);
            else {
                var constructor=leaf.getDeclaredConstructor(BigInteger.class,BigInteger.class);constructor.setAccessible(true);
                result=owner.getMethod("scale",leaf).invoke(null,constructor.newInstance(a,b));
            }
            var first=leaf.getMethod("first");first.setAccessible(true);var second=leaf.getMethod("second");second.setAccessible(true);
            return List.of(first.invoke(result).toString(),second.invoke(result).toString(),Integer.toString(owner.getField("absCalls").getInt(null)));
        }
    }
}
