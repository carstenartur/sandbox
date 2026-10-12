/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;
import com.fasterxml.jackson.databind.ObjectMapper;

class MathematicalReviewJsonTest {
    @Test void serializedReportRetainsEvidenceAndAddsSeparateReviewMetadata() throws Exception {
        var file = new MathematicalReport.FileReport("Sample.java", 17, MathCleanUpOptions.defaults(17).toMap(),
                "source", "after", "return x;", "return x;", Map.of(), "environment", List.of(), List.of(),
                List.of(new VerifiedRegion(0, 9, null, null, "return x;", "return x;")), false, "source", "ORIGINAL");
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(file));
        assertEquals(1, json.path("evidence").size());
        assertEquals(1, json.path("reviewCandidates").size());
        assertEquals("UNASSESSED", json.path("reviewCandidates").get(0).path("assessment").path("disposition").asText());
        assertFalse(json.path("applied").asBoolean());
        assertEquals("ORIGINAL", json.path("applicationStatus").asText());
        assertEquals(file.reviewCandidates(), file.withObserved("return x;").reviewCandidates());
        var decoded = new ObjectMapper().readValue(json.toString(), MathematicalReport.FileReport.class);
        assertEquals(file.evidence(), decoded.evidence());
        assertEquals(file.reviewCandidates(), decoded.reviewCandidates());
    }
}
