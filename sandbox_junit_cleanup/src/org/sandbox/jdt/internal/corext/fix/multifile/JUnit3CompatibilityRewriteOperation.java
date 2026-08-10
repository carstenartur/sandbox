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
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/**
 * Removes only planner-authorized JUnit 3 compatibility members whose complete
 * source semantics have already been proven redundant after the Jupiter rewrite.
 */
final class JUnit3CompatibilityRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	private final Set<MethodDeclaration> declarations;
	private final Set<ExpressionStatement> lifecycleSuperCalls;

	JUnit3CompatibilityRewriteOperation(Collection<MethodDeclaration> declarations,
			Collection<ExpressionStatement> lifecycleSuperCalls) {
		this.declarations= Set.copyOf(new LinkedHashSet<>(declarations));
		this.lifecycleSuperCalls= Set.copyOf(new LinkedHashSet<>(lifecycleSuperCalls));
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		TextEditGroup group= createTextEditGroup(
				"Remove proven redundant JUnit 3 compatibility members", cuRewrite); //$NON-NLS-1$
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		for (ExpressionStatement statement : lifecycleSuperCalls) {
			rewrite.remove(statement, group);
		}
		for (MethodDeclaration declaration : declarations) {
			rewrite.remove(declaration, group);
		}
	}
}
