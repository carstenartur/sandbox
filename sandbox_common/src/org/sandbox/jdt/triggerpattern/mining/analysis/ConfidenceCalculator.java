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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

/**
 * Computes a heuristic confidence score for an {@link InferredRule}.
 *
 * <p>Heuristics:</p>
 * <ul>
 *   <li><b>High (&gt; 0.9)</b>: only leaf nodes changed (API replacement), same overall structure</li>
 *   <li><b>Medium (0.5–0.9)</b>: argument reordering, one argument more/less</li>
 *   <li><b>Low (&lt; 0.5)</b>: completely different structure, control-flow changes</li>
 * </ul>
 *
 * @since 1.2.6
 */
public class ConfidenceCalculator {

	/** Base confidence for leaf-only modifications (API renames, etc.). */
	private static final double LEAF_MODIFICATION_BASE_CONFIDENCE = 0.8;

	/** Bonus multiplied by the identical-ratio when only leaf nodes are modified. */
	private static final double LEAF_MODIFICATION_IDENTICAL_BONUS = 0.15;

	/** Base confidence when structural (non-leaf) changes are present. */
	private static final double STRUCTURAL_CHANGE_BASE_CONFIDENCE = 0.5;

	/** Bonus multiplied by the identical-ratio for structural changes. */
	private static final double STRUCTURAL_CHANGE_IDENTICAL_BONUS = 0.4;

	/** Minimum confidence returned for structurally incompatible diffs. */
	private static final double MIN_INCOMPATIBLE_CONFIDENCE = 0.1;

	/** Scaling factor for the identical-ratio in incompatible diffs. */
	private static final double INCOMPATIBLE_IDENTICAL_SCALE = 0.5;

	/** Default confidence for structurally incompatible diffs without inserts/deletes. */
	private static final double DEFAULT_INCOMPATIBLE_CONFIDENCE = 0.3;

	/** Absolute upper bound for any confidence value. */
	private static final double MAX_CONFIDENCE = 1.0;

	/**
	 * Calculates a confidence score for the given diff.
	 *
	 * @param diff the AST diff
	 * @return a value between 0.0 and 1.0
	 */
	public double calculate(AstDiff diff) {
		if (diff.alignments().isEmpty()) {
			return 0.0;
		}

		if (!diff.structurallyCompatible()) {
			return calculateIncompatible(diff);
		}

		long identical = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.IDENTICAL).count();
		long modified = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.MODIFIED).count();
		long total = diff.alignments().size();

		if (modified == 0 && identical == total) {
			return MAX_CONFIDENCE;
		}

		// High confidence when mostly identical with only leaf-level modifications
		double identicalRatio = (double) identical / total;
		boolean onlyLeafModifications = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.MODIFIED)
				.allMatch(a -> isLeafNode(a.beforeNode()) || isLeafNode(a.afterNode()));

		if (onlyLeafModifications) {
			return LEAF_MODIFICATION_BASE_CONFIDENCE + LEAF_MODIFICATION_IDENTICAL_BONUS * identicalRatio;
		}

		return STRUCTURAL_CHANGE_BASE_CONFIDENCE + STRUCTURAL_CHANGE_IDENTICAL_BONUS * identicalRatio;
	}

	/**
	 * Calculates a confidence score for the given diff with optional change-pair
	 * context.
	 * <p>
	 * The {@code pair} parameter is currently ignored and reserved for future
	 * heuristics that may take additional change context into account.
	 * </p>
	 *
	 * @param diff the AST diff
	 * @param pair the code change pair providing additional context (currently unused)
	 * @return a value between 0.0 and 1.0
	 */
	public double calculate(AstDiff diff, CodeChangePair pair) {
		// pair is intentionally not used yet; kept for future heuristic extensions
		return calculate(diff);
	}

	private double calculateIncompatible(AstDiff diff) {
		long inserted = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.INSERTED).count();
		long deleted = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.DELETED).count();

		if (inserted > 0 || deleted > 0) {
			long identical = diff.alignments().stream()
					.filter(a -> a.kind() == AlignmentKind.IDENTICAL).count();
			long total = diff.alignments().size();
			return Math.max(MIN_INCOMPATIBLE_CONFIDENCE,
					INCOMPATIBLE_IDENTICAL_SCALE * ((double) identical / total));
		}
		return DEFAULT_INCOMPATIBLE_CONFIDENCE;
	}

	@SuppressWarnings("unchecked")
	private static boolean isLeafNode(ASTNode node) {
		if (node == null) {
			return true;
		}
		java.util.List<StructuralPropertyDescriptor> props = node.structuralPropertiesForType();
		return props.stream()
				.noneMatch(p -> p.isChildProperty() || p.isChildListProperty());
	}
}
