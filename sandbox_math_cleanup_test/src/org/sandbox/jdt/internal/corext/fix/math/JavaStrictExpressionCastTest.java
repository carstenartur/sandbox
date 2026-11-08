/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.math.tests.StrictCompilerOptions;

class JavaStrictExpressionCastTest extends JavaContextTestSupport {
    @ParameterizedTest
    @CsvSource({"4,128,warning", "5,112,warning", "7,100,warning", "40,80,warning",
            "4,128,error", "5,112,error", "7,100,error", "40,80,error"})
    void bouncyCastleConditionalArmsStillOptimizeWithStrictCastDiagnostics(int base, int threshold, String severity) throws Exception {
        String arm = base + " + (certainty - " + threshold + " + 1) / 2";
        String source = "public class Sample { public static int run(int certainty) { return certainty <= "
                + threshold + " ? " + base + " : " + arm + "; }}";
        String generated = strictChanged(source, compiler(severity));
        equivalentAt(source, generated, Integer.MIN_VALUE, -1, 0, threshold - 1, threshold,
                threshold + 1, threshold + 2, threshold + 3, Integer.MAX_VALUE);
    }

    @Test void requiredCharPromotionKeepsTheConditionalOverload() throws Exception {
        String source = "public class Sample { static int pick(char value){return 1;} static int pick(int value){return 2;}"
                + " public static int run(int n){char value=(char)n; char fallback=7;"
                + " return pick(n>0 ? value+0 : fallback); }}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -1, 0, 1, 7, 65535, 65536, Integer.MAX_VALUE);
    }

    @Test void intArithmeticStillOverflowsBeforeLongConditionalPromotion() throws Exception {
        String source = "public class Sample { public static long run(int n){return n>0 ? (n+1)+0 : 0L;}}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    }

    @ParameterizedTest @ValueSource(strings = {"byte", "short", "char"})
    void losslessNarrowPromotionRoundTripsKeepTheirConditionalType(String kind) throws Exception {
        String source = "public class Sample { static int pick(" + kind + " value){return value;} static int pick(int value){return 100000+value;}"
                + " public static int run(int n){" + kind + " value=(" + kind + ")n; " + kind + " fallback=7;"
                + " return pick(n!=0 ? (" + kind + ")((value+1)-1) : fallback); }}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -129, -1, 0, 1, 127, 128, 65535, 65536, Integer.MAX_VALUE);
    }

    @Test void lossyIntByteIntRoundTripRetainsNarrowingAndOverload() throws Exception {
        String source = "public class Sample { static int pick(byte value){return 100000+value;} static int pick(int value){return value;}"
                + " public static int run(int n){return pick(n==0 ? 0 : (int)(byte)((n+1)-1)); }}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -129, -128, -1, 0, 1, 127, 128, 255, 256, Integer.MAX_VALUE);
    }

    @Test void longReturnConversionKeepsTheOriginalIntOverflow() throws Exception {
        String source = "public class Sample { public static long run(int n){return (n+1)+0;}}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    }

    @Test void mixedWidthBitwiseOperandsKeepTheLongShiftPromotion() throws Exception {
        String source = "public class Sample { public static long run(int n){long value=(long)n<<32;"
                + " return (value & n) ^ (n & 0x100000000L);}}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -129, -1, 0, 1, 127, Integer.MAX_VALUE);
    }

    @ParameterizedTest @ValueSource(strings = {"long", "var"})
    void declarationConversionRetainsInferredTypesAndLaterOverloads(String declaration) throws Exception {
        String conversion = declaration.equals("var") ? "(long)" : "";
        String source = "public class Sample { static long pick(int value){return 99;} static long pick(long value){return value;}"
                + " public static long run(int n){" + declaration + " value=" + conversion + "((n+1)+0); return pick(value); }}";
        String generated = strictChanged(source, compiler(JavaCore.ERROR));
        equivalentAt(source, generated, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
    }

    private static Map<String, String> compiler(String severity) {
        var compiler = StrictCompilerOptions.options(17);
        compiler.put(JavaCore.COMPILER_PB_UNNECESSARY_TYPE_CHECK, severity);
        return compiler;
    }

    @ParameterizedTest @ValueSource(strings = {
            "public static int run(Integer first,Integer second,int mask){int a=first & mask;int b=second & mask;return a ^ b;}",
            "static class Holder {Integer value;} public static int run(Holder holder,int mask){int a=holder.value & mask;int b=holder.value & mask;return a ^ b;}",
            "public static int run(int[][] first,int[][] second,int i,int mask){int a=first[i][(int)(long)i] & mask;int b=second[i][i] & mask;return a ^ b;}"})
    void captureEditsRetainAuthoredWarningsAtTheirExactReadOccurrence(String method) throws Exception {
        String source = "public class Sample {" + method + "}";
        var compiler = compiler(JavaCore.WARNING); var before = parse(source, compiler);
        long warnings = java.util.Arrays.stream(before.getProblems()).filter(p -> p.isWarning()).count();
        assertTrue(warnings > 0);
        var environment = new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(), List.of(), compiler);
        var settings = new MathCleanUpOptions(true, Set.of(de.regelsuche.sdk.optimization.NumericKind.INT),
                de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA, de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,
                1_000_000, 20_000, false, 17, List.of());
        var analysis = MathematicalAnalysis.analyze(before, source, settings, null, -1, 0, environment);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().stream().allMatch(e -> e.sourcePlan() != null && !e.sourcePlan().pinned().isEmpty()));
        var edit = analysis.newEdit(); var document = new Document(source);
        var undo = edit.apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        var after = parse(document.get(), compiler);
        assertEquals(warnings, java.util.Arrays.stream(after.getProblems()).filter(p -> p.isWarning()).count());
        GeneratedSourceDiagnostics.verify(before, source, after, document.get(), GeneratedSourceDiagnostics.edits(analysis.newEdit()), null);
        assertThrows(IllegalArgumentException.class, () -> GeneratedSourceDiagnostics.verify(before, source, after, document.get(),
                List.of(new GeneratedSourceDiagnostics.Edit(0, source.length(), document.getLength())), null));
        undo.apply(document); assertEquals(source, document.get());
    }

    @ParameterizedTest @CsvSource({"Byte,byte,int,warning", "Short,short,int,warning", "Character,char,int,warning", "Integer,int,long,warning",
            "Byte,byte,int,error", "Integer,int,long,error"})
    void capturedUnboxingKeepsItsPrimitiveKindWithoutIntroducingAConversionWarning(String wrapper, String primitive, String promoted, String severity) throws Exception {
        String conversion = primitive.equals("int") ? "" : "(" + primitive + ")";
        String accessor = severity.equals(JavaCore.ERROR) ? "." + primitive + "Value()" : "";
        String source = "public class Sample { public static long run(int n){return calculate(n==0?null:" + wrapper
                + ".valueOf(" + conversion + "n)," + wrapper + ".valueOf(" + conversion + "31),n);} static " + promoted
                + " calculate(" + wrapper + " first," + wrapper + " second," + promoted + " mask){" + promoted
                + " a=(first)" + accessor + " & mask;" + promoted + " b=second" + accessor + " & mask;return a ^ b;} }";
        var compiler = compiler(JavaCore.WARNING); compiler.put(JavaCore.COMPILER_PB_AUTOBOXING, severity); var before = parse(source, compiler);
        if (severity.equals(JavaCore.ERROR)) assertClean(before);
        assertEquals(severity.equals(JavaCore.WARNING) ? 2 : 0, java.util.Arrays.stream(before.getProblems()).filter(p -> p.getID() == org.eclipse.jdt.core.compiler.IProblem.UnboxingConversion
                && p.getArguments()[1].equals(promoted)).count());
        var environment = new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(), List.of(), compiler);
        var settings = new MathCleanUpOptions(true, java.util.EnumSet.allOf(de.regelsuche.sdk.optimization.NumericKind.class),
                de.regelsuche.sdk.optimization.SafetyProfile.PRESERVE_JAVA, de.regelsuche.sdk.optimization.OptimizationGoal.READABILITY,
                1_000_000, 20_000, false, 17, List.of());
        var analysis = MathematicalAnalysis.analyze(before, source, settings, null, -1, 0, environment);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        var document = new Document(source); analysis.newEdit().apply(document);
        String generated = document.get(); assertTrue(generated.contains("." + primitive + "Value()"), generated);
        assertClean(parse(generated, compiler));
        equivalentAt(source, generated, Integer.MIN_VALUE, -129, -1, 1, 127, 128, 65535, Integer.MAX_VALUE);
    }

    private static String strictChanged(String source, Map<String, String> compiler) throws Exception {
        var before = parse(source, compiler);
        assertClean(before);
        var environment = new MathematicalAnalysis.SourceEnvironment("Sample.java", List.of(), List.of(), List.of(), compiler);
        var analysis = MathematicalAnalysis.analyze(before, source, options(), null, -1, 0, environment);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        var document = new Document(source);
        analysis.newEdit().apply(document);
        String generated = document.get();
        assertClean(parse(generated, compiler));
        return generated;
    }

    private static CompilationUnit parse(String source, Map<String, String> compiler) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setUnitName("Sample.java");
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(compiler);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private static void assertClean(CompilationUnit unit) {
        for (var problem : unit.getProblems()) assertFalse(problem.isError() || problem.isWarning(), problem.toString());
    }

    private void equivalentAt(String source, String generated, int... inputs) throws Exception {
        try (var before = compile(source); var after = compile(generated)) {
            var original = before.loadClass("Sample").getMethod("run", int.class);
            var optimized = after.loadClass("Sample").getMethod("run", int.class);
            for (int input : inputs) assertEquals(original.invoke(null, input), optimized.invoke(null, input), "input=" + input);
        }
    }

    @Override protected URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "strict-compiled");
        Path file = directory.resolve("Sample.java");
        Files.writeString(file, source);
        var diagnostics = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                "--release", "17", "-Xlint:all", "-Werror", "-d", directory.toString(), file.toString()),
                () -> diagnostics.toString(StandardCharsets.UTF_8));
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
