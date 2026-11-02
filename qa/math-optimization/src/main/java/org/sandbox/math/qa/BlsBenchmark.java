/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.openjdk.jmh.annotations.*;

/** Complete G2 hashing or pairing, including serialization and every emitted guard/fallback. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms128m", "-Xmx512m"})
public class BlsBenchmark {
    static final Set<String> WORKLOADS = Set.of("G2_32", "G2_256", "PAIRING");
    private static final Set<String> COHORT = BlsSourceCohort.TYPES.stream()
            .map(type -> "org.bouncycastle.crypto.bls." + type).collect(java.util.stream.Collectors.toUnmodifiableSet());

    @State(Scope.Thread)
    public static class Inputs implements AutoCloseable {
        @Param({""}) public String fixtureDirectory;
        @Param({""}) public String fixtureManifestSha256;
        @Param({""}) public String binaryArchive;
        @Param({""}) public String sourceArchive;
        @Param({"G2_32", "G2_256", "PAIRING"}) public String workload;
        Function<byte[], byte[]> original, generated;
        private final byte[][] messages = new byte[16][];
        private final List<VariantLoader> loaders = new ArrayList<>();
        private Path compiled;
        private int cursor;

        @Setup(Level.Trial) public void setup() throws Exception {
            if (fixtureDirectory == null || fixtureDirectory.isBlank() || binaryArchive == null || binaryArchive.isBlank()
                    || sourceArchive == null || sourceArchive.isBlank() || !WORKLOADS.contains(workload))
                throw new IllegalArgumentException("Supply a complete BLS fixture, both pinned archives and a supported workload");
            requireCurrent();
            try {
                compiled = Files.createTempDirectory("bc-bls-jmh-");
                original = compile("original"); generated = compile("generated");
                var random = new Random(93802455L);
                for (int index = 0; index < messages.length; index++) {
                    messages[index] = new byte[workload.equals("G2_256") ? 256 : 32];
                    random.nextBytes(messages[index]); messages[index][0] = (byte) index;
                    byte[] before = original.apply(messages[index]), after = generated.apply(messages[index]);
                    if (!Arrays.equals(before, after) || before.length != (workload.equals("PAIRING") ? 576 : 96))
                        throw new IOException("Complete BLS outputs differ: " + workload + " sample " + index);
                }
                if (Arrays.equals(original.apply(messages[0]), original.apply(messages[1])))
                    throw new IOException("The qualified BLS workload is vacuous");
                Set<String> required = workload.equals("PAIRING")
                        ? Set.of("BLS12_381Pairing", "Fp12Element", "Fp6Element", "Fp2Element")
                        : Set.of("BLS12_381G2HashToCurve", "Fp2Element");
                for (var loader : loaders) for (String type : required)
                    if (!loader.loaded.contains("org.bouncycastle.crypto.bls." + type))
                        throw new IOException("Actual call path did not load the compiled " + type);
                requireCurrent(); cursor = 0;
            } catch (Exception | Error failure) {
                try { cleanup(); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        Set<String> loaded(String variant) { return Set.copyOf(loaders.get(variant.equals("original") ? 0 : 1).loaded); }
        private void requireCurrent() throws Exception {
            BlsSourceCohort.requireCurrent(Path.of(fixtureDirectory), Path.of(binaryArchive), Path.of(sourceArchive), fixtureManifestSha256);
        }
        @SuppressWarnings("unchecked")
        private Function<byte[], byte[]> compile(String variant) throws Exception {
            Path output = Files.createDirectory(compiled.resolve(variant));
            var arguments = new ArrayList<>(List.of("--release", "17", "-proc:none", "-cp",
                    Path.of(binaryArchive).toAbsolutePath().toString(), "-d", output.toString()));
            for (String entry : BlsSourceCohort.ENTRIES) {
                Path source = output.resolve(entry); Files.createDirectories(source.getParent());
                Files.copy(Path.of(fixtureDirectory).resolve(variant).resolve(entry), source); arguments.add(source.toString());
            }
            Path bridge = output.resolve("BlsCall.java");
            Files.writeString(bridge, """
                import java.math.BigInteger;
                import java.nio.charset.StandardCharsets;
                import java.util.function.Function;
                import org.bouncycastle.crypto.bls.*;
                import org.bouncycastle.math.ec.ECPoint;
                import org.bouncycastle.util.BigIntegers;
                public final class BlsCall implements Function<byte[], byte[]> {
                    private final boolean pairing;
                    private final BLS12_381G2HashToCurve hashing;
                    private final ECPoint[] first = new ECPoint[16];
                    private final BLS12_381G2Point[] second = new BLS12_381G2Point[16];
                    public BlsCall(String workload) {
                        pairing = workload.equals("PAIRING");
                        hashing = pairing ? null : new BLS12_381G2HashToCurve(
                                "sandbox-qualified-bls-source".getBytes(StandardCharsets.US_ASCII));
                        if (pairing) {
                            var curve = BLS12_381G1.createCurve();
                            var g1 = BLS12_381G1.getGenerator(curve);
                            var g2 = BLS12_381G2.getGenerator();
                            for (int i = 0; i < first.length; i++) {
                                first[i] = g1.multiply(BigInteger.valueOf(i + 1));
                                second[i] = g2.multiply(BigInteger.valueOf(i + 2));
                            }
                        }
                    }
                    public byte[] apply(byte[] message) {
                        if (!pairing) return BLS12_381Serialization.compressG2(hashing.hashToCurve(message));
                        int index = message.length == 0 ? 0 : message[0] & 15;
                        var result = BLS12_381Pairing.pair(first[index], second[index]);
                        byte[] encoded = new byte[12 * 48]; int offset = 0;
                        for (var outer : new Fp6Element[]{result.c0(), result.c1()})
                            for (var middle : new Fp2Element[]{outer.c0(), outer.c1(), outer.c2()})
                                for (var leaf : new BigInteger[]{middle.c0(), middle.c1()}) {
                                    byte[] value = BigIntegers.asUnsignedByteArray(48, leaf);
                                    System.arraycopy(value, 0, encoded, offset, value.length); offset += value.length;
                                }
                        return encoded;
                    }
                }
                """);
            arguments.add(bridge.toString()); var diagnostics = new ByteArrayOutputStream();
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null || compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)) != 0)
                throw new IOException("Cannot compile complete " + variant + " BLS cohort: " + diagnostics.toString(StandardCharsets.UTF_8));
            var loader = new VariantLoader(output, Path.of(binaryArchive)); loaders.add(loader);
            return (Function<byte[], byte[]>) loader.loadClass("BlsCall").getConstructor(String.class).newInstance(workload);
        }

        @TearDown(Level.Trial) @Override public void close() throws Exception {
            try { if (compiled != null) requireCurrent(); }
            finally { cleanup(); }
        }
        private void cleanup() throws IOException {
            IOException failure = null;
            for (var loader : loaders) try { loader.close(); } catch (IOException stopped) {
                if (failure == null) failure = stopped; else failure.addSuppressed(stopped);
            }
            loaders.clear();
            if (compiled != null) {
                try (var paths = Files.walk(compiled)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException stopped) {
                    if (failure == null) failure = stopped; else failure.addSuppressed(stopped);
                } finally { compiled = null; }
            }
            if (failure != null) throw failure;
        }
    }

    /** All BC dependencies use one isolated namespace and the running JVM's multi-release archive view. */
    static final class VariantLoader extends ClassLoader implements AutoCloseable {
        private final Path classes;
        private final JarFile archive;
        final Set<String> loaded = new HashSet<>();
        VariantLoader(Path classes, Path binary) throws IOException {
            super(ClassLoader.getPlatformClassLoader()); this.classes = classes;
            archive = new JarFile(binary.toFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion());
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals("BlsCall") && !name.startsWith("org.bouncycastle.")) throw new ClassNotFoundException(name);
            String entry = name.replace('.', '/') + ".class"; Path source = classes.resolve(entry);
            try {
                byte[] bytes;
                if (Files.isRegularFile(source)) {
                    bytes = Files.readAllBytes(source); if (COHORT.contains(name)) loaded.add(name);
                } else {
                    if (COHORT.contains(name) || name.equals("BlsCall")) throw new ClassNotFoundException("Missing compiled cohort class: " + name);
                    var item = archive.getJarEntry(entry); if (item == null) throw new ClassNotFoundException(name);
                    try (var input = archive.getInputStream(item)) { bytes = input.readAllBytes(); }
                }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
        @Override public void close() throws IOException { archive.close(); }
    }

    @Benchmark public byte[] original(Inputs inputs) { return inputs.original.apply(inputs.messages[inputs.cursor++ & 15]); }
    @Benchmark public byte[] generated(Inputs inputs) { return inputs.generated.apply(inputs.messages[inputs.cursor++ & 15]); }
}
