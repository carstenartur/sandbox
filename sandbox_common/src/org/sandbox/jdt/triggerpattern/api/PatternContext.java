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
package org.sandbox.jdt.triggerpattern.api;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Context object providing access to pattern matching and AST rewriting facilities.
 * 
 * <p>This class wraps all the necessary components for implementing pattern-based
 * transformations in a cleanup plugin:</p>
 * <ul>
 *   <li>{@link Match} - The matched pattern with bindings</li>
 *   <li>{@link ASTRewrite} - For performing AST modifications</li>
 *   <li>{@link AST} - For creating new AST nodes</li>
 *   <li>{@link ImportRewrite} - For managing imports</li>
 *   <li>{@link TextEditGroup} - For grouping related edits</li>
 * </ul>
 * 
 * <p>This context is passed to methods annotated with {@link PatternHandler}.</p>
 * 
 * @since 1.3.0
 */
public final class PatternContext {
	private final Match match;
	private final ASTRewrite rewrite;
	private final AST ast;
	private final ImportRewrite importRewrite;
	private final TextEditGroup editGroup;
	
	/**
	 * Creates a new pattern context.
	 * 
	 * @param match the pattern match with bindings
	 * @param rewrite the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param editGroup the text edit group
	 */
	public PatternContext(Match match, ASTRewrite rewrite, AST ast, 
			ImportRewrite importRewrite, TextEditGroup editGroup) {
		this.match = Objects.requireNonNull(match, "Match cannot be null"); //$NON-NLS-1$
		this.rewrite = Objects.requireNonNull(rewrite, "ASTRewrite cannot be null"); //$NON-NLS-1$
		this.ast = Objects.requireNonNull(ast, "AST cannot be null"); //$NON-NLS-1$
		this.importRewrite = Objects.requireNonNull(importRewrite, "ImportRewrite cannot be null"); //$NON-NLS-1$
		this.editGroup = Objects.requireNonNull(editGroup, "TextEditGroup cannot be null"); //$NON-NLS-1$
	}
	
	/**
	 * Returns the pattern match.
	 * 
	 * @return the match
	 */
	public Match getMatch() {
		return match;
	}
	
	/**
	 * Returns the AST rewriter.
	 * 
	 * @return the rewriter
	 */
	public ASTRewrite getRewrite() {
		return rewrite;
	}
	
	/**
	 * Returns the AST instance.
	 * 
	 * @return the AST
	 */
	public AST getAST() {
		return ast;
	}
	
	/**
	 * Returns the import rewriter.
	 * 
	 * @return the import rewriter
	 */
	public ImportRewrite getImportRewrite() {
		return importRewrite;
	}
	
	/**
	 * Returns the text edit group.
	 * 
	 * @return the edit group
	 */
	public TextEditGroup getEditGroup() {
		return editGroup;
	}
	
	/**
	 * Gets a bound node for a single placeholder.
	 * 
	 * <p>Convenience method equivalent to {@code match.getBinding(name)}.</p>
	 * 
	 * @param placeholderName the placeholder name including $ marker (e.g., "$x")
	 * @return the bound node, or null if not found
	 */
	public ASTNode getBoundNode(String placeholderName) {
		return match.getBinding(placeholderName);
	}
	
	/**
	 * Gets a bound list of nodes for a multi-placeholder.
	 * 
	 * <p>Convenience method equivalent to {@code match.getListBinding(name)}.</p>
	 * 
	 * @param placeholderName the placeholder name including $ markers (e.g., "$args$")
	 * @return the list of bound nodes (never null, empty list if not found)
	 */
	public List<ASTNode> getBoundList(String placeholderName) {
		return match.getListBinding(placeholderName);
	}
	
	/**
	 * Gets the matched node itself.
	 * 
	 * <p>Convenience method equivalent to {@code match.getMatchedNode()}.</p>
	 * 
	 * @return the matched AST node
	 */
	public ASTNode getMatchedNode() {
		return match.getMatchedNode();
	}
}
