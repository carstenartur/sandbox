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
package org.sandbox.jdt.triggerpattern.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds discovery-first prompts for one focused TriggerPattern candidate or an
 * explicit no-cleanup decision. Broad architectural scoring and plugin analysis
 * are deliberately excluded from the normal mining request.
 */
public class PromptBuilder {

	/** Commit data included in a discovery request. */
	public record CommitData(String commitHash, String commitMessage, String diff) {
	}

	private static final String DSL_EXPLANATION_RESOURCE = "/dsl-explanation.md"; //$NON-NLS-1$

	private final String dslExplanation;
	private String typeContext;
	private String errorFeedback;

	public PromptBuilder() {
		this.dslExplanation = loadResource(DSL_EXPLANATION_RESOURCE);
	}

	/** Sets optional deterministic type context extracted from the current diff. */
	public void setTypeContext(String typeContext) {
		this.typeContext = typeContext;
	}

	/** Sets concise feedback about recurring response errors. */
	public void setErrorFeedback(String errorFeedback) {
		this.errorFeedback = errorFeedback;
	}

	/** Builds a prompt for one commit. */
	public String buildPrompt(String dslContext, String categoriesJson,
			String diff, String commitMessage) {
		return buildPrompt(dslContext, categoriesJson, diff, commitMessage, null);
	}

	/**
	 * Builds a prompt for one commit. Legacy context parameters are retained for
	 * binary/source compatibility but duplicate rejection is performed
	 * deterministically after generation rather than by sending all known rules to
	 * the model.
	 */
	public String buildPrompt(String dslContext, String categoriesJson,
			String diff, String commitMessage, String previousResults) {
		StringBuilder prompt = new StringBuilder();
		appendHeader(prompt);
		appendOptionalContext(prompt);
		prompt.append("## Commit to analyze\n\n"); //$NON-NLS-1$
		prompt.append("### Message\n").append(nullToEmpty(commitMessage)).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		prompt.append("### Diff\n```diff\n").append(nullToEmpty(diff)).append("\n```\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		appendTask(prompt, false, 1);
		return prompt.toString();
	}

	/** Builds a batch discovery prompt. */
	public String buildBatchPrompt(String dslContext, String categoriesJson,
			List<CommitData> commits) {
		return buildBatchPrompt(dslContext, categoriesJson, commits, null);
	}

	/**
	 * Builds a batch discovery prompt. Each commit must receive exactly one
	 * candidate-or-no-cleanup object in the original order.
	 */
	public String buildBatchPrompt(String dslContext, String categoriesJson,
			List<CommitData> commits, String previousResults) {
		StringBuilder prompt = new StringBuilder();
		appendHeader(prompt);
		appendOptionalContext(prompt);
		prompt.append("## Commits to analyze\n\n"); //$NON-NLS-1$
		for (int i = 0; i < commits.size(); i++) {
			CommitData commit = commits.get(i);
			prompt.append("### Commit ").append(i).append(" (") //$NON-NLS-1$ //$NON-NLS-2$
					.append(commit.commitHash()).append(")\n"); //$NON-NLS-1$
			prompt.append("#### Message\n").append(nullToEmpty(commit.commitMessage())).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			prompt.append("#### Diff\n```diff\n").append(nullToEmpty(commit.diff())).append("\n```\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		appendTask(prompt, true, commits.size());
		return prompt.toString();
	}

	private void appendHeader(StringBuilder prompt) {
		prompt.append("You discover small, local, semantics-preserving Java cleanups from commit diffs.\n"); //$NON-NLS-1$
		prompt.append("For each commit, propose exactly one reusable TriggerPattern DSL candidate or explicitly return noCleanup.\n"); //$NON-NLS-1$
		prompt.append("Do not perform architectural analysis, plugin replacement analysis, or speculative DSL redesign.\n\n"); //$NON-NLS-1$
		prompt.append("## TriggerPattern DSL\n\n").append(dslExplanation).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void appendOptionalContext(StringBuilder prompt) {
		if (typeContext != null && !typeContext.isBlank()) {
			prompt.append("## Deterministic type context\n\n") //$NON-NLS-1$
					.append(typeContext.strip()).append("\n\n"); //$NON-NLS-1$
		}
		if (errorFeedback != null && !errorFeedback.isBlank()) {
			prompt.append("## Recurring response errors to avoid\n\n") //$NON-NLS-1$
					.append(errorFeedback.strip()).append("\n\n"); //$NON-NLS-1$
		}
	}

	private static void appendTask(StringBuilder prompt, boolean array, int count) {
		prompt.append("## Task\n\n"); //$NON-NLS-1$
		prompt.append("Identify one local transformation that is safe for code matching the supplied examples.\n"); //$NON-NLS-1$
		prompt.append("Return noCleanup=true when the change is uncertain, context-dependent, already merely stylistic, or not expressible safely.\n\n"); //$NON-NLS-1$
		appendSafetyPolicy(prompt);
		appendDslRuleChecklist(prompt);
		if (array) {
			prompt.append("Return a JSON array with exactly ").append(count) //$NON-NLS-1$
					.append(" objects, one per commit, in the same order.\n\n"); //$NON-NLS-1$
		} else {
			prompt.append("Return exactly one JSON object.\n\n"); //$NON-NLS-1$
		}
		appendJsonSchema(prompt, array);
	}

	private static void appendSafetyPolicy(StringBuilder prompt) {
		prompt.append("### Mandatory safety policy\n\n"); //$NON-NLS-1$
		prompt.append("Return noCleanup=true for:\n"); //$NON-NLS-1$
		prompt.append("- import-only or formatting-only changes;\n"); //$NON-NLS-1$
		prompt.append("- type-changing replacements without a guard proving context and type safety;\n"); //$NON-NLS-1$
		prompt.append("- architecture refactorings, multi-file migrations, or statement restructuring;\n"); //$NON-NLS-1$
		prompt.append("- changes whose correctness depends on unavailable data-flow, overload, synchronization, or nullability facts;\n"); //$NON-NLS-1$
		prompt.append("- multiple unrelated transformations in one proposal;\n"); //$NON-NLS-1$
		prompt.append("- a candidate without complete compiling before, after, and negative examples.\n\n"); //$NON-NLS-1$
	}

	private static void appendDslRuleChecklist(StringBuilder prompt) {
		prompt.append("### DSL validation rules\n\n"); //$NON-NLS-1$
		prompt.append("1. Emit plain DSL only; never emit XML tags.\n"); //$NON-NLS-1$
		prompt.append("2. Use instanceof($var, \"fully.qualified.Type\") for type guards; never use isType().\n"); //$NON-NLS-1$
		prompt.append("3. End every rule with `;;` on its own line.\n"); //$NON-NLS-1$
		prompt.append("4. A quick-fix rule contains exactly one `=>`.\n"); //$NON-NLS-1$
		prompt.append("5. Every replacement placeholder must be bound by the source pattern.\n"); //$NON-NLS-1$
		prompt.append("6. Source and replacement must differ.\n"); //$NON-NLS-1$
		prompt.append("7. Use fully qualified concrete API names and placeholders only for variable parts.\n"); //$NON-NLS-1$
		prompt.append("8. Do not emit per-rule `/*!...*/` directives.\n"); //$NON-NLS-1$
		prompt.append("9. Drop the proposal rather than returning invalid or context-dependent DSL.\n\n"); //$NON-NLS-1$
	}

	private static void appendJsonSchema(StringBuilder prompt, boolean array) {
		String prefix = array ? "[\n  {\n" : "{\n"; //$NON-NLS-1$ //$NON-NLS-2$
		String indent = array ? "    " : "  "; //$NON-NLS-1$ //$NON-NLS-2$
		prompt.append(prefix);
		prompt.append(indent).append("\"noCleanup\": false,\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"reason\": \"short reason, especially when noCleanup=true\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"relevant\": true,\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"trafficLight\": \"GREEN|NOT_APPLICABLE\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"confidence\": 0.0,\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"category\": \"short stable category\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"canImplementInCurrentDsl\": true,\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"dslRule\": \"one raw .sandbox-hint rule or null\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"targetHintFile\": \"simple .sandbox-hint filename or null\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"sourceVersion\": \"21\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"beforeExample\": \"minimal self-contained compiling Java class or null\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"afterExample\": \"complete expected Java class or null\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"negativeExample\": \"similar compiling class that must not match or null\",\n"); //$NON-NLS-1$
		prompt.append(indent).append("\"summary\": \"brief local transformation summary\"\n"); //$NON-NLS-1$
		prompt.append(array ? "  }\n]\n" : "}\n"); //$NON-NLS-1$ //$NON-NLS-2$
		prompt.append("confidence must be between 0.0 and 1.0. For noCleanup=true, set relevant=false, trafficLight=NOT_APPLICABLE, and candidate fields to null.\n"); //$NON-NLS-1$
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value; //$NON-NLS-1$
	}

	private static String loadResource(String resourcePath) {
		try (InputStream input = PromptBuilder.class.getResourceAsStream(resourcePath)) {
			if (input == null) {
				return "(Resource not available: " + resourcePath + ')'; //$NON-NLS-1$
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "(Failed to load resource: " + resourcePath + ')'; //$NON-NLS-1$
		}
	}
}
