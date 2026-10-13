/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** The actual source is input; explanations must not select a mathematical task. */
class MathematicalExplanationTest {
    private static final String OPTION = "cleanup.mathematics.explanations";
    @TempDir Path temporary;

    @Test void allCommentModesRoundTripAndInvalidModesAreRejected() {
        for (String mode : List.of("NONE", "NONTRIVIAL", "ALL")) {
            var options = options(mode, SafetyProfile.PRESERVE_JAVA);
            assertEquals(mode, options.toMap().get(OPTION));
            assertEquals(options, MathCleanUpOptions.parse(options.toMap(), 17));
        }
        assertEquals("NONTRIVIAL", MathCleanUpOptions.defaults(17).toMap().get(OPTION));
        assertThrows(IllegalArgumentException.class, () -> options("GUESS_A_PROOF", SafetyProfile.PRESERVE_JAVA));
    }

    @Test void commentsShowActualJavaNamesPlansAndNumericContract() throws Exception {
        String source = source("x*(a+b)-x*a");
        var result = analyze(source, "ALL", SafetyProfile.PRESERVE_JAVA);
        assertTrue(result.changed(), result.diagnostics().toString());
        String generated = applyAndUndo(source, result);
        System.out.println("ACTUAL_EXPLAINED_JAVA=" + generated);
        assertTrue(generated.contains("// Verified mathematics"), generated);
        assertTrue(generated.contains("Original values:"), generated);
        assertTrue(generated.contains("Replacement values:"), generated);
        assertTrue(generated.contains("Java integral wraparound"), generated);
        assertTrue(generated.contains("not a timing measurement"), generated);
        assertFalse(generated.contains("RFC 9380"), generated);
        compare(source, generated);
        assertTrue(result.replacements().getFirst().description().contains("Original values:"));
    }

    @Test void disablingSourceCommentsRetainsPreviewExplanation() throws Exception {
        String source = source("x*(a+b)-x*a");
        var result = analyze(source, "NONE", SafetyProfile.PRESERVE_JAVA);
        assertTrue(result.changed(), result.diagnostics().toString());
        String generated = applyAndUndo(source, result);
        assertFalse(generated.contains("// Verified mathematics"), generated);
        assertTrue(result.replacements().getFirst().description().contains("Replacement values:"));
        compare(source, generated);
    }

    @Test void helperNamesDoNotChooseTheExplanationAndExistingCommentSurvives() throws Exception {
        String source = """
                public class Calculation {
                    private static int blend(int p, int q) { return p + q; }
                    public static int compute(int distance, int scale, int bias) {
                        // Keep this original explanation.
                        return distance * blend(scale, bias) - distance * scale;
                    }
                }
                """;
        var result = analyze(source, "ALL", SafetyProfile.PRESERVE_JAVA);
        assertTrue(result.changed(), result.diagnostics().toString());
        String generated = applyAndUndo(source, result);
        assertTrue(generated.contains("Keep this original explanation."), generated);
        assertTrue(generated.contains("distance"), generated);
        assertTrue(generated.contains("bias"), generated);
        compare(source, generated);
        assertFalse(analyze(generated, "ALL", SafetyProfile.PRESERVE_JAVA).changed(), generated);
    }

    @Test void constantsDoNotBecomeCandidatesWhenCommentsAreEnabled() {
        for (String expression : List.of("5*7*11", "x*(5*7*11)")) {
            var result = analyze(source(expression), "ALL", SafetyProfile.PRESERVE_JAVA);
            assertFalse(result.changed(), result.diagnostics().toString());
        }
    }

    @Test void checkedExplanationDoesNotClaimUnchangedJavaContract() throws Exception {
        String source = source("(x+1)-1");
        var result = analyze(source, "ALL", SafetyProfile.CHECKED_THROW);
        assertTrue(result.changed(), result.diagnostics().toString());
        String generated = applyAndUndo(source, result);
        assertTrue(generated.contains("ArithmeticException"), generated);
        assertTrue(generated.contains("original and replacement"), generated);
        assertFalse(generated.contains("Java integral wraparound"), generated);
        try (var compiled = compile(generated)) { assertNotNull(compiled.loadClass("Calculation")); }
    }

    @Test void commentsRespectCrLfAndCanBeUndoneExactly() throws Exception {
        String source = "public class Calculation {\r\n    public static int compute(int x,int a,int b) {\r\n        return x*(a+b)-x*a;\r\n    }\r\n}\r\n";
        var result = analyze(source, "ALL", SafetyProfile.PRESERVE_JAVA);
        assertTrue(result.changed(), result.diagnostics().toString());
        String generated = applyAndUndo(source, result);
        assertFalse(generated.replace("\r\n", "").contains("\n"), generated);
        compare(source, generated);
    }

    @Test void cancellationCannotLeaveAnExplainedReplacement() {
        String source = source("x*(a+b)-x*a");
        var monitor = new NullProgressMonitor();
        monitor.setCanceled(true);
        var result = MathematicalAnalysis.analyze(MathTestSupport.parse(source), source,
                options("ALL", SafetyProfile.PRESERVE_JAVA), monitor, -1, 0);
        assertFalse(result.changed());
        assertTrue(result.evidence().isEmpty());
    }

    @Test void nontrivialModeActuallyControlsTheGeneratedSourceAtItsBoundary() throws Exception {
        for (int work : new int[] {7, 8}) {
            String source = source("x" + " + 0".repeat(work));
            var result = analyze(source, "NONTRIVIAL", SafetyProfile.PRESERVE_JAVA);
            assertTrue(result.changed(), result.diagnostics().toString());
            assertEquals(work, result.evidence().getFirst().cost().sourceCost().operationWork());
            String generated = applyAndUndo(source, result);
            assertEquals(work >= 8, generated.contains("// Verified mathematics"), generated);
            assertTrue(result.replacements().getFirst().description().contains("Original values:"));
            compare(source, generated);
        }
    }

    private static String source(String expression) {
        return "public class Calculation { public static int compute(int x,int a,int b) { return " + expression + "; }}";
    }
    private static MathCleanUpOptions options(String mode, SafetyProfile safety) {
        var values = new HashMap<>(new MathCleanUpOptions(true, Set.of(NumericKind.INT), safety,
                OptimizationGoal.READABILITY, 2_000_000L, 20_000, safety == SafetyProfile.CHECKED_THROW, 17, List.of()).toMap());
        values.put(OPTION, mode);
        return MathCleanUpOptions.parse(values, 17);
    }
    private static MathematicalAnalysis.Analysis analyze(String source, String mode, SafetyProfile safety) {
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options(mode, safety),
                new NullProgressMonitor(), -1, 0);
    }
    private static String applyAndUndo(String source, MathematicalAnalysis.Analysis analysis) throws Exception {
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        String generated = document.get();
        undo.apply(document);
        assertEquals(source, document.get());
        return generated;
    }
    private void compare(String source, String generated) throws Exception {
        try (var original = compile(source); var replacement = compile(generated)) {
            var before = original.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
            var after = replacement.loadClass("Calculation").getMethod("compute", int.class, int.class, int.class);
            int[] edges = {0, 1, -1, 17, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 30};
            for (int x : edges) for (int a : edges) for (int b : edges)
                assertEquals(before.invoke(null, x, a, b), after.invoke(null, x, a, b));
        }
    }
    private URLClassLoader compile(String source) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "classes-");
        Path file = directory.resolve("Calculation.java");
        Files.writeString(file, source, StandardCharsets.UTF_8);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-d", directory.toString(), file.toString()), source);
        return new URLClassLoader(new java.net.URL[] {directory.toUri().toURL()}, null);
    }
}
