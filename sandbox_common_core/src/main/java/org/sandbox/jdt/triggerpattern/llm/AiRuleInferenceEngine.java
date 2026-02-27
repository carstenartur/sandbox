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

import java.io.IOException;
import java.util.Optional;

import org.sandbox.jdt.triggerpattern.internal.DslValidator;

/**
 * AI-powered rule inference engine that uses an LLM to generate
 * TriggerPattern DSL rules from before/after code pairs or unified diffs.
 *
 * <p>This engine is Eclipse-independent and can be used from both the
 * CLI ({@code sandbox_mining_core}) and Eclipse plugins
 * ({@code sandbox_triggerpattern}).</p>
 *
 * <p>The engine composes:</p>
 * <ul>
 *   <li>{@link PromptBuilder} — constructs the LLM prompt with DSL context</li>
 *   <li>{@link LlmClient} — sends the prompt to the AI and parses the response</li>
 *   <li>{@link DslValidator} — validates the generated DSL rule</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * try (LlmClient client = LlmClientFactory.createFromEnvironment(null)) {
 *     AiRuleInferenceEngine engine = new AiRuleInferenceEngine(client);
 *     Optional&lt;CommitEvaluation&gt; result = engine.inferRule(before, after);
 *     result.ifPresent(eval -&gt; System.out.println(eval.dslRule()));
 * }
 * </pre>
 *
 * @since 1.2.6
 */
public class AiRuleInferenceEngine {

	private final LlmClient llmClient;
	private final PromptBuilder promptBuilder;
	private final DslValidator validator;

	/**
	 * Creates an engine with the given LLM client.
	 *
	 * @param llmClient the LLM client to use for inference
	 */
	public AiRuleInferenceEngine(LlmClient llmClient) {
		this(llmClient, new PromptBuilder(), new DslValidator());
	}

	/**
	 * Creates an engine with explicit dependencies (useful for testing).
	 *
	 * @param llmClient     the LLM client
	 * @param promptBuilder the prompt builder
	 * @param validator     the DSL validator
	 */
	public AiRuleInferenceEngine(LlmClient llmClient, PromptBuilder promptBuilder,
			DslValidator validator) {
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
		this.validator = validator;
	}

	/**
	 * Infers a DSL rule from before/after code snippets.
	 *
	 * <p>Constructs a unified diff from the two snippets, builds an LLM prompt,
	 * sends it for evaluation, and validates the resulting DSL rule.</p>
	 *
	 * @param codeBefore the original code
	 * @param codeAfter  the modified code
	 * @return an evaluation with a validated DSL rule, or empty if inference failed
	 */
	public Optional<CommitEvaluation> inferRule(String codeBefore, String codeAfter) {
		if (codeBefore == null || codeAfter == null) {
			return Optional.empty();
		}
		String diff = buildSimpleDiff(codeBefore, codeAfter);
		return inferRuleFromDiff(diff);
	}

	/**
	 * Infers a DSL rule from a unified diff string.
	 *
	 * <p>Builds an LLM prompt from the diff, sends it for evaluation,
	 * and validates the resulting DSL rule.</p>
	 *
	 * @param unifiedDiff the unified diff
	 * @return an evaluation with a validated DSL rule, or empty if inference failed
	 */
	public Optional<CommitEvaluation> inferRuleFromDiff(String unifiedDiff) {
		if (unifiedDiff == null || unifiedDiff.isBlank()) {
			return Optional.empty();
		}
		if (!llmClient.hasRemainingQuota() || llmClient.isApiUnavailable()) {
			return Optional.empty();
		}

		String prompt = promptBuilder.buildPrompt(
				null, // no existing DSL context needed for ad-hoc inference
				"[]", //$NON-NLS-1$
				unifiedDiff,
				"Infer DSL rule from code change"); //$NON-NLS-1$

		try {
			CommitEvaluation evaluation = llmClient.evaluate(
					prompt,
					"inline", //$NON-NLS-1$
					"AI rule inference", //$NON-NLS-1$
					"local"); //$NON-NLS-1$

			if (evaluation == null) {
				return Optional.empty();
			}

			return validateAndEnrich(evaluation);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * Validates the DSL rule in the evaluation and returns an enriched copy
	 * with the validation result set. Always returns the evaluation (even if
	 * DSL validation fails), so callers can inspect the validation message.
	 *
	 * @param evaluation the raw evaluation from the LLM
	 * @return the enriched evaluation with {@code dslValidationResult} populated
	 */
	private Optional<CommitEvaluation> validateAndEnrich(CommitEvaluation evaluation) {
		if (!evaluation.relevant()) {
			return Optional.of(evaluation);
		}

		String dslRule = evaluation.dslRule();
		if (dslRule == null || dslRule.isBlank()) {
			return Optional.of(evaluation);
		}

		DslValidator.ValidationResult validation = validator.validate(dslRule);
		String validationResult = validation.valid() ? "VALID" : validation.message(); //$NON-NLS-1$

		CommitEvaluation enriched = new CommitEvaluation(
				evaluation.commitHash(), evaluation.commitMessage(), evaluation.repoUrl(),
				evaluation.evaluatedAt(), evaluation.commitDate(), evaluation.relevant(), evaluation.irrelevantReason(),
				evaluation.isDuplicate(), evaluation.duplicateOf(),
				evaluation.reusability(), evaluation.codeImprovement(),
				evaluation.implementationEffort(),
				evaluation.trafficLight(), evaluation.category(), evaluation.isNewCategory(),
				evaluation.categoryReason(), evaluation.canImplementInCurrentDsl(),
				evaluation.dslRule(), evaluation.targetHintFile(),
				evaluation.languageChangeNeeded(), evaluation.dslRuleAfterChange(),
				evaluation.summary(), validationResult);

		return Optional.of(enriched);
	}

	/**
	 * Builds a simple unified diff from before/after code.
	 *
	 * @param before the original code
	 * @param after  the modified code
	 * @return a unified diff string
	 */
	static String buildSimpleDiff(String before, String after) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- a/snippet.java\n"); //$NON-NLS-1$
		sb.append("+++ b/snippet.java\n"); //$NON-NLS-1$

		String[] beforeLines = before.split("\n", -1); //$NON-NLS-1$
		String[] afterLines = after.split("\n", -1); //$NON-NLS-1$

		sb.append("@@ -1,").append(beforeLines.length); //$NON-NLS-1$
		sb.append(" +1,").append(afterLines.length).append(" @@\n"); //$NON-NLS-1$ //$NON-NLS-2$

		for (String line : beforeLines) {
			sb.append('-').append(line).append('\n');
		}
		for (String line : afterLines) {
			sb.append('+').append(line).append('\n');
		}
		return sb.toString();
	}
}
