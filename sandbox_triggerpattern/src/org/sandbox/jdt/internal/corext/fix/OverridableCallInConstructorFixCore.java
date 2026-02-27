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
package org.sandbox.jdt.internal.corext.fix;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Fix core for detecting overridable method calls in constructors.
 *
 * <p>Calling non-final, non-private, non-static methods from a constructor
 * is dangerous because the subclass may not be fully initialized yet.</p>
 *
 * @since 1.3.9
 */
public class OverridableCallInConstructorFixCore {

	/**
	 * Finds overridable method calls in constructors in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (!node.isConstructor()) {
					return true;
				}
				if (node.getBody() != null) {
					node.getBody().accept(new ASTVisitor() {
						@Override
						public boolean visit(MethodInvocation invocation) {
							if (invocation.getExpression() != null) {
								return true;
							}
							IMethodBinding binding = invocation.resolveMethodBinding();
							if (binding == null) {
								return true;
							}
							int modifiers = binding.getModifiers();
							if (!Modifier.isPrivate(modifiers)
									&& !Modifier.isStatic(modifiers)
									&& !Modifier.isFinal(modifiers)) {
								operations.add(new HintOnlyOperation());
							}
							return true;
						}
					});
				}
				return false;
			}
		});
	}

	private static class HintOnlyOperation extends CompilationUnitRewriteOperation {
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			// Hint-only: no rewrite
		}
	}
}
