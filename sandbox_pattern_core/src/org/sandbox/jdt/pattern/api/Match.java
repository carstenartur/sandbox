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
package org.sandbox.jdt.pattern.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Represents a successful match of a pattern against source code.
 * <p>
 * A match contains:
 * <ul>
 * <li>The matched AST node</li>
 * <li>Bindings mapping placeholder names to their matched AST nodes</li>
 * <li>Source position information (offset and length)</li>
 * </ul>
 * </p>
 * 
 * @since 1.0
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
	 * @param bindings map of placeholder names to their matched nodes
	 * @param offset the start offset in the source file
	 * @param length the length of the matched text
	 */
	public Match(ASTNode matchedNode, Map<String, ASTNode> bindings, int offset, int length) {
		this.matchedNode= Objects.requireNonNull(matchedNode, "Matched node cannot be null");
		this.bindings= Collections.unmodifiableMap(Objects.requireNonNull(bindings, "Bindings cannot be null"));
		this.offset= offset;
		this.length= length;
	}

	/**
	 * Returns the AST node that matched the pattern.
	 * 
	 * @return the matched node
	 */
	public ASTNode getMatchedNode() {
		return matchedNode;
	}

	/**
	 * Returns the bindings of placeholder names to matched AST nodes.
	 * <p>
	 * For example, if the pattern was {@code "$x.toString()"} and it matched
	 * {@code "myObj.toString()"}, the bindings would contain {@code "x" -> SimpleName(myObj)}.
	 * </p>
	 * 
	 * @return unmodifiable map of placeholder bindings
	 */
	public Map<String, ASTNode> getBindings() {
		return bindings;
	}

	/**
	 * Returns the start offset of the match in the source file.
	 * 
	 * @return the start offset
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Returns the length of the matched text.
	 * 
	 * @return the length in characters
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Returns the binding for a specific placeholder name.
	 * 
	 * @param name the placeholder name (without the $ prefix)
	 * @return the bound AST node, or null if not found
	 */
	public ASTNode getBinding(String name) {
		return bindings.get(name);
	}

	@Override
	public String toString() {
		return "Match[offset=" + offset + ", length=" + length + ", bindings=" + bindings.size() + "]";
	}
}
