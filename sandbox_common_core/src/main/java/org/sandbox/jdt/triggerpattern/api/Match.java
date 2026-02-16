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
import java.util.LinkedHashMap;
import java.util.List;
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
 *   <li>A map of multi-placeholder bindings (e.g., {@code "$args$" -> List<Expression>})</li>
 *   <li>Source location (offset and length)</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public final class Match {
	/**
	 * Represents a type-safe placeholder binding.
	 * 
	 * @since 1.2.6
	 */
	public sealed interface Binding {
		/**
		 * A binding to a single AST node.
		 */
		record SingleNode(ASTNode node) implements Binding {}
		
		/**
		 * A binding to a list of AST nodes (for variadic/multi-placeholders like $args$).
		 */
		record NodeList(List<ASTNode> nodes) implements Binding {}
	}

	private final ASTNode matchedNode;
	private final Map<String, Object> bindings;  // Changed to Object to support both ASTNode and List<ASTNode>
	private final int offset;
	private final int length;
	
	/**
	 * Creates a new match.
	 * 
	 * @param matchedNode the AST node that matched the pattern
	 * @param bindings map of placeholder names to their bound AST nodes or lists of AST nodes
	 * @param offset the character offset of the match in the source
	 * @param length the character length of the match in the source
	 */
	public Match(ASTNode matchedNode, Map<String, Object> bindings, int offset, int length) {
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
	 * @return an unmodifiable map of placeholder names to AST nodes or lists of AST nodes
	 */
	public Map<String, Object> getBindings() {
		return bindings;
	}
	
	/**
	 * Gets a single-placeholder binding as an AST node.
	 * 
	 * @param placeholderName the placeholder name including $ marker (e.g., "$x")
	 * @return the bound AST node, or null if not found or if it's a multi-placeholder binding
	 */
	public ASTNode getBinding(String placeholderName) {
		Object binding = bindings.get(placeholderName);
		if (binding instanceof ASTNode) {
			return (ASTNode) binding;
		}
		return null;
	}
	
	/**
	 * Gets a multi-placeholder binding as a list of nodes.
	 * 
	 * @param placeholderName the placeholder name including $ markers (e.g., "$args$")
	 * @return the list of matched nodes, or empty list if not found or if it's a single-placeholder binding
	 */
	@SuppressWarnings("unchecked")
	public List<ASTNode> getListBinding(String placeholderName) {
		Object binding = bindings.get(placeholderName);
		if (binding instanceof List<?>) {
			return (List<ASTNode>) binding;
		}
		return Collections.emptyList();
	}
	
	/**
	 * Returns the placeholder bindings as type-safe {@link Binding} objects.
	 * 
	 * @return an unmodifiable map of placeholder names to their typed bindings
	 * @since 1.2.6
	 */
	public Map<String, Binding> getTypedBindings() {
		Map<String, Binding> typed = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : bindings.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof ASTNode astNode) {
				typed.put(entry.getKey(), new Binding.SingleNode(astNode));
			} else if (value instanceof List<?> list) {
				@SuppressWarnings("unchecked")
				List<ASTNode> nodeList = (List<ASTNode>) list;
				typed.put(entry.getKey(), new Binding.NodeList(Collections.unmodifiableList(nodeList)));
			}
		}
		return Collections.unmodifiableMap(typed);
	}

	/**
	 * Checks if a binding exists for the given placeholder name.
	 * 
	 * @param placeholderName the placeholder name including $ marker
	 * @return {@code true} if a binding exists
	 * @since 1.2.6
	 */
	public boolean hasBinding(String placeholderName) {
		return bindings.containsKey(placeholderName);
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
