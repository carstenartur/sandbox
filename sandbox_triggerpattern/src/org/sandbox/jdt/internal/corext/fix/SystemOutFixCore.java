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
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Fix core for detecting {@code System.out.println()} and {@code System.err.println()} calls.
 *
 * <p>This is a hint-only cleanup that flags direct usage of
 * {@code System.out}/{@code System.err} for printing.
 * Findings are reported as problem markers via {@link HintFinding}.</p>
 */
public class SystemOutFixCore {

	/**
	 * Finds System.out/System.err usage in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param findings the list to collect hint-only findings into
	 */
	public static void findFindings(CompilationUnit compilationUnit,
			List<HintFinding> findings) {

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				Expression expr = node.getExpression();
				if (expr == null) {
					return true;
				}
				if (isSystemOutOrErr(expr)) {
					findings.add(new HintFinding(
							"System.out/err usage \u2014 consider using a logger", //$NON-NLS-1$
							compilationUnit.getLineNumber(node.getStartPosition()),
							node.getStartPosition(),
							node.getStartPosition() + node.getLength(),
							IMarker.SEVERITY_WARNING));
				}
				return true;
			}
		});
	}

	private static boolean isSystemOutOrErr(Expression expr) {
		if (expr instanceof QualifiedName qn) {
			String fullName = qn.getFullyQualifiedName();
			if ("System.out".equals(fullName) || "System.err".equals(fullName) //$NON-NLS-1$ //$NON-NLS-2$
					|| "java.lang.System.out".equals(fullName) //$NON-NLS-1$
					|| "java.lang.System.err".equals(fullName)) { //$NON-NLS-1$
				return true;
			}
			// Fall through to binding-based check for aliases or static imports
			IVariableBinding binding = resolveFieldBinding(qn);
			if (binding != null) {
				return isSystemField(binding);
			}
		}
		if (expr instanceof FieldAccess fa) {
			IVariableBinding binding = fa.resolveFieldBinding();
			if (binding != null) {
				return isSystemField(binding);
			}
		}
		if (expr instanceof Name name) {
			IVariableBinding binding = resolveFieldBinding(name);
			if (binding != null) {
				return isSystemField(binding);
			}
		}
		return false;
	}

	private static boolean isSystemField(IVariableBinding binding) {
		String fieldName = binding.getName();
		if ("out".equals(fieldName) || "err".equals(fieldName)) { //$NON-NLS-1$ //$NON-NLS-2$
			return binding.getDeclaringClass() != null
					&& "java.lang.System".equals(binding.getDeclaringClass().getQualifiedName()); //$NON-NLS-1$
		}
		return false;
	}

	private static IVariableBinding resolveFieldBinding(Name name) {
		if (name.resolveBinding() instanceof IVariableBinding vb && vb.isField()) {
			return vb;
		}
		return null;
	}
}
