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

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Fix core for detecting missing {@code hashCode()} when {@code equals()} is overridden.
 *
 * <p>Walks type declarations and checks whether a class overrides {@code equals(Object)}
 * without also overriding {@code hashCode()}.
 * Findings are reported as problem markers via {@link HintFinding}.</p>
 */
public class MissingHashCodeFixCore {

	/**
	 * Finds types with equals() but no hashCode() in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param findings the list to collect hint-only findings into
	 */
	public static void findFindings(CompilationUnit compilationUnit,
			List<HintFinding> findings) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				if (node.isInterface()) {
					return true;
				}
				boolean hasEquals = false;
				boolean hasHashCode = false;

				for (MethodDeclaration method : node.getMethods()) {
					IMethodBinding binding = method.resolveBinding();
					if (binding == null) {
						continue;
					}
					String name = binding.getName();
					ITypeBinding[] paramTypes = binding.getParameterTypes();
					if ("equals".equals(name) && paramTypes.length == 1 //$NON-NLS-1$
							&& "java.lang.Object".equals(paramTypes[0].getQualifiedName())) { //$NON-NLS-1$
						hasEquals = true;
					}
					if ("hashCode".equals(name) && paramTypes.length == 0) { //$NON-NLS-1$
						hasHashCode = true;
					}
				}

				if (hasEquals && !hasHashCode) {
					findings.add(new HintFinding(
							"equals() overridden without hashCode() \u2014 violates Object.hashCode() contract", //$NON-NLS-1$
							compilationUnit.getLineNumber(node.getStartPosition()),
							node.getStartPosition(),
							node.getStartPosition() + node.getLength(),
							IMarker.SEVERITY_WARNING));
				}
				return true;
			}
		});
	}
}
