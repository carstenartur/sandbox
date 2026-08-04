/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;

import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchSeed;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;

class ContainerFlowResolvedSearchPlanTest {

	@Test
	void directMethodSearchRetainsExactIntent() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit unit= unit(project, "Source.java"); //$NON-NLS-1$
		IMethod method= proxy(IMethod.class, "method", unit); //$NON-NLS-1$
		ContainerFlowScopeSearch search= search(
				Map.of("method", method), //$NON-NLS-1$
				(candidate, monitor) -> new ContainerFlowScopeSearch.MethodFamily(
						List.of(candidate), true, List.of()));

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(methodSeed(
						SearchKind.METHOD_CALLERS, "method"))), //$NON-NLS-1$
				List.of(unit),
				List.of(unit),
				null);

		assertTrue(result.complete());
		assertEquals(1, result.resolvedPlan().targets().size());
		ResolvedSearchTarget resolved= result.resolvedPlan().targets().get(0);
		assertEquals(SearchKind.METHOD_CALLERS, resolved.searchKind());
		assertEquals("method", resolved.javaElementHandle()); //$NON-NLS-1$
		assertEquals(0, resolved.signatureIndex());
	}

	@Test
	void overrideFamilyRetainsEveryConcreteMember() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit unit= unit(project, "Source.java"); //$NON-NLS-1$
		IMethod root= proxy(IMethod.class, "root", unit); //$NON-NLS-1$
		IMethod override= proxy(IMethod.class, "override", unit); //$NON-NLS-1$
		List<IJavaElement> searchedTargets= new ArrayList<>();
		ContainerFlowScopeSearch search= new ContainerFlowScopeSearch(
				Map.of("root", root)::get, //$NON-NLS-1$
				(method, monitor) -> new ContainerFlowScopeSearch.MethodFamily(
						List.of(override, method, override), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) -> {
					searchedTargets.addAll(targets);
					return new RelatedCompilationUnitSearch.Result(
							List.copyOf(initial), true, List.of());
				});

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(methodSeed(
						SearchKind.METHOD_OVERRIDE_FAMILY, "root"))), //$NON-NLS-1$
				List.of(unit),
				List.of(unit),
				null);

		assertTrue(result.complete());
		assertEquals(List.of(override, root), searchedTargets);
		assertEquals(List.of("override", "root"), //$NON-NLS-1$ //$NON-NLS-2$
				result.resolvedPlan().targets().stream()
						.map(ResolvedSearchTarget::javaElementHandle)
						.sorted()
						.toList());
		assertTrue(result.resolvedPlan().targets().stream()
				.allMatch(target -> target.searchKind()
						== SearchKind.METHOD_OVERRIDE_FAMILY));
	}

	private static ContainerFlowScopeSearch search(
			Map<String, ? extends IJavaElement> elements,
			ContainerFlowScopeSearch.MethodFamilyResolver familyResolver) {
		return new ContainerFlowScopeSearch(
				elements::get,
				familyResolver,
				(project, targets, initial, allowed, monitor) ->
						new RelatedCompilationUnitSearch.Result(
								List.copyOf(initial), true, List.of()));
	}

	private static SearchSeed methodSeed(SearchKind kind, String handle) {
		return new SearchSeed(
				"parameter:method:0", //$NON-NLS-1$
				kind,
				"parameter-binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				handle,
				0,
				"Continue method flow"); //$NON-NLS-1$
	}

	private static ICompilationUnit unit(IJavaProject project, String handle) {
		return proxy(ICompilationUnit.class, handle, project);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, String handle, Object context) {
		return (T) Proxy.newProxyInstance(
				ContainerFlowResolvedSearchPlanTest.class.getClassLoader(),
				new Class<?>[] { type },
				(proxy, method, arguments) -> invoke(
						proxy, method, arguments, handle, context));
	}

	private static Object invoke(
			Object proxy,
			Method method,
			Object[] arguments,
			String handle,
			Object context) {
		return switch (method.getName()) {
			case "exists" -> true; //$NON-NLS-1$
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getPrimary" -> proxy; //$NON-NLS-1$
			case "getJavaProject" -> context instanceof IJavaProject project ? project : null; //$NON-NLS-1$
			case "getAncestor" -> context instanceof ICompilationUnit unit //$NON-NLS-1$
					&& arguments != null && arguments.length == 1
					&& Integer.valueOf(IJavaElement.COMPILATION_UNIT).equals(arguments[0])
							? unit : null;
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
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == char.class) {
			return '\0';
		}
		return null;
	}
}
