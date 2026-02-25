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
package org.sandbox.mining.core.inference;

import java.util.ArrayList;
import java.util.List;

import org.sandbox.mining.core.astdiff.AstDiff;
import org.sandbox.mining.core.astdiff.AstDiffAnalyzer;
import org.sandbox.mining.core.astdiff.AstNodeChange;
import org.sandbox.mining.core.astdiff.CodeChangePair;
import org.sandbox.mining.core.astdiff.PlaceholderGeneralizer;
import org.sandbox.mining.core.astdiff.PlaceholderGeneralizer.GeneralizedPair;
import org.sandbox.mining.core.dsl.DslValidator;
import org.sandbox.mining.core.dsl.DslValidator.ValidationResult;

/**
 * Configurable engine for inferring sandbox DSL transformation rules from
 * before/after code pairs.
 *
 * <p>This is the core component described in Phase 3 of Issue #727:
 * it takes code change pairs (from git diffs or direct input), analyzes them,
 * generalizes concrete values into placeholders, and produces candidate
 * {@link TransformationRule} instances in {@code .sandbox-hint} DSL format.</p>
 *
 * <p>The engine supports multiple AI backends for enhanced analysis, configurable
 * via {@link InferenceEngineConfig}. When AI analysis is disabled, the engine
 * uses text-based heuristics and placeholder generalization only.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 *   InferenceEngineConfig config = new InferenceEngineConfig()
 *       .llmProvider(LlmProvider.OPENAI)
 *       .minConfidence(0.8)
 *       .enableAiAnalysis(false);
 *
 *   RuleInferenceEngine engine = new RuleInferenceEngine(config);
 *   List&lt;TransformationRule&gt; rules = engine.inferRules(
 *       "new String(buf, \"UTF-8\")",
 *       "new String(buf, StandardCharsets.UTF_8)");
 * </pre>
 */
public class RuleInferenceEngine {

	private final InferenceEngineConfig config;
	private final AstDiffAnalyzer diffAnalyzer;
	private final PlaceholderGeneralizer generalizer;
	private final DslValidator dslValidator;

	/**
	 * Creates an engine with the given configuration.
	 *
	 * @param config the engine configuration
	 */
	public RuleInferenceEngine(InferenceEngineConfig config) {
		this.config = config;
		this.diffAnalyzer = new AstDiffAnalyzer();
		this.generalizer = new PlaceholderGeneralizer();
		this.dslValidator = new DslValidator();
	}

	/**
	 * Creates an engine with default configuration.
	 */
	public RuleInferenceEngine() {
		this(new InferenceEngineConfig());
	}

	/**
	 * Infers transformation rules from a before/after code pair.
	 * This is the primary API as described in Phase 3 of Issue #727.
	 *
	 * @param before the code before the change
	 * @param after  the code after the change
	 * @return list of inferred transformation rules
	 */
	public List<TransformationRule> inferRules(String before, String after) {
		CodeChangePair pair = CodeChangePair.of(before, after);
		return inferRulesFromPair(pair);
	}

	/**
	 * Infers rules from a {@link CodeChangePair}.
	 *
	 * @param pair the code change pair
	 * @return list of inferred transformation rules
	 */
	public List<TransformationRule> inferRulesFromPair(CodeChangePair pair) {
		List<TransformationRule> rules = new ArrayList<>();

		AstDiff diff = diffAnalyzer.analyze(pair);
		if (diff.isEmpty()) {
			return rules;
		}

		for (AstNodeChange change : diff.changes()) {
			List<TransformationRule> changeRules = inferFromChange(change, pair);
			rules.addAll(changeRules);
			if (rules.size() >= config.getMaxRulesPerPair()) {
				break;
			}
		}

		return rules.stream()
				.filter(r -> r.confidence() >= config.getMinConfidence())
				.limit(config.getMaxRulesPerPair())
				.toList();
	}

	/**
	 * Infers rules from multiple code change pairs (batch mode).
	 *
	 * @param pairs the list of code change pairs
	 * @return list of all inferred transformation rules
	 */
	public List<TransformationRule> inferRulesBatch(List<CodeChangePair> pairs) {
		if (pairs == null || pairs.isEmpty()) {
			return List.of();
		}
		List<TransformationRule> allRules = new ArrayList<>();
		for (CodeChangePair pair : pairs) {
			allRules.addAll(inferRulesFromPair(pair));
		}
		return allRules;
	}

	/**
	 * Generates a complete {@code .sandbox-hint} file content from a list of rules.
	 *
	 * @param rules the transformation rules
	 * @param id    the hint file id
	 * @param description the hint file description
	 * @return the DSL file content
	 */
	public String generateHintFile(List<TransformationRule> rules, String id, String description) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!id: ").append(id).append(">\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("<!description: ").append(description).append(">\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("<!tags: [inferred, mining]>\n"); //$NON-NLS-1$
		sb.append('\n');

		for (TransformationRule rule : rules) {
			if (rule.dslRule() != null && !rule.dslRule().isBlank()) {
				sb.append(rule.dslRule());
			} else {
				sb.append(rule.pattern()).append('\n');
				sb.append("=> ").append(rule.replacement()).append('\n'); //$NON-NLS-1$
				sb.append(";;\n"); //$NON-NLS-1$
			}
			sb.append('\n');
		}

		return sb.toString();
	}

	/**
	 * Returns the current configuration.
	 */
	public InferenceEngineConfig getConfig() {
		return config;
	}

	private List<TransformationRule> inferFromChange(AstNodeChange change, CodeChangePair pair) {
		List<TransformationRule> rules = new ArrayList<>();

		switch (change.changeType()) {
			case REPLACE -> {
				// Generalize identifiers
				GeneralizedPair genIds = generalizer.generalize(change.before(), change.after());
				double confidence = computeConfidence(change, genIds);
				String dsl = buildDslRule(genIds.pattern(), genIds.replacement());
				TransformationRule rule = new TransformationRule(
						genIds.pattern(), genIds.replacement(),
						confidence, 1, pair.commitId(), null, dsl);

				if (shouldIncludeRule(rule)) {
					rules.add(rule);
				}

				// Also try string generalization
				GeneralizedPair genStrings = generalizer.generalizeStrings(
						change.before(), change.after());
				if (!genStrings.pattern().equals(genIds.pattern())) {
					String dsl2 = buildDslRule(genStrings.pattern(), genStrings.replacement());
					TransformationRule stringRule = new TransformationRule(
							genStrings.pattern(), genStrings.replacement(),
							confidence * 0.9, 1, pair.commitId(), null, dsl2);
					if (shouldIncludeRule(stringRule)) {
						rules.add(stringRule);
					}
				}
			}
			case INSERT, DELETE -> {
				// INSERT and DELETE generate simpler rules
				double confidence = 0.6;
				String pattern = change.before().isBlank() ? change.after() : change.before();
				String replacement = change.after();
				String dsl = buildDslRule(pattern, replacement);
				TransformationRule rule = new TransformationRule(
						pattern, replacement, confidence, 1,
						pair.commitId(), null, dsl);
				if (shouldIncludeRule(rule)) {
					rules.add(rule);
				}
			}
		}

		return rules;
	}

	private double computeConfidence(AstNodeChange change, GeneralizedPair gen) {
		double base = 0.75;

		// Higher confidence for method invocation replacements
		if ("MethodInvocation".equals(change.nodeType())) { //$NON-NLS-1$
			base = 0.85;
		}
		if ("ClassInstanceCreation".equals(change.nodeType())) { //$NON-NLS-1$
			base = 0.85;
		}

		// More placeholders = more general = slightly lower confidence
		int placeholderCount = gen.placeholderMap().size();
		if (placeholderCount > 3) {
			base -= 0.1;
		}

		return Math.max(0.0, Math.min(1.0, base));
	}

	private String buildDslRule(String pattern, String replacement) {
		StringBuilder sb = new StringBuilder();
		sb.append(pattern).append('\n');
		sb.append("=> ").append(replacement).append('\n'); //$NON-NLS-1$
		sb.append(";;\n"); //$NON-NLS-1$
		return sb.toString();
	}

	private boolean shouldIncludeRule(TransformationRule rule) {
		if (rule.pattern() == null || rule.pattern().isBlank()) {
			return false;
		}
		if (rule.replacement() == null) {
			return false;
		}

		if (config.isValidateDsl() && rule.dslRule() != null) {
			ValidationResult result = dslValidator.validate(rule.dslRule());
			return result.valid();
		}

		return true;
	}
}
