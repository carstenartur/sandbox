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
package org.sandbox.ast.api.jdt;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Fluent wrapper around {@link ASTRewrite} providing convenient
 * methods for common rewrite operations.
 */
public class FluentRewriter {
	private final ASTRewrite rewrite;
	private final TextEditGroup group;

	public FluentRewriter(ASTRewrite rewrite, TextEditGroup group) {
		this.rewrite = rewrite;
		this.group = group;
	}

	/** Remove a node, keeping its comments. */
	public FluentRewriter remove(ASTNode node) {
		ASTNodes.removeButKeepComment(rewrite, node, group);
		return this;
	}

	/** Replace a node, keeping its comments. */
	public FluentRewriter replace(ASTNode oldNode, ASTNode newNode) {
		ASTNodes.replaceButKeepComment(rewrite, oldNode, newNode, group);
		return this;
	}

	/** Create a copy target (deep copy) for the given node. */
	@SuppressWarnings("unchecked")
	public <T extends ASTNode> T copyTarget(T node) {
		return (T) rewrite.createCopyTarget(node);
	}

	/** Create a move target for the given statement. */
	public ASTNode moveTarget(Statement statement) {
		return ASTNodes.createMoveTarget(rewrite, statement);
	}

	/** Raw remove without comment preservation. */
	public FluentRewriter removeRaw(ASTNode node) {
		rewrite.remove(node, group);
		return this;
	}

	/** Get the underlying ASTRewrite for advanced operations. */
	public ASTRewrite getRewrite() {
		return rewrite;
	}

	/** Get the underlying TextEditGroup. */
	public TextEditGroup getGroup() {
		return group;
	}
}
