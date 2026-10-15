/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Full unchanged SWU source, fixed file budget, independent proof and executed hash-to-curve behavior. */
class BouncyCastleVersionProjectionTest {
    private static final String ENTRY = "org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java";
    @TempDir Path temporary;

    @Test void wholeFileBudgetFindsTheSharedResiduesAndPreservesFourCurveProfiles() throws Exception {
        Path binary = dependency("sandbox.math.bc.jar", "bcprov");
        byte[] bytes;
        try (var jar = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile());
                var input = jar.getInputStream(jar.getJarEntry(ENTRY))) { bytes = input.readAllBytes(); }
        assertEquals("6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String original = new String(bytes, StandardCharsets.UTF_8);
        var result = analyze(original, binary);
        var region = result.evidence().stream().filter(e -> e.original().contains("BigInteger tv1 = u.modPow")
                && e.original().contains("tv3 = B.multiply(tv3)")).findFirst().orElseThrow(
                        () -> new AssertionError(result.diagnostics().toString()));
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_STATEMENTS); parser.setSource(region.replacement().toCharArray());
        int[] optimizedCalls = {-1, -1};
        parser.createAST(null).accept(new ASTVisitor() {
            @Override public boolean visit(IfStatement branch) {
                optimizedCalls[0] = 0; optimizedCalls[1] = 0;
                branch.getThenStatement().accept(new ASTVisitor() {
                    @Override public boolean visit(MethodInvocation call) {
                        if (call.getName().getIdentifier().equals("mod")) optimizedCalls[0]++;
                        if (call.getName().getIdentifier().equals("modPow")) optimizedCalls[1]++;
                        return true;
                    }
                });
                return false;
            }
        });
        assertArrayEquals(new int[] {3, 0}, optimizedCalls, region.replacement());
        assertTrue(region.cost().estimatedRuntimeImprovement(), "Receiver checks are included in retained cost");
        assertFalse(region.proof().checkerRevision().isBlank());
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        try (var before = new IsolatedBC(compile(original, binary), binary);
                var after = new IsolatedBC(compile(generated, binary), binary)) {
            for (String profile : List.of("P256_XMD_SHA_256", "P384_XMD_SHA_384", "P521_XMD_SHA_512",
                    "BLS12_381_G1_XMD_SHA_256_SSWU_RO")) {
                for (String message : List.of("", "abc", "a".repeat(200), "Mathematik Δ")) {
                    assertArrayEquals(hash(before, profile, message.getBytes(StandardCharsets.UTF_8)),
                            hash(after, profile, message.getBytes(StandardCharsets.UTF_8)), profile);
                }
                var oldFailure = assertThrows(InvocationTargetException.class, () -> hash(before, profile, null)).getCause();
                var newFailure = assertThrows(InvocationTargetException.class, () -> hash(after, profile, null)).getCause();
                assertEquals(oldFailure.getClass(), newFailure.getClass());
            }
        }
    }

    private static MathematicalAnalysis.Analysis analyze(String source, Path binary) {
        var options = new MathCleanUpOptions(true, EnumSet.allOf(NumericKind.class), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of(), true,
                MathCleanUpOptions.ExplanationMode.NONTRIVIAL);
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName(ENTRY); parser.setSource(source.toCharArray()); parser.setResolveBindings(true);
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0,
                new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(binary.toString()), List.of(), List.of()));
    }

    private Path compile(String source, Path binary) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "compiled-"); Path file = directory.resolve(ENTRY);
        Files.createDirectories(directory.resolve(ENTRY.substring(0, ENTRY.lastIndexOf('/'))));
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-cp",
                binary.toString(), "-d", directory.toString(), file.toString()));
        return directory;
    }

    private static byte[] hash(ClassLoader loader, String profileName, byte[] message) throws Exception {
        Class<?> profile = loader.loadClass("org.bouncycastle.crypto.hash2curve.HashToCurveProfile");
        Class<?> hashing = loader.loadClass("org.bouncycastle.crypto.hash2curve.HashToEllipticCurve");
        Object instance = hashing.getMethod("getInstance", profile, String.class).invoke(null,
                profile.getField(profileName).get(null), "sandbox-qualified-source-test");
        Object point = hashing.getMethod("hashToCurve", byte[].class).invoke(instance, (Object) message);
        return (byte[]) loader.loadClass("org.bouncycastle.math.ec.ECPoint").getMethod("getEncoded", boolean.class).invoke(point, false);
    }

    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath() : Path.of(configured);
    }

    private static final class IsolatedBC extends ClassLoader implements AutoCloseable {
        private final Path classes;
        private final JarFile dependency;
        IsolatedBC(Path classes, Path binary) throws IOException {
            super(ClassLoader.getPlatformClassLoader()); this.classes = classes; this.dependency = new JarFile(binary.toFile());
        }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.bouncycastle.")) throw new ClassNotFoundException(name);
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
