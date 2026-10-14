/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Actual immutable BC source and definition chain, not a hand-simplified substitute formula. */
class BouncyCastleSourceContextTest {
    private static final String PREFIX = "org/bouncycastle/crypto/bls/";
    private static final String ENTRY = PREFIX + "BLS12_381Serialization.java";
    @TempDir Path temporary;

    @Test void actualBlsPatchNeedsNoManualGuardOrFactoryRepair() throws Exception {
        String source = sources(); Path binary = dependency("sandbox.math.bc.jar", "bcprov");
        var analysis = analyze(source, binary, true);
        assertEquals(1, analysis.replacements().size(), analysis.diagnostics().toString());
        assertTrue(analysis.evidence().getFirst().explanation().guardedReceivers().isEmpty());
        assertEquals("BigInteger ySquared = x.multiply(x).multiply(x)\n            .add(BigInteger.valueOf(4)).mod(P);",
                analysis.replacements().getFirst().replacement());
        assertEquals(2, analysis.sourceDependencies().receipts().size());
        assertFalse(analysis.evidence().getFirst().proof().checkerRevision().isBlank());
        var document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertTrue(generated.contains("result reference identity may change"), "Normal Apply retains the explicit mathematical contract");
        undo.apply(document); assertEquals(source, document.get());
        Files.createDirectories(temporary.resolve("generated").resolve(PREFIX));
        Path generatedFile = temporary.resolve("generated").resolve(ENTRY);
        Files.writeString(generatedFile, generated, StandardCharsets.UTF_8);
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler, "A full JDK is required");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-cp", binary.toString(), "-d",
                classes.toString(), generatedFile.toString()));
        Files.writeString(temporary.resolve("sources").resolve(PREFIX + "BLS12_381G1.java"), "// changed source\\n",
				StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, analysis::newEdit);
    }
    @Test void availableDefinitionsDoNotEnableMathematicalModeImplicitly() throws Exception {
        String source = sources();
        var result = analyze(source, dependency("sandbox.math.bc.jar", "bcprov"), false);
        assertFalse(result.changed(), result.diagnostics().toString());
    }
    private String sources() throws Exception {
        Path root = temporary.resolve("sources");
        try (var archive = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile())) {
            Files.createDirectories(root.resolve(PREFIX));
            for (String name : List.of("BLS12_381Serialization", "Fp2Element", "BLS12_381G1")) {
                var entry = archive.getJarEntry(PREFIX + name + ".java"); assertNotNull(entry, name);
                Path file = root.resolve(entry.getName());
                try (var input = archive.getInputStream(entry)) { Files.write(file, input.readAllBytes()); }
            }
        }
        byte[] bytes = Files.readAllBytes(root.resolve(ENTRY));
        assertEquals("8584e1ddbcd04ddd5a8f9aee49485756459c75b95a986e471d8408c0f8c61186",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private MathematicalAnalysis.Analysis analyze(String source, Path binary, boolean mathematical) {
        String root = temporary.resolve("sources").toString();
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName(ENTRY);
        parser.setEnvironment(new String[] {binary.toString()}, new String[] {root}, new String[] {"UTF-8"}, true);
        parser.setResolveBindings(true);
        var compiler = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", compiler); parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var options = new MathCleanUpOptions(true, EnumSet.of(NumericKind.BIG_INTEGER), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of(), mathematical);
        int offset = source.indexOf("BigInteger ySquared ="), length = source.indexOf(';', offset) + 1 - offset;
        return MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), offset, length,
                new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(binary.toString()), List.of(root), List.of("UTF-8")));
    }
    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath() : Path.of(configured);
    }
}
