/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.FrameworkUtil;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Whole, unmodified production classes, actual Java cleanup and complete digest execution. */
class BouncyCastleRuntimeCorpusTest {
    private static final String PACKAGE = "org/bouncycastle/crypto/digests/";
    private static final List<String> FILES = List.of("SHA1Digest", "SHA256Digest", "LongDigest", "MD4Digest", "MD5Digest");
    private static final Map<String, Integer> CHANGES = Map.of("SHA1Digest", 2, "SHA256Digest", 1, "LongDigest", 2, "MD4Digest", 2, "MD5Digest", 2);
    @TempDir Path temporary;

    @Test void realProductionClassesAreOptimizedCompiledAndRunWithoutChangingConstants() throws Exception {
        Map<String, String> sources = productionSources();
        Path dependency = dependency();
        Path before = temporary.resolve("before"), after = temporary.resolve("after");
        int changed = 0;
        for (String name : FILES) {
            String source = sources.get(name);
            var analysis = analyze(name, source, dependency);
            assertEquals(CHANGES.get(name).intValue(), analysis.replacements().size(), name + ": " + analysis.diagnostics());
            assertEquals(analysis.replacements().size(), analysis.evidence().size());
            Document document = new Document(source);
            var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
            String generated = document.get(); undo.apply(document);
            assertEquals(source, document.get(), "byte-exact Undo");
            assertEquals(constants(name, source, dependency), constants(name, generated, dependency), "Authored constants must be preserved");
            assertFalse(generated.contains("de.regelsuche"));
            for (var evidence : analysis.evidence()) {
                assertTrue(evidence.cost().estimatedRuntimeImprovement());
                assertEquals(0, evidence.cost().checkWork());
                assertEquals(0, evidence.cost().fallbackOperations());
                assertFalse(evidence.replacement().contains("if"));
                assertFalse(evidence.replacement().contains("?"));
                assertFalse(evidence.replacement().contains("["));
            }
            assertFalse(analyze(name, generated, dependency).changed(), "Second cleanup must be stable");
            write(before.resolve(PACKAGE + name + ".java"), source);
            write(after.resolve(PACKAGE + name + ".java"), generated);
            changed += analysis.replacements().size();
        }
        assertEquals(9, changed);
        Path beforeClasses = compile(before, dependency), afterClasses = compile(after, dependency);
        try (var original = new IsolatedBC(beforeClasses, dependency); var replacement = new IsolatedBC(afterClasses, dependency)) {
            SplittableRandom random = new SplittableRandom(1657);
            for (String name : List.of("SHA1Digest", "SHA256Digest", "SHA384Digest", "SHA512Digest", "MD4Digest", "MD5Digest")) {
                for (int size : new int[]{0, 1, 3, 55, 56, 63, 64, 65, 111, 112, 127, 128, 129, 1024, 8192}) {
                    byte[] message = new byte[size];
                    for (int i = 0; i < message.length; i++) message[i] = (byte) random.nextInt(256);
                    byte[] expected = digest(original, name, message), actual = digest(replacement, name, message);
                    assertArrayEquals(expected, actual, name + ", message length " + size);
                    String standard = switch (name) { case "SHA1Digest" -> "SHA-1"; case "SHA256Digest" -> "SHA-256";
                        case "SHA384Digest" -> "SHA-384"; case "SHA512Digest" -> "SHA-512"; case "MD5Digest" -> "MD5"; default -> null; };
                    if (standard != null) assertArrayEquals(MessageDigest.getInstance(standard).digest(message), actual);
                }
            }
        }
        String export = System.getProperty("sandbox.math.production.output", System.getenv("MATH_BC_ARCHIVE_OUTPUT"));
        if (export != null) for (String name : FILES) {
            write(Path.of(export).resolve("original/" + PACKAGE + name + ".java"), Files.readString(before.resolve(PACKAGE + name + ".java")));
            write(Path.of(export).resolve("generated/" + PACKAGE + name + ".java"), Files.readString(after.resolve(PACKAGE + name + ".java")));
        }
    }

    @Test void documentedInputsAreUnmodifiedEntriesOfThePinnedProductionArchive() throws Exception {
        Map<String, String> sources = productionSources();
        Path directory = checkout().resolve("sandbox_eclipse_help_swtbot_test/fixtures/mathematics");
        try (var entries = Files.list(directory)) {
            var fixtures = entries.filter(Files::isDirectory).sorted().toList();
            assertEquals(4, fixtures.size());
            for (Path fixture : fixtures) {
                var metadata = new java.util.Properties();
                try (var reader = Files.newBufferedReader(fixture.resolve("example.properties"))) { metadata.load(reader); }
                String file = metadata.getProperty("fileName");
                assertNotNull(file);
                String expected = sources.get(file.replace(".java", ""));
                assertNotNull(expected, file);
                assertEquals(expected, Files.readString(fixture.resolve("before.java.txt")), fixture.toString());
            }
        }
    }

    /** Read real source entries instead of retaining a second, drifting copy of each class. */
    private static Map<String, String> productionSources() throws Exception {
        Path fixture = checkout().resolve("sandbox_math_cleanup_test/fixtures/bcprov-1.85.2");
        var provenance = new com.fasterxml.jackson.databind.ObjectMapper().readTree(fixture.resolve("provenance.json").toFile());
        var bundle = Platform.getBundle("bcprov.source");
        assertNotNull(bundle, "The pinned source bundle must be installed for production qualification");
        Path archive = FileLocator.getBundleFileLocation(bundle).orElseThrow().toPath();
        assertEquals(provenance.get("sourceArchiveSha256").asText(), digest(Files.readAllBytes(archive)));
        Map<String, String> sources = new java.util.LinkedHashMap<>();
        try (var jar = new JarFile(archive.toFile())) {
            for (String name : FILES) {
                var entry = jar.getJarEntry(PACKAGE + name + ".java");
                assertNotNull(entry, name);
                try (var input = jar.getInputStream(entry)) {
                    byte[] bytes = input.readAllBytes();
                    assertEquals(provenance.get("files").get(name + ".java").asText(), digest(bytes), name);
                    sources.put(name, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        return Map.copyOf(sources);
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static MathematicalAnalysis.Analysis analyze(String name, String source, Path dependency) {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of());
        return MathematicalAnalysis.analyze(parse(name, source, dependency), source, options, new NullProgressMonitor(), -1, 0,
                new MathematicalAnalysis.SourceEnvironment(PACKAGE + name + ".java", List.of(dependency.toString()), List.of(), List.of()));
    }
    private static CompilationUnit parse(String name, String source, Path dependency) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setUnitName(PACKAGE + name + ".java"); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[]{dependency.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions(JavaCore.VERSION_17, options); parser.setCompilerOptions(options);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
    private static List<String> constants(String name, String source, Path dependency) {
        var constants = new java.util.ArrayList<String>();
        parse(name, source, dependency).accept(new ASTVisitor() {
            @Override public boolean visit(VariableDeclarationFragment declaration) {
                var initializer = declaration.getInitializer();
                if (initializer != null && initializer.resolveConstantExpressionValue() != null)
                    constants.add(declaration.getName() + "=" + source.substring(initializer.getStartPosition(), initializer.getStartPosition() + initializer.getLength()));
                return true;
            }
        });
        return constants;
    }
    private Path compile(Path source, Path dependency) throws IOException {
        Path classes = Files.createDirectory(source.resolve("classes"));
        var arguments = new java.util.ArrayList<>(List.of("--release", "17", "-cp", dependency.toString(), "-d", classes.toString()));
        for (String name : FILES) arguments.add(source.resolve(PACKAGE + name + ".java").toString());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        return classes;
    }
    private static byte[] digest(ClassLoader loader, String name, byte[] message) throws Exception {
        Class<?> type = loader.loadClass("org.bouncycastle.crypto.digests." + name);
        Object digest = type.getConstructor().newInstance();
        Method update = type.getMethod("update", byte[].class, int.class, int.class);
        // Exercise buffering as well as complete blocks.
        int first = message.length / 3; update.invoke(digest, message, 0, first); update.invoke(digest, message, first, message.length - first);
        byte[] output = new byte[(Integer) type.getMethod("getDigestSize").invoke(digest)];
        type.getMethod("doFinal", byte[].class, int.class).invoke(digest, output, 0);
        return output;
    }
    private static Path dependency() throws Exception {
        String configured = System.getProperty("sandbox.math.bc.jar");
        if (configured != null) return Path.of(configured);
        Class<?> digest = Class.forName("org.bouncycastle.crypto.Digest");
        var bundle = FrameworkUtil.getBundle(digest);
        if (bundle != null) return FileLocator.getBundleFileLocation(bundle).orElseThrow().toPath();
        return Path.of(digest.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
    private static Path checkout() {
        String configured = System.getProperty("sandbox.repository.root");
        if (configured != null) return Path.of(configured);
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent())
            if (Files.isDirectory(current.resolve("sandbox_math_cleanup_test/fixtures/bcprov-1.85.2"))) return current;
        throw new IllegalStateException("Sandbox checkout required for pinned production fixtures");
    }
    private static void write(Path path, String value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, value);
    }

    /** Load every BC class in one isolated namespace. The fixture classes are unsigned
     * compiler output, so signed archive protection domains cannot be mixed in its package.
     * This changes no class bytes and never alters the installed BC dependency. */
    private static final class IsolatedBC extends ClassLoader implements AutoCloseable {
        private final Path classes; private final JarFile dependency;
        IsolatedBC(Path classes, Path dependency) throws IOException { super(ClassLoader.getPlatformClassLoader()); this.classes=classes; this.dependency=new JarFile(dependency.toFile()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.bouncycastle.")) throw new ClassNotFoundException(name);
            String path=name.replace('.', '/')+".class";
            try {
                byte[] bytes;
                if(Files.isRegularFile(classes.resolve(path))) bytes=Files.readAllBytes(classes.resolve(path));
                else { var entry=dependency.getJarEntry(path); if(entry==null) throw new ClassNotFoundException(name);
                    try(var input=dependency.getInputStream(entry)){bytes=input.readAllBytes();} }
                return defineClass(name,bytes,0,bytes.length);
            } catch(IOException error){throw new ClassNotFoundException(name,error);}
        }
        @Override public void close() throws IOException { dependency.close(); }
    }
}
