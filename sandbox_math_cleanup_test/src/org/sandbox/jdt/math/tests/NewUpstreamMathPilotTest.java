/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
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
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.JavaComputationExtractor;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathReviewReport;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Exploratory production-adapter runs: zero edits is reported, not disguised as a hit. */
class NewUpstreamMathPilotTest {
    private static final String PAETH = "org/apache/commons/imaging/formats/png/scanlinefilters/ScanlineFilterPaeth.java";
    private static final String IDCT = "org/jcodec/common/dct/IntDCT.java";
    @TempDir Path temporary;

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void commonsImagingPaethPreservesEveryByteTripleAndRealScanlines() throws Throwable {
        Pilot pilot = analyze("imaging", PAETH, "org.apache.commons:commons-imaging:1.0.0-alpha6", 8);
        try (var before = loader(pilot.originalClasses(), pilot.binary());
                var after = loader(pilot.generatedClasses(), pilot.binary())) {
            Class<?> oldType = before.loadClass(className(PAETH));
            Class<?> newType = after.loadClass(className(PAETH));
            MethodHandle oldPredictor = predictor(oldType), newPredictor = predictor(newType);
            long cases = 0;
            for (int a = 0; a < 256; a++) {
                for (int b = 0; b < 256; b++) {
                    for (int c = 0; c < 256; c++) {
                        int expected = (int) oldPredictor.invokeExact(a, b, c);
                        int actual = (int) newPredictor.invokeExact(a, b, c);
                        if (expected != actual) fail("Paeth mismatch at " + a + "," + b + "," + c);
                        cases++;
                    }
                }
            }
            SplittableRandom random = new SplittableRandom(0x5041455448L);
            Method oldUnfilter = oldType.getMethod("unfilter", byte[].class, byte[].class, byte[].class);
            Method newUnfilter = newType.getMethod("unfilter", byte[].class, byte[].class, byte[].class);
            int scanlines = 0;
            for (int bytesPerPixel : new int[] {1, 2, 3, 4, 6, 8}) {
                Object oldFilter = oldType.getConstructor(int.class).newInstance(bytesPerPixel);
                Object newFilter = newType.getConstructor(int.class).newInstance(bytesPerPixel);
                for (int length : new int[] {0, 1, 7, 32, 257}) {
                    for (boolean previousRow : new boolean[] {false, true}) {
                        byte[] source = new byte[length], up = previousRow ? new byte[length] : null;
                        random.nextBytes(source);
                        if (up != null) random.nextBytes(up);
                        byte[] expected = new byte[length], actual = new byte[length];
                        oldUnfilter.invoke(oldFilter, source.clone(), expected, up == null ? null : up.clone());
                        newUnfilter.invoke(newFilter, source.clone(), actual, up == null ? null : up.clone());
                        assertArrayEquals(expected, actual, "Paeth bpp=" + bytesPerPixel + " length=" + length);
                        scanlines++;
                    }
                }
            }
            finish(pilot, Map.of("predictorByteTriples", cases, "scanlines", scanlines,
                    "scope", "Complete Paeth class; exhaustive byte-domain comparison and scanline samples; no timing claim"));
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void jcodecIntDctIsAnExplicitJava8PilotNotAJava6CompatibilityClaim() throws Exception {
        // Upstream declares Java 6; never silently claim that this adapter supports that target.
        assertThrows(IllegalArgumentException.class, () -> MathCleanUpOptions.defaults(6));
        Pilot pilot = analyze("jcodec", IDCT, "org.jcodec:jcodec:0.2.3", 6);
        try (var before = loader(pilot.originalClasses(), pilot.binary());
                var after = loader(pilot.generatedClasses(), pilot.binary())) {
            Class<?> oldType = before.loadClass(className(IDCT)), newType = after.loadClass(className(IDCT));
            Object oldDecoder = oldType.getConstructor().newInstance(), newDecoder = newType.getConstructor().newInstance();
            Method oldDecode = oldType.getMethod("decode", int[].class), newDecode = newType.getMethod("decode", int[].class);
            SplittableRandom random = new SplittableRandom(0x49444354L);
            int blocks = 0;
            for (int sample = 0; sample < 512; sample++) {
                int[] input = new int[64];
                for (int i = 0; i < input.length; i++) {
                    input[i] = switch (sample % 4) {
                        case 0 -> i == 0 ? sample - 256 : 0;
                        case 1 -> random.nextInt(4096) - 2048;
                        case 2 -> random.nextInt();
                        default -> (i & 1) == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
                    };
                }
                int[] oldInput = input.clone(), newInput = input.clone();
                int[] expected = (int[]) oldDecode.invoke(oldDecoder, (Object) oldInput);
                int[] actual = (int[]) newDecode.invoke(newDecoder, (Object) newInput);
                assertArrayEquals(expected, actual, "IDCT block " + sample);
                assertArrayEquals(oldInput, newInput, "IDCT input side effects " + sample);
                blocks++;
            }
            finish(pilot, Map.of("decodedBlocks", blocks,
                    "scope", "Complete IntDCT class under an explicit Java 8 test target; not Java 6 qualification, a codec-wide test or a timing measurement"));
        }
    }

    private Pilot analyze(String id, String entry, String coordinates, int upstreamJava) throws Exception {
        Path binary = configured("sandbox.math." + id + ".binary");
        Path archive = configured("sandbox.math." + id + ".sources");
        byte[] bytes;
        try (var jar = new JarFile(archive.toFile())) {
            var sourceEntry = jar.getJarEntry(entry);
            assertNotNull(sourceEntry, entry);
            try (var input = jar.getInputStream(sourceEntry)) { bytes = input.readAllBytes(); }
        }
        String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        var values = new HashMap<>(MathCleanUpOptions.defaults(8).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BYTE,SHORT,CHAR,INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        var options = MathCleanUpOptions.parse(values, 8);
        assertFalse(options.mathematicalOptIn());
        CompilationUnit ast = parse(source, entry, binary);
        var extraction = new JavaComputationExtractor().extractRuntime(ast, source, options);
        var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(binary.toString()), List.of(), List.of());
        var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
        assertFalse(analysis.diagnostics().stream().anyMatch(d -> List.of("CANCELLED", "STALE_AST_SOURCE",
                "COMBINED_EDITS_INVALID").contains(d.code())), analysis.diagnostics().toString());
        assertEquals(analysis.replacements().size(), analysis.evidence().size());
        for (var replacement : analysis.replacements()) {
            var proof = analysis.evidence().stream().filter(e -> e.offset() == replacement.offset()
                    && e.length() == replacement.length() && e.replacement().equals(replacement.replacement()))
                    .findFirst().orElseThrow();
            assertNotNull(proof.proof());
            assertEquals(source.substring(proof.offset(), proof.offset() + proof.length()), proof.original());
            assertTrue(proof.cost().estimatedRuntimeImprovement());
        }
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        parse(generated, entry, binary);
        undo.apply(document);
        assertEquals(source, document.get(), "Exact Undo of the complete upstream class");
        Path directory = Files.createDirectories(Path.of(System.getProperty("sandbox.math.corpus.evidence"), "new-upstream", id));
        Files.writeString(directory.resolve("original.java"), source, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("generated.java"), generated, StandardCharsets.UTF_8);
        var report = new TreeMap<String, Object>();
        report.put("coordinates", coordinates);
        report.put("sourceEntry", entry);
        report.put("sourceEntrySha256", hash(bytes));
        report.put("sourceArchiveSha256", hash(Files.readAllBytes(archive)));
        report.put("binaryArchiveSha256", hash(Files.readAllBytes(binary)));
        report.put("generatedSha256", hash(generated.getBytes(StandardCharsets.UTF_8)));
        report.put("javaRuntime", System.getProperty("java.runtime.version"));
        report.put("upstreamDeclaredJava", upstreamJava);
        report.put("pilotTargetJava", 8);
        report.put("options", options.toMap());
        report.put("extractedRegions", extraction.regions().size());
        report.put("extractionDiagnostics", extraction.diagnostics());
        report.put("acceptedRegions", analysis.replacements().size());
        var counts = new TreeMap<String, Integer>();
        analysis.diagnostics().forEach(d -> counts.merge(d.code(), 1, Integer::sum));
        report.put("diagnosticCounts", counts);
        report.put("diagnostics", analysis.diagnostics());
        report.put("reviewCandidates", MathReviewReport.ranked(analysis.evidence(), options));
        report.put("evidence", analysis.evidence());
        report.put("status", "ANALYZED_NOT_YET_EXECUTION_QUALIFIED");
        writeReport(directory, report);
        System.out.printf("NEW_UPSTREAM_MATH id=%s extracted=%d accepted=%d targetJava=8 upstreamJava=%d%n",
                id, extraction.regions().size(), analysis.replacements().size(), upstreamJava);
        return new Pilot(directory, binary, compile(source, entry, binary), compile(generated, entry, binary), report);
    }

    private static void finish(Pilot pilot, Map<String, Object> execution) throws Exception {
        pilot.report().put("executionComparison", execution);
        pilot.report().put("status", "COMPLETE");
        writeReport(pilot.directory(), pilot.report());
        System.out.println("NEW_UPSTREAM_MATH_COMPLETE " + new ObjectMapper().writeValueAsString(pilot.report()));
    }
    private static void writeReport(Path directory, Map<String, Object> report) throws Exception {
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("review.json").toFile(), report);
    }
    private static MethodHandle predictor(Class<?> type) throws Exception {
        Method method = type.getDeclaredMethod("paethPredictor", int.class, int.class, int.class);
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method).bindTo(type.getConstructor(int.class).newInstance(1));
    }
    private Path compile(String source, String entry, Path binary) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "compiled-");
        Path file = directory.resolve(entry);
        Path parent = Objects.requireNonNull(file.getParent(), "Source file must have a parent directory");
        Files.createDirectories(parent);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "A complete JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "8", "-proc:none", "-cp",
                binary.toString(), "-d", directory.toString(), file.toString()), entry);
        return directory;
    }
    private static URLClassLoader loader(Path classes, Path binary) throws Exception {
        return new URLClassLoader(new URL[] {classes.toUri().toURL(), binary.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
    private static String className(String entry) { return entry.substring(0, entry.length() - 5).replace('/', '.'); }
    private static CompilationUnit parse(String source, String entry, Path binary) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName(entry); parser.setSource(source.toCharArray()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("1.8", settings);
        parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        assertFalse(Arrays.stream(ast.getProblems()).anyMatch(p -> p.isError()), Arrays.toString(ast.getProblems()));
        return ast;
    }
    private static Path configured(String name) {
        String value = System.getProperty(name); assertNotNull(value, "Run the existing Maven module: " + name);
        Path path = Path.of(value).toAbsolutePath(); assertTrue(Files.isRegularFile(path), path.toString()); return path;
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private record Pilot(Path directory, Path binary, Path originalClasses, Path generatedClasses, Map<String, Object> report) { }
}
