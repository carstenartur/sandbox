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
import java.util.List;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

/**
 * Computes a structural diff between two AST nodes by recursively comparing
 * their children and classifying each pair as IDENTICAL, MODIFIED, INSERTED, or DELETED.
 *
 * <p>The algorithm walks both trees top-down. When the root node types match it
 * recurses into children (by structural property); when they differ the pair is
 * marked MODIFIED and no further descent occurs.</p>
 *
 * @since 1.2.6
 */
public class AstDiffAnalyzer {

	private static final ASTMatcher SUBTREE_MATCHER = new ASTMatcher(true);

	/**
	 * Computes the diff between two AST nodes.
	 *
	 * @param before the AST node from the original code
	 * @param after  the AST node from the modified code
	 * @return the computed {@link AstDiff}
	 */
	public AstDiff computeDiff(ASTNode before, ASTNode after) {
		List<NodeAlignment> alignments = new ArrayList<>();
		boolean compatible = diff(before, after, alignments);
		return new AstDiff(compatible, alignments);
	}

	@SuppressWarnings("unchecked")
	private boolean diff(ASTNode before, ASTNode after, List<NodeAlignment> alignments) {
		if (before == null && after == null) {
			return true;
		}
		if (before == null) {
			alignments.add(new NodeAlignment(null, after, AlignmentKind.INSERTED));
			return false;
		}
		if (after == null) {
			alignments.add(new NodeAlignment(before, null, AlignmentKind.DELETED));
			return false;
		}

		if (before.subtreeMatch(SUBTREE_MATCHER, after)) {
			alignments.add(new NodeAlignment(before, after, AlignmentKind.IDENTICAL));
			return true;
		}

		if (before.getNodeType() != after.getNodeType()) {
			alignments.add(new NodeAlignment(before, after, AlignmentKind.MODIFIED));
			return false;
		}

		// Same node type but different content – recurse into children
		boolean allChildrenCompatible = true;
		boolean hasChildProperties = false;
		List<StructuralPropertyDescriptor> properties = before.structuralPropertiesForType();
		for (StructuralPropertyDescriptor prop : properties) {
			if (prop.isSimpleProperty()) {
				Object bVal = before.getStructuralProperty(prop);
				Object aVal = after.getStructuralProperty(prop);
				if (bVal != null && !bVal.equals(aVal)) {
					allChildrenCompatible = false;
				}
			} else if (prop.isChildProperty()) {
				hasChildProperties = true;
				ASTNode bChild = (ASTNode) before.getStructuralProperty(prop);
				ASTNode aChild = (ASTNode) after.getStructuralProperty(prop);
				if (!diff(bChild, aChild, alignments)) {
					allChildrenCompatible = false;
				}
			} else if (prop.isChildListProperty()) {
				hasChildProperties = true;
				List<ASTNode> bChildren = (List<ASTNode>) before.getStructuralProperty(prop);
				List<ASTNode> aChildren = (List<ASTNode>) after.getStructuralProperty(prop);
				if (!diffLists(bChildren, aChildren, alignments)) {
					allChildrenCompatible = false;
				}
			}
		}
		// If the node has no child/list properties (leaf node) and simple props differ,
		// register a MODIFIED alignment so the caller can see the change.
		if (!allChildrenCompatible && !hasChildProperties) {
			alignments.add(new NodeAlignment(before, after, AlignmentKind.MODIFIED));
		}
		return allChildrenCompatible;
	}

	private boolean diffLists(List<ASTNode> before, List<ASTNode> after,
			List<NodeAlignment> alignments) {
		int minSize = Math.min(before.size(), after.size());
		boolean allCompatible = before.size() == after.size();

		for (int i = 0; i < minSize; i++) {
			if (!diff(before.get(i), after.get(i), alignments)) {
				allCompatible = false;
			}
		}
		for (int i = minSize; i < before.size(); i++) {
			alignments.add(new NodeAlignment(before.get(i), null, AlignmentKind.DELETED));
			allCompatible = false;
		}
		for (int i = minSize; i < after.size(); i++) {
			alignments.add(new NodeAlignment(null, after.get(i), AlignmentKind.INSERTED));
			allCompatible = false;
		}
		return allCompatible;
	}
}
