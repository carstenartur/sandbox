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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;

/**
 * An AST matcher that supports placeholder matching.
 * 
 * <p>Placeholders are identified by a {@code $} prefix in SimpleName nodes.
 * When a placeholder is encountered:</p>
 * <ul>
 *   <li>If it's the first occurrence, the placeholder is bound to the corresponding node</li>
 *   <li>If it's a subsequent occurrence, the node must match the previously bound node</li>
 * </ul>
 * 
 * <p>Example: In pattern {@code "$x + $x"}, both occurrences of {@code $x} must match
 * the same expression.</p>
 * 
 * @since 1.2.2
 */
public class PlaceholderAstMatcher extends ASTMatcher {
	
	private final Map<String, ASTNode> bindings = new HashMap<>();
	private final ASTMatcher reusableMatcher = new ASTMatcher();
	
	/**
	 * Creates a new placeholder matcher.
	 */
	public PlaceholderAstMatcher() {
		super();
	}
	
	/**
	 * Returns the placeholder bindings.
	 * 
	 * @return a map of placeholder names to bound AST nodes
	 */
	public Map<String, ASTNode> getBindings() {
		return new HashMap<>(bindings);
	}
	
	/**
	 * Clears all placeholder bindings.
	 */
	public void clearBindings() {
		bindings.clear();
	}
	
	@Override
	public boolean match(SimpleName patternNode, Object other) {
		if (!(other instanceof ASTNode)) {
			return false;
		}
		
		String name = patternNode.getIdentifier();
		
		// Check if this is a placeholder (starts with $)
		if (name != null && name.startsWith("$")) { //$NON-NLS-1$
			ASTNode otherNode = (ASTNode) other;
			
			// Check if this placeholder has been bound before
			if (bindings.containsKey(name)) {
				// Placeholder already bound - must match the previously bound node
				ASTNode boundNode = bindings.get(name);
				return boundNode.subtreeMatch(reusableMatcher, otherNode);
			} else {
				// First occurrence - bind the placeholder to this node
				bindings.put(name, otherNode);
				return true;
			}
		}
		
		// Not a placeholder - use default matching
		return super.match(patternNode, other);
	}
	
	/**
	 * Matches marker annotations (e.g., @Before, @After).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(MarkerAnnotation patternNode, Object other) {
		if (!(other instanceof MarkerAnnotation)) {
			return false;
		}
		MarkerAnnotation otherAnnotation = (MarkerAnnotation) other;
		
		// Match annotation name
		return patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName());
	}
	
	/**
	 * Matches single member annotations (e.g., @Test(expected=Exception.class)).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(SingleMemberAnnotation patternNode, Object other) {
		if (!(other instanceof SingleMemberAnnotation)) {
			return false;
		}
		SingleMemberAnnotation otherAnnotation = (SingleMemberAnnotation) other;
		
		// Match annotation name
		if (!patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName())) {
			return false;
		}
		
		// Match the value with placeholder support
		return safeSubtreeMatch(patternNode.getValue(), otherAnnotation.getValue());
	}
	
	/**
	 * Matches normal annotations (e.g., @Test(expected=Exception.class, timeout=1000)).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(NormalAnnotation patternNode, Object other) {
		if (!(other instanceof NormalAnnotation)) {
			return false;
		}
		NormalAnnotation otherAnnotation = (NormalAnnotation) other;
		
		// Match annotation name
		if (!patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName())) {
			return false;
		}
		
		// Match member-value pairs with placeholder support
		@SuppressWarnings("unchecked")
		List<MemberValuePair> patternPairs = patternNode.values();
		@SuppressWarnings("unchecked")
		List<MemberValuePair> otherPairs = otherAnnotation.values();
		
		// Must have same number of pairs
		if (patternPairs.size() != otherPairs.size()) {
			return false;
		}
		
		// Create a map for O(n) lookup instead of O(n²)
		Map<String, MemberValuePair> otherPairMap = new HashMap<>();
		for (MemberValuePair otherPair : otherPairs) {
			otherPairMap.put(otherPair.getName().getIdentifier(), otherPair);
		}
		
		// Match each pattern pair with corresponding pair in other annotation
		// (annotation pairs can be in any order)
		for (MemberValuePair patternPair : patternPairs) {
			String patternName = patternPair.getName().getIdentifier();
			
			// Find corresponding pair in other annotation
			MemberValuePair matchingOtherPair = otherPairMap.get(patternName);
			
			// If no matching pair found, annotations don't match
			if (matchingOtherPair == null) {
				return false;
			}
			
			// Values must match (with placeholder support)
			if (!safeSubtreeMatch(patternPair.getValue(), matchingOtherPair.getValue())) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Helper method to perform subtree matching using this matcher.
	 */
	private boolean safeSubtreeMatch(ASTNode node1, ASTNode node2) {
		if (node1 == null) {
			return node2 == null;
		}
		return node1.subtreeMatch(this, node2);
	}
}
