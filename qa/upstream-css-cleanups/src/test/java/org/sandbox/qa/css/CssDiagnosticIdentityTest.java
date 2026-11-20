/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CssDiagnosticIdentityTest {
    @Test
    void formattingMayMoveAnExistingDuplicateSelector() {
        assertEquals(duplicate("error", "h2", 6), duplicate("error", "h2", 28));
        assertNotEquals(duplicate("error", "h2", 6), duplicate("error", "h3", 6));
        assertNotEquals(duplicate("error", "h2", 6), duplicate("warning", "h2", 6));
    }

    @Test
    void formattingMayMoveAnExistingSpecificityReference() {
        String rule = "no-descending-specificity";
        assertEquals(CssDiagnosticIdentity.of("error", rule, "Expected selector h1 to come before selector .title, at line 3 (" + rule + ")"),
                CssDiagnosticIdentity.of("error", rule, "Expected selector h1 to come before selector .title, at line 30 (" + rule + ")"));
    }

    @Test
    void otherNumbersAndRulesRemainPartOfTheDiagnostic() {
        assertNotEquals(CssDiagnosticIdentity.of("error", "color-no-invalid-hex", "Unexpected #12345"),
                CssDiagnosticIdentity.of("error", "color-no-invalid-hex", "Unexpected #1234567"));
        assertNotEquals(CssDiagnosticIdentity.of("error", "other-rule", "at line 3"),
                CssDiagnosticIdentity.of("error", "other-rule", "at line 30"));
    }

    private static CssDiagnosticIdentity duplicate(String severity, String selector, int firstLine) {
        return CssDiagnosticIdentity.of(severity, "no-duplicate-selectors",
                "Duplicate selector \"" + selector + "\", first used at line " + firstLine + " (no-duplicate-selectors)");
    }
}
