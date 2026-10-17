/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Resource settings must survive the same profile round trip as the numerical contract. */
class MathSearchBudgetPreferencesTest {
    private static final String TIMEOUT = "cleanup.mathematics.timeoutSeconds";

    @Test void oldProfilesAcquireTheExistingFiveSecondLimit() {
        var options = MathCleanUpOptions.parse(Map.of(), 25);
        assertEquals("5", options.toMap().get(TIMEOUT));
        assertEquals(100_000, options.workBudget());
        assertEquals(2000, options.maxStates());
        assertFalse(options.enabled());
        assertEquals(SafetyProfile.PRESERVE_JAVA, options.safety());
    }

    @Test void customResourceLimitsSurviveProfileExportAndImport() {
        var values = new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(TIMEOUT, "17");
        values.put(MathCleanUpOptions.WORK_BUDGET, "750000");
        values.put(MathCleanUpOptions.MAX_STATES, "12000");
        var options = assertDoesNotThrow(() -> MathCleanUpOptions.parse(values, 17));
        assertEquals("17", options.toMap().get(TIMEOUT));
        assertEquals(750_000, options.workBudget());
        assertEquals(12_000, options.maxStates());
        assertEquals(options, MathCleanUpOptions.parse(options.toMap(), 17));
        assertEquals(SafetyProfile.PRESERVE_JAVA, options.safety());
        assertFalse(options.mathematicalOptIn());
        assertFalse(options.checkedOptIn());
    }

    @Test void timeLimitsAreFiniteAndValidatedWithoutClamping() {
        for (String seconds : List.of("1", "300")) {
            var options = assertDoesNotThrow(() -> MathCleanUpOptions.parse(Map.of(TIMEOUT, seconds), 25));
            assertEquals(seconds, options.toMap().get(TIMEOUT));
        }
        for (String invalid : List.of("", "0", "-1", "301", "1.5", "forever", "2147483648")) {
            assertThrows(IllegalArgumentException.class,
                    () -> MathCleanUpOptions.parse(Map.of(TIMEOUT, invalid), 25), invalid);
        }
    }

    @Test void existingProgrammaticCallersRetainTheSameLimit() {
        var options = new MathCleanUpOptions(true, Set.of(NumericKind.INT), SafetyProfile.PRESERVE_JAVA,
                OptimizationGoal.LOWER_ESTIMATED_RUNTIME, 100_000, 2000, false, 17, List.of());
        assertEquals("5", options.toMap().get(TIMEOUT));
        assertEquals(MathCleanUpOptions.ExplanationMode.NONE, options.explanations());
    }
}
