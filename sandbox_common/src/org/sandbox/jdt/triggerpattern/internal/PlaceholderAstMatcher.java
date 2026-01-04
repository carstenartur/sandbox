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
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;

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
}
