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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Fix core for detecting obsolete collection types.
 *
 * <p>Warns on usage of {@code Vector}, {@code Hashtable}, and {@code Stack},
 * which are legacy synchronized collections that should typically be replaced.</p>
 *
 * @since 1.3.9
 */
public class ObsoleteCollectionFixCore {

	private static final Set<String> OBSOLETE_TYPES = Set.of(
			"java.util.Vector", //$NON-NLS-1$
			"java.util.Hashtable", //$NON-NLS-1$
			"java.util.Stack" //$NON-NLS-1$
	);

	/**
	 * Finds obsolete collection usage in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(ClassInstanceCreation node) {
				ITypeBinding typeBinding = node.resolveTypeBinding();
				if (typeBinding != null && OBSOLETE_TYPES.contains(typeBinding.getErasure().getQualifiedName())) {
					operations.add(new HintOnlyOperation());
				}
				return true;
			}

			@Override
			public boolean visit(VariableDeclarationStatement node) {
				Type type = node.getType();
				if (type instanceof SimpleType simpleType) {
					ITypeBinding binding = simpleType.resolveBinding();
					if (binding != null && OBSOLETE_TYPES.contains(binding.getErasure().getQualifiedName())) {
						operations.add(new HintOnlyOperation());
					}
				}
				return true;
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
