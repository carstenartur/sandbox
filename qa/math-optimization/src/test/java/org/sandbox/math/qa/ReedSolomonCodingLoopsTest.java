/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Run the existing source scanner on the upstream inventory without supplying a target. */
@EnabledIfSystemProperty(named = "sandbox.math.backblaze", matches = "true")
class ReedSolomonCodingLoopsTest {
    private static final String REMOTE = "https://github.com/Backblaze/JavaReedSolomon.git";
    private static final String PIN = "d3c481dc69471e0c47ff6f67f33d53bde941675e";
    private static final String PACKAGE = "com.backblaze.erasure.";
    @TempDir Path temporary;

    @Test void scansEveryRegisteredLoopAndComparesOrdinaryApplyToTheUpstreamEncoder() throws Exception {
        URI remote = URI.create(REMOTE);
        String mirror = System.getProperty("sandbox.math.backblaze.mirror");
        if (mirror != null) {
            Path directory = Path.of(mirror).toRealPath();
            try (var git = Git.open(directory.toFile())) {
                assertEquals(REMOTE, git.getRepository().getConfig().getString("remote", "origin", "url"));
            }
            remote = directory.toUri();
        }
        try (var checkout = PinnedGitRepository.cloneAtCommit(temporary.resolve("upstream"), remote,
                "refs/heads/master", PIN)) {
            assertEquals(PIN, checkout.headCommit());
            Path source = checkout.directory().resolve("src/main/java");
            Path original = compile(source, temporary.resolve("original"), temporary.resolve("empty-overlay"));
            Path output = Files.createDirectory(Path.of(System.getProperty("sandbox.math.backblaze.output",
                    temporary.resolve("reports").toString())));
            Path binaryJar = archive(original, output.resolve("binary.jar"));
            Path sourceJar = archive(source, output.resolve("sources.jar"));
            try (var loader = loader(original)) {
                var loops = implementations(loader);
                var names = Arrays.stream(loops).map(loop -> loop.getClass().getName()).sorted().toList();
                assertEquals(12, names.stream().distinct().count());
                var codec = loader.loadClass(PACKAGE + "ReedSolomon");
                var reference = codec.getMethod("create", int.class, int.class).invoke(null, 4, 2);
                var expected = exercise(reference);
                for (var loop : loops) assertTrue(Arrays.deepEquals(expected, exercise(encoder(loader, loop))));
                for (String profile : List.of("bc-strict", "bc-mathematical")) {
                    Path scan = output.resolve(profile);
                    scan(binaryJar, sourceJar, profile, scan, names);
                    Path classes = compile(source, temporary.resolve(profile), scan.resolve("generated"));
                    try (var generated = loader(classes)) {
                        for (var loop : implementations(generated)) {
                            assertTrue(Arrays.deepEquals(expected, exercise(encoder(generated, loop))),
                                    loop.getClass().getName());
                        }
                    }
                }
                if (Boolean.getBoolean("sandbox.math.backblaze.timing")) {
                    ReedSolomonEncodingTiming.measure(source, temporary.resolve("timing-classes"), output.resolve("timing"));
                }
            }
        }
    }

    private static void scan(Path binary, Path source, String profile, Path output, List<String> names)
            throws Exception {
        var args = new ArrayList<>(List.of(binary.toString(), source.toString(),
                "qa/math-optimization/profiles/" + profile + ".properties", output.toString(),
                CorpusFiles.sha256(source), "17", "--tasks"));
        var entries = names.stream().map(name -> name.replace('.', '/') + ".java").toList();
        args.addAll(entries);
        ProjectMathScan.main(args.toArray(String[]::new));
        var report = new ObjectMapper().readTree(output.resolve("review.json").toFile());
        assertEquals("COMPLETED", report.required("status").asText());
        assertEquals(entries.size(), report.required("parsedFiles").asInt());
        assertEquals(0, report.required("parseErrorFiles").asInt());
        List<String> seen = new ArrayList<>();
        for (var row : report.required("files")) {
            assertEquals("ANALYZED", row.required("status").asText());
            seen.add(row.required("path").asText());
        }
        assertEquals(entries, seen);
    }

    private static Path compile(Path sources, Path classes, Path overlay) throws IOException {
        Files.createDirectory(classes);
        var args = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-d", classes.toString()));
        try (var stream = Files.walk(sources)) {
            for (var source : stream.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                var replacement = overlay.resolve(sources.relativize(source));
                args.add((Files.exists(replacement) ? replacement : source).toString());
            }
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        return classes;
    }

    private static Path archive(Path root, Path target) throws IOException {
        try (var jar = new JarOutputStream(Files.newOutputStream(target)); var stream = Files.walk(root)) {
            for (var path : stream.filter(Files::isRegularFile).sorted().toList()) {
                var entry = new JarEntry(root.relativize(path).toString().replace(java.io.File.separatorChar, '/'));
                entry.setTimeLocal(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
                jar.putNextEntry(entry);
                Files.copy(path, jar);
                jar.closeEntry();
            }
        }
        return target;
    }

    private static URLClassLoader loader(Path classes) throws IOException {
        return new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static Object[] implementations(URLClassLoader loader) throws ReflectiveOperationException {
        return (Object[]) loader.loadClass(PACKAGE + "CodingLoop").getField("ALL_CODING_LOOPS").get(null);
    }

    private static Object encoder(URLClassLoader loader, Object loop) throws ReflectiveOperationException {
        return loader.loadClass(PACKAGE + "ReedSolomon").getConstructor(int.class, int.class,
                loader.loadClass(PACKAGE + "CodingLoop")).newInstance(4, 2, loop);
    }

    private static byte[][] exercise(Object codec) throws ReflectiveOperationException {
        var shards = new byte[6][75];
        var random = new Random(727L);
        for (var shard : shards) random.nextBytes(shard);
        var verify = codec.getClass().getMethod("isParityCorrect", byte[][].class, int.class, int.class);
        codec.getClass().getMethod("encodeParity", byte[][].class, int.class, int.class)
                .invoke(codec, shards, 3, 67);
        assertTrue((boolean) verify.invoke(codec, shards, 3, 67));
        shards[4][19] ^= 1;
        assertFalse((boolean) verify.invoke(codec, shards, 3, 67));
        shards[4][19] ^= 1;
        return shards;
    }
}
