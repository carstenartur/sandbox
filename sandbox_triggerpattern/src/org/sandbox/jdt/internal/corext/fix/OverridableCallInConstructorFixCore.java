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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Fix core for detecting overridable method calls in constructors.
 *
 * <p>Calling non-final, non-private, non-static methods from a constructor
 * is dangerous because the subclass may not be fully initialized yet.
 * Findings are reported as problem markers via {@link HintFinding}.</p>
 */
public class OverridableCallInConstructorFixCore {

	/**
	 * Finds overridable method calls in constructors in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param findings the list to collect hint-only findings into
	 */
	public static void findFindings(CompilationUnit compilationUnit,
			List<HintFinding> findings) {

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
							// Skip calls on other receivers (e.g., someObject.method())
							// but allow unqualified calls and this.method() calls
							Expression receiver = invocation.getExpression();
							if (receiver != null && !(receiver instanceof ThisExpression)) {
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
								findings.add(new HintFinding(
										"Overridable method call in constructor \u2014 subclass may not be initialized", //$NON-NLS-1$
										compilationUnit.getLineNumber(invocation.getStartPosition()),
										invocation.getStartPosition(),
										invocation.getStartPosition() + invocation.getLength(),
										IMarker.SEVERITY_WARNING));
							}
							return true;
						}
					});
				}
				return false;
			}
		});
	}
}
