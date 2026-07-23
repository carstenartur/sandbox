/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.SearchMatch;

class RelatedCompilationUnitSearchTest {

	@Test
	void accurateAllowedMatchesProduceDeterministicClosedScope() {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "z/Selected.java"); //$NON-NLS-1$
		ICompilationUnit related= unit(project, "a/Related.java"); //$NON-NLS-1$
		RelatedCompilationUnitSearch.MatchAccumulator accumulator=
				new RelatedCompilationUnitSearch.MatchAccumulator(project, List.of(selected),
						List.of(selected, related));

		accumulator.accept(element(related), SearchMatch.A_ACCURATE);
		RelatedCompilationUnitSearch.Result result= accumulator.finish();

		assertTrue(result.complete());
		assertEquals(List.of("a/Related.java", "z/Selected.java"), //$NON-NLS-1$ //$NON-NLS-2$
				result.compilationUnits().stream().map(ICompilationUnit::getHandleIdentifier).toList());
		assertTrue(result.rejectionReasons().isEmpty());
	}

	@Test
	void inaccurateBinaryExternalAndExcludedMatchesFailClosed() {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		IJavaProject otherProject= proxy(IJavaProject.class, "other", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "src/Selected.java"); //$NON-NLS-1$
		ICompilationUnit excluded= unit(project, "generated/Generated.java"); //$NON-NLS-1$
		ICompilationUnit external= unit(otherProject, "other/External.java"); //$NON-NLS-1$
		RelatedCompilationUnitSearch.MatchAccumulator accumulator=
				new RelatedCompilationUnitSearch.MatchAccumulator(project, List.of(selected), List.of(selected));

		accumulator.accept(element(selected), SearchMatch.A_INACCURATE);
		accumulator.accept(proxy(IJavaElement.class, "binary", null), SearchMatch.A_ACCURATE); //$NON-NLS-1$
		accumulator.accept(element(external), SearchMatch.A_ACCURATE);
		accumulator.accept(element(excluded), SearchMatch.A_ACCURATE);
		RelatedCompilationUnitSearch.Result result= accumulator.finish();

		assertFalse(result.complete());
		assertEquals(List.of("src/Selected.java"), //$NON-NLS-1$
				result.compilationUnits().stream().map(ICompilationUnit::getHandleIdentifier).toList());
		assertEquals(3, result.rejectionReasons().size());
		assertTrue(result.rejectionReasons().stream().anyMatch(reason -> reason.contains("inaccurate"))); //$NON-NLS-1$
		assertTrue(result.rejectionReasons().stream().anyMatch(reason -> reason.contains("binary"))); //$NON-NLS-1$
		assertTrue(result.rejectionReasons().stream().anyMatch(reason -> reason.contains("outside"))); //$NON-NLS-1$
	}

	@Test
	void declarationOutsideAllowListPreventsClosure() {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "src/Selected.java"); //$NON-NLS-1$
		ICompilationUnit generated= unit(project, "generated/Owner.java"); //$NON-NLS-1$
		RelatedCompilationUnitSearch.MatchAccumulator accumulator=
				new RelatedCompilationUnitSearch.MatchAccumulator(project, List.of(selected), List.of(selected));

		accumulator.addTarget(element(generated));
		RelatedCompilationUnitSearch.Result result= accumulator.finish();

		assertFalse(result.complete());
		assertEquals(1, result.rejectionReasons().size());
		assertTrue(result.rejectionReasons().get(0).contains("declaration")); //$NON-NLS-1$
	}

	private static ICompilationUnit unit(IJavaProject project, String handle) {
		return proxy(ICompilationUnit.class, handle, project);
	}

	private static IJavaElement element(ICompilationUnit unit) {
		return proxy(IJavaElement.class, "element:" + unit.getHandleIdentifier(), unit); //$NON-NLS-1$
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, String handle, Object context) {
		return (T) Proxy.newProxyInstance(RelatedCompilationUnitSearchTest.class.getClassLoader(),
				new Class<?>[] { type }, (proxy, method, arguments) -> invoke(proxy, method, arguments, handle, context));
	}

	private static Object invoke(Object proxy, Method method, Object[] arguments, String handle, Object context) {
		return switch (method.getName()) {
			case "exists" -> true; //$NON-NLS-1$
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getPrimary" -> proxy; //$NON-NLS-1$
			case "getJavaProject" -> context instanceof IJavaProject project ? project : null; //$NON-NLS-1$
			case "getAncestor" -> context instanceof ICompilationUnit unit //$NON-NLS-1$
					&& arguments != null && arguments.length == 1
					&& Integer.valueOf(IJavaElement.COMPILATION_UNIT).equals(arguments[0]) ? unit : null;
			case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
			case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
			case "toString" -> handle; //$NON-NLS-1$
			default -> primitiveDefault(method.getReturnType());
		};
	}

	private static Object primitiveDefault(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return '\0';
		}
		return null;
	}
}
