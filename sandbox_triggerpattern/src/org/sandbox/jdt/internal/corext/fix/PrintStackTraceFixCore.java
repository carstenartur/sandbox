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
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Fix core for detecting {@code ex.printStackTrace()} calls.
 *
 * <p>This is a hint-only cleanup that flags {@code printStackTrace()} calls
 * but does not replace them, since the appropriate logger varies per project.
 * Findings are reported as problem markers via {@link HintFinding}.</p>
 */
public class PrintStackTraceFixCore {

	/**
	 * Finds printStackTrace() calls in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param findings the list to collect hint-only findings into
	 */
	public static void findFindings(CompilationUnit compilationUnit,
			List<HintFinding> findings) {

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
						findings.add(new HintFinding(
								"printStackTrace() call \u2014 consider using a logger", //$NON-NLS-1$
								compilationUnit.getLineNumber(node.getStartPosition()),
								node.getStartPosition(),
								node.getStartPosition() + node.getLength(),
								IMarker.SEVERITY_WARNING));
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
}
