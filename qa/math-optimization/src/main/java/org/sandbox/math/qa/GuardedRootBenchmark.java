/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/** Times the independently compiled original and ordinary Apply output, including the emitted runtime guards. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms256m", "-Xmx768m"})
@State(Scope.Thread)
public class GuardedRootBenchmark {
    private static final String RECEIPT = "root-qualification.properties";
    private static final String COMMIT = "6560e91d0a164b8ab58266f141ee6daeec21a340";
    private static final String PACKAGE = "org.bouncycastle.crypto.bls.";
    private static final String TARGET = "BLS12_381G2HashToCurve";
    private static final List<String> VARIANTS = List.of("original", "generated");
    @Param({""}) public String exportDirectory;
    @Param({""}) public String qualificationSha256;
    @Param({"sqrtOrSqrtZ", "hashToCurve32"}) public String workload;

    private Path scratch;
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private Calls baseline;
    private Calls applied;

    @Benchmark public Object original() { return baseline.timed().get(); }
    @Benchmark public Object generated() { return applied.timed().get(); }

    @Setup(Level.Trial)
    public void setup() throws Exception {
        if (!"sqrtOrSqrtZ".equals(workload) && !"hashToCurve32".equals(workload))
            throw new IOException("Unknown guarded root workload: " + workload);
        requireCurrent(Path.of(exportDirectory), qualificationSha256);
        scratch = Files.createTempDirectory("guarded-root-jmh-");
        try {
            baseline = compile("original");
            applied = compile("generated");
            compareExactValues();
            requireCurrent(Path.of(exportDirectory), qualificationSha256);
        } catch (Exception | Error failure) {
            try { cleanup(); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @TearDown(Level.Trial)
    public void close() throws Exception {
        if (scratch == null) return;
        try {
            compareExactValues();
            requireCurrent(Path.of(exportDirectory), qualificationSha256);
        } catch (Exception | Error failure) {
            try { cleanup(); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
        cleanup();
    }

    private void compareExactValues() throws Exception {
        Object first = baseline.sample().invoke(baseline.timed(), 0);
        Object second = baseline.sample().invoke(baseline.timed(), 1);
        if (first.equals(second)) throw new IOException("The two starting samples must give distinct values");
        for (int index = 0; index < 32; index++) {
            Object original = baseline.sample().invoke(baseline.timed(), index);
            Object generated = applied.sample().invoke(applied.timed(), index);
            if (!original.equals(generated))
                throw new IOException("Exact " + workload + " outputs differ at sample " + index);
        }
    }

    private record Calls(Supplier<?> timed, Method sample) { }

    private Calls compile(String variant) throws Exception {
        Path classes = Path.of(exportDirectory).toAbsolutePath().normalize().resolve(variant).resolve("classes");
        Path output = Files.createDirectory(scratch.resolve(variant));
        Path source = Files.writeString(output.resolve("GuardedRootCall.java"), BRIDGE);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new ByteArrayOutputStream();
        if (compiler == null || compiler.run(null, diagnostics, diagnostics, "--release", "17", "-proc:none",
                "-Xlint:all", "-Werror", "-sourcepath", "", "-cp", classes.toString(), "-d", output.toString(),
                source.toString()) != 0)
            throw new IOException("Cannot compile " + variant + " benchmark bridge: " + diagnostics.toString(StandardCharsets.UTF_8));
        URL classUrl = classes.toUri().toURL();
        var loader = new URLClassLoader(new URL[]{output.toUri().toURL(), classUrl}, ClassLoader.getPlatformClassLoader());
        loaders.add(loader);
        Class<?> target = loader.loadClass(PACKAGE + TARGET);
        if (!classUrl.equals(target.getProtectionDomain().getCodeSource().getLocation()))
            throw new IOException("The benchmark did not load the qualified " + variant + " target");
        Class<?> bridge = loader.loadClass(PACKAGE + "GuardedRootCall");
        Object call = bridge.getConstructor(boolean.class).newInstance(workload.equals("sqrtOrSqrtZ"));
        return new Calls(Supplier.class.cast(call), bridge.getMethod("sample", int.class));
    }

    private void cleanup() throws IOException {
        var failures = new ArrayList<IOException>();
        for (var loader : loaders) try { loader.close(); } catch (IOException failure) { failures.add(failure); }
        loaders.clear();
        baseline = null;
        applied = null;
        try (var paths = Files.walk(scratch)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                try { Files.delete(path); } catch (IOException failure) { failures.add(failure); }
        } catch (IOException failure) {
            failures.add(failure);
        } finally {
            scratch = null;
        }
        if (!failures.isEmpty()) {
            IOException failure = failures.removeFirst();
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    static void requireCurrent(Path export, String receiptSha256) throws IOException {
        Path receipt = export.resolve(RECEIPT);
        CorpusFiles.requireHash(receipt, receiptSha256);
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(receipt)) { properties.load(reader); }
        if (!COMMIT.equals(properties.getProperty("upstreamCommit"))
                || properties.getProperty("originalSourceSha256", "").equals(properties.getProperty("generatedSourceSha256", ""))
                || properties.getProperty("originalClassesSha256", "").equals(properties.getProperty("generatedClassesSha256", "")))
            throw new IOException("Expected a changed, qualified guarded root from " + COMMIT);
        for (String variant : VARIANTS) {
            Path output = export.resolve(variant);
            CorpusFiles.requireHash(output.resolve(TARGET + ".java"), properties.getProperty(variant + "SourceSha256", ""));
            if (!classTreeSha256(output.resolve("classes")).equals(properties.getProperty(variant + "ClassesSha256")))
                throw new IOException("Qualified class files changed for " + variant);
        }
        CorpusFiles.requireHash(receipt, receiptSha256);
    }

    static String classTreeSha256(Path classes) throws IOException {
        try (var paths = Files.walk(classes)) {
            var digest = MessageDigest.getInstance("SHA-256");
            boolean found = false;
            // Hash a sorted manifest of relative paths and byte digests, with NUL and LF delimiters.
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) throw new IOException("Unexpected link in qualified classes: " + path);
                if (Files.isDirectory(path)) continue;
                if (!Files.isRegularFile(path)) throw new IOException("Unexpected file in qualified classes: " + path);
                String relative = classes.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
                digest.update((relative + '\0' + CorpusFiles.sha256(path) + '\n').getBytes(StandardCharsets.UTF_8));
                found = true;
            }
            if (!found) throw new IOException("Missing qualified classes in " + classes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply a qualified export directory and a fresh JMH JSON path");
        Path export = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        String digest = CorpusFiles.sha256(export.resolve(RECEIPT));
        requireCurrent(export, digest);
        Files.createFile(output);
        try {
            new Runner(new OptionsBuilder().include("^" + Pattern.quote(GuardedRootBenchmark.class.getName()) + "\\..*$")
                    .param("exportDirectory", export.toString()).param("qualificationSha256", digest)
                    .shouldFailOnError(true).addProfiler(GCProfiler.class)
                    .resultFormat(ResultFormatType.JSON).result(output.toString()).build()).run();
            requireCurrent(export, digest);
        } catch (Exception | Error failure) {
            try { Files.deleteIfExists(output); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    private static final String BRIDGE = """
            package org.bouncycastle.crypto.bls;
            import java.math.BigInteger;
            import java.nio.charset.StandardCharsets;
            import java.util.List;
            import java.util.Random;
            import java.util.function.Supplier;

            public final class GuardedRootCall implements Supplier<Object> {
                private record Root(boolean square, Fp2Element value) { }
                private final boolean roots;
                private final BLS12_381G2HashToCurve hashing = new BLS12_381G2HashToCurve(
                        "QUUX-V01-CS02-with-BLS12381G2_XMD:SHA-256_SSWU_RO_".getBytes(StandardCharsets.US_ASCII));
                private final Fp2Element[] fields = new Fp2Element[32];
                private final byte[][] messages = new byte[32][32];
                private int cursor;

                public GuardedRootCall(boolean roots) {
                    this.roots = roots;
                    var random = new Random(9380381L);
                    for (int i = 0; i < fields.length; i++) {
                        fields[i] = i < 3 ? Fp2Element.of(i, 0) : Fp2Element.of(
                                new BigInteger(381, random).mod(Fp2Element.P),
                                new BigInteger(381, random).mod(Fp2Element.P));
                        random.nextBytes(messages[i]);
                    }
                }

                private Object call(int index) {
                    if (!roots) return hashing.hashToCurve(messages[index]);
                    var value = new Fp2Element[1];
                    boolean square = BLS12_381G2HashToCurve.sqrtOrSqrtZ(fields[index], value);
                    return new Root(square, value[0]);
                }

                @Override public Object get() { return call(cursor++ & 31); }

                public Object sample(int index) {
                    Object result = call(index);
                    if (result instanceof Root root) return List.of(root.square(), root.value().c0(), root.value().c1());
                    var point = (BLS12_381G2Point) result;
                    if (point.isInfinity()) return List.of(true);
                    return List.of(false, point.x().c0(), point.x().c1(), point.y().c0(), point.y().c1());
                }
            }
            """;
}
