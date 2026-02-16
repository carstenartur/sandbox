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
		sb.append("You are an expert in Eclipse JDT code transformations and the TriggerPattern DSL.\n\n");
		sb.append("## DSL Explanation\n");
		sb.append(dslExplanation).append("\n\n");
		sb.append("## Existing DSL Rules\n");
		sb.append(dslContext != null ? dslContext : "(none)").append("\n\n");
		sb.append("## Existing Categories\n");
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n");
		sb.append("## Commit to Analyze\n\n");
		sb.append("### Commit Message\n");
		sb.append(commitMessage).append("\n\n");
		sb.append("### Diff\n```\n");
		sb.append(diff).append("\n```\n\n");
		sb.append("## Task\n");
		sb.append("Analyze this commit and determine whether the code change\n");
		sb.append("can be generalized into a reusable TriggerPattern DSL rule.\n");
		sb.append("Respond with a JSON object:\n\n");
		sb.append("{\n");
		sb.append("  \"relevant\": true/false,\n");
		sb.append("  \"irrelevantReason\": \"reason if not relevant\",\n");
		sb.append("  \"isDuplicate\": true/false,\n");
		sb.append("  \"duplicateOf\": \"name of existing rule if duplicate\",\n");
		sb.append("  \"reusability\": 1-10,\n");
		sb.append("  \"codeImprovement\": 1-10,\n");
		sb.append("  \"implementationEffort\": 1-10,\n");
		sb.append("  \"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n");
		sb.append("  \"category\": \"category name\",\n");
		sb.append("  \"isNewCategory\": true/false,\n");
		sb.append("  \"categoryReason\": \"why this category\",\n");
		sb.append("  \"canImplementInCurrentDsl\": true/false,\n");
		sb.append("  \"dslRule\": \"the DSL rule if applicable\",\n");
		sb.append("  \"targetHintFile\": \"suggested .sandbox-hint filename\",\n");
		sb.append("  \"languageChangeNeeded\": \"what DSL change would be needed\",\n");
		sb.append("  \"dslRuleAfterChange\": \"DSL rule after language extension\",\n");
		sb.append("  \"summary\": \"brief summary of the analysis\"\n");
		sb.append("}\n\n");
		sb.append("Traffic light meanings:\n");
		sb.append("- GREEN: Directly implementable as a DSL rule\n");
		sb.append("- YELLOW: Implementable with minor DSL extensions\n");
		sb.append("- RED: Not implementable in current or foreseeable DSL\n");
		sb.append("- NOT_APPLICABLE: Commit is not relevant for DSL mining\n");
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
