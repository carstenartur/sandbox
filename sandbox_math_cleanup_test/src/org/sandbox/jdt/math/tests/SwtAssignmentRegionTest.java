/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Unmodified SWT source and actual addMerge execution, without constructing native widgets. */
class SwtAssignmentRegionTest {
    private static final String ENTRY = "org/eclipse/swt/custom/StyledTextRenderer.java";
    @TempDir Path temporary;

    @Test void originalRendererExposesBothCancellationsAndPreservesStyleMerging() throws Exception {
        Path binary = Path.of(System.getProperty("sandbox.math.swt.binary"));
        Path sources = Path.of(System.getProperty("sandbox.math.swt.sources"));
        assertEquals("8f96c6e714b55ecd0868bfd42cfcd4eeb8a19c4bbee34d6bbb17cb6e593f7f19", hash(sources));
        assertEquals("0799e4475b8937e05a0f13c6cbeadbf066ca56dd3622bfd8c7a4ba133b2db2e2", hash(binary));
        String original;
        try (var jar = new JarFile(sources.toFile()); var input = jar.getInputStream(jar.getJarEntry(ENTRY))) {
            original = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName(ENTRY); parser.setSource(original.toCharArray()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("21", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BYTE, NumericKind.SHORT, NumericKind.CHAR,
                NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA, OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
                1_000_000L, 20_000, false, 21, List.of());
        var result = MathematicalAnalysis.analyze(ast, original, options, new NullProgressMonitor(), -1, 0,
                new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(binary.toString()), List.of(), List.of()));
        var cancellations = result.evidence().stream().filter(e -> e.original().trim().equals(
                "grow = (j - modifyStart) - (modifyEnd - modifyStart);")).toList();
        assertEquals(2, cancellations.size(), "Both original addMerge overloads must yield a verified cancellation");
        for (var evidence : cancellations) {
            assertFalse(evidence.proof().checkerRevision().isBlank());
            assertTrue(evidence.cost().estimatedRuntimeImprovement());
            assertEquals("grow=j-modifyEnd;", evidence.replacement().replaceAll("[()\\s]", ""));
        }
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        try (var before = new IsolatedSwt(compile(original, binary), binary);
                var after = new IsolatedSwt(compile(generated, binary), binary)) {
            for (boolean ranges : new boolean[] {false, true}) {
                for (int count : new int[] {1, 2, 4}) for (int styleMask = 0; styleMask < 16; styleMask++) {
                    assertEquals(merge(before, ranges, count, styleMask), merge(after, ranges, count, styleMask),
                            "ranges=" + ranges + ", count=" + count + ", styles=" + styleMask);
                }
            }
        }
    }

    private static List<String> merge(ClassLoader loader, boolean ranges, int count, int mask) throws Exception {
        Class<?> renderer = loader.loadClass("org.eclipse.swt.custom.StyledTextRenderer");
        Class<?> style = loader.loadClass("org.eclipse.swt.custom.StyleRange");
        var constructor = renderer.getDeclaredConstructor(loader.loadClass("org.eclipse.swt.graphics.Device"),
                loader.loadClass("org.eclipse.swt.custom.StyledText"));
        constructor.setAccessible(true); Object instance = constructor.newInstance(null, null);
        Object styles = Array.newInstance(style, 16), inserted = Array.newInstance(style, count);
        int[] existingRanges = new int[32], newRanges = new int[count * 2];
        for (int i = 0; i < 3; i++) {
            Array.set(styles, i, style(style, i * 4, 4, mask >> i & 1));
            existingRanges[i * 2] = i * 4; existingRanges[i * 2 + 1] = 4;
        }
        for (int i = 0; i < count; i++) {
            Array.set(inserted, i, style(style, 4 + i * (4 / count), 4 / count, mask >> 3 & 1));
            newRanges[i * 2] = 4 + i * (4 / count); newRanges[i * 2 + 1] = 4 / count;
        }
        field(renderer, "styles").set(instance, styles);
        field(renderer, "ranges").set(instance, existingRanges);
        field(renderer, "styleCount").setInt(instance, 3);
        var method = ranges ? renderer.getDeclaredMethod("addMerge", int[].class, inserted.getClass(), int.class, int.class, int.class)
                : renderer.getDeclaredMethod("addMerge", inserted.getClass(), int.class, int.class, int.class);
        method.setAccessible(true);
        Object growth = ranges ? method.invoke(instance, newRanges, inserted, count * 2, 2, 4)
                : method.invoke(instance, inserted, count, 1, 2);
        int size = field(renderer, "styleCount").getInt(instance);
        var result = new ArrayList<String>(); result.add(growth + ":" + size);
        Object merged = field(renderer, "styles").get(instance);
        int[] mergedRanges = (int[]) field(renderer, "ranges").get(instance);
        for (int i = 0; i < size; i++) {
            Object value = Array.get(merged, i);
            result.add((ranges ? mergedRanges[2 * i] : style.getField("start").getInt(value)) + ":"
                    + (ranges ? mergedRanges[2 * i + 1] : style.getField("length").getInt(value)) + ":"
                    + style.getField("fontStyle").getInt(value));
        }
        return result;
    }

    private static Object style(Class<?> type, int start, int length, int font) throws Exception {
        Object value = type.getConstructor().newInstance();
        type.getField("start").setInt(value, start); type.getField("length").setInt(value, length);
        type.getField("fontStyle").setInt(value, font); return value;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private Path compile(String source, Path binary) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "compiled-"); Path file = directory.resolve(ENTRY);
        Files.createDirectories(java.util.Objects.requireNonNull(file.getParent())); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "21", "-cp",
                binary.toString(), "-d", directory.toString(), file.toString()));
        return directory;
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static final class IsolatedSwt extends ClassLoader implements AutoCloseable {
        private final Path classes;
        private final JarFile dependency;
        IsolatedSwt(Path classes, Path binary) throws IOException {
            super(ClassLoader.getPlatformClassLoader()); this.classes = classes; dependency = new JarFile(binary.toFile());
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.eclipse.swt.")) throw new ClassNotFoundException(name);
            String path = name.replace('.', '/') + ".class";
            try {
                byte[] bytes;
                if (Files.isRegularFile(classes.resolve(path))) bytes = Files.readAllBytes(classes.resolve(path));
                else { var entry = dependency.getJarEntry(path); if (entry == null) throw new ClassNotFoundException(name);
                    try (var input = dependency.getInputStream(entry)) { bytes = input.readAllBytes(); } }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
        @Override public void close() throws IOException { dependency.close(); }
    }
}
