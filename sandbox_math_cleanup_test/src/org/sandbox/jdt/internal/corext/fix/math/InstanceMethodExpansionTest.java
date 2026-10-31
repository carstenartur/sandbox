/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import de.regelsuche.sdk.optimization.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceMethodExpansionTest {
    @TempDir Path directory;
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private MathematicalAnalysis.SourceEnvironment environment() {
        return new MathematicalAnalysis.SourceEnvironment(directory.resolve("Calculation.java").toString(),
                List.of(), List.of(directory.toString()), List.of("UTF-8"));
    }
    private CompilationUnit parse(String source) throws Exception {
        Files.writeString(directory.resolve("Calculation.java"), source, StandardCharsets.UTF_8);
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray());
        parser.setUnitName(directory.resolve("Calculation.java").toString()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[0], new String[]{directory.toString()}, new String[]{"UTF-8"}, true);
        var compiler = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", compiler); parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit)parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
    private MathematicalAnalysis.Analysis analyze(String source) throws Exception {
        return MathematicalAnalysis.analyze(parse(source), source, options(), null, -1, 0, environment());
    }
    private String generated(String source) throws Exception {
        var result = analyze(source); var document = new Document(source); var undo = result.newEdit().apply(document);
        String generated = document.get(); undo.apply(document); assertEquals(source, document.get());
        return generated;
    }
    private static String retained(String setup, String call) {
        return "public class Calculation { public static int run(int x,int y,int[] out) { " + setup
                + " int a=" + call + "; out[0]=a; return a-x; } }";
    }
    @Test void removableImplicitAndExplicitThisHaveExactDispatch() throws Exception {
        for (String modifier : List.of("private", "final")) for (String receiver : List.of("", "this.")) {
            String source = "public class Calculation { " + modifier + " int add(int x,int y){return x+y;}"
                    + " public int run(int x,int y) {return " + receiver + "add(x,y)-x;} }";
            String output = generated(source);
            assertTrue(output.contains("return y;"), output);
        }
        String source = "public final class Calculation { int add(int x,int y){return x+y;}"
                + " public int run(int x,int y) {return add(x,y)-x;} }";
        assertTrue(generated(source).contains("return y;"));
    }
    @Test void retainedFinalClassReceiverKeepsNullCheckAndStore() throws Exception {
        Files.writeString(directory.resolve("Helper.java"),
				"public final class Helper { public int add(int x,int y){return x+y;} }", StandardCharsets.UTF_8);
        String source = "public class Calculation { public static int run(Helper h,int x,int y,int[] out) {int a=h.add(x,y); out[0]=a; return a-x;} }";
        String output = generated(source);
        assertTrue(output.contains("int a=h.add(x,y); out[0]=a; return y;"), output);
        compareExecutions(source, output, true);
    }
    @Test void retainedReferenceKeepsCreationCallCommentsAndDependencyReceipt() throws Exception {
        Files.writeString(directory.resolve("Helper.java"),
				"public class Helper { public static int add(int x,int y){return x+y;} }", StandardCharsets.UTF_8);
        String source = retained("java.util.function.IntBinaryOperator f=Helper::add; /*creation*/", "f.applyAsInt(x,y)");
        var result = analyze(source); var document = new Document(source); result.newEdit().apply(document);
        String output = document.get();
        assertTrue(output.contains("f=Helper::add; /*creation*/ int a=f.applyAsInt(x,y); out[0]=a; return y;"), output);
        assertEquals(1, result.sourceDependencies().receipts().size());
        compareExecutions(source, output, false);
        Files.writeString(directory.resolve("Helper.java"),
				"public class Helper { public static int add(int x,int y){return x-y;} }", StandardCharsets.UTF_8);
        assertFalse(result.matches(source, Map.of())); assertThrows(IllegalArgumentException.class, result::newEdit);
    }
    @Test void boundReferenceKeepsNullReceiverAndThrowingFactoryAtCreation() throws Exception {
        Files.writeString(directory.resolve("Helper.java"),
				"public final class Helper { public int add(int x,int y){return x+y;} }", StandardCharsets.UTF_8);
        for (String receiver : List.of("h", "factory(h)")) {
            String source = "public class Calculation { public static Helper factory(Helper h) {if(h==null) throw new IllegalStateException(); return h;}"
                    + " public static int run(Helper h,int x,int y,int[] out) {java.util.function.IntBinaryOperator f="
                    + receiver + "::add; int a=f.applyAsInt(x,y); out[0]=a; return a-x;} }";
            String output = generated(source);
            assertTrue(output.contains("f="+receiver+"::add; int a=f.applyAsInt(x,y); out[0]=a; return y;"), output);
            compareExecutions(source, output, true);
        }
    }
    @Test void referenceDominatesNestedBlockWithoutCrossingCallable() throws Exception {
        String helper = "static int add(int x,int y){return x+y;}";
        String source = "public class Calculation {" + helper
                + "public static int run(int x,int y,int[] out) {final java.util.function.IntBinaryOperator f=Calculation::add;"
                + " {int a=f.applyAsInt(x,y); out[0]=a; return a-x;} } }";
        assertTrue(generated(source).contains("return y;"));
        String captured = "public class Calculation {" + helper
                + "java.util.function.IntUnaryOperator make(int y,int[] out) {java.util.function.IntBinaryOperator f=Calculation::add;"
                + " return x->{int a=f.applyAsInt(x,y); out[0]=a; return a-x;};} }";
        assertTrue(generated(captured).contains("return a-x;"));
    }
    @Test void allAllowedPrimitiveSamSignaturesAreExact() throws Exception {
        for (String[] fixture : List.of(
                new String[]{"IntBinaryOperator", "int add(int x,int y){return x+y;}", "applyAsInt(x,y)", "a-x", "y"},
                new String[]{"LongBinaryOperator", "long add(long x,long y){return x+y;}", "applyAsLong(x,y)", "a-x", "y"},
                new String[]{"IntUnaryOperator", "int add(int x){return x+x;}", "applyAsInt(x)", "a-x", "x"},
                new String[]{"LongUnaryOperator", "long add(long x){return x+x;}", "applyAsLong(x)", "a-x", "x"},
                new String[]{"IntSupplier", "int add(){return 0;}", "getAsInt()", "a+x", "x"},
                new String[]{"LongSupplier", "long add(){return 0L;}", "getAsLong()", "a+x", "x"},
                new String[]{"IntToLongFunction", "long add(int x){return x;}", "applyAsLong(x)", "a-x", "0L"},
                new String[]{"LongToIntFunction", "int add(long x){return (int)x;}", "applyAsInt(x)", "a-(int)x", "0"})) {
            boolean longInput = fixture[0].startsWith("Long");
            String resultType = fixture[0].equals("IntToLongFunction") || fixture[0].startsWith("Long") && !fixture[0].equals("LongToIntFunction") ? "long" : "int";
            String source = "class Calculation {static " + fixture[1] + " static " + resultType + " run("
                    + (longInput ? "long" : "int") + " x," + (longInput ? "long" : "int") + " y," + resultType + "[] out){"
                    + "java.util.function."+fixture[0]+" f=Calculation::add; "+resultType+" a=f."+fixture[2]
                    + ";out[0]=a;return "+fixture[3]+";} }";
            String output = generated(source);
            assertFalse(output.contains("return "+fixture[3]+";"), fixture[0]+": "+output);
        }
    }
    @Test void uncertainDispatchStateAndEffectfulRemovableReceiversStayAuthored() throws Exception {
        for (String declaration : List.of("int add(int x,int y){return x+y;}", "private int add(int x,int y){return x+y+state;}",
                "private synchronized int add(int x,int y){return x+y;}", "private int add(int x,int y){return add(x,y);}")) {
            String source = "class Calculation {int state;"+declaration+" int run(int x,int y){return add(x,y)-x;} }";
            assertTrue(generated(source).contains("return add(x,y)-x;"));
        }
        String source = "class Calculation {private int add(int x,int y){return x+y;} Calculation factory(){throw new IllegalStateException();}"
                + "int run(int x,int y){return factory().add(x,y)-x;} }";
        assertTrue(generated(source).contains("return factory().add(x,y)-x;"));
    }
    @Test void referenceRejectsAliasesReassignmentCustomInterfacesAndAdaptation() throws Exception {
        for (String[] fixture : List.of(
                new String[]{"java.util.function.IntBinaryOperator", "Calculation::add", "f=f;", "int", "int"},
                new String[]{"java.util.function.IntBinaryOperator", "Calculation::add", "java.util.function.IntBinaryOperator g=f;", "int", "int"},
                new String[]{"Custom", "Calculation::add", "", "int", "int"})) {
            String name = fixture[2].contains("g=f") ? "g" : "f";
            String source = "class Calculation {interface Custom extends java.util.function.IntBinaryOperator {} static "
                    + fixture[4]+" add("+fixture[3]+" x,"+fixture[3]+" y){return x+y;} static "+fixture[4]+" run(int x,int y,"+fixture[4]+"[] out){"
                    + fixture[0]+" f="+fixture[1]+";"+fixture[2]+fixture[4]+" a="+name+"."
                    + (fixture[0].contains("Long") ? "applyAsLong" : "applyAsInt")+"(x,y);out[0]=a;return a-x;} }";
            assertTrue(generated(source).contains("return a-x;"), source);
        }
        String adapted = "class Calculation {static int add(int x,int y){return x+y;} static long run(int x,int y,long[] out){"
                + "java.util.function.IntBinaryOperator f=Calculation::add;long a=f.applyAsInt(x,y);out[0]=a;return a-x;} }";
        // The int addition wraps before widening; subtracting x in long cannot cancel it.
        assertTrue(generated(adapted).contains("return a-x;"));
        String widening = "class Calculation {static long add(long x,long y){return x+y;} static long run(int x,int y,long[] out){"
                + "java.util.function.IntBinaryOperator f=(a,b)->(int)add(a,b);int a=f.applyAsInt(x,y);out[0]=a;return a-x;} }";
        assertTrue(generated(widening).contains("return a-x;"));
    }
    @Test void referenceRejectsWideningVarargsGenericTargetsAndUnretainedCalls() throws Exception {
        for (String[] fixture : List.of(
                new String[]{"IntToLongFunction", "int add(int x){return x;}", "long", "applyAsLong(x)"},
                new String[]{"IntUnaryOperator", "int add(long x){return (int)x;}", "int", "applyAsInt(x)"},
                new String[]{"IntUnaryOperator", "int add(int... x){return x[0];}", "int", "applyAsInt(x)"},
                new String[]{"IntUnaryOperator", "<T> int add(int x){return x;}", "int", "applyAsInt(x)"})) {
            String source = "class Calculation { static "+fixture[1]+" static "+fixture[2]+" run(int x,"+fixture[2]+"[] out){"
                    + "java.util.function."+fixture[0]+" f=Calculation::add;"+fixture[2]+" a=f."+fixture[3]+";out[0]=a;return a-x;} }";
            assertTrue(generated(source).contains("return a-x;"), source);
        }
        String source = "class Calculation {static int add(int x,int y){return x+y;} static int run(int x,int y){"
                + "java.util.function.IntBinaryOperator f=Calculation::add;return f.applyAsInt(x,y)-x;} }";
        assertTrue(generated(source).contains("return f.applyAsInt(x,y)-x;"));
    }
    @Test void exactFinalMethodReferenceSelectsResolvedOverloadAndRejectsState() throws Exception {
        for (String body : List.of("return x+y;", "return x+y+state;", "return x+y+this.state;")) {
            Files.writeString(directory.resolve("Helper.java"),
					("public class Helper {int state;public final int add(int x,int y){" + body
							+ "} public final long add(long x,long y){return x-y;} }"),
					StandardCharsets.UTF_8);
            String source = "public class Calculation {public static int run(Helper h,int x,int y,int[] out){"
                    + "java.util.function.IntBinaryOperator f=h::add;int a=f.applyAsInt(x,y);out[0]=a;return a-x;} }";
            String output = generated(source);
            assertEquals(!body.contains("state"), output.contains("return y;"), output);
        }
    }
    @Test void freshSourceMustStillBeFinalForRetainedDispatch() throws Exception {
        for (boolean reference : List.of(false,true)) for (boolean finalClass : List.of(false,true)) {
            String originalHelper = finalClass ? "public final class Helper {public int add(int x,int y){return x+y;}}"
                    : "public class Helper {public final int add(int x,int y){return x+y;}}";
            Files.writeString(directory.resolve("Helper.java"), originalHelper, StandardCharsets.UTF_8);
            String source = "class Calculation {int run(Helper h,int x,int y){"
                    + (reference ? "java.util.function.IntBinaryOperator f=h::add;return f.applyAsInt(x,y)-x;" : "return h.add(x,y)-x;") + "}}";
            var unit = parse(source); var calls = new ArrayList<MethodInvocation>();
            unit.accept(new ASTVisitor(){@Override public boolean visit(MethodInvocation call){calls.add(call);return true;}});
            Files.writeString(directory.resolve("Helper.java"), originalHelper.replace("final ", ""),
					StandardCharsets.UTF_8);
            try (var context = JavaSourceContext.open(unit, source, environment(), null, options())) {
                assertNull(JavaScalarMethodBody.resolve(unit, calls.getFirst(), false, true));
            }
        }
    }
    @Test void overrideDispatchIsNotReplacedByTheBaseBody() throws Exception {
        Files.writeString(directory.resolve("Helper.java"),
				"public class Helper {public int add(int x,int y){return x+y;}} class OverrideHelper extends Helper {public int add(int x,int y){return x-y;}}",
				StandardCharsets.UTF_8);
        for (String call : List.of("h.add(x,y)", "f.applyAsInt(x,y)")) {
            String source = "public class Calculation {public static int run(Helper h,int x,int y,int[] out){"
                    + (call.startsWith("f") ? "java.util.function.IntBinaryOperator f=h::add;" : "")
                    + "int a="+call+";out[0]=a;return a-x;} }";
            assertTrue(generated(source).contains("return a-x;"));
        }
    }
    @Test void parameterizedReferenceTargetIsOutsideThePrimitiveContract() throws Exception {
        Files.writeString(directory.resolve("Helper.java"),
				"public final class Helper<T> {public int add(int x,int y){return x+y;}}", StandardCharsets.UTF_8);
        String source = "class Calculation {int run(Helper<String> h,int x,int y,int[] out){"
                + "java.util.function.IntBinaryOperator f=h::add;int a=f.applyAsInt(x,y);out[0]=a;return a-x;} }";
        assertTrue(generated(source).contains("return a-x;"));
    }
    private void compareExecutions(String original, String generated, boolean receiver) throws Exception {
        var outcomes = new ArrayList<List<String>>();
        for (String source : List.of(original, generated)) {
            Path output = Files.createTempDirectory(directory, "compiled"); Path file = output.resolve("Calculation.java"); Files.writeString(file, source, StandardCharsets.UTF_8);
            assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","17","-d",output.toString(),file.toString(),directory.resolve("Helper.java").toString()));
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class<?> type = loader.loadClass("Calculation"), helper = loader.loadClass("Helper");
                var method = receiver ? type.getMethod("run",helper,int.class,int.class,int[].class) : type.getMethod("run",int.class,int.class,int[].class);
                var values = new ArrayList<String>();
                for (int x : new int[]{0,-1,Integer.MAX_VALUE,Integer.MIN_VALUE}) for (int y : new int[]{0,1,Integer.MIN_VALUE}) {
                    for (boolean nullReceiver : receiver ? List.of(false,true) : List.of(false)) {
                        int[] out={17}; Object value;
                        try { value=method.invoke(null,receiver ? new Object[]{nullReceiver ? null : helper.getConstructor().newInstance(),x,y,out} : new Object[]{x,y,out}); }
                        catch(java.lang.reflect.InvocationTargetException failure){value=failure.getCause().getClass().getName();}
                        values.add(value+":"+out[0]);
                    }
                }
                outcomes.add(values);
            }
        }
        assertEquals(outcomes.getFirst(), outcomes.getLast());
    }
}
