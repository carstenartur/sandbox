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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Fix core for detecting {@code ex.printStackTrace()} calls.
 *
 * <p>This is a hint-only cleanup that flags {@code printStackTrace()} calls
 * but does not replace them, since the appropriate logger varies per project.</p>
 *
 * @since 1.3.9
 */
public class PrintStackTraceFixCore {

	/**
	 * Finds printStackTrace() calls in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (!"printStackTrace".equals(node.getName().getIdentifier())) { //$NON-NLS-1$
					return true;
				}
				if (!node.arguments().isEmpty()) {
					return true;
				}
				IMethodBinding binding = node.resolveMethodBinding();
				if (binding != null) {
					ITypeBinding declaringClass = binding.getDeclaringClass();
					if (declaringClass != null && isThrowable(declaringClass)) {
						operations.add(new HintOnlyOperation());
					}
				}
				return true;
			}
		});
	}

	private static boolean isThrowable(ITypeBinding type) {
		ITypeBinding current = type;
		while (current != null) {
			if ("java.lang.Throwable".equals(current.getQualifiedName())) { //$NON-NLS-1$
				return true;
			}
			current = current.getSuperclass();
		}
		return false;
	}

	private static class HintOnlyOperation extends CompilationUnitRewriteOperation {
		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			// Hint-only: no rewrite
		}
	}
}
