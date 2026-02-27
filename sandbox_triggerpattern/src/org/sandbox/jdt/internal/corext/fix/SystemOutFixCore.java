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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Fix core for detecting {@code System.out.println()} and {@code System.err.println()} calls.
 *
 * <p>This is a hint-only cleanup that flags direct usage of
 * {@code System.out}/{@code System.err} for printing.</p>
 *
 * @since 1.3.9
 */
public class SystemOutFixCore {

	/**
	 * Finds System.out/System.err usage in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				Expression expr = node.getExpression();
				if (expr == null) {
					return true;
				}
				if (isSystemOutOrErr(expr)) {
					operations.add(new HintOnlyOperation());
				}
				return true;
			}
		});
	}

	private static boolean isSystemOutOrErr(Expression expr) {
		if (expr instanceof QualifiedName qn) {
			String fullName = qn.getFullyQualifiedName();
			return "System.out".equals(fullName) || "System.err".equals(fullName); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (expr instanceof FieldAccess fa) {
			IVariableBinding binding = fa.resolveFieldBinding();
			if (binding != null) {
				String fieldName = binding.getName();
				if ("out".equals(fieldName) || "err".equals(fieldName)) { //$NON-NLS-1$ //$NON-NLS-2$
					if (binding.getDeclaringClass() != null
							&& "java.lang.System".equals(binding.getDeclaringClass().getQualifiedName())) { //$NON-NLS-1$
						return true;
					}
				}
			}
		}
		if (expr instanceof Name name) {
			IVariableBinding binding = resolveFieldBinding(name);
			if (binding != null) {
				String fieldName = binding.getName();
				if ("out".equals(fieldName) || "err".equals(fieldName)) { //$NON-NLS-1$ //$NON-NLS-2$
					if (binding.getDeclaringClass() != null
							&& "java.lang.System".equals(binding.getDeclaringClass().getQualifiedName())) { //$NON-NLS-1$
						return true;
					}
				}
			}
		}
		return false;
	}

	private static IVariableBinding resolveFieldBinding(Name name) {
		if (name.resolveBinding() instanceof IVariableBinding vb && vb.isField()) {
			return vb;
		}
		return null;
	}

	private static class HintOnlyOperation extends CompilationUnitRewriteOperation {
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			// Hint-only: no rewrite
		}
	}
}
