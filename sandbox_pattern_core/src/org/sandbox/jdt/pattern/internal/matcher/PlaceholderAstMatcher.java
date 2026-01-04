/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.internal.matcher;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * AST matcher that supports placeholder matching.
 * <p>
 * Placeholders are identified by SimpleName nodes starting with '$'.
 * When a placeholder is first encountered, it binds to the corresponding node.
 * Subsequent occurrences of the same placeholder must match the same node structure.
 * </p>
 * <p>
 * Example: Pattern "$x + $x" will match "a + a" but not "a + b".
 * </p>
 */
public class PlaceholderAstMatcher extends ASTMatcher {
	
	private final Map<String, ASTNode> bindings;
	
	/**
	 * Creates a new placeholder matcher.
	 */
	public PlaceholderAstMatcher() {
		this.bindings = new HashMap<>();
	}
	
	/**
	 * Returns the bindings map containing placeholder names to matched nodes.
	 * 
	 * @return unmodifiable view of the bindings
	 */
	public Map<String, ASTNode> getBindings() {
		return new HashMap<>(bindings);
	}
	
	/**
	 * Matches two nodes, handling placeholders.
	 * <p>
	 * If the pattern node is a SimpleName starting with '$', it's treated as a placeholder.
	 * Otherwise, delegates to the standard AST matching logic.
	 * </p>
	 * 
	 * @param pattern the pattern node (may contain placeholders)
	 * @param node the actual source code node to match against
	 * @return true if the nodes match
	 */
	public boolean safeMatch(ASTNode pattern, ASTNode node) {
		if (pattern == null || node == null) {
			return pattern == node;
		}
		
		// Check if pattern is a placeholder
		if (pattern instanceof SimpleName) {
			SimpleName name = (SimpleName) pattern;
			String identifier = name.getIdentifier();
			
			if (identifier.startsWith("$")) {
				// This is a placeholder
				String placeholderName = identifier.substring(1); // Remove '$' prefix
				
				if (bindings.containsKey(placeholderName)) {
					// Placeholder already bound - must match the same structure
					ASTNode boundNode = bindings.get(placeholderName);
					return boundNode.subtreeMatch(new ASTMatcher(), node);
				} else {
					// First occurrence - bind it
					bindings.put(placeholderName, node);
					return true;
				}
			}
		}
		
		// Not a placeholder - use standard matching
		return pattern.subtreeMatch(this, node);
	}
	
	@Override
	public boolean match(SimpleName pattern, Object other) {
		if (!(other instanceof ASTNode)) {
			return false;
		}
		
		ASTNode node = (ASTNode) other;
		String identifier = pattern.getIdentifier();
		
		if (identifier.startsWith("$")) {
			// This is a placeholder
			String placeholderName = identifier.substring(1); // Remove '$' prefix
			
			if (bindings.containsKey(placeholderName)) {
				// Placeholder already bound - must match the same structure
				ASTNode boundNode = bindings.get(placeholderName);
				return boundNode.subtreeMatch(new ASTMatcher(), node);
			} else {
				// First occurrence - bind it
				bindings.put(placeholderName, node);
				return true;
			}
		}
		
		// Not a placeholder - use standard matching
		return super.match(pattern, other);
	}
}
