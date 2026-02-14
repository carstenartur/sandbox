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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Batch processor for applying all transformation rules from a {@link HintFile}
 * to a compilation unit in a single efficient pass using {@link PatternIndex}.
 *
 * <p>Instead of manually defining each pattern and matching operation (as done in
 * {@code StringSimplificationFixCore}), this processor reads rules from a
 * {@code .sandbox-hint} file and applies them automatically. This eliminates
 * boilerplate code and enables users to define transformation rules declaratively.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * HintFile hintFile = parser.parse(content);
 * BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
 * List&lt;TransformationResult&gt; results = processor.process(compilationUnit);
 * // Each result contains match details and the applicable replacement
 * </pre>
 *
 * @since 1.3.3
 */
public final class BatchTransformationProcessor {

	private final HintFile hintFile;
	private final PatternIndex patternIndex;

	/**
	 * Creates a new batch transformation processor for the given hint file.
	 *
	 * @param hintFile the hint file containing transformation rules
	 */
	public BatchTransformationProcessor(HintFile hintFile) {
		this.hintFile = hintFile;
		this.patternIndex = new PatternIndex(hintFile.getRules());
	}

	/**
	 * Returns the hint file used by this processor.
	 *
	 * @return the hint file
	 */
	public HintFile getHintFile() {
		return hintFile;
	}

	/**
	 * Returns the pattern index used for efficient matching.
	 *
	 * @return the pattern index
	 */
	public PatternIndex getPatternIndex() {
		return patternIndex;
	}

	/**
	 * Processes a compilation unit, finding all matches and determining applicable
	 * replacements for each match.
	 *
	 * <p>For each match, guard conditions are evaluated to select the appropriate
	 * rewrite alternative. If no alternative matches (all guards fail), the match
	 * is still reported as a hint-only result.</p>
	 *
	 * @param cu the compilation unit to process
	 * @return list of transformation results (may be empty, never null)
	 */
	public List<TransformationResult> process(CompilationUnit cu) {
		return process(cu, null);
	}

	/**
	 * Processes a compilation unit with explicit compiler options for guard evaluation.
	 *
	 * @param cu the compilation unit to process
	 * @param compilerOptions compiler options for guard context (may be null)
	 * @return list of transformation results (may be empty, never null)
	 */
	public List<TransformationResult> process(CompilationUnit cu, Map<String, String> compilerOptions) {
		if (cu == null) {
			return Collections.emptyList();
		}

		Map<TransformationRule, List<Match>> allMatches = patternIndex.findAllMatches(cu);
		if (allMatches.isEmpty()) {
			return Collections.emptyList();
		}

		List<TransformationResult> results = new ArrayList<>();

		for (Map.Entry<TransformationRule, List<Match>> entry : allMatches.entrySet()) {
			TransformationRule rule = entry.getKey();
			List<Match> matches = entry.getValue();

			for (Match match : matches) {
				GuardContext guardCtx;
				if (compilerOptions != null) {
					guardCtx = GuardContext.fromMatch(match, cu, compilerOptions);
				} else {
					guardCtx = GuardContext.fromMatch(match, cu);
				}

				// Evaluate source guard first
				if (rule.sourceGuard() != null && !rule.sourceGuard().evaluate(guardCtx)) {
					continue;
				}

				// Find matching alternative
				RewriteAlternative alt = rule.findMatchingAlternative(guardCtx);
				String replacement = null;
				if (alt != null) {
					replacement = substituteBindings(alt.replacementPattern(), match);
				}

				// Collect import directives
				ImportDirective imports = rule.hasImportDirective() ? rule.getImportDirective() : null;

				results.add(new TransformationResult(
						rule, match, replacement, imports,
						rule.getDescription(),
						computeLineNumber(cu, match)));
			}
		}

		return results;
	}

	/**
	 * Substitutes placeholder bindings in a replacement pattern.
	 *
	 * @param pattern the replacement pattern with placeholders
	 * @param match the match providing binding values
	 * @return the pattern with placeholders replaced by matched text
	 */
	private String substituteBindings(String pattern, Match match) {
		if (pattern == null || match.getBindings().isEmpty()) {
			return pattern;
		}

		String result = pattern;
		for (Map.Entry<String, Object> binding : match.getBindings().entrySet()) {
			String placeholder = binding.getKey();
			Object value = binding.getValue();
			String replacement;
			if (value instanceof ASTNode astNode) {
				replacement = astNode.toString();
			} else if (value instanceof List<?> list) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < list.size(); i++) {
					if (i > 0) {
						sb.append(", "); //$NON-NLS-1$
					}
					sb.append(list.get(i));
				}
				replacement = sb.toString();
			} else {
				replacement = String.valueOf(value);
			}
			result = result.replace(placeholder, Matcher.quoteReplacement(replacement));
		}
		return result;
	}

	/**
	 * Computes the line number for a match in the compilation unit.
	 */
	private int computeLineNumber(CompilationUnit cu, Match match) {
		return cu.getLineNumber(match.getOffset());
	}

	/**
	 * Result of applying a transformation rule to a specific match.
	 *
	 * @param rule the transformation rule that matched
	 * @param match the match details (AST node, bindings, offset, length)
	 * @param replacement the substituted replacement text (null if hint-only)
	 * @param importDirective import changes to apply (null if none)
	 * @param description the rule description (may be null)
	 * @param lineNumber the line number of the match
	 * @since 1.3.3
	 */
	public record TransformationResult(
			TransformationRule rule,
			Match match,
			String replacement,
			ImportDirective importDirective,
			String description,
			int lineNumber) {

		/**
		 * Returns whether this result has a replacement.
		 *
		 * @return true if a replacement is available
		 */
		public boolean hasReplacement() {
			return replacement != null;
		}

		/**
		 * Returns whether this result has import directives.
		 *
		 * @return true if import changes are needed
		 */
		public boolean hasImportDirective() {
			return importDirective != null && !importDirective.isEmpty();
		}

		/**
		 * Returns the matched source text.
		 *
		 * @return the matched code text
		 */
		public String matchedText() {
			return match.getMatchedNode().toString();
		}
	}
}
