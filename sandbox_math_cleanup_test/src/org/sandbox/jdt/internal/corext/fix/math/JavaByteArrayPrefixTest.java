/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class JavaByteArrayPrefixTest {
    @TempDir Path directory;

    private static final String UTILITY = """
            final class ByteUtilities {
                static byte[] clone(byte[] data) {
                    return null == data ? null : data.clone();
                }
                static byte[] concatenate(byte[] a, byte[] b) {
                    if (null == a) { return clone(b); }
                    if (null == b) { return clone(a); }
                    byte[] r = new byte[a.length + b.length];
                    System.arraycopy(a, 0, r, 0, a.length);
                    System.arraycopy(b, 0, r, a.length, b.length);
                    return r;
                }
                static byte[] copyOfRange(byte[] original, int from, int to) {
                    int newLength = getLength(from, to);
                    byte[] copy = new byte[newLength];
                    System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
                    return copy;
                }
                private static int getLength(int from, int to) {
                    int newLength = to - from;
                    if (newLength < 0) { throw new IllegalArgumentException(from + " > " + to); }
                    return newLength;
                }
            }
            """; //$NON-NLS-1$
    private static final String SOURCE = """
            import java.util.function.IntFunction;
            public class Prefix {
                public static byte[] run(IntFunction<byte[]> blocks, int count, int bytes) {
                    if (bytes > 64) throw new IllegalArgumentException("limit");
                    byte[] output = ByteUtilities.clone(blocks.apply(0));
                    for (int i = 1; i < count; i++) {
                        byte[] block = blocks.apply(i);
                        output = ByteUtilities.concatenate(output, block);
                    }
                    return ByteUtilities.copyOfRange(output, 0, bytes);
                }
            }
            """; //$NON-NLS-1$

    @Test void directCopiesPreserveContentsExceptionsEffectsAndExactUndo() throws Exception {
        String generated = apply(SOURCE, analyze(SOURCE, UTILITY, -1));
        assertTrue(generated.contains("java.lang.System.arraycopy"), generated); //$NON-NLS-1$
        assertFalse(generated.contains("ByteUtilities.concatenate"), generated); //$NON-NLS-1$
        assertFalse(generated.contains("ByteUtilities.clone"), generated); //$NON-NLS-1$
        try (var original = compile(SOURCE); var optimized = compile(generated)) {
            for (int size : new int[]{Integer.MIN_VALUE, -1, 0, 1, 3, 5, 8, 64, 65}) {
                for (byte[][] blocks : List.of(new byte[][]{null}, new byte[][]{{}},
                        new byte[][]{{1, 2}, {3}, {4, 5}, {6, 7}}, new byte[][]{null, {1, 2}, null, {}, {3, 4}},
                        new byte[][]{null, null, null})) {
                    for (int failAt : new int[]{-1, 0, 2}) {
                        assertEquals(run(original, blocks, size, failAt), run(optimized, blocks, size, failAt));
                    }
                }
            }
        }
        assertFalse(analyze(generated, UTILITY, -1).changed());
    }

    @Test void indexedBlocksUseSelectionAndKeepTheExactBlockProducer() throws Exception {
        String indexedSeed = """
                byte[][] parts = new byte[count][];
                parts[0] = blocks.apply(0);
                byte[] output = ByteUtilities.clone(parts[0]);"""; //$NON-NLS-1$
        String indexed = SOURCE.replace("byte[] output = ByteUtilities.clone(blocks.apply(0));", indexedSeed) //$NON-NLS-1$
                .replace("byte[] block = blocks.apply(i);", "parts[i] = blocks.apply(i);") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("concatenate(output, block)", "concatenate(output, parts[i])") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("i++) {", "i++)\n        {"); //$NON-NLS-1$ //$NON-NLS-2$
        var selected = analyze(indexed, UTILITY, indexed.indexOf("concatenate")); //$NON-NLS-1$
        String generated = apply(indexed, selected);
        assertTrue(generated.contains("parts[i] = blocks.apply(i);"), generated); //$NON-NLS-1$
        assertTrue(generated.contains("java.lang.System.arraycopy"), generated); //$NON-NLS-1$
        assertTrue(generated.contains("if (prefixLength < 0)\n                {"), generated); //$NON-NLS-1$
        assertTrue(generated.contains("if (previousLength < output.length)\n                {"), generated); //$NON-NLS-1$
        assertFalse(analyze(indexed, UTILITY, 0).changed());
    }

    @Test void rejectsObservedPrefixUnboundedOrMutableLengthAndDifferentAppendOrder() throws Exception {
        for (String source : List.of(
                SOURCE.replace("byte[] block = blocks.apply(i);", "if (output == null) break; byte[] block = blocks.apply(i);"), //$NON-NLS-1$ //$NON-NLS-2$
                SOURCE.replace("bytes > 64", "bytes > 65536"), //$NON-NLS-1$ //$NON-NLS-2$
                SOURCE.replace("byte[] block = blocks.apply(i);", "bytes--; byte[] block = blocks.apply(i);"), //$NON-NLS-1$ //$NON-NLS-2$
                SOURCE.replace("concatenate(output, block)", "concatenate(block, output)"), //$NON-NLS-1$ //$NON-NLS-2$
                SOURCE.replace("copyOfRange(output, 0, bytes)", "copyOfRange(output, 1, bytes)"), //$NON-NLS-1$ //$NON-NLS-2$
                SOURCE.replace("concatenate(output, block)", "concatenate(output, /* preserve */ block)"))) { //$NON-NLS-1$ //$NON-NLS-2$
            assertFalse(analyze(source, UTILITY, -1).changed(), source);
        }
    }

    @Test void requiresResolvedUtilityBodiesAndRejectsEffectsOrInitialization() throws Exception {
        for (String utility : List.of(
                UTILITY.replace("class ByteUtilities {", "class ByteUtilities { static { System.out.print(1); }"), //$NON-NLS-1$ //$NON-NLS-2$
                UTILITY.replace("return r;", "System.out.print(1); return r;"), //$NON-NLS-1$ //$NON-NLS-2$
                UTILITY.replace("to - from", "from - to"))) { //$NON-NLS-1$ //$NON-NLS-2$
            assertFalse(analyze(SOURCE, utility, -1).changed(), utility);
        }
    }

    @Test void generatedPlatformCallsCannotBindToAnEnclosingType() throws Exception {
        String shadowingType = """
                public class Prefix {
                    static class java {
                        static class lang {
                            static class Math {
                                static int max(int a, int b) { return 0; }
                                static int min(int a, int b) { return 0; }
                            }
                            static class System {
                                static void arraycopy(Object a, int x, Object b, int y, int count) { }
                            }
                        }
                    }
                """; //$NON-NLS-1$
        String shadowed = SOURCE.replace("public class Prefix {", shadowingType); //$NON-NLS-1$
        assertFalse(analyze(shadowed, UTILITY, -1).changed());
    }

    @Test void sourceDependencyChangesInvalidateApply() throws Exception {
        var result = analyze(SOURCE, UTILITY, -1);
        assertEquals(1, result.sourceDependencyReceipts().size());
        assertTrue(result.matches(SOURCE, result.compilerOptions()));
        Files.writeString(directory.resolve("ByteUtilities.java"), UTILITY.replace("return r;", "return null;"),
				StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertFalse(result.matches(SOURCE, result.compilerOptions()));
        assertThrows(IllegalArgumentException.class, result::newEdit);
    }

    @Test void utilityDiscoveryAndRewriteShareTheRequestBudget() throws Exception {
        String largerUtility = UTILITY + " ".repeat(6_000); //$NON-NLS-1$
        long limitedBudget = 4L * SOURCE.length() + 256;
        assertTrue(analyze(SOURCE, UTILITY, -1, limitedBudget).changed());
        assertFalse(analyze(SOURCE, largerUtility, -1, limitedBudget).changed());
        assertTrue(analyze(SOURCE, largerUtility, -1, limitedBudget + 256).changed());
    }

    private MathematicalAnalysis.Analysis analyze(String source, String utility, int selection) throws Exception {
        return analyze(source, utility, selection, 100_000);
    }

    private MathematicalAnalysis.Analysis analyze(String source, String utility, int selection, long workBudget) throws Exception {
        Files.writeString(directory.resolve("ByteUtilities.java"), utility, StandardCharsets.UTF_8); //$NON-NLS-1$
        String unit = directory.resolve("Prefix.java").toString(); //$NON-NLS-1$
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, workBudget, 2000, false, 17, List.of());
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler); //$NON-NLS-1$
        var environment = new MathematicalAnalysis.SourceEnvironment(unit, List.of(), List.of(directory.toString()),
                List.of("UTF-8"), compiler); //$NON-NLS-1$
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setCompilerOptions(compiler);
        parser.setEnvironment(new String[0], new String[]{directory.toString()}, new String[]{"UTF-8"}, true); //$NON-NLS-1$
        parser.setUnitName(unit);
        parser.setResolveBindings(true);
        parser.setSource(source.toCharArray());
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return MathematicalAnalysis.analyze(ast, source, options, null, selection, 1, environment);
    }

    private static String apply(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        return generated;
    }

    private URLClassLoader compile(String source) throws Exception {
        Path output = Files.createTempDirectory(directory, "prefix"); //$NON-NLS-1$
        Path script = output.resolve("Prefix.java"); //$NON-NLS-1$
        Path utility = output.resolve("ByteUtilities.java"); //$NON-NLS-1$
        Files.writeString(script, source, StandardCharsets.UTF_8);
        Files.writeString(utility, UTILITY, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", //$NON-NLS-1$ //$NON-NLS-2$
                "-d", output.toString(), script.toString(), utility.toString())); //$NON-NLS-1$
        return new URLClassLoader(new URL[]{output.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private record Outcome(String output, Class<?> error, String message, List<Integer> evaluations) { }

    private static Outcome run(URLClassLoader loader, byte[][] originals, int size, int failAt) throws Exception {
        var evaluations = new ArrayList<Integer>();
        byte[][] blocks = Arrays.stream(originals).map(block -> block == null ? null : block.clone()).toArray(byte[][]::new);
        IntFunction<byte[]> producer = i -> {
            evaluations.add(i);
            if (i == failAt) throw new IllegalStateException("block"); //$NON-NLS-1$
            if (i > 0 && blocks[i - 1] != null) Arrays.fill(blocks[i - 1], (byte) 99);
            return blocks[i];
        };
        var method = loader.loadClass("Prefix").getMethod("run", IntFunction.class, int.class, int.class); //$NON-NLS-1$ //$NON-NLS-2$
        try {
            return new Outcome(Arrays.toString((byte[]) method.invoke(null, producer, blocks.length, size)), null, null, evaluations);
        } catch (InvocationTargetException failure) {
            var cause = failure.getCause();
            return new Outcome(null, cause.getClass(), cause.getMessage(), evaluations);
        }
    }
}
