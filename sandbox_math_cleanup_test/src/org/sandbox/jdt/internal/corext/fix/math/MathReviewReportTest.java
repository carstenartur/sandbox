/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

class MathReviewReportTest {
    @Test void ranksActualVerifiedBitwiseEditsWithoutChangingTheAnalysis() throws Exception {
        String source = "class Sample { int f(int u,int v,int w) { return (u & v) | (~u & w); } }";
        var options = options();
        var analysis = MathematicalAnalysis.analyze(parse(source), source, options, new NullProgressMonitor(), -1, 0);
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        var changes = List.copyOf(analysis.replacements());
        var ranked = MathReviewReport.ranked(analysis.evidence(), options);
        assertEquals(analysis.evidence().size(), ranked.size());
        assertEquals("COMPACT_REVIEW", ranked.getFirst().assessment().disposition());
        assertEquals(analysis.evidence().getFirst().offset(), ranked.getFirst().offset());
        assertEquals(changes, analysis.replacements(), "Ranking is not a selection or safety gate");
        String json = new ObjectMapper().writeValueAsString(ranked);
        assertTrue(json.contains("ESTIMATE_NOT_BENCHMARK"));
        assertTrue(json.contains("TIMING_BEHAVIOR_NOT_VERIFIED"));
    }
    @Test void legacyEvidenceWithoutAnExplanationIsExplicitlyUnassessed() {
        var evidence = new MathematicalAnalysis.VerifiedRegion(3, 9, null, null, "return x;", "return x;");
        var assessment = MathReviewReport.ranked(List.of(evidence), options()).getFirst().assessment();
        assertEquals("UNASSESSED", assessment.disposition());
        assertEquals(-1, assessment.changedLines());
        assertTrue(assessment.cautions().contains("INSUFFICIENT_REVIEW_METADATA"));
    }
    @Test void malformedReviewTextDoesNotDiscardVerifiedReportEvidence() {
        String source = "class Sample { int f(int u,int v,int w) { return (u & v) | (~u & w); } }";
        var analysis = MathematicalAnalysis.analyze(parse(source), source, options(), new NullProgressMonitor(), -1, 0);
        var actual = analysis.evidence().getFirst();
        var malformed = new MathematicalAnalysis.VerifiedRegion(actual.offset(), actual.length(), actual.proof(), actual.cost(),
                actual.original(), "return ( ;", actual.explanation());
        var ranked = MathReviewReport.ranked(List.of(malformed), options());
        assertEquals("UNASSESSED", ranked.getFirst().assessment().disposition());
        assertTrue(ranked.getFirst().assessment().cautions().contains("REVIEW_SOURCE_NOT_PARSED"));
    }
    private static MathCleanUpOptions options() {
        return new MathCleanUpOptions(true, EnumSet.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 1_000_000, 20_000, false, 17, List.of());
    }
    private static CompilationUnit parse(String source) {
        var parser = ASTParser.newParser(AST.getJLSLatest()); parser.setUnitName("Sample.java"); parser.setSource(source.toCharArray());
        parser.setEnvironment(new String[0], new String[0], null, true); parser.setResolveBindings(true);
        var options = new HashMap<String, String>(); JavaCore.setComplianceOptions("17", options); parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }
}
