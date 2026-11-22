/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.qa.css;

/** Keeps diagnostic meaning and severity while excluding formatter-moved locations. */
public record CssDiagnosticIdentity(String severity, String rule, String message) {
    public static CssDiagnosticIdentity of(String severity, String rule, String message) {
        String stable = switch (rule) {
            case "no-duplicate-selectors" -> message.replaceFirst(
                    ", first used at line [0-9]+(?= \\(no-duplicate-selectors\\)$)", ", first used at line <line>");
            case "no-descending-specificity" -> message.replaceFirst(
                    ", at line [0-9]+(?= \\(no-descending-specificity\\)$)", ", at line <line>");
            default -> message;
        };
        return new CssDiagnosticIdentity(severity, rule, stable);
    }
}
