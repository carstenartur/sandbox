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
package org.sandbox.mining.core.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds prompts combining DSL context,
 * existing categories, and commit diff information.
 */
public class PromptBuilder {

	/**
	 * Holds data for a single commit to be included in a batch prompt.
	 *
	 * @param commitHash    the commit hash
	 * @param commitMessage the commit message
	 * @param diff          the commit diff
	 */
	public record CommitData(String commitHash, String commitMessage, String diff) {
	}

	private static final String DSL_EXPLANATION_RESOURCE = "/dsl-explanation.md";

	private String dslExplanation;

	public PromptBuilder() {
		this.dslExplanation = loadDslExplanation();
	}

	/**
	 * Builds a complete prompt for LLM evaluation.
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
		sb.append("You are an expert in Eclipse JDT code transformations and the TriggerPattern DSL.\n\n"); //$NON-NLS-1$
		sb.append("## DSL Explanation\n"); //$NON-NLS-1$
		sb.append(dslExplanation).append("\n\n"); //$NON-NLS-1$
		sb.append("## Existing DSL Rules\n"); //$NON-NLS-1$
		sb.append(dslContext != null ? dslContext : "(none)").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Existing Categories\n"); //$NON-NLS-1$
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Commit to Analyze\n\n"); //$NON-NLS-1$
		sb.append("### Commit Message\n"); //$NON-NLS-1$
		sb.append(commitMessage).append("\n\n"); //$NON-NLS-1$
		sb.append("### Diff\n```\n"); //$NON-NLS-1$
		sb.append(diff).append("\n```\n\n"); //$NON-NLS-1$
		sb.append("## Task\n"); //$NON-NLS-1$
		sb.append("Analyze this commit and determine whether the code change\n"); //$NON-NLS-1$
		sb.append("can be generalized into a reusable TriggerPattern DSL rule.\n"); //$NON-NLS-1$
		sb.append("Respond with a JSON object:\n\n"); //$NON-NLS-1$
		sb.append("{\n"); //$NON-NLS-1$
		sb.append("  \"relevant\": true/false,\n"); //$NON-NLS-1$
		sb.append("  \"irrelevantReason\": \"reason if not relevant\",\n"); //$NON-NLS-1$
		sb.append("  \"isDuplicate\": true/false,\n"); //$NON-NLS-1$
		sb.append("  \"duplicateOf\": \"name of existing rule if duplicate\",\n"); //$NON-NLS-1$
		sb.append("  \"reusability\": 1-10,\n"); //$NON-NLS-1$
		sb.append("  \"codeImprovement\": 1-10,\n"); //$NON-NLS-1$
		sb.append("  \"implementationEffort\": 1-10,\n"); //$NON-NLS-1$
		sb.append("  \"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n"); //$NON-NLS-1$
		sb.append("  \"category\": \"category name\",\n"); //$NON-NLS-1$
		sb.append("  \"isNewCategory\": true/false,\n"); //$NON-NLS-1$
		sb.append("  \"categoryReason\": \"why this category\",\n"); //$NON-NLS-1$
		sb.append("  \"canImplementInCurrentDsl\": true/false,\n"); //$NON-NLS-1$
		sb.append("  \"dslRule\": \"the DSL rule if applicable\",\n"); //$NON-NLS-1$
		sb.append("  \"targetHintFile\": \"suggested .sandbox-hint filename\",\n"); //$NON-NLS-1$
		sb.append("  \"languageChangeNeeded\": \"what DSL change would be needed\",\n"); //$NON-NLS-1$
		sb.append("  \"dslRuleAfterChange\": \"DSL rule after language extension\",\n"); //$NON-NLS-1$
		sb.append("  \"summary\": \"brief summary of the analysis\"\n"); //$NON-NLS-1$
		sb.append("}\n\n"); //$NON-NLS-1$
		sb.append("Traffic light meanings:\n"); //$NON-NLS-1$
		sb.append("- GREEN: Directly implementable as a DSL rule\n"); //$NON-NLS-1$
		sb.append("- YELLOW: Implementable with minor DSL extensions\n"); //$NON-NLS-1$
		sb.append("- RED: Not implementable in current or foreseeable DSL\n"); //$NON-NLS-1$
		sb.append("- NOT_APPLICABLE: Commit is not relevant for DSL mining\n"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Builds a batch prompt for evaluating multiple commits in a single API call.
	 *
	 * @param dslContext     existing DSL rules context
	 * @param categoriesJson existing categories as JSON
	 * @param commits        list of commits to evaluate
	 * @return the complete batch prompt string
	 */
	public String buildBatchPrompt(String dslContext, String categoriesJson,
			List<CommitData> commits) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are an expert in Eclipse JDT code transformations and the TriggerPattern DSL.\n\n"); //$NON-NLS-1$
		sb.append("## DSL Explanation\n"); //$NON-NLS-1$
		sb.append(dslExplanation).append("\n\n"); //$NON-NLS-1$
		sb.append("## Existing DSL Rules\n"); //$NON-NLS-1$
		sb.append(dslContext != null ? dslContext : "(none)").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Existing Categories\n"); //$NON-NLS-1$
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Commits to Analyze\n\n"); //$NON-NLS-1$
		for (int i = 0; i < commits.size(); i++) {
			CommitData cd = commits.get(i);
			sb.append("### Commit ").append(i).append(" (").append(cd.commitHash()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sb.append("#### Message\n"); //$NON-NLS-1$
			sb.append(cd.commitMessage()).append("\n\n"); //$NON-NLS-1$
			sb.append("#### Diff\n```\n"); //$NON-NLS-1$
			sb.append(cd.diff()).append("\n```\n\n"); //$NON-NLS-1$
		}
		sb.append("## Task\n"); //$NON-NLS-1$
		sb.append("Analyze each commit above and determine whether its code change\n"); //$NON-NLS-1$
		sb.append("can be generalized into a reusable TriggerPattern DSL rule.\n\n"); //$NON-NLS-1$
		sb.append("Return a JSON array with exactly ").append(commits.size()); //$NON-NLS-1$
		sb.append(" evaluation objects, one per commit, in the same order as presented.\n"); //$NON-NLS-1$
		sb.append("Each object has the same schema as before:\n\n"); //$NON-NLS-1$
		sb.append("[\n  {\n"); //$NON-NLS-1$
		sb.append("    \"relevant\": true/false,\n"); //$NON-NLS-1$
		sb.append("    \"irrelevantReason\": \"reason if not relevant\",\n"); //$NON-NLS-1$
		sb.append("    \"isDuplicate\": true/false,\n"); //$NON-NLS-1$
		sb.append("    \"duplicateOf\": \"name of existing rule if duplicate\",\n"); //$NON-NLS-1$
		sb.append("    \"reusability\": 1-10,\n"); //$NON-NLS-1$
		sb.append("    \"codeImprovement\": 1-10,\n"); //$NON-NLS-1$
		sb.append("    \"implementationEffort\": 1-10,\n"); //$NON-NLS-1$
		sb.append("    \"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n"); //$NON-NLS-1$
		sb.append("    \"category\": \"category name\",\n"); //$NON-NLS-1$
		sb.append("    \"isNewCategory\": true/false,\n"); //$NON-NLS-1$
		sb.append("    \"categoryReason\": \"why this category\",\n"); //$NON-NLS-1$
		sb.append("    \"canImplementInCurrentDsl\": true/false,\n"); //$NON-NLS-1$
		sb.append("    \"dslRule\": \"the DSL rule if applicable\",\n"); //$NON-NLS-1$
		sb.append("    \"targetHintFile\": \"suggested .sandbox-hint filename\",\n"); //$NON-NLS-1$
		sb.append("    \"languageChangeNeeded\": \"what DSL change would be needed\",\n"); //$NON-NLS-1$
		sb.append("    \"dslRuleAfterChange\": \"DSL rule after language extension\",\n"); //$NON-NLS-1$
		sb.append("    \"summary\": \"brief summary of the analysis\"\n"); //$NON-NLS-1$
		sb.append("  },\n  ...\n]\n\n"); //$NON-NLS-1$
		sb.append("Traffic light meanings:\n"); //$NON-NLS-1$
		sb.append("- GREEN: Directly implementable as a DSL rule\n"); //$NON-NLS-1$
		sb.append("- YELLOW: Implementable with minor DSL extensions\n"); //$NON-NLS-1$
		sb.append("- RED: Not implementable in current or foreseeable DSL\n"); //$NON-NLS-1$
		sb.append("- NOT_APPLICABLE: Commit is not relevant for DSL mining\n"); //$NON-NLS-1$
		return sb.toString();
	}

	private static String loadDslExplanation() {
		try (InputStream is = PromptBuilder.class.getResourceAsStream(DSL_EXPLANATION_RESOURCE)) {
			if (is == null) {
				return "(DSL explanation not available)";
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "(Failed to load DSL explanation)";
		}
	}
}
