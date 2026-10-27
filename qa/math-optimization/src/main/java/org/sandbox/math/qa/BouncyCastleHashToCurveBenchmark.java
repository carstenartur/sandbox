/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;

/** Complete public hashToCurve call plus point encoding, using the scanner's exact ordinary Apply output. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class BouncyCastleHashToCurveBenchmark {
    private static final String TYPE = "org.bouncycastle.crypto.hash2curve.impl.SimplifiedShallueVanDeWoestijneMapToCurve";
    private static final String ENTRY = TYPE.replace('.', '/') + ".java";
    static final Set<String> PROFILES = Set.of("P256_XMD_SHA_256", "P384_XMD_SHA_384", "P521_XMD_SHA_512",
            "BLS12_381_G1_XMD_SHA_256_SSWU_RO");

    @State(Scope.Thread)
    public static class Inputs implements AutoCloseable {
        @Param({""}) public String scanDirectory;
        @Param({""}) public String binaryArchive;
        @Param({"P256_XMD_SHA_256", "P384_XMD_SHA_384", "P521_XMD_SHA_512", "BLS12_381_G1_XMD_SHA_256_SSWU_RO"})
        public String curveProfile;
        @Param({"32", "256"}) public int messageBytes;
        Function<byte[], byte[]> original, generated;
        private final List<VariantLoader> loaders = new ArrayList<>();
        private final byte[][] messages = new byte[64][];
        private Path compiled;
        private Properties manifest;
        private String manifestHash;
        private int cursor;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            if (scanDirectory == null || scanDirectory.isBlank() || binaryArchive == null || binaryArchive.isBlank()
                    || !PROFILES.contains(curveProfile) || messageBytes < 0 || messageBytes > 1_048_576)
                throw new IllegalArgumentException("Supply a completed scan, its binary archive, supported curve and bounded message size");
            Path directory = Path.of(scanDirectory);
            manifest = new Properties();
            try (var reader = Files.newBufferedReader(directory.resolve("benchmark.properties"))) { manifest.load(reader); }
            manifestHash = CorpusFiles.sha256(directory.resolve("benchmark.properties"));
            if (!"sandbox.generated-corpus-fixture/v1".equals(manifest.getProperty("schema"))
                    || !"17".equals(manifest.getProperty("targetJava"))
                    || !Files.isRegularFile(directory.resolve("COMPLETED"))
                    || manifest.getProperty("original." + ENTRY, "").equals(manifest.getProperty("generated." + ENTRY, "")))
                throw new IOException("Expected a completed Java-17 scan with a changed SWU class");
            requireCurrent();
            var report = new ObjectMapper().readTree(directory.resolve("review.json").toFile());
            for (String key : List.of("binarySha256", "sourcesSha256", "profileSha256", "targetJava")) {
                String expected = manifest.getProperty(key, "");
                if (expected.isBlank() || !expected.equals(report.path(key).asText()))
                    throw new IOException("Fixture differs from its analysis report: " + key);
            }
            int matchingFiles = 0;
            for (var file : report.path("files")) if (ENTRY.equals(file.path("path").asText())) {
                matchingFiles++;
                if (!manifest.getProperty("original." + ENTRY, "").equals(file.path("sourceSha256").asText())
                        || !manifest.getProperty("generated." + ENTRY, "").equals(file.path("afterSha256").asText())
                        || file.path("acceptedRegions").asInt() <= 0)
                    throw new IOException("Fixture source differs from the verified analysis output");
            }
            if (matchingFiles != 1) throw new IOException("Expected exactly one qualified SWU file in the report");
            try {
                compiled = Files.createTempDirectory("bc-hash-jmh-");
                original = compile("original"); generated = compile("generated");
                var random = new Random(93802455L);
                for (int i = 0; i < messages.length; i++) {
                    messages[i] = new byte[messageBytes]; random.nextBytes(messages[i]);
                    if (!Arrays.equals(original.apply(messages[i]), generated.apply(messages[i])))
                        throw new IOException("Complete Hash-to-Curve outputs differ at sample " + i);
                }
                for (var loader : loaders) if (!loader.loadedReplacement)
                    throw new IOException("The measured call path did not load the compiled SWU variant");
                requireCurrent(); cursor = 0;
            } catch (Exception | Error failure) {
                try { cleanup(); } catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        private void requireCurrent() throws IOException {
            Path directory = Path.of(scanDirectory);
            CorpusFiles.requireHash(directory.resolve("benchmark.properties"), manifestHash);
            CorpusFiles.requireHash(Path.of(binaryArchive), manifest.getProperty("binarySha256", ""));
            CorpusFiles.requireHash(directory.resolve("review.json"), manifest.getProperty("reviewSha256", ""));
            for (String variant : List.of("original", "generated"))
                CorpusFiles.requireHash(directory.resolve(variant).resolve(ENTRY), manifest.getProperty(variant + "." + ENTRY, ""));
        }

        @SuppressWarnings("unchecked")
        private Function<byte[], byte[]> compile(String variant) throws Exception {
            Path output = Files.createDirectory(compiled.resolve(variant));
            Path originalSource = Path.of(scanDirectory).resolve(variant).resolve(ENTRY);
            Path source = output.resolve(ENTRY); Files.createDirectories(source.getParent()); Files.copy(originalSource, source);
            // The same small public-API bridge is compiled for both variants; the SWU source is copied byte-exact.
            Path bridge = output.resolve("HashCall.java");
            Files.writeString(bridge, """
                import java.util.function.Function;
                import org.bouncycastle.crypto.hash2curve.HashToCurveProfile;
                import org.bouncycastle.crypto.hash2curve.HashToEllipticCurve;
                public final class HashCall implements Function<byte[], byte[]> {
                    private final HashToEllipticCurve hashing;
                    public HashCall(String profile) throws Exception {
                        hashing = HashToEllipticCurve.getInstance((HashToCurveProfile)HashToCurveProfile.class.getField(profile).get(null),
                                "sandbox-qualified-source-test");
                    }
                    public byte[] apply(byte[] message) { return hashing.hashToCurve(message).getEncoded(false); }
                }
                """);
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null || compiler.run(null, null, null, "--release", "17", "-proc:none", "-cp",
                    Path.of(binaryArchive).toAbsolutePath().toString(), "-d", output.toString(), source.toString(), bridge.toString()) != 0)
                throw new IOException("Cannot compile complete " + variant + " BC class");
            var loader = new VariantLoader(output, Path.of(binaryArchive)); loaders.add(loader);
            return (Function<byte[], byte[]>) loader.loadClass("HashCall").getConstructor(String.class).newInstance(curveProfile);
        }

        @TearDown(Level.Trial)
        @Override public void close() throws IOException {
            try { if (compiled != null) requireCurrent(); }
            finally { cleanup(); }
        }

        private void cleanup() throws IOException {
            try { for (var loader : loaders) loader.close(); }
            finally {
                loaders.clear();
                if (compiled != null) {
                    try (var paths = Files.walk(compiled)) {
                        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                    } finally { compiled = null; }
                }
            }
        }
    }

    /** Separate namespaces also avoid delegating BC calls to an original jar on the harness classpath. */
    static final class VariantLoader extends ClassLoader implements AutoCloseable {
        private final Path classes;
        private final JarFile binary;
        private boolean loadedReplacement;
        VariantLoader(Path classes, Path archive) throws IOException {
            super(ClassLoader.getPlatformClassLoader()); this.classes = classes;
            this.binary = new JarFile(archive.toFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion());
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals("HashCall") && !name.startsWith("org.bouncycastle.")) throw new ClassNotFoundException(name);
            String path = name.replace('.', '/') + ".class";
            try {
                byte[] bytes;
                if (Files.isRegularFile(classes.resolve(path))) {
                    bytes = Files.readAllBytes(classes.resolve(path));
                    if (name.equals(TYPE)) loadedReplacement = true;
                } else {
                    var entry = binary.getJarEntry(path); if (entry == null) throw new ClassNotFoundException(name);
                    try (var input = binary.getInputStream(entry)) { bytes = input.readAllBytes(); }
                }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) { throw new ClassNotFoundException(name, failure); }
        }
        @Override public void close() throws IOException { binary.close(); }
    }

    @Benchmark public byte[] original(Inputs inputs) {
        return inputs.original.apply(inputs.messages[inputs.cursor++ & 63]);
    }
    @Benchmark public byte[] generated(Inputs inputs) {
        return inputs.generated.apply(inputs.messages[inputs.cursor++ & 63]);
    }
}
