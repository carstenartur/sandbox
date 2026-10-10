/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
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
        assertEquals(1, result.replacements().size(), result.diagnostics().toString());
        assertEquals(1, result.evidence().size());
        Document document = new Document(original);
        var undo = result.newEdit().apply(document, org.eclipse.text.edits.TextEdit.CREATE_UNDO);
        String generated = document.get(); undo.apply(document); assertEquals(original, document.get());
        assertTrue(generated.contains("u.multiply(u).mod(p)"), generated);
        assertEquals(original.split("/\\*\\*", -1).length, generated.split("/\\*\\*", -1).length);
        assertTrue(generated.contains("reference identity"));
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

    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath() : Path.of(configured);
    }
    private static CompilationUnit parse(String source, Path dependency) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName(ENTRY); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {dependency.toString()}, new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
    private Path compile(String source, Path dependency) throws IOException {
        Path directory = Files.createTempDirectory(temporary, "compiled-"); Path file = directory.resolve(ENTRY);
        Files.createDirectories(file.getParent()); Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-cp",
                dependency.toString(), "-d", directory.toString(), file.toString()));
        return directory;
    }
    private static Object mapper(ClassLoader loader) throws Exception {
        Object parameters = loader.loadClass("org.bouncycastle.crypto.ec.CustomNamedCurves").getMethod("getByName", String.class).invoke(null, "secp256r1");
        Object curve = parameters.getClass().getMethod("getCurve").invoke(parameters);
        return loader.loadClass(NAME).getConstructor(loader.loadClass("org.bouncycastle.math.ec.ECCurve"), BigInteger.class).newInstance(curve, BigInteger.valueOf(-10));
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
