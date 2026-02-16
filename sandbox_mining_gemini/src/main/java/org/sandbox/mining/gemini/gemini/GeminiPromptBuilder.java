/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.gemini.gemini;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds prompts for the Gemini API combining DSL context,
 * existing categories, and commit diff information.
 */
public class GeminiPromptBuilder {

	private static final String DSL_EXPLANATION_RESOURCE = "/dsl-explanation.md";

	private String dslExplanation;

	public GeminiPromptBuilder() {
		this.dslExplanation = loadDslExplanation();
	}

	/**
	 * Builds a complete prompt for Gemini evaluation.
	 *
	 * @param dslContext       existing DSL rules context
	 * @param categoriesJson   existing categories as JSON
	 * @param diff             the commit diff
	 * @param commitMessage    the commit message
	 * @return the complete prompt string
	 */
	public String buildPrompt(String dslContext, String categoriesJson,
			String diff, String commitMessage) {
		StringBuilder sb = new StringBuilder();
		sb.append("Du bist ein Experte für Eclipse JDT Code-Transformationen und die TriggerPattern-DSL.\n\n");
		sb.append("## DSL-Erklärung\n");
		sb.append(dslExplanation).append("\n\n");
		sb.append("## Bestehende DSL-Regeln\n");
		sb.append(dslContext != null ? dslContext : "(keine)").append("\n\n");
		sb.append("## Bestehende Kategorien\n");
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n");
		sb.append("## Zu analysierender Commit\n\n");
		sb.append("### Commit-Nachricht\n");
		sb.append(commitMessage).append("\n\n");
		sb.append("### Diff\n```\n");
		sb.append(diff).append("\n```\n\n");
		sb.append("## Aufgabe\n");
		sb.append("Analysiere diesen Commit und bestimme, ob die Code-Änderung\n");
		sb.append("in eine wiederverwendbare TriggerPattern-DSL-Regel verallgemeinert werden kann.\n");
		sb.append("Antworte mit einem JSON-Objekt:\n\n");
		sb.append("{\n");
		sb.append("  \"relevant\": true/false,\n");
		sb.append("  \"irrelevantReason\": \"Grund falls nicht relevant\",\n");
		sb.append("  \"isDuplicate\": true/false,\n");
		sb.append("  \"duplicateOf\": \"Name der bestehenden Regel falls Duplikat\",\n");
		sb.append("  \"reusability\": 1-10,\n");
		sb.append("  \"codeImprovement\": 1-10,\n");
		sb.append("  \"implementationEffort\": 1-10,\n");
		sb.append("  \"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n");
		sb.append("  \"category\": \"Kategoriename\",\n");
		sb.append("  \"isNewCategory\": true/false,\n");
		sb.append("  \"categoryReason\": \"Warum diese Kategorie\",\n");
		sb.append("  \"canImplementInCurrentDsl\": true/false,\n");
		sb.append("  \"dslRule\": \"die DSL-Regel falls anwendbar\",\n");
		sb.append("  \"targetHintFile\": \"vorgeschlagener .sandbox-hint Dateiname\",\n");
		sb.append("  \"languageChangeNeeded\": \"welche DSL-Änderung nötig wäre\",\n");
		sb.append("  \"dslRuleAfterChange\": \"DSL-Regel nach Spracherweiterung\",\n");
		sb.append("  \"summary\": \"kurze Zusammenfassung der Analyse\"\n");
		sb.append("}\n\n");
		sb.append("Ampel-Bedeutungen:\n");
		sb.append("- GREEN: Direkt als DSL-Regel umsetzbar\n");
		sb.append("- YELLOW: Mit kleinen DSL-Erweiterungen umsetzbar\n");
		sb.append("- RED: Nicht im aktuellen oder absehbaren DSL umsetzbar\n");
		sb.append("- NOT_APPLICABLE: Commit ist nicht relevant für DSL-Mining\n");
		return sb.toString();
	}

	private static String loadDslExplanation() {
		try (InputStream is = GeminiPromptBuilder.class.getResourceAsStream(DSL_EXPLANATION_RESOURCE)) {
			if (is == null) {
				return "(DSL explanation not available)";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "(Failed to load DSL explanation)";
		}
	}
}
