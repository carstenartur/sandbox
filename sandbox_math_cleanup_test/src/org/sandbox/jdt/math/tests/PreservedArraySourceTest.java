/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Source reads retain their spelling and evaluation; only captured scalar values are optimized. */
class PreservedArraySourceTest {
    @TempDir Path temporary;

    @Test void computedIndexFormulasDoNotHideRuntimeMaskFactoring() throws Exception {
        String source = "public class Calculation { static final int STRIDE = 2; "
                + "public static int compute(int[] left, int[] right, int mask) { "
                + "int a = left[STRIDE * 2] & mask; int b = right[STRIDE * 3] & mask; return a ^ b; }}";
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertTrue(extraction.regions().stream().anyMatch(region -> region.arrayReads().size() == 2), extraction.toString());
        String generated = rewrite(source);
        assertTrue(generated.contains("left[STRIDE * 2]"), generated);
        assertTrue(generated.contains("right[STRIDE * 3]"), generated);
        assertEquals(1, occurrences(generated, "left[STRIDE * 2]"));
        assertEquals(1, occurrences(generated, "right[STRIDE * 3]"));
        try (var before = compile(source); var after = compile(generated)) {
            int[] left = {0, 0, 0, 0, Integer.MIN_VALUE}, right = {0, 0, 0, 0, 0, 0, -1};
            assertEquals(invoke(before, left, right, 0x55555555), invoke(after, left, right, 0x55555555));
            assertEquals(NullPointerException.class, invoke(after, null, new int[0], -1));
            assertEquals(invoke(before, null, new int[0], -1), invoke(after, null, new int[0], -1));
            assertEquals(ArrayIndexOutOfBoundsException.class, invoke(after, new int[0], null, -1));
            assertEquals(invoke(before, new int[0], null, -1), invoke(after, new int[0], null, -1));
        }
    }

    @Test void unqualifiedAndThisFieldArraysAreCapturedWithoutLosingWritesOrExceptions() throws Exception {
        String source = "public class Calculation { private int[] words; private int[] other; "
                + "private int calculate(int mask) { int a = this.words[0] & mask; "
                + "int b = other[0] & mask; return a ^ b; } "
                + "public static int compute(int[] left, int[] right, int mask) { "
                + "Calculation c = new Calculation(); c.words = left; c.other = right; return c.calculate(mask); }}";
        var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
        assertTrue(extraction.regions().stream().anyMatch(region -> region.arrayReads().size() == 2), extraction.toString());
        String generated = rewrite(source);
        assertEquals(1, occurrences(generated, "this.words[0]"));
        assertEquals(1, occurrences(generated, "other[0]"));
        assertTrue(generated.indexOf("this.words[0]") < generated.indexOf("other[0]"), generated);
        try (var before = compile(source); var after = compile(generated)) {
            for (int mask : new int[] {0, -1, Integer.MIN_VALUE, 0x55555555})
                assertEquals(invoke(before, new int[] {31}, new int[] {-7}, mask),
                        invoke(after, new int[] {31}, new int[] {-7}, mask));
            assertEquals(NullPointerException.class, invoke(after, null, new int[0], -1));
            assertEquals(invoke(before, null, new int[0], -1), invoke(after, null, new int[0], -1));
            assertEquals(ArrayIndexOutOfBoundsException.class, invoke(after, new int[0], null, -1));
            assertEquals(invoke(before, new int[0], null, -1), invoke(after, new int[0], null, -1));
        }
    }

    @Test void staticFieldArraysAndQualifiedConstantIndicesKeepTheirOriginalText() throws Exception {
        String source = "public class Calculation { static int[] words; static final int INDEX = 1; "
                + "public static int compute(int[] left, int[] right, int mask) { words = left; "
                + "int a = Calculation.words[Calculation.INDEX + 1] & mask; "
                + "int b = right[Calculation.INDEX + 2] & mask; return a ^ b; }}";
        String generated = rewrite(source);
        assertTrue(generated.contains("Calculation.words[Calculation.INDEX + 1]"), generated);
        assertTrue(generated.contains("right[Calculation.INDEX + 2]"), generated);
        assertTrue(generated.indexOf("words = left") < generated.indexOf("Calculation.words["), generated);
        try (var before = compile(source); var after = compile(generated)) {
            assertEquals(invoke(before, new int[] {0, 0, 42}, new int[] {0, 0, 0, -1}, -1),
                    invoke(after, new int[] {0, 0, 42}, new int[] {0, 0, 0, -1}, -1));
            assertEquals(invoke(before, null, new int[0], -1), invoke(after, null, new int[0], -1));
        }
    }

    @Test void volatileReceiversAndEffectfulOrMutableIndicesRemainExcluded() {
        for (String read : List.of("VOLATILE[0]", "words[mutable + 1]", "words[index++]",
                "get(words)[0]", "words[words[0]]", "words[index / mask]")) {
            String source = "class Calculation { static volatile int[] VOLATILE; static int mutable; "
                    + "static int[] get(int[] x) {return x;} int compute(int[] words, int index, int mask) { "
                    + "return (" + read + " & mask) ^ (" + read + " & mask); }}";
            var extraction = new JavaComputationExtractor().extractRuntime(MathTestSupport.parse(source), source, options());
            assertTrue(extraction.regions().stream().allMatch(region -> region.arrayReads().isEmpty()), read + ": " + extraction);
        }
    }

    @Test void actualBouncyCastleKeccakRoundConstantIsBoundedByTheExternalWriteNotItsFieldReceiver() throws Exception {
        Path binary = Path.of(Objects.requireNonNull(System.getProperty("sandbox.math.bc.mavenBinary")));
        Path sources = Path.of(Objects.requireNonNull(System.getProperty("sandbox.math.bc.mavenSources")));
        String entry = "org/bouncycastle/crypto/digests/KeccakDigest.java";
        String source;
        try (var jar = new JarFile(sources.toFile())) {
            var item = jar.getJarEntry(entry);
            assertNotNull(item, entry);
            try (var input = jar.getInputStream(item)) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        }
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray()); parser.setUnitName(entry); parser.setResolveBindings(true);
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, settings);
        parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(problem -> problem.isError()), Arrays.toString(ast.getProblems()));
        var extraction = new JavaComputationExtractor().extractRuntime(ast, source,
                MathCleanUpOptions.parse(options().toMap(), 8));
        long fieldReads = extraction.regions().stream().flatMap(region -> region.arrayReads().stream()).filter(read -> {
            var node = NodeFinder.perform(ast, read.start(), read.length());
            if (!(node instanceof ArrayAccess array)) return false;
            var receiver = array.getArray();
            IVariableBinding binding = receiver instanceof Name name && name.resolveBinding() instanceof IVariableBinding variable
                    ? variable : receiver instanceof FieldAccess field ? field.resolveFieldBinding() : null;
            return binding != null && binding.isField();
        }).count();
        System.out.println("PRESERVED_BC_KECCAK_FIELD_READS=" + fieldReads + " regions=" + extraction.regions().size());
        // Keccak's only field-array read is the round constant inside an incoming compound
        // write to a loop-carried local. That external write remains its own boundary; the
        // admitted field receiver itself must never be the reported rejection.
        String roundConstant = "a00 ^= KeccakRoundConstants[i];";
        int statement = source.indexOf(roundConstant);
        assertTrue(statement >= 0, entry);
        assertTrue(extraction.diagnostics().stream().anyMatch(diagnostic -> diagnostic.offset() == statement
                && diagnostic.length() == roundConstant.length()
                && diagnostic.code().equals("PARTIAL_OUTPUT_OR_EXTERNAL_WRITE")), extraction.diagnostics().toString());
        int read = statement + roundConstant.indexOf("KeccakRoundConstants[i]");
        assertFalse(extraction.diagnostics().stream().anyMatch(diagnostic -> diagnostic.offset() == read
                && diagnostic.code().startsWith("ARRAY_READ_")), extraction.diagnostics().toString());
    }

    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR,
                NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA, OptimizationGoal.READABILITY,
                1_000_000L, 2000, false, 17, List.of());
    }
    private static String rewrite(String source) throws Exception {
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        assertTrue(result.changed(), result.diagnostics().toString());
        Document document = new Document(source);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document); assertEquals(source, document.get());
        MathTestSupport.parse(generated);
        assertEquals(result.replacements().size(), result.evidence().size());
        return generated;
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "array-source-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler());
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", directory.toString(), file.toString()));
        return new URLClassLoader(new URL[] {directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static Object invoke(ClassLoader loader, int[] left, int[] right, int mask) throws Exception {
        try {
            return loader.loadClass("Calculation").getMethod("compute", int[].class, int[].class, int.class)
                    .invoke(null, left == null ? null : left.clone(), right == null ? null : right.clone(), mask);
        } catch (InvocationTargetException thrown) { return thrown.getCause().getClass(); }
    }
    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(fragment, at)) >= 0; at += fragment.length()) count++;
        return count;
    }
}
