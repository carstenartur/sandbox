/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import de.regelsuche.sdk.optimization.SemanticAssumption.Kind;

/** Catches lost byte-array bounds, modular congruence, or a rejected trailing modPow hiding a valid region. */
class BouncyCastleModularResidueTest {
    private static final String NAME = "org.bouncycastle.crypto.bls.BLS12_381Serialization";
    private static final String ENTRY = NAME.replace('.', '/') + ".java";
    @TempDir Path temporary;

    @Test void actualG1DecompressionHasOneVerifiedCheaperResidueAndPreservesItsPublicBehavior() throws Exception {
        Path dependency = dependency("sandbox.math.bc.jar", "bcprov");
        byte[] bytes;
        try (var jar = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile());
                var stream = jar.getInputStream(jar.getJarEntry(ENTRY))) { bytes = stream.readAllBytes(); }
        assertEquals("8584e1ddbcd04ddd5a8f9aee49485756459c75b95a986e471d8408c0f8c61186",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String original = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(analyze(original, dependency, false).changed(), "Strict Java retains the existing BC contract");
        var result = analyze(original, dependency, true);
        assertEquals(1, result.replacements().size(), result.diagnostics().toString());
        assertEquals(1, result.evidence().size());
        var evidence = result.evidence().getFirst();
        assertTrue(evidence.original().contains("BigInteger ySquared"), evidence.original());
        assertFalse(evidence.original().contains("modPow"), "Unsupported shifted exponent must remain outside the retained region");
        assertTrue(evidence.cost().estimatedRuntimeImprovement());
        assertTrue(evidence.cost().sourceScore() > evidence.cost().candidateScore(), "Cost includes emitted receiver checks");
        assertFalse(evidence.proof().checkerRevision().isBlank());
        assertEquals(Set.of("P"), evidence.explanation().guardedReceivers());
        assertEquals(evidence.proof(), evidence.explanation().verified().proof());
        assertTrue(evidence.explanation().verified().assumptions().stream().anyMatch(a ->
                a.kind() == Kind.BIG_INTEGER_BIT_LENGTH_BOUND && a.parameter().equals("384")
                && "x".equals(evidence.explanation().inputNames().get(a.subject()))), "48 magnitude bytes prove x's 384-bit bound");
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        assertFalse(analyze(generated, dependency, true).changed(), "Retained rewrite must be idempotent");
        try (var before = new IsolatedBC(compile(original, dependency), dependency);
                var after = new IsolatedBC(compile(generated, dependency), dependency)) {
            Object curve = before.loadClass("org.bouncycastle.crypto.bls.BLS12_381G1").getMethod("createCurve").invoke(null);
            Class<?> curveType = before.loadClass("org.bouncycastle.math.ec.ECCurve");
            Object generator = before.loadClass("org.bouncycastle.crypto.bls.BLS12_381G1").getMethod("getGenerator", curveType).invoke(null, curve);
            Class<?> pointType = before.loadClass("org.bouncycastle.math.ec.ECPoint");
            for (int scalar : new int[] {1, 2, 17}) for (boolean negate : new boolean[] {false, true}) {
                Object point = pointType.getMethod("multiply", BigInteger.class).invoke(generator, BigInteger.valueOf(scalar));
                if (negate) point = pointType.getMethod("negate").invoke(point);
                byte[] encoding = (byte[]) before.loadClass(NAME).getMethod("compressG1", pointType).invoke(null, point);
                byte[] expected = (byte[]) pointType.getMethod("getEncoded", boolean.class).invoke(point, false);
                assertArrayEquals(expected, decode(before, encoding)); assertArrayEquals(expected, decode(after, encoding));
            }
            byte[] infinity = new byte[48]; infinity[0] = (byte) 0xc0;
            assertArrayEquals(new byte[] {0}, decode(before, infinity)); assertArrayEquals(new byte[] {0}, decode(after, infinity));
            byte[] signInfinity = infinity.clone(); signInfinity[0] = (byte) 0xe0;
            byte[] nonzeroInfinity = infinity.clone(); nonzeroInfinity[47] = 1;
            // x = 1 gives ySquared = 5, a nonresidue because this field prime is 2 modulo 5.
            byte[] nonresidue = new byte[48]; nonresidue[0] = (byte) 0x80; nonresidue[47] = 1;
            byte[] outOfRange = HexFormat.of().parseHex("1a0111ea397fe69a4b1ba7b6434bacd764774b84f38512bf6730d2a0f6b0f6241eabfffeb153ffffb9feffffffffaaab");
            outOfRange[0] |= (byte) 0x80;
            for (byte[] invalid : new byte[][] {null, new byte[47], new byte[49], new byte[48], signInfinity, nonzeroInfinity, outOfRange, nonresidue}) {
                Throwable oldFailure = assertThrows(InvocationTargetException.class, () -> decode(before, invalid)).getCause();
                Throwable newFailure = assertThrows(InvocationTargetException.class, () -> decode(after, invalid)).getCause();
                assertEquals(IllegalArgumentException.class, oldFailure.getClass());
                assertEquals(oldFailure.getClass(), newFailure.getClass()); assertEquals(oldFailure.getMessage(), newFailure.getMessage());
            }
        }
    }

    private static MathematicalAnalysis.Analysis analyze(String source, Path dependency, boolean mathematical) {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000L, 20_000, false, 17, List.of(), mathematical);
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName(ENTRY); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {dependency.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var environment = new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(dependency.toString()), List.of(), List.of());
        return MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0, environment);
    }
    private Path compile(String source, Path dependency) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "compiled-"); Path file = directory.resolve(ENTRY);
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "The fixture source must have a parent directory"));
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertNotNull(ToolProvider.getSystemJavaCompiler(), "A full JDK is required");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-cp",
                dependency.toString(), "-d", directory.toString(), file.toString()));
        return directory;
    }
    private static byte[] decode(ClassLoader loader, byte[] bytes) throws Exception {
        Class<?> curveType = loader.loadClass("org.bouncycastle.math.ec.ECCurve");
        Object curve = loader.loadClass("org.bouncycastle.crypto.bls.BLS12_381G1").getMethod("createCurve").invoke(null);
        Object point = loader.loadClass(NAME).getMethod("decompressG1", byte[].class, curveType).invoke(null, bytes, curve);
        return (byte[]) loader.loadClass("org.bouncycastle.math.ec.ECPoint").getMethod("getEncoded", boolean.class).invoke(point, false);
    }
    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath() : Path.of(configured);
    }
    /** Loads both variants and their archive dependencies in the same unsigned, isolated BC namespace. */
    private static final class IsolatedBC extends ClassLoader implements AutoCloseable {
        private final Path classes; private final JarFile dependency;
        IsolatedBC(Path classes, Path dependency) throws IOException { super(ClassLoader.getPlatformClassLoader()); this.classes = classes; this.dependency = new JarFile(dependency.toFile()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("org.bouncycastle.")) throw new ClassNotFoundException(name);
            String path = name.replace('.', '/') + ".class";
            try {
                byte[] bytes;
                if (Files.isRegularFile(classes.resolve(path))) bytes = Files.readAllBytes(classes.resolve(path));
                else { var entry = dependency.getJarEntry(path); if (entry == null) throw new ClassNotFoundException(name);
                    try (var input = dependency.getInputStream(entry)) { bytes = input.readAllBytes(); } }
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) { throw new ClassNotFoundException(name, error); }
        }
        @Override public void close() throws IOException { dependency.close(); }
    }
}
