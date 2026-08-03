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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.ITypeBinding;

import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration.HierarchyMatch;

class BuiltInGuardRegistrationHierarchyTest {

	@Test
	void recoveredSupertypeMakesANegativeResultUnknown() {
		ITypeBinding recovered= binding("missing.Base", true, null); //$NON-NLS-1$
		ITypeBinding child= binding("sample.Child", false, recovered); //$NON-NLS-1$

		assertEquals(HierarchyMatch.UNKNOWN, BuiltInGuardRegistration.subtypeMatch(
				child, "java.lang.Number", new HashSet<>())); //$NON-NLS-1$
	}

	@Test
	void resolvedMatchWinsOverAnUnresolvedSiblingBranch() {
		ITypeBinding recovered= binding("missing.Contract", true, null); //$NON-NLS-1$
		ITypeBinding number= binding("java.lang.Number", false, null); //$NON-NLS-1$
		ITypeBinding child= binding("sample.Child", false, null, recovered, number); //$NON-NLS-1$

		assertEquals(HierarchyMatch.MATCH, BuiltInGuardRegistration.subtypeMatch(
				child, "java.lang.Number", new HashSet<>())); //$NON-NLS-1$
	}

	@Test
	void completeHierarchyCanProveNoMatch() {
		ITypeBinding object= binding("java.lang.Object", false, null); //$NON-NLS-1$
		ITypeBinding child= binding("sample.Child", false, object); //$NON-NLS-1$

		assertEquals(HierarchyMatch.NO_MATCH, BuiltInGuardRegistration.subtypeMatch(
				child, "java.lang.Number", new HashSet<>())); //$NON-NLS-1$
	}

	private static ITypeBinding binding(String qualifiedName, boolean recovered,
			ITypeBinding superclass, ITypeBinding... interfaces) {
		return (ITypeBinding) Proxy.newProxyInstance(
				BuiltInGuardRegistrationHierarchyTest.class.getClassLoader(),
				new Class<?>[] { ITypeBinding.class },
				(proxy, method, arguments) -> switch (method.getName()) {
				case "getTypeDeclaration" -> proxy; //$NON-NLS-1$
				case "getKey" -> "L" + qualifiedName.replace('.', '/') + ";"; //$NON-NLS-1$ //$NON-NLS-2$
				case "getQualifiedName", "toString" -> qualifiedName; //$NON-NLS-1$ //$NON-NLS-2$
				case "getName" -> qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1); //$NON-NLS-1$
				case "isRecovered" -> recovered; //$NON-NLS-1$
				case "getSuperclass" -> superclass; //$NON-NLS-1$
				case "getInterfaces" -> interfaces; //$NON-NLS-1$
				case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
				case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
				default -> defaultValue(method.getReturnType());
				});
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return type.isArray() ? Array.newInstance(type.componentType(), 0) : null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
		}
		return 0;
	}
}
