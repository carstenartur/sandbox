/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.List;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

class SourceComputationSliceTest {
    private static final String HEADER = "class Sample { static void audit() {} static int f(int a, int b, int c) {\n";
    private static final String BODY = "  int left = a * b;\n  audit(); // keep this call and comment\n  int right = a * c;\n  return left + right;\n";

    @Test void contiguousControlHasAGenuineAlgebraicCandidate() throws Exception {
        var result = analyze(HEADER + BODY.replace("  audit(); // keep this call and comment\n", "") + "}}", false);
        assertTrue(result.changed(), result.diagnostics().toString());
    }

    @Test void connectsLocalValuesAcrossAnUnchangedCallAndUsesSeveralSmallEdits() throws Exception {
        String source = HEADER + BODY + "}}";
        var result = analyze(source, false);
        assertTrue(result.changed(), result.diagnostics().toString());
        assertEquals(1, result.evidence().size(), "One jointly proved computation, not three independent rewrites");
        var edit = result.newEdit();
        assertTrue(leaves(edit).size() >= 2, "Deletion and output change must remain separate small edits");
        int gapStart = source.indexOf("audit(); //"), gapEnd = source.indexOf('\n', gapStart);
        for (var leaf : leaves(edit)) assertFalse(leaf.getOffset() < gapEnd && leaf.getExclusiveEnd() > gapStart,
                "No edit may include the unchanged call/comment: " + leaf);
        Document doc = new Document(source);
        var undo = edit.apply(doc, TextEdit.CREATE_UNDO);
        assertTrue(doc.get().contains("audit(); // keep this call and comment"));
        assertEquals(1, doc.get().split("audit\\(\\);", -1).length - 1);
        assertFalse(doc.get().contains("int left"), doc.get());
        assertFalse(doc.get().contains("int right"), doc.get());
        parse(doc.get());
        assertFalse(analyze(doc.get(), false).changed(), "The complete rewrite is idempotent");
        undo.apply(doc); assertEquals(source, doc.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"audit(left)", "audit(b++)", "audit(b = 7)"})
    void doesNotMoveCalculationsAcrossObservedValuesOrAssignments(String gap) {
        String source = "class Sample { static void audit(int x) {} static int f(int a,int b,int c) {"
                + "int left=a*b; " + gap + "; int right=a*c; return left+right; }}";
        var result = analyze(source, false);
        assertFalse(result.changed(), result.replacements().toString());
    }

    @Test void doesNotMovePotentiallyThrowingArithmetic() {
        String source = HEADER + "int left=a/b; audit(); int right=a*c; return left+right; }}";
        assertFalse(analyze(source, false).changed());
    }

    @Test void unknownAliasingDoesNotMergeTwoArrayReads() {
        String source = "class Sample { static void audit(int[] x) { x[0]++; } static int f(int[] x,int b,int c) {"
                + "int left=x[0]*b; audit(x); int right=x[0]*c; return left+right; }}";
        var result = analyze(source, false);
        assertFalse(result.replacements().stream().anyMatch(r -> r.offset() <= source.indexOf("int left")
                && r.offset() + r.length() >= source.indexOf("return")), result.replacements().toString());
    }

    @Test void cancellationDoesNotReturnPartiallyApplicableChanges() {
        String source = HEADER + BODY + "}}";
        var monitor = new NullProgressMonitor(); monitor.setCanceled(true);
        var result = MathematicalAnalysis.analyze(parse(source), source, options(false), monitor, -1, 0);
        assertFalse(result.changed());
    }

    static MathematicalAnalysis.Analysis analyze(String source, boolean mathematical) {
        return MathematicalAnalysis.analyze(parse(source), source, options(mathematical), new NullProgressMonitor(), -1, 0);
    }
    static MathCleanUpOptions options(boolean mathematical) {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP, "true"); values.put(MathCleanUpOptions.KINDS, "BYTE,SHORT,CHAR,INT,LONG");
        values.put(MathCleanUpOptions.WORK_BUDGET, "1000000"); values.put(MathCleanUpOptions.MAX_STATES, "20000");
        values.put(MathCleanUpOptions.EXPLANATIONS, "NONE"); values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN, Boolean.toString(mathematical));
        return MathCleanUpOptions.parse(values, 17);
    }
    static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName("Sample.java"); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var settings = new HashMap<String,String>(); JavaCore.setComplianceOptions("17", settings); parser.setCompilerOptions(settings);
        var unit = (CompilationUnit) parser.createAST(null);
        for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem.toString());
        return unit;
    }
    static List<TextEdit> leaves(TextEdit edit) {
        if (!edit.hasChildren()) return List.of(edit);
        return java.util.Arrays.stream(edit.getChildren()).flatMap(child -> leaves(child).stream()).toList();
    }
}
