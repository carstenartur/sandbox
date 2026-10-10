/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.jar.JarFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class BouncyCastlePreparationBoundaryTest {
    @Test void oversizedPreparationRetriesSmallerRealSourceRegions() throws Exception {
        String configured = System.getProperty("sandbox.math.bc.sourceArchive");
        Path archive = configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle("bcprov.source")).orElseThrow().toPath()
                : Path.of(configured);
        byte[] bytes;
        try (var jar = new JarFile(archive.toFile()); var stream = jar.getInputStream(jar.getJarEntry("org/bouncycastle/math/raw/GF256AES.java"))) {
            bytes = stream.readAllBytes();
        }
        assertEquals("cb639be0aacfafa55bd50533b9195c8070c10bac925bb174006be23a03f3536c", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        String source = new String(bytes, StandardCharsets.UTF_8);
        String dependency = System.getProperty("sandbox.math.bc.jar");
        if (dependency == null) dependency = FileLocator.getBundleFileLocation(Platform.getBundle("bcprov")).orElseThrow().toString();
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName("org/bouncycastle/math/raw/GF256AES.java");
        parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {dependency}, new String[0], null, true);
        parser.setResolveBindings(true);
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler); parser.setCompilerOptions(compiler);
        CompilationUnit ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true"); values.put(MathCleanUpOptions.KINDS, "INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); values.put(MathCleanUpOptions.MAX_STATES, "20000");
        var analysis = MathematicalAnalysis.analyze(ast, source, MathCleanUpOptions.parse(values, 17),
                new NullProgressMonitor(), -1, 0, new MathematicalAnalysis.SourceEnvironment(
                        "org/bouncycastle/math/raw/GF256AES.java", java.util.List.of(dependency), java.util.List.of(), java.util.List.of()));
        assertTrue(analysis.diagnostics().stream().anyMatch(d -> d.code().equals("REGION_PREPARATION_SPLIT")), analysis.diagnostics().toString());
        assertFalse(analysis.diagnostics().stream().anyMatch(d -> d.message().contains("plan preparation work bound exceeded")), analysis.diagnostics().toString());
    }
}
