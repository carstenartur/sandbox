/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.math.tests;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Diagnostic;
import com.fasterxml.jackson.databind.ObjectMapper;

class MathDiagnosticStageTest {
    @ParameterizedTest
    @CsvSource({"SIDE_EFFECT_BOUNDARY,SOURCE", "SOURCE_SLICE_CONNECTED,SOURCE", "NOIMPROVEMENT,SEARCH", "INCONCLUSIVE,SEARCH_OR_PROOF",
        "REVERIFICATION_FAILED,PROOF", "COMBINED_EDITS_INVALID,EMISSION", "NO_RUNTIME_IMPROVEMENT,COST",
        "BUDGET_EXCEEDED,BUDGET", "CANCELLED,CANCELLATION", "VERIFIED_CANDIDATE,RESULT", "FUTURE_DIAGNOSTIC,UNKNOWN"})
    void reportSeparatesObservedPipelineStagesWithoutPretendingToKnowFutureCodes(String code,String expected) throws Exception {
        var mapper=new ObjectMapper();
        String oldJson="{\"code\":\""+code+"\",\"message\":\"details\",\"offset\":7,\"length\":3}";
        var diagnostic=mapper.readValue(oldJson,Diagnostic.class);
        var json=mapper.readTree(mapper.writeValueAsString(diagnostic));
        assertEquals(expected,json.path("stage").asText());
        assertEquals(code,json.path("code").asText());
        assertEquals(diagnostic,mapper.readValue(json.toString(),Diagnostic.class));
    }
}
