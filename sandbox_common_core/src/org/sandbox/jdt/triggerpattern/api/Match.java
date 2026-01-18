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
package org.sandbox.jdt.triggerpattern.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Represents a successful match of a pattern against Java code.
 * 
 * <p>A match contains:</p>
 * <ul>
 *   <li>The matched AST node</li>
 *   <li>A map of placeholder bindings (e.g., {@code "$x" -> InfixExpression})</li>
 *   <li>Source location (offset and length)</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public final class Match {
	private final ASTNode matchedNode;
	private final Map<String, ASTNode> bindings;
	private final int offset;
	private final int length;
	
	/**
	 * Creates a new match.
	 * 
	 * @param matchedNode the AST node that matched the pattern
	 * @param bindings map of placeholder names to their bound AST nodes
	 * @param offset the character offset of the match in the source
	 * @param length the character length of the match in the source
	 */
	public Match(ASTNode matchedNode, Map<String, ASTNode> bindings, int offset, int length) {
		this.matchedNode = Objects.requireNonNull(matchedNode, "Matched node cannot be null"); //$NON-NLS-1$
		this.bindings = bindings != null ? Collections.unmodifiableMap(bindings) : Collections.emptyMap();
		this.offset = offset;
		this.length = length;
	}
	
	/**
	 * Returns the matched AST node.
	 * 
	 * @return the matched node
	 */
	public ASTNode getMatchedNode() {
		return matchedNode;
	}
	
	/**
	 * Returns the placeholder bindings.
	 * 
	 * @return an unmodifiable map of placeholder names to AST nodes
	 */
	public Map<String, ASTNode> getBindings() {
		return bindings;
	}
	
	/**
	 * Returns the character offset of the match in the source.
	 * 
	 * @return the offset
	 */
	public int getOffset() {
		return offset;
	}
	
	/**
	 * Returns the character length of the match in the source.
	 * 
	 * @return the length
	 */
	public int getLength() {
		return length;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Match other = (Match) obj;
		return offset == other.offset 
				&& length == other.length
				&& Objects.equals(matchedNode, other.matchedNode);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(matchedNode, offset, length);
	}
	
	@Override
	public String toString() {
		return "Match[offset=" + offset + ", length=" + length + ", bindings=" + bindings.keySet() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
