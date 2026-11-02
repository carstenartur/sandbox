/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaValueCallTest {
    @TempDir Path directory;
    static String fixture() {
        return "import java.math.BigInteger; public final class Calculation {"
            +"static final Leaf TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE);"
            +"public static Leaf scale(Leaf x){return x.combine(TWIST);}}"
            +"final class Leaf {private final BigInteger amber,blue; public static final BigInteger P=BigInteger.valueOf(101);"
            +"private Leaf(BigInteger a,BigInteger b){this.amber=a;this.blue=b;}"
            +"public static Leaf of(BigInteger a,BigInteger b){return new Leaf(a.mod(P),b.mod(P));}"
            +"public BigInteger first(){return amber;} public BigInteger second(){return blue;}"
            +"public Leaf combine(Leaf other){BigInteger a=amber.multiply(other.amber),b=blue.multiply(other.blue);"
            +"BigInteger c=amber.multiply(other.blue),d=blue.multiply(other.amber);"
            +"return new Leaf(a.subtract(b).mod(P),c.add(d).mod(P));}}";
    }
    private MathematicalAnalysis.Analysis analyze(String source) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;
        return MathematicalAnalysis.analyze(fixture.parse(source),source,options(),null,-1,0,fixture.environment());
    }
    static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true,Set.of(NumericKind.BIG_INTEGER),SafetyProfile.PRESERVE_JAVA,
            OptimizationGoal.LOWER_ESTIMATED_RUNTIME,20_000_000,20_000,false,17,List.of(),true);
    }
    private String apply(String source,MathematicalAnalysis.Analysis result) throws Exception {
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;return fixture.apply(source,result);
    }
    @Test void sourceConstantSpecializesAtOrdinaryCallAndKeepsFreshResults() throws Exception {
        String source=fixture();var result=analyze(source);String generated=apply(source,result);
        assertTrue(generated.contains("Mathematics constant value"),result.diagnostics().toString());
        assertFalse(result.evidence().isEmpty());
        String specialization=result.replacements().stream().filter(edit->edit.replacement().contains("Mathematics constant value")).findFirst().orElseThrow().replacement();
        assertFalse(specialization.substring(0,specialization.lastIndexOf(" : ")).contains(".mod("),"the source factory already normalizes its arguments");
        assertEquals(execute(source),execute(generated));
        assertTrue(result.documentation().stream().anyMatch(edit->edit.replacement().contains("BigInteger component reference identity may change")),
            "An accepted mathematical value call must document its changed reference-identity contract on the enclosing method");
    }
    @Test void repeatedCleanupAndAuthoredConditionalCallsKeepTheirExistingEvaluationEnvelope() throws Exception {
        String source=fixture();String generated=apply(source,analyze(source));
        assertTrue(generated.contains("Mathematics constant value"));
        assertTrue(analyze(generated).replacements().stream().noneMatch(edit->edit.replacement().contains("Mathematics constant value")),
            "A retained original call must not acquire another nested guarded specialization");
        String conditional=source.replace("return x.combine(TWIST);","return x == null ? null : x.combine(TWIST);");
        assertTrue(analyze(conditional).replacements().stream().noneMatch(edit->edit.replacement().contains("Mathematics constant value")));
    }
    @Test void qualifiedFieldGuardPreservesShadowingAndEarlyInitializationFailure() throws Exception {
        String source=fixture()
            .replace("static final Leaf TWIST=","static final Leaf EARLY=scale(Leaf.of(BigInteger.ONE,BigInteger.ONE),Leaf.of(BigInteger.ONE,BigInteger.ONE));static final Leaf TWIST=")
            .replace("scale(Leaf x){return x.combine(TWIST);}","scale(Leaf x,Leaf TWIST){return x.combine(Calculation.TWIST);}");
        var result=analyze(source);String generated=apply(source,result);
        assertTrue(generated.contains("Mathematics constant value"),result.diagnostics().toString());
        assertEquals("ExceptionInInitializerError/NullPointerException",staticObservation(source,false));
        assertEquals(staticObservation(source,false),staticObservation(generated,false));
    }
    @Test void expressionQualifiedStaticFieldKeepsItsOriginalEvaluation() throws Exception {
        String source=fixture().replace("return x.combine(TWIST);","return x.combine(mark().TWIST);")
            .replace("public static Leaf scale","public static int marks;static Calculation mark(){marks++;return null;}public static Leaf scale");
        var result=analyze(source);String generated=apply(source,result);
        assertEquals("1",staticObservation(source,true));assertEquals(staticObservation(source,true),staticObservation(generated,true));
        assertFalse(generated.contains("Mathematics constant value"));
        String variable=fixture().replace("return x.combine(TWIST);","Calculation holder=null;return x.combine(holder.TWIST);");
        assertFalse(apply(variable,analyze(variable)).contains("Mathematics constant value"));
    }
    @Test void selectedConstantCallUsesHalfOpenSourceIntervals() throws Exception {
        String source=fixture();var helper=new JavaImmutableValueTest();helper.directory=directory;
        int start=source.indexOf("x.combine(TWIST)"),length="x.combine(TWIST)".length();
        for(int[] selection:new int[][]{{start-1,1},{start+length,0},{start,0},{start+length-1,1}}) {
            var result=MathematicalAnalysis.analyze(helper.parse(source),source,options(),null,selection[0],selection[1],helper.environment());
            boolean overlaps=selection[0]>=start && selection[0]<start+length;
            assertEquals(overlaps,result.replacements().stream().anyMatch(edit->edit.replacement().contains("Mathematics constant value")),Arrays.toString(selection));
        }
    }
    private String staticObservation(String source,boolean invoke) throws Exception {
        Path output=Files.createTempDirectory(directory,"static-projection");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            try {
                var owner=Class.forName("Calculation",true,loader);if(!invoke)return "initialized";
                var leaf=loader.loadClass("Leaf");var constructor=leaf.getDeclaredConstructor(BigInteger.class,BigInteger.class);constructor.setAccessible(true);
                owner.getMethod("scale",leaf).invoke(null,constructor.newInstance(BigInteger.ONE,BigInteger.TWO));
                return Integer.toString(owner.getField("marks").getInt(null));
            } catch(ExceptionInInitializerError failure) {
                return failure.getClass().getSimpleName()+"/"+failure.getCause().getClass().getSimpleName();
            }
        }
    }
    @Test void completeActualFp6SpecializesNonResidueThroughLegalFactory() throws Exception {
        Path archive=Path.of(System.getProperty("sandbox.math.bc.sourceArchive")),binary=Path.of(System.getProperty("sandbox.math.bc.jar"));
        assertEquals("b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        Path root=directory.resolve("actual");Files.createDirectories(root.resolve("org/bouncycastle/crypto/bls"));
        try(var zip=new java.util.zip.ZipFile(archive.toFile())) {
            for(String name:List.of("Fp2Element","Fp6Element","BLS12_381G1")) {
                String relative="org/bouncycastle/crypto/bls/"+name+".java";Files.write(root.resolve(relative),zip.getInputStream(zip.getEntry(relative)).readAllBytes());
            }
        }
        Path file=root.resolve("org/bouncycastle/crypto/bls/Fp6Element.java");String source=Files.readString(file, StandardCharsets.UTF_8);
        var parser=org.eclipse.jdt.core.dom.ASTParser.newParser(org.eclipse.jdt.core.dom.AST.getJLSLatest());parser.setSource(source.toCharArray());parser.setUnitName(file.toString());parser.setResolveBindings(true);
        parser.setEnvironment(new String[]{binary.toString()},new String[]{root.toString()},new String[]{"UTF-8"},true);
        var compiler=new HashMap<String,String>();org.eclipse.jdt.core.JavaCore.setComplianceOptions("17",compiler);parser.setCompilerOptions(compiler);
        var ast=(org.eclipse.jdt.core.dom.CompilationUnit)parser.createAST(null);for(var problem:ast.getProblems())assertFalse(problem.isError(),problem.toString());
        var environment=new MathematicalAnalysis.SourceEnvironment(file.toString(),List.of(binary.toString()),List.of(root.toString()),List.of("UTF-8"));
        var result=MathematicalAnalysis.analyze(ast,source,options(),null,-1,0,environment);String generated=apply(source,result);
        String rotation=generated.substring(generated.indexOf("public Fp6Element mulByV()"),generated.indexOf("public Fp6Element mulFp2("));
        assertTrue(rotation.contains("Mathematics constant value"),result.diagnostics().toString());
        assertEquals(executeActual(source,binary),executeActual(generated,binary));
    }
    private List<String> executeActual(String source,Path binary) throws Exception {
        Path output=Files.createTempDirectory(directory,"actualcall");Path file=output.resolve("Fp6Element.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-cp",binary.toString(),"-d",output.toString(),file.toString()));
        try(var loader=new JavaImmutableValueTest.IsolatedBC(output,binary)) {
            var type=loader.loadClass("org.bouncycastle.crypto.bls.Fp6Element");var leaf=loader.loadClass("org.bouncycastle.crypto.bls.Fp2Element");
            var leafConstructor=leaf.getDeclaredConstructor(BigInteger.class,BigInteger.class);leafConstructor.setAccessible(true);
            var constructor=type.getDeclaredConstructor(leaf,leaf,leaf);constructor.setAccessible(true);
            var rotate=type.getMethod("mulByV");var result=new ArrayList<String>();
            BigInteger[] values={BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(-7),BigInteger.ONE.shiftLeft(5000),null,new JavaImmutableValueTest.Derived("9")};
            for(BigInteger a:values)for(BigInteger b:values) {
                Object value=leafConstructor.newInstance(a,b),input=constructor.newInstance(value,value,value);
                try {Object first=rotate.invoke(input),second=rotate.invoke(input);assertNotSame(first,input);assertNotSame(first,second);result.add(first.toString());}
                catch(java.lang.reflect.InvocationTargetException failure){result.add(failure.getCause().getClass().getName());}
            }
            Object input=constructor.newInstance(null,null,null);
            try{rotate.invoke(input);fail();}catch(java.lang.reflect.InvocationTargetException failure){result.add(failure.getCause().getClass().getName());}
            return result;
        }
    }
    @Test void effectfulGetterOrFactoryCannotBeUsedForSourceEmission() throws Exception {
        for(String source:List.of(fixture().replace("return amber;","System.out.print(amber); return amber;"),
            fixture().replace("return new Leaf(a.mod(P),b.mod(P));","System.out.print(a);return new Leaf(a.mod(P),b.mod(P));"),
            fixture().replace("private final BigInteger amber,blue;","private BigInteger amber,blue;"))) {
            String generated=apply(source,analyze(source));assertFalse(generated.contains("Mathematics constant value"),generated);
        }
    }
    @Test void freshGetterVisibilityMustAgreeWithTheAlreadyResolvedDependencyBinding() throws Exception {
        String source=fixture();int split=source.indexOf("final class Leaf");Path dependency=directory.resolve("Leaf.java");
        Files.writeString(dependency, ("import java.math.BigInteger;" + source.substring(split)),
				StandardCharsets.UTF_8);source=source.substring(0,split);
        var fixture=new JavaImmutableValueTest();fixture.directory=directory;var ast=fixture.parse(source);
        var calls=new ArrayList<org.eclipse.jdt.core.dom.MethodInvocation>();ast.accept(new org.eclipse.jdt.core.dom.ASTVisitor(){
            @Override public boolean visit(org.eclipse.jdt.core.dom.MethodInvocation call){if(call.getName().getIdentifier().equals("combine"))calls.add(call);return true;}});
        Files.writeString(dependency,
				Files.readString(dependency).replace("public BigInteger first()", "private BigInteger first()"),
				StandardCharsets.UTF_8);
        var options=options();try(var context=JavaSourceContext.open(ast,source,fixture.environment(),null,options)) {
            var work=new JavaValueSourceSummary.Work(options.workBudget(),options,()->false);var session=new JavaValueSourceSummary.Session(ast,options,work);
            assertThrows(IllegalArgumentException.class,()->JavaValueAccess.discover(ast,session,calls.getFirst().getExpression().resolveTypeBinding(),work));
        }
    }
    @Test void constantInitializerArithmeticCannotBypassTheSharedWorkBound() throws Exception {
        String source=fixture().replace("TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE)","TWIST=Leaf.of(BigInteger.TWO.pow(100000),BigInteger.ONE)");
        assertFalse(apply(source,analyze(source)).contains("Mathematics constant value"));
    }
    List<String> execute(String source) throws Exception {
        Path output=Files.createTempDirectory(directory,"valuecall");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var owner=loader.loadClass("Calculation");var type=loader.loadClass("Leaf");
            var constructor=type.getDeclaredConstructor(BigInteger.class,BigInteger.class);constructor.setAccessible(true);
            var first=type.getMethod("first");first.setAccessible(true);var second=type.getMethod("second");second.setAccessible(true);
            var method=owner.getMethod("scale",type);var result=new ArrayList<String>();
            BigInteger[] values={BigInteger.ZERO,BigInteger.ONE,BigInteger.valueOf(-7),BigInteger.valueOf(100),BigInteger.valueOf(101),
                BigInteger.ONE.shiftLeft(5000),null,new JavaImmutableValueTest.Derived("9")};
            for(BigInteger a:values)for(BigInteger b:values) {
                Object input=constructor.newInstance(a,b);
                try {Object one=method.invoke(null,input),two=method.invoke(null,input);assertNotSame(input,one);assertNotSame(one,two);
                    result.add(first.invoke(one)+":"+second.invoke(one));}
                catch(java.lang.reflect.InvocationTargetException failure){result.add(failure.getCause().getClass().getName());}
            }
            try{method.invoke(null,new Object[]{null});fail();}catch(java.lang.reflect.InvocationTargetException failure){result.add(failure.getCause().getClass().getName());}
            return result;
        }
    }
    @Test void foreignScalarHelperInitializationCannotDisappearWithConstantSpecialization() throws Exception {
        String source=fixture().replace("BigInteger a=amber.multiply", "BigInteger ignored=InitProbe.touch(amber);BigInteger a=amber.multiply")
            +"final class InitProbe {static {System.setProperty(\"value.init.probe\",\"initialized\");} static java.math.BigInteger touch(java.math.BigInteger x){return x;}}";
        String generated=apply(source,analyze(source));
        assertEquals("initialized",executeInitialization(source));assertEquals(executeInitialization(source),executeInitialization(generated));
        assertFalse(generated.contains("Mathematics constant value"));
    }
    @Test void chosenFactoryCannotIntroduceForeignHelperInitialization() throws Exception {
        String source=fixture().replace("TWIST=Leaf.of(BigInteger.ONE,BigInteger.ONE)","TWIST=Leaf.twist()")
            .replace("public static Leaf of(","public static Leaf twist(){return new Leaf(BigInteger.ONE,BigInteger.ONE);}public static Leaf of(")
            .replace("new Leaf(a.mod(P),b.mod(P))","new Leaf(InitProbe.touch(a).mod(P),b.mod(P))")
            +"final class InitProbe {static {System.setProperty(\"value.init.probe\",\"initialized\");} static java.math.BigInteger touch(java.math.BigInteger x){return x;}}";
        String generated=apply(source,analyze(source));assertNull(executeInitialization(source));
        assertEquals(executeInitialization(source),executeInitialization(generated));assertFalse(generated.contains("Mathematics constant value"));
    }
    @Test void foreignStaticValueInitializationCannotDisappearAsNumericMetadata() throws Exception {
        String source=fixture().replace("BigInteger a=amber.multiply", "Leaf ignored=ValueProbe.VALUE;BigInteger a=amber.multiply")
            +"final class ValueProbe {static {System.setProperty(\"value.init.probe\",\"initialized\");} public static final Leaf VALUE=Leaf.of(java.math.BigInteger.ONE,java.math.BigInteger.ZERO);}";
        String generated=apply(source,analyze(source));assertEquals("initialized",executeInitialization(source));
        assertEquals(executeInitialization(source),executeInitialization(generated));assertFalse(generated.contains("Mathematics constant value"));
    }
    private String executeInitialization(String source) throws Exception {
        Path output=Files.createTempDirectory(directory,"initialization");Path file=output.resolve("Calculation.java");Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString()));
        System.clearProperty("value.init.probe");
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var type=loader.loadClass("Leaf");var constructor=type.getDeclaredConstructor(BigInteger.class,BigInteger.class);constructor.setAccessible(true);
            Object value=constructor.newInstance(BigInteger.ONE,BigInteger.TWO);
            loader.loadClass("Calculation").getMethod("scale",type).invoke(null,value);return System.getProperty("value.init.probe");
        } finally {System.clearProperty("value.init.probe");}
    }

}
