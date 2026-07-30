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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ONEPARAM_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.TWOPARAM_ASSERTIONS;

import java.util.Set;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;

/** Binding-aware semantic facts used by the coordinated JUnit 3 planner. */
public final class JUnit3SemanticSupport {

	public static final String JUNIT3_TEST_CASE= "junit.framework.TestCase"; //$NON-NLS-1$
	public static final String JUNIT3_ASSERT= "junit.framework.Assert"; //$NON-NLS-1$

	public static final Set<String> ASSERTION_METHODS= Set.of(
			"assertEquals", "assertArrayEquals", "assertTrue", "assertFalse", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			"assertNull", "assertNotNull", "assertSame", "assertNotSame", "fail"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	private JUnit3SemanticSupport() {
	}

	public static boolean isExactTestMethod(MethodDeclaration method) {
		return !method.isConstructor() && method.getName().getIdentifier().startsWith("test") //$NON-NLS-1$
				&& Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
				&& method.parameters().isEmpty() && isVoidReturnType(method);
	}

	public static boolean isLifecycleMethod(MethodDeclaration method, String expectedName) {
		return expectedName.equals(method.getName().getIdentifier())
				&& !Modifier.isStatic(method.getModifiers()) && !Modifier.isPrivate(method.getModifiers())
				&& method.parameters().isEmpty() && isVoidReturnType(method);
	}

	/** Returns whether the JUnit 3 overload stores a message in its first argument. */
	public static boolean hasLeadingMessage(MethodInvocation invocation, IMethodBinding binding) {
		String name= invocation.getName().getIdentifier();
		int argumentCount= invocation.arguments().size();
		boolean messageShape= argumentCount == 2 && ONEPARAM_ASSERTIONS.contains(name)
				|| argumentCount == 3 && TWOPARAM_ASSERTIONS.contains(name)
				|| argumentCount == 4 && ("assertEquals".equals(name) || "assertArrayEquals".equals(name)); //$NON-NLS-1$ //$NON-NLS-2$
		if (!messageShape || binding == null || binding.getParameterTypes().length == 0) {
			return false;
		}
		ITypeBinding first= binding.getParameterTypes()[0].getErasure();
		return "java.lang.String".equals(first.getQualifiedName()); //$NON-NLS-1$
	}

	private static boolean isVoidReturnType(MethodDeclaration method) {
		Type returnType= method.getReturnType2();
		return returnType != null && returnType.isPrimitiveType()
				&& PrimitiveType.VOID.equals(((PrimitiveType) returnType).getPrimitiveTypeCode());
	}
}
