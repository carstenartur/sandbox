/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaValueCallAdmissionTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(ints={2,3})
    void differentConstantAndReceiverTypesRetainTheOriginalCall(int components)throws Exception {
        String source=source(components);
        var expected=List.of(components==3?"1:2:3":"1:2","java.lang.NullPointerException");
        assertEquals(expected,execute(source));
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        var ast=fixture.parse(source);
        var analysis=assertDoesNotThrow(()->MathematicalAnalysis.analyze(ast,source,JavaValueCallTest.options(),null,-1,0,fixture.environment()));
        String generated=fixture.apply(source,analysis);
        assertFalse(generated.contains("Mathematics constant value"),generated);
        assertTrue(generated.contains("x.convert(FIXED)"),generated);
        assertEquals(expected,execute(generated));
    }

    private static String source(int components) {
        String extra=components==3?",BigInteger third":"";
        return """
            import java.math.BigInteger;
            public final class Calculation {
                public static String run(boolean missing) {
                    Leaf x=missing?null:Leaf.of(BigInteger.TWO,BigInteger.TEN);
                    return Result.calculate(x).text();
                }
            }
            final class Leaf {
                private final BigInteger amber,blue;
                private Leaf(BigInteger a,BigInteger b){this.amber=a;this.blue=b;}
                public static Leaf of(BigInteger a,BigInteger b){return new Leaf(a,b);}
                public BigInteger first(){return amber;}
                public BigInteger second(){return blue;}
                public Result convert(Result constant){return constant;}
            }
            final class Result {
                static final Result FIXED=new Result(BigInteger.ONE,BigInteger.TWO%s);
                private final BigInteger first,second%s;
                private Result(BigInteger first,BigInteger second%s){this.first=first;this.second=second;%s}
                public static Result calculate(Leaf x){return x.convert(FIXED);}
                public String text(){return first+":"+second%s;}
            }
            """.formatted(components==3?",BigInteger.valueOf(3)":"",components==3?",third":"",extra,
                components==3?"this.third=third;":"",components==3?"+\":\"+third":"");
    }

    private List<String> execute(String source)throws Exception {
        Path output=Files.createTempDirectory(directory,"cross-type");Path file=output.resolve("Calculation.java");Files.writeString(file,source);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var run=loader.loadClass("Calculation").getMethod("run",boolean.class);var results=new ArrayList<String>();
            for(boolean missing:new boolean[]{false,true}) {
                try{results.add((String)run.invoke(null,missing));}
                catch(java.lang.reflect.InvocationTargetException failure){results.add(failure.getCause().getClass().getName());}
            }
            return results;
        }
    }
}
