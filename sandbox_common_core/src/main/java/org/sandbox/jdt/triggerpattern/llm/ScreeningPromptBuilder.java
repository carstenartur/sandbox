/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.llm;

import java.util.List;

import org.sandbox.jdt.triggerpattern.llm.PromptBuilder.CommitData;

/**
 * Builds compact first-stage prompts for cheap commit screening.
 * <p>
 * The screening prompt intentionally avoids full DSL context and does not ask
 * for DSL rules or code examples. It is intended for Gemini free-tier friendly
 * mining runs where only promising commits should continue to the expensive
 * candidate-generation stage.
 * </p>
 */
public class ScreeningPromptBuilder {

	private static final int DEFAULT_MAX_DIFF_CHARS = 4_000;

	private final int maxDiffChars;

	/** Create a builder with the default diff truncation limit. */
	public ScreeningPromptBuilder() {
		this(DEFAULT_MAX_DIFF_CHARS);
	}

	/**
	 * Create a builder with an explicit diff truncation limit.
	 *
	 * @param maxDiffChars maximum characters of each diff included in the prompt
	 */
	public ScreeningPromptBuilder(int maxDiffChars) {
		if (maxDiffChars < 500) {
			throw new IllegalArgumentException("maxDiffChars must be at least 500"); //$NON-NLS-1$
		}
		this.maxDiffChars = maxDiffChars;
	}

	/**
	 * Builds a compact batch prompt for first-stage screening.
	 *
	 * @param commits commits to screen
	 * @return prompt text
	 */
	public String buildScreeningPrompt(List<CommitData> commits) {
		StringBuilder sb = new StringBuilder();
		sb.append("You are screening commits for reusable Java cleanup/refactoring patterns.\n"); //$NON-NLS-1$
		sb.append("Do not produce DSL rules. Do not produce before/after examples.\n"); //$NON-NLS-1$
		sb.append("Only decide whether each commit is worth a later, more expensive candidate-generation request.\n\n"); //$NON-NLS-1$
		sb.append("Return a JSON array with exactly ").append(commits.size()); //$NON-NLS-1$
		sb.append(" objects, one per commit, in the same order as presented.\n"); //$NON-NLS-1$
		sb.append("Schema per object:\n"); //$NON-NLS-1$
		sb.append("{\n"); //$NON-NLS-1$
		sb.append("  \"commitHash\": \"hash\",\n"); //$NON-NLS-1$
		sb.append("  \"relevant\": true,\n"); //$NON-NLS-1$
		sb.append("  \"trafficLight\": \"GREEN|YELLOW|RED|NOT_APPLICABLE\",\n"); //$NON-NLS-1$
		sb.append("  \"category\": \"short category or null\",\n"); //$NON-NLS-1$
		sb.append("  \"confidence\": 0.0,\n"); //$NON-NLS-1$
		sb.append("  \"reason\": \"one short sentence\"\n"); //$NON-NLS-1$
		sb.append("}\n"); //$NON-NLS-1$
		sb.append("confidence MUST be a decimal between 0.0 and 1.0 (not 0–100).\n\n"); //$NON-NLS-1$
		for (int i = 0; i < commits.size(); i++) {
			CommitData commit = commits.get(i);
			sb.append("## Commit ").append(i).append(" (").append(commit.commitHash()).append(")\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sb.append("Message:\n").append(commit.commitMessage()).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("Diff:\n```\n"); //$NON-NLS-1$
			sb.append(truncate(commit.diff())).append("\n```\n\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	private String truncate(String diff) {
		if (diff == null) {
			return ""; //$NON-NLS-1$
		}
		if (diff.length() <= maxDiffChars) {
			return diff;
		}
		return diff.substring(0, maxDiffChars) + "\n... [diff truncated for screening]"; //$NON-NLS-1$
	}
}
