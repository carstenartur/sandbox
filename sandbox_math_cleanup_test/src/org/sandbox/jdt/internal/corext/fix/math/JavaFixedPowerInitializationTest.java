/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFixedPowerInitializationTest {
    @TempDir Path directory;
    private static final String PROPERTY="sandbox.fixedPower.initialization";

    @Test void retainsSuccessfulForeignInitializationInSourceOrder()throws Exception {verify(false);}
    @Test void retainsThrowingForeignInitializationAndSubsequentFailure()throws Exception {verify(true);}

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
        Path root=Files.createTempDirectory(directory,"initialization");Path file=root.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
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
