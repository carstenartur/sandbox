/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.regelsuche.sdk.optimization.ComputationExplanations;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.ComputationExplanation;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;

/** Explanations expose the actual verified computation, never a named example's solution. */
class VerifiedComputationExplanationTest {
    @Test void ordinaryJavaEvidenceIncludesTheVerifiedPlansAndOriginalJavaNames() throws Exception {
        String source = "public class Calculation { public static int compute(int position,int scale,int delta) {"
                + " return position*(scale+delta)-position*scale; }}";
        var analysis = analyze(source, SafetyProfile.PRESERVE_JAVA, new NullProgressMonitor());
        JsonNode explanation = explanation(analysis);
        Set<String> names = new HashSet<>();
        explanation.path("inputNames").elements().forEachRemaining(n -> names.add(n.asText()));
        assertEquals(Set.of("position", "scale", "delta"), names);
        assertEquals("return value", explanation.path("outputNames").elements().next().asText());
        var verified = explanation.path("verified");
        // Compare JSON with the same numeric-node construction on both sides;
        // separately retain exact typed equality of the immutable proof records.
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree(mapper.writeValueAsString(analysis.evidence().getFirst().proof())), verified.path("proof"));
        assertEquals(analysis.evidence().getFirst().proof(), analysis.evidence().getFirst().explanation().verified().proof());
        assertTrue(verified.path("original").path("steps").size() > verified.path("replacement").path("steps").size());
        assertFalse(verified.path("proof").path("proofMethods").isEmpty());
        var derivation = analysis.evidence().getFirst().explanation().verified().derivation().orElseThrow();
        assertEquals(mapper.readTree(mapper.writeValueAsString(derivation)), explanation.path("derivation"));
        assertFalse(explanation.path("derivation").path("steps").isEmpty());
        assertFalse(explanation.toString().contains("BouncyCastle"));
        Document document = new Document(source);
        var undo = analysis.newEdit().apply(document, TextEdit.CREATE_UNDO);
        assertFalse(document.get().contains("//"), "Report integration must not silently introduce source comments");
        assertFalse(document.get().contains("de.regelsuche"));
        undo.apply(document);
        assertEquals(source, document.get());
        assertTrue(analysis.replacements().stream().allMatch(r -> r.description().contains("Original values:")
                && r.description().contains("Recorded search path:")),
                "Source-comment policy must not hide the detailed verified preview");
    }

    @Test void helperCallsUseTheSameExplanationPathWithoutAnExpectedFormula() throws Exception {
        String source = "public class Calculation { private static int join(int p,int q){return p+q;}"
                + " public static int compute(int x,int a,int b){return x*join(a,b)-x*a;} }";
        var analysis = analyze(source, SafetyProfile.PRESERVE_JAVA, new NullProgressMonitor());
        var json = explanation(analysis);
        assertTrue(json.path("verified").path("original").path("steps").size() >
                json.path("verified").path("replacement").path("steps").size());
        assertTrue(analysis.evidence().getFirst().original().contains("join"));
        assertFalse(analysis.evidence().getFirst().replacement().contains("join"));
    }

    @Test void checkedExplanationRetainsItsExplicitNumericalObligations() throws Exception {
        String source = "public class Calculation { public static int compute(int x) {return (x+1)-1;} }";
        var analysis = analyze(source, SafetyProfile.CHECKED_THROW, new NullProgressMonitor());
        var json = explanation(analysis).path("verified");
        assertEquals("CHECKED_THROW", json.path("proof").path("safetyProfile").asText());
        assertTrue(json.path("obligations").path("checkIntegralRange").asBoolean());
        assertTrue(analysis.evidence().getFirst().replacement().contains("Math.addExact"));
    }

    @Test void cancellationPublishesNeitherChangesNorExplanationEvidence() {
        String source = "public class Calculation { public static int compute(int x,int a,int b){return x*(a+b)-x*a;} }";
        var monitor = new NullProgressMonitor(); monitor.setCanceled(true);
        var analysis = analyze(source, SafetyProfile.PRESERVE_JAVA, monitor);
        assertFalse(analysis.changed());
        assertTrue(analysis.evidence().isEmpty());
    }

    @Test void reportExplanationDoesNotReenableConstantFolding() {
        String source = "public class Calculation { public static int compute(){return 5*7*11;} }";
        var analysis = analyze(source, SafetyProfile.PRESERVE_JAVA, new NullProgressMonitor());
        assertFalse(analysis.changed());
        assertTrue(analysis.evidence().isEmpty());
    }

    @Test void historicalValueViewsDoNotInventARecordedSearchPath() throws Exception {
        String source = "public class Calculation { public static int compute(int x) {return x+0;} }";
        var current = analyze(source, SafetyProfile.PRESERVE_JAVA, new NullProgressMonitor())
                .evidence().getFirst().explanation();
        var verified = current.verified();
        var historical = new ComputationExplanations.Explanation(verified.proof(), verified.assumptions(),
                verified.obligations(), verified.original(), verified.replacement(),
                verified.originalModularPowers(), verified.replacementModularPowers(), verified.presentationWork());
        var report = new ComputationExplanation(current.inputNames(), current.outputNames(),
                current.guardedReceivers(), historical);
        var mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(mapper.writeValueAsString(report));
        assertTrue(report.verified().derivation().isEmpty());
        assertTrue(json.has("derivation"));
        assertTrue(json.path("derivation").isNull());
        assertEquals(mapper.readTree(mapper.writeValueAsString(verified.proof())), json.path("verified").path("proof"));
    }

    private static JsonNode explanation(MathematicalAnalysis.Analysis analysis) throws Exception {
        assertTrue(analysis.changed(), analysis.diagnostics().toString());
        assertFalse(analysis.evidence().isEmpty());
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(analysis.evidence().getFirst()));
        assertTrue(json.hasNonNull("explanation"), "A verified source edit needs its SDK explanation in the report: " + json);
        return json.path("explanation");
    }

    private static MathematicalAnalysis.Analysis analyze(String source, SafetyProfile safety, NullProgressMonitor monitor) {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT, NumericKind.LONG), safety,
                safety == SafetyProfile.CHECKED_THROW ? OptimizationGoal.READABILITY : OptimizationGoal.LOWER_ESTIMATED_RUNTIME,
                2_000_000L, 20_000, safety == SafetyProfile.CHECKED_THROW, 17, List.of());
        return MathematicalAnalysis.analyze(MathTestSupport.parse(source), source, options, monitor, -1, 0);
    }
}
