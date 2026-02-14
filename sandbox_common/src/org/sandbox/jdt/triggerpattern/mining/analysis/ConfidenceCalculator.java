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
			return 1.0;
		}

		// High confidence when mostly identical with only leaf-level modifications
		double identicalRatio = (double) identical / total;
		boolean onlyLeafModifications = diff.alignments().stream()
				.filter(a -> a.kind() == AlignmentKind.MODIFIED)
				.allMatch(a -> isLeafNode(a.beforeNode()) || isLeafNode(a.afterNode()));

		if (onlyLeafModifications) {
			return 0.8 + 0.15 * identicalRatio;
		}

		return 0.5 + 0.4 * identicalRatio;
	}

	/**
	 * Calculates a confidence score for a diff with incompatible structure.
	 *
	 * @param diff the AST diff
	 * @param pair the code change pair providing additional context
	 * @return a value between 0.0 and 1.0
	 */
	public double calculate(AstDiff diff, CodeChangePair pair) {
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
			return Math.max(0.1, 0.5 * ((double) identical / total));
		}
		return 0.3;
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
