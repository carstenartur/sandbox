/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class BouncyCastleBigIntegerTest {
    private static final String NAME = "org.bouncycastle.crypto.hash2curve.impl.SimplifiedShallueVanDeWoestijneMapToCurve";
    private static final String ENTRY = NAME.replace('.', '/') + ".java";
    @TempDir Path temporary;

    @Test void actualCurveMappingHasAVerifiedSquareAndPreservesPoints() throws Exception {
        Path dependency = dependency("sandbox.math.bc.jar", "bcprov");
        byte[] bytes;
        try (var jar = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile());
                var stream = jar.getInputStream(jar.getJarEntry(ENTRY))) { bytes = stream.readAllBytes(); }
        assertEquals("6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String original = new String(bytes, StandardCharsets.UTF_8);
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); values.put(MathCleanUpOptions.MAX_STATES, "20000");
        var options = MathCleanUpOptions.parse(values, 17);
        var environment = new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(dependency.toString()), List.of(), List.of());
        var result = MathematicalAnalysis.analyze(parse(original, dependency), original, options, new NullProgressMonitor(), -1, 0, environment);
        assertEquals(4, result.replacements().size(), result.diagnostics().toString());
        assertEquals(4, result.evidence().size());
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        assertTrue(generated.contains("u.multiply(u).mod(p)"), generated);
        assertEquals(original.split("/\\*\\*", -1).length, generated.split("/\\*\\*", -1).length);
        assertTrue(generated.contains("reference identity"));
        int documentedContracts = 0;
        for (Object comment : parse(generated, dependency).getCommentList()) {
            if (comment instanceof Javadoc documentation) {
                String documentationSource = generated.substring(documentation.getStartPosition(),
                        documentation.getStartPosition() + documentation.getLength());
                documentedContracts += documentationSource.split("reference identity", -1).length - 1;
            }
        }
        assertEquals(1, documentedContracts, "The method contract remains deduplicated in Javadoc");
        assertEquals(4, generated.lines().filter(line -> line.stripLeading()
                .startsWith("// Verified mathematics (MATHEMATICAL).")).count(),
                "NONTRIVIAL explanations retain each verified region's changed contract");
        assertFalse(MathematicalAnalysis.analyze(parse(generated, dependency), generated, options,
                new NullProgressMonitor(), -1, 0, environment).changed());
        try (var before = new IsolatedBC(compile(original, dependency), dependency);
                var after = new IsolatedBC(compile(generated, dependency), dependency)) {
            Object oldMap = mapper(before), newMap = mapper(after);
            var random = new Random(1657);
            for (int i = 0; i < 100; i++) {
                BigInteger input = i == 0 ? BigInteger.ZERO : i == 1 ? BigInteger.ONE
                        : i == 2 ? BigInteger.ONE.shiftLeft(5000) : new BigInteger(256, random).multiply(BigInteger.valueOf(i % 2 == 0 ? 1 : -1));
                assertArrayEquals(point(oldMap, input), point(newMap, input), "input " + i);
            }
        }
    }

    @Test void actualPrimalityTestsRetainDecisionsAndFactors() throws Exception {
        String name = "org.bouncycastle.math.Primes";
        try (var pair = rewritten(name, "47b68cd6d275c4d1b837202abbaeb6a0e658a789018a458079e43293f294a613", 3)) {
            Class<?> before = pair.before.loadClass(name), after = pair.after.loadClass(name);
            for (int n = 5; n <= 257; n += 2) {
                BigInteger input = BigInteger.valueOf(n);
                var signature = new Class<?>[] {BigInteger.class, SecureRandom.class, int.class};
                assertEquals(before.getMethod("isMRProbablePrime", signature).invoke(null, input, random(n), 4),
                        after.getMethod("isMRProbablePrime", signature).invoke(null, input, random(n), 4));
                Object a = before.getMethod("enhancedMRProbablePrimeTest", signature).invoke(null, input, random(n), 4);
                Object b = after.getMethod("enhancedMRProbablePrimeTest", signature).invoke(null, input, random(n), 4);
                for (String getter : List.of("isProvablyComposite", "isNotPrimePower", "getFactor"))
                    assertEquals(a.getClass().getMethod(getter).invoke(a), b.getClass().getMethod(getter).invoke(b));
                assertEquals(before.getMethod("isMRProbablePrimeToBase", BigInteger.class, BigInteger.class).invoke(null, input, BigInteger.TWO),
                        after.getMethod("isMRProbablePrimeToBase", BigInteger.class, BigInteger.class).invoke(null, input, BigInteger.TWO));
            }
        }
    }

    @Test void actualCramerShoupGeneratorRetainsSeededOutputs() throws Exception {
        String name = "org.bouncycastle.crypto.generators.CramerShoupParametersGenerator";
        try (var pair = rewritten(name, "370c69eb4ecf946ddbbf708bbc6c2c8c30259dc3196cd47ca0bbad96e1454e3e", 1)) {
            var before = pair.before.loadClass(name + "$ParametersHelper").getDeclaredMethod("selectGenerator", BigInteger.class, SecureRandom.class);
            var after = pair.after.loadClass(name + "$ParametersHelper").getDeclaredMethod("selectGenerator", BigInteger.class, SecureRandom.class);
            before.setAccessible(true); after.setAccessible(true);
            for (int p : new int[] {7, 11, 23, 47, 101, 283}) for (int seed = 0; seed < 16; seed++)
                assertEquals(before.invoke(null, BigInteger.valueOf(p), random(seed)), after.invoke(null, BigInteger.valueOf(p), random(seed)));
        }
    }

    @Test void actualGenericSqrtRatioRetainsSignedResults() throws Exception {
        String name = "org.bouncycastle.crypto.hash2curve.impl.GenericSqrtRatioCalculator";
        try (var pair = rewritten(name, "1a206b4ee919922d5a3cbcd185f56a759e7b9a4fbf0bf4d7318f239add5b2fbd", 1)) {
            Object before = mapper(pair.before, name), after = mapper(pair.after, name);
            for (int u = -10; u <= 10; u++) for (int v = 1; v <= 10; v++) {
                Object a = before.getClass().getMethod("sqrtRatio", BigInteger.class, BigInteger.class).invoke(before, BigInteger.valueOf(u), BigInteger.valueOf(v));
                Object b = after.getClass().getMethod("sqrtRatio", BigInteger.class, BigInteger.class).invoke(after, BigInteger.valueOf(u), BigInteger.valueOf(v));
                for (String getter : List.of("isQR", "getRatio")) assertEquals(a.getClass().getMethod(getter).invoke(a), b.getClass().getMethod(getter).invoke(b));
            }
        }
    }

    @Test void actualTonelliShanksRetainsRootsAndNonresidueDecisions() throws Exception {
        String name = "org.bouncycastle.pqc.crypto.sqisign.Ibz";
        try (var pair = rewritten(name, "b0d359b86c9bf2a994eea49833da99cfae04caf2dcd2df19b5a7ba32d5497a84", 1)) {
            for (int p : new int[] {17, 41, 73, 97}) for (int x = -p; x <= p; x++)
                assertEquals(root(pair.before.loadClass(name), x, p), root(pair.after.loadClass(name), x, p));
        }
    }

    private static List<Object> root(Class<?> type, int x, int p) throws Exception {
        var constructor = type.getConstructor(BigInteger.class); constructor.setAccessible(true);
        Object result = constructor.newInstance(BigInteger.ZERO), input = constructor.newInstance(BigInteger.valueOf(x)), modulus = constructor.newInstance(BigInteger.valueOf(p));
        var method = type.getMethod("sqrtModP", type, type, type); method.setAccessible(true);
        var value = type.getField("v"); value.setAccessible(true);
        Object status = method.invoke(null, result, input, modulus);
        return List.of(status, value.get(result));
    }

    private static SecureRandom random(int seed) {
        return new SecureRandom() {
            private static final long serialVersionUID = 1L;
            private final Random values = new Random(seed);
            @Override public void nextBytes(byte[] bytes) { values.nextBytes(bytes); }
        };
    }

    private Pair rewritten(String name, String hash, int changes) throws Exception {
        Path dependency = dependency("sandbox.math.bc.jar", "bcprov");
        String entry = name.replace('.', '/') + ".java";
        byte[] bytes;
        try (var jar = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile());
                var stream = jar.getInputStream(jar.getJarEntry(entry))) { bytes = stream.readAllBytes(); }
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String original = new String(bytes, StandardCharsets.UTF_8);
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true"); values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, "true");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); values.put(MathCleanUpOptions.MAX_STATES, "20000");
        var options = MathCleanUpOptions.parse(values, 17);
        var environment = new MathematicalAnalysis.SourceEnvironment(entry, List.of(dependency.toString()), List.of(), List.of());
        var result = MathematicalAnalysis.analyze(parse(original, dependency, entry), original, options, new NullProgressMonitor(), -1, 0, environment);
        assertEquals(changes, result.replacements().size(), result.diagnostics().toString());
        assertEquals(changes, result.evidence().size());
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        assertFalse(MathematicalAnalysis.analyze(parse(generated, dependency, entry), generated, options, new NullProgressMonitor(), -1, 0, environment).changed());
        return new Pair(new IsolatedBC(compile(original, dependency, entry), dependency), new IsolatedBC(compile(generated, dependency, entry), dependency));
    }

    private record Pair(IsolatedBC before, IsolatedBC after) implements AutoCloseable {
        @Override public void close() throws IOException { try { before.close(); } finally { after.close(); } }
    }

    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath() : Path.of(configured);
    }
    private static CompilationUnit parse(String source, Path dependency) {
        return parse(source, dependency, ENTRY);
    }
    private static CompilationUnit parse(String source, Path dependency, String entry) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName(entry); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {dependency.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
    private Path compile(String source, Path dependency) throws IOException {
        return compile(source, dependency, ENTRY);
    }
    private Path compile(String source, Path dependency, String entry) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "compiled-"); Path file = directory.resolve(entry);
        Files.createDirectories(Objects.requireNonNull(file.getParent(), "The fixture source must have a parent directory"));
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-cp",
                dependency.toString(), "-d", directory.toString(), file.toString()));
        return directory;
    }
    private static Object mapper(ClassLoader loader) throws Exception {
        return mapper(loader, NAME);
    }
    private static Object mapper(ClassLoader loader, String name) throws Exception {
        Object parameters = loader.loadClass("org.bouncycastle.crypto.ec.CustomNamedCurves").getMethod("getByName", String.class).invoke(null, "secp256r1");
        Object curve = parameters.getClass().getMethod("getCurve").invoke(parameters);
        return loader.loadClass(name).getConstructor(loader.loadClass("org.bouncycastle.math.ec.ECCurve"), BigInteger.class).newInstance(curve, BigInteger.valueOf(-10));
    }
    private static byte[] point(Object mapper, BigInteger input) throws Exception {
        Object point = mapper.getClass().getMethod("process", BigInteger.class).invoke(mapper, input);
        return (byte[]) point.getClass().getMethod("getEncoded", boolean.class).invoke(point, false);
    }
    /** Same isolated, unsigned namespace for original and generated fixture, including archive dependencies. */
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
