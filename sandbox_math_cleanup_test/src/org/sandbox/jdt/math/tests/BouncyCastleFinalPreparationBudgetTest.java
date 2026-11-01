/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

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
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Retains real scalar improvements when connected array attempts share the full-file budget. */
class BouncyCastleFinalPreparationBudgetTest {
    private static final String ENTRY = "org/bouncycastle/pqc/crypto/hawk/HawkEngine.java";
    private static final String SOURCE_SHA256 = "afccf30d462347704f3e090542b75e81bb782e567abffee780af4dfd3e7de1a7";

    // Exact accepted source spans from completed CI run 41336653257 on PR #1784.
    // Additional candidates must not conceal losses within the unchanged full-file budget.
    private record Span(int offset, int length) { }
    private static final List<Span> MATHEMATICAL_BASELINE = List.of(
            new Span(50036, 196), new Span(50750, 165), new Span(52342, 77), new Span(64177, 246),
            new Span(64971, 292), new Span(66146, 87), new Span(72611, 451), new Span(76081, 81),
            new Span(77214, 113), new Span(82149, 83), new Span(83332, 112), new Span(84387, 63),
            new Span(87154, 53), new Span(90873, 157), new Span(106702, 304), new Span(110134, 418),
            new Span(116440, 69), new Span(123678, 67), new Span(125905, 239), new Span(128239, 839));

    // Complete original fragments from accepted-differences.json in the qualification at
    // sandbox commit b8b56a8ab8166b5fd3070f0586aecce9387097e3:
    // qa/math-optimization/results/2026-10-11-generic-numeric-domains-main-integration/.
    private static final String HAWK_2471 = "int ws = -(lastWord >>> 30) >>> 1;  // Sign extension word\n"
            + "\n"
            + "            // Conditionally apply sign extension\n"
            + "            int mask0 = -((len - sch) >>> 31);      // -1 if len < sch\n"
            + "            int mask1 = -((len - sch - 1) >>> 31);  // -1 if len < sch+1\n"
            + "            int mask2 = -((len - sch - 2) >>> 31);";
    private static final String HAWK_2575 = "long xl = x & 0xFFFFFFFFL;          // Extract low 32 bits (unsigned)\n"
            + "        int xh = (int)(x >>> 32);            // Extract high 32 bits (signed)\n"
            + "\n"
            + "        long z0 = (xl * xl) >>> 32;         // Unsigned multiplication and shift\n"
            + "        long z1 = xl * (long)xh;            // Signed multiplication\n"
            + "        long z3 = ((long)xh * (long)xh) << 32;  // Signed multiplication and shift\n"
            + "\n"
            + "        return z0 + (z1 << 1) + z3;";

    @Test void strictAcceptsBothHawkFragmentsWithinTheFullFileBudget() throws Exception {
        assertBothFragmentsAccepted(false);
    }

    @Test void mathematicalAcceptsBothHawkFragmentsWithinTheFullFileBudget() throws Exception {
        assertBothFragmentsAccepted(true);
    }

    private static void assertBothFragmentsAccepted(boolean mathematical) throws Exception {
        Path binary = dependency("sandbox.math.bc.jar", "bcprov");
        byte[] bytes;
        try (var archive = new JarFile(dependency("sandbox.math.bc.sourceArchive", "bcprov.source").toFile())) {
            var entry = archive.getJarEntry(ENTRY);
            assertNotNull(entry, "The pinned bcprov.source 1.85.2 must contain HawkEngine.java");
            try (var input = archive.getInputStream(entry)) {
                bytes = input.readAllBytes();
            }
        }
        assertEquals(SOURCE_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                "Use the qualified Hawk entry bytes; Maven and OSGi source archives have different packaging");
        String source = new String(bytes, StandardCharsets.UTF_8);
        CompilationUnit ast = parse(source, binary);
        int first = fragmentStart(source, ast, HAWK_2471, 2471, 106702, 107006);
        int second = fragmentStart(source, ast, HAWK_2575, 2575, 110134, 110552);

        // Match BigIntegerCorpusScan, including explanation work and all numeric kinds.
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true");
        values.put(MathCleanUpOptions.KINDS, "BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,BIG_INTEGER");
        values.put(MathCleanUpOptions.SAFETY, "PRESERVE_JAVA");
        values.put(MathCleanUpOptions.GOAL, "LOWER_ESTIMATED_RUNTIME");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000");
        values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.TIMEOUT_SECONDS, "5");
        values.put(MathCleanUpOptions.EXPLANATIONS, "NONTRIVIAL");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, Boolean.toString(mathematical));
        var options = MathCleanUpOptions.parse(values, 17);
        // Full-file selection preserves allocation across connected attempts and scalar retries.
        // Production analysis supplies the same 64-candidate / 5,000-ms request limits.
        var analysis = MathematicalAnalysis.analyze(ast, source, options, new NullProgressMonitor(), -1, 0,
                new MathematicalAnalysis.SourceEnvironment(ENTRY, List.of(binary.toString()), List.of(), List.of()));
        assertAll(mathematical ? "mathematical Hawk coverage" : "strict Hawk coverage",
                () -> assertAcceptedCoverage(analysis, source, HAWK_2471, first, 2471),
                () -> assertAcceptedCoverage(analysis, source, HAWK_2575, second, 2575));
        if (mathematical) {
            var missing = MATHEMATICAL_BASELINE.stream().filter(span -> analysis.replacements().stream()
                    .noneMatch(edit -> edit.offset() == span.offset() && edit.length() == span.length())).toList();
            assertEquals(List.of(), missing, "Every baseline span must survive independently of new candidates");
        }

        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        assertNotEquals(source, generated, "Accepted improvements must produce actual source edits");
        undo.apply(document);
        assertEquals(source, document.get(), "The complete accepted edit must support exact Undo");
        parse(generated, binary);
        System.out.printf("HAWK_FINAL_PREPARATION mode=%s replacements=%d generatedSha256=%s%n",
                mathematical ? "mathematical" : "strict", analysis.replacements().size(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(generated.getBytes(StandardCharsets.UTF_8))));
    }

    private static int fragmentStart(String source, CompilationUnit ast, String fragment,
            int line, int expectedStart, int expectedEnd) {
        int start = source.indexOf(fragment);
        assertTrue(start >= 0, "Missing complete original Hawk fragment at line " + line);
        assertEquals(-1, source.indexOf(fragment, start + 1), "Original fragment must be unique at line " + line);
        assertEquals(line, ast.getLineNumber(start));
        assertEquals(expectedStart, start);
        assertEquals(expectedEnd, start + fragment.length());
        return start;
    }

    private static void assertAcceptedCoverage(MathematicalAnalysis.Analysis analysis, String source,
            String fragment, int start, int line) {
        int end = start + fragment.length();
        // A larger accepted connected region is valid; partial overlap or documentation alone is not.
        var replacement = analysis.replacements().stream()
                .filter(r -> r.offset() <= start && (long) r.offset() + r.length() >= end)
                .findFirst().orElseThrow(() -> new AssertionError("No accepted replacement covers the complete Hawk fragment at line "
                        + line + ": " + analysis.diagnostics()));
        var evidence = analysis.evidence().stream()
                .filter(e -> e.offset() == replacement.offset() && e.length() == replacement.length()
                        && e.replacement().equals(replacement.replacement()))
                .findFirst().orElseThrow(() -> new AssertionError("Accepted Hawk replacement lacks matching verified evidence at line " + line));
        String original = source.substring(replacement.offset(), replacement.offset() + replacement.length());
        assertEquals(original, evidence.original(), "Proof evidence must cover the actual original edit span");
        assertNotEquals(original, replacement.replacement());
        assertTrue(evidence.cost().estimatedRuntimeImprovement(), evidence.cost().toString());
        System.out.printf("HAWK_ACCEPTED originalLine=%d offset=%d length=%d%n",
                line, replacement.offset(), replacement.length());
    }

    private static Path dependency(String property, String bundle) throws IOException {
        String configured = System.getProperty(property);
        return configured == null ? FileLocator.getBundleFileLocation(Platform.getBundle(bundle)).orElseThrow().toPath()
                : Path.of(configured);
    }

    private static CompilationUnit parse(String source, Path binary) {
        var parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setUnitName(ENTRY);
        parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[] {binary.toString()}, new String[0], null, true);
        parser.setResolveBindings(true);
        var compiler = new HashMap<String, String>();
        JavaCore.setComplianceOptions("17", compiler);
        parser.setCompilerOptions(compiler);
        var ast = (CompilationUnit) parser.createAST(null);
        for (var problem : ast.getProblems()) assertFalse(problem.isError(), problem.toString());
        return ast;
    }
}
