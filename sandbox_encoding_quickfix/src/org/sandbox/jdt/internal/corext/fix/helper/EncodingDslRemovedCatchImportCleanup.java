/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/** Tracks catches removed by atomic encoding DSL rewrites for import cleanup. */
public final class EncodingDslRemovedCatchImportCleanup extends CompilationUnitRewriteOperation {
	private static final String EXCEPTION = "UnsupportedEncodingException"; //$NON-NLS-1$
	private static final Set<String> ENCODINGS = Set.of(
			"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"US-ASCII", "ISO-8859-1"); //$NON-NLS-1$ //$NON-NLS-2$

	private final Set<CatchClause> removedCatches;

	private EncodingDslRemovedCatchImportCleanup(Set<CatchClause> removedCatches) {
		this.removedCatches = removedCatches;
	}

	public static CompilationUnitRewriteOperation create(Set<ASTNode> processedNodes) {
		Set<CatchClause> catches = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ASTNode node : processedNodes) {
			if (containsEncoding(node)) {
				CatchClause catchClause = removedCatch(node);
				if (catchClause != null) {
					catches.add(catchClause);
				}
			}
		}
		if (catches.isEmpty() || hasExternalTypeUse(catches.iterator().next().getRoot(), catches)) {
			return null;
		}
		return new EncodingDslRemovedCatchImportCleanup(catches);
	}

	@Override
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
		for (CatchClause catchClause : removedCatches) {
			cuRewrite.getImportRemover().registerRemovedNode(catchClause);
		}
	}

	private static boolean containsEncoding(ASTNode node) {
		boolean[] found = { false };
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral literal) {
				found[0] = ENCODINGS.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT));
				return !found[0];
			}
		});
		return found[0];
	}

	private static boolean hasExternalTypeUse(ASTNode root, Set<CatchClause> catches) {
		boolean[] found = { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleType type) {
				if (EXCEPTION.equals(type.getName().toString()) && !inside(type, catches)) {
					found[0] = true;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static boolean inside(ASTNode node, Set<CatchClause> catches) {
		for (ASTNode current = node; current != null; current = current.getParent()) {
			if (catches.contains(current)) {
				return true;
			}
		}
		return false;
	}

	private static CatchClause removedCatch(ASTNode node) {
		Statement statement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
		if (statement == null || !(statement.getParent() instanceof Block body)
				|| !(body.getParent() instanceof TryStatement tryStatement)
				|| tryStatement.getBody() != body || !(tryStatement.getParent() instanceof Block)
				|| !tryStatement.resources().isEmpty() || tryStatement.getFinally() != null) {
			return null;
		}
		@SuppressWarnings("unchecked")
		List<CatchClause> catches = tryStatement.catchClauses();
		if (catches.size() != 1) {
			return null;
		}
		CatchClause catchClause = catches.get(0);
		Type type = catchClause.getException().getType();
		return type instanceof SimpleType simpleType && EXCEPTION.equals(simpleType.getName().toString())
				? catchClause : null;
	}
}
