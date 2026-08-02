/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
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

/**
 * Finalizes import tracking for encoding transformations handled by the DSL.
 *
 * <p>The atomic DSL rewrite replaces the body of a removable encoding
 * {@code try/catch} and removes the surrounding try statement. The statements
 * remain semantically present, but the checked-exception catch clause does not.
 * Registering precisely that catch clause lets JDT's {@code ImportRemover}
 * remove {@code UnsupportedEncodingException} only when no other type use
 * survives in the compilation unit.</p>
 */
public final class EncodingDslRemovedCatchImportCleanup extends CompilationUnitRewriteOperation {

	private static final String UNSUPPORTED_ENCODING_EXCEPTION = "UnsupportedEncodingException"; //$NON-NLS-1$

	private static final Set<String> KNOWN_ENCODINGS = Set.of(
			"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"US-ASCII", "ISO-8859-1"); //$NON-NLS-1$ //$NON-NLS-2$

	private final Set<CatchClause> removedCatches;

	private EncodingDslRemovedCatchImportCleanup(Set<CatchClause> removedCatches) {
		this.removedCatches = removedCatches;
	}

	/**
	 * Creates the finalizer for DSL-matched encoding expressions.
	 *
	 * @param processedNodes nodes already claimed by the encoding DSL
	 * @return a finalizer, or {@code null} when no removable catch was matched
	 */
	public static CompilationUnitRewriteOperation create(Set<ASTNode> processedNodes) {
		Set<CatchClause> removedCatches = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ASTNode processedNode : processedNodes) {
			if (!containsKnownEncodingLiteral(processedNode)) {
				continue;
			}
			CatchClause removedCatch = findAtomicallyRemovedCatch(processedNode);
			if (removedCatch != null) {
				removedCatches.add(removedCatch);
			}
		}
		return removedCatches.isEmpty() ? null : new EncodingDslRemovedCatchImportCleanup(removedCatches);
	}

	@Override
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
		for (CatchClause removedCatch : removedCatches) {
			cuRewrite.getImportRemover().registerRemovedNode(removedCatch);
		}
	}

	private static boolean containsKnownEncodingLiteral(ASTNode node) {
		boolean[] found = { false };
		node.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral literal) {
				found[0] = KNOWN_ENCODINGS.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT));
				return !found[0];
			}
		});
		return found[0];
	}

	private static CatchClause findAtomicallyRemovedCatch(ASTNode node) {
		Statement statement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
		if (statement == null || !(statement.getParent() instanceof Block tryBody)
				|| !(tryBody.getParent() instanceof TryStatement tryStatement)
				|| tryStatement.getBody() != tryBody
				|| !(tryStatement.getParent() instanceof Block)
				|| !tryStatement.resources().isEmpty()
				|| tryStatement.getFinally() != null) {
			return null;
		}

		@SuppressWarnings("unchecked")
		List<CatchClause> catchClauses = tryStatement.catchClauses();
		if (catchClauses.size() != 1) {
			return null;
		}
		CatchClause catchClause = catchClauses.get(0);
		Type exceptionType = catchClause.getException().getType();
		if (exceptionType instanceof SimpleType simpleType
				&& UNSUPPORTED_ENCODING_EXCEPTION.equals(simpleType.getName().toString())) {
			return catchClause;
		}
		return null;
	}
}
