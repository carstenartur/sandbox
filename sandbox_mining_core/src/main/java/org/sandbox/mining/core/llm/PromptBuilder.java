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

	private static final String DSL_EXPLANATION_RESOURCE = "/dsl-explanation.md"; //$NON-NLS-1$
	private static final String EXISTING_PLUGINS_RESOURCE = "/existing-java-plugins.md"; //$NON-NLS-1$

	private String dslExplanation;
	private String existingPluginsContext;

	public PromptBuilder() {
		this.dslExplanation = loadResource(DSL_EXPLANATION_RESOURCE);
		this.existingPluginsContext = loadResource(EXISTING_PLUGINS_RESOURCE);
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
		return buildPrompt(dslContext, categoriesJson, diff, commitMessage, null);
	}

	/**
	 * Builds a complete prompt for LLM evaluation with optional previously discovered rules.
	 *
	 * @param dslContext        existing DSL rules context
	 * @param categoriesJson    existing categories as JSON
	 * @param diff              the commit diff
	 * @param commitMessage     the commit message
	 * @param previousResults   JSON array of previously discovered rules (from evaluations.json), or null
	 * @return the complete prompt string
	 */
	public String buildPrompt(String dslContext, String categoriesJson,
			String diff, String commitMessage, String previousResults) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are an expert in Eclipse JDT code transformations and the TriggerPattern DSL.\n\n"); //$NON-NLS-1$
		sb.append("## DSL Explanation\n"); //$NON-NLS-1$
		sb.append(dslExplanation).append("\n\n"); //$NON-NLS-1$
		sb.append("## Existing DSL Rules\n"); //$NON-NLS-1$
		sb.append(dslContext != null ? dslContext : "(none)").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Existing Categories\n"); //$NON-NLS-1$
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		appendExistingPluginsSection(sb);
		appendPreviousResultsSection(sb, previousResults);
		sb.append("## Commit to Analyze\n\n"); //$NON-NLS-1$
		sb.append("### Commit Message\n"); //$NON-NLS-1$
		sb.append(commitMessage).append("\n\n"); //$NON-NLS-1$
		sb.append("### Diff\n```\n"); //$NON-NLS-1$
		sb.append(diff).append("\n```\n\n"); //$NON-NLS-1$
		appendTaskSection(sb);
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
		return buildBatchPrompt(dslContext, categoriesJson, commits, null);
	}

	/**
	 * Builds a batch prompt with optional previously discovered rules.
	 *
	 * @param dslContext       existing DSL rules context
	 * @param categoriesJson   existing categories as JSON
	 * @param commits          list of commits to evaluate
	 * @param previousResults  JSON array of previously discovered rules, or null
	 * @return the complete batch prompt string
	 */
	public String buildBatchPrompt(String dslContext, String categoriesJson,
			List<CommitData> commits, String previousResults) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are an expert in Eclipse JDT code transformations and the TriggerPattern DSL.\n\n"); //$NON-NLS-1$
		sb.append("## DSL Explanation\n"); //$NON-NLS-1$
		sb.append(dslExplanation).append("\n\n"); //$NON-NLS-1$
		sb.append("## Existing DSL Rules\n"); //$NON-NLS-1$
		sb.append(dslContext != null ? dslContext : "(none)").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("## Existing Categories\n"); //$NON-NLS-1$
		sb.append(categoriesJson != null ? categoriesJson : "[]").append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		appendExistingPluginsSection(sb);
		appendPreviousResultsSection(sb, previousResults);
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
		appendJsonSchema(sb, true);
		appendTrafficLightMeanings(sb);
		return sb.toString();
	}

	private void appendExistingPluginsSection(StringBuilder sb) {
		sb.append("## Existing Java-Based Cleanup Plugins\n\n"); //$NON-NLS-1$
		sb.append(existingPluginsContext).append("\n\n"); //$NON-NLS-1$
	}

	private static void appendPreviousResultsSection(StringBuilder sb, String previousResults) {
		if (previousResults != null && !previousResults.isBlank()) {
			sb.append("## Previously Discovered Rules\n\n"); //$NON-NLS-1$
			sb.append("The following rules have already been discovered in prior mining runs. "); //$NON-NLS-1$
			sb.append("Do NOT re-propose identical rules. If you see a similar pattern, "); //$NON-NLS-1$
			sb.append("acknowledge it with `\"previouslyProposed\": \"<rule summary>\"` and explain "); //$NON-NLS-1$
			sb.append("how your proposal differs or improves on it.\n\n"); //$NON-NLS-1$
			sb.append(previousResults).append("\n\n"); //$NON-NLS-1$
		}
	}

	private void appendTaskSection(StringBuilder sb) {
		sb.append("## Task\n"); //$NON-NLS-1$
		sb.append("Analyze this commit and determine whether the code change\n"); //$NON-NLS-1$
		sb.append("can be generalized into a reusable TriggerPattern DSL rule.\n\n"); //$NON-NLS-1$
		sb.append("IMPORTANT: The dslRule field must contain plain DSL text only.\n"); //$NON-NLS-1$
		sb.append("Never use <trigger>, <import>, <pattern>, or any XML tags.\n"); //$NON-NLS-1$
		sb.append("Never use isType() — use instanceof($var, \"TypeName\") instead.\n\n"); //$NON-NLS-1$
		sb.append("Respond with a JSON object:\n\n"); //$NON-NLS-1$
		appendJsonSchema(sb, false);
		appendTrafficLightMeanings(sb);
	}

	private static void appendJsonSchema(StringBuilder sb, boolean asArray) {
		if (asArray) {
			sb.append("[\n  {\n"); //$NON-NLS-1$
		} else {
			sb.append("{\n"); //$NON-NLS-1$
		}
		String indent = asArray ? "    " : "  "; //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(indent).append("\"relevant\": true/false,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"irrelevantReason\": \"reason if not relevant\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"isDuplicate\": true/false,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"duplicateOf\": \"name of existing rule if duplicate\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"reusability\": 1-10,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"codeImprovement\": 1-10,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"implementationEffort\": 1-10,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"category\": \"category name\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"isNewCategory\": true/false,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"categoryReason\": \"why this category\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"canImplementInCurrentDsl\": true/false,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"dslRule\": \"raw .sandbox-hint rule (NO <trigger> tags, NO <import> tags, NO XML)\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"targetHintFile\": \"suggested .sandbox-hint filename\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"languageChangeNeeded\": \"what DSL change would be needed\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"dslRuleAfterChange\": \"DSL rule after language extension\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"existsAsJavaPlugin\": true/false,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"replacesPlugin\": \"name of Java plugin this would replace, or null\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"previouslyProposed\": \"summary of similar prior rule, or null\",\n"); //$NON-NLS-1$
		sb.append(indent).append("\"sourceVersion\": 11,\n"); //$NON-NLS-1$
		sb.append(indent).append("\"summary\": \"brief summary of the analysis\"\n"); //$NON-NLS-1$
		if (asArray) {
			sb.append("  },\n  ...\n]\n\n"); //$NON-NLS-1$
		} else {
			sb.append("}\n\n"); //$NON-NLS-1$
		}
	}

	private static void appendTrafficLightMeanings(StringBuilder sb) {
		sb.append("Traffic light meanings:\n"); //$NON-NLS-1$
		sb.append("- GREEN: Directly implementable as a DSL rule\n"); //$NON-NLS-1$
		sb.append("- YELLOW: Implementable with minor DSL extensions\n"); //$NON-NLS-1$
		sb.append("- RED: Requires DSL extensions not yet available (may be supported in future DSL versions)\n"); //$NON-NLS-1$
		sb.append("- NOT_APPLICABLE: Commit is not relevant for DSL mining\n"); //$NON-NLS-1$
	}

	private static String loadResource(String resourcePath) {
		try (InputStream is = PromptBuilder.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				return "(Resource not available: " + resourcePath + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "(Failed to load resource: " + resourcePath + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
