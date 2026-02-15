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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Derives placeholder patterns from an {@link AstDiff}.
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li>All {@link AlignmentKind#IDENTICAL IDENTICAL} alignments become
 *       {@code $placeholder} with auto-generated names.</li>
 *   <li>All {@link AlignmentKind#MODIFIED MODIFIED} alignments remain literal
 *       in both source and replacement.</li>
 *   <li>The source pattern is assembled from the before-side with placeholders.</li>
 *   <li>The replacement pattern is assembled from the after-side with placeholders.</li>
 * </ol>
 *
 * @since 1.2.6
 */
public class PlaceholderGeneralizer {

	private final PlaceholderNamer namer = new PlaceholderNamer();
	private final ConfidenceCalculator confidenceCalculator = new ConfidenceCalculator();

	/**
	 * Generalizes an AST diff into an {@link InferredRule}.
	 *
	 * @param diff   the AST diff to generalize
	 * @param before the original source text
	 * @param after  the modified source text
	 * @param kind   the pattern kind
	 * @return the inferred rule, or {@code null} if the diff cannot be generalized
	 */
	public InferredRule generalize(AstDiff diff, String before, String after, PatternKind kind) {
		return generalize(diff, before, after, kind, null);
	}

	/**
	 * Generalizes an AST diff into an {@link InferredRule} with import changes.
	 *
	 * @param diff           the AST diff to generalize
	 * @param before         the original source text
	 * @param after          the modified source text
	 * @param kind           the pattern kind
	 * @param importDirective import changes (may be {@code null})
	 * @return the inferred rule, or {@code null} if the diff cannot be generalized
	 */
	public InferredRule generalize(AstDiff diff, String before, String after,
			PatternKind kind, ImportDirective importDirective) {

		if (diff.alignments().isEmpty()) {
			return null;
		}

		namer.reset();

		// Build a mapping: identical sub-trees → placeholder name
		Map<String, String> identicalPlaceholders = new HashMap<>();
		List<String> placeholderNames = new ArrayList<>();

		String sourcePattern = before;
		String replacementPattern = after;

		for (NodeAlignment alignment : diff.alignments()) {
			if (alignment.kind() == AlignmentKind.IDENTICAL
					&& alignment.beforeNode() != null
					&& alignment.afterNode() != null) {

				String beforeText = alignment.beforeNode().toString().trim();
				String afterText = alignment.afterNode().toString().trim();

				if (beforeText.isEmpty()) {
					continue;
				}

				// Avoid replacing string literals that are part of the transformation
				// (they define what is being changed, not what stays the same)
				if (isLiteralNode(alignment.beforeNode())) {
					continue;
				}

				String placeholder = identicalPlaceholders.get(beforeText);
				if (placeholder == null) {
					placeholder = namer.nameFor(alignment.beforeNode());
					identicalPlaceholders.put(beforeText, placeholder);
					placeholderNames.add(placeholder);
				}

				// Note: replaceFirst uses indexOf which may match the wrong occurrence
				// if the target substring appears multiple times in the source.
				// TODO: Use AST node positions for precise replacement (issue #727)
				sourcePattern = replaceFirst(sourcePattern, beforeText, placeholder);
				replacementPattern = replaceFirst(replacementPattern, afterText, placeholder);
			}
		}

		double confidence = confidenceCalculator.calculate(diff);

		return new InferredRule(sourcePattern, replacementPattern, kind,
				confidence, List.copyOf(placeholderNames), importDirective);
	}

	private static boolean isLiteralNode(ASTNode node) {
		int type = node.getNodeType();
		return type == ASTNode.STRING_LITERAL
				|| type == ASTNode.NUMBER_LITERAL
				|| type == ASTNode.CHARACTER_LITERAL
				|| type == ASTNode.BOOLEAN_LITERAL
				|| type == ASTNode.NULL_LITERAL;
	}

	private static String replaceFirst(String text, String target, String replacement) {
		int idx = text.indexOf(target);
		if (idx < 0) {
			return text;
		}
		return text.substring(0, idx) + replacement + text.substring(idx + target.length());
	}
}
