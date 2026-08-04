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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;

import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchSeed;

class ContainerFlowScopeSearchTest {

	@Test
	void emptyPlanValidatesAndReturnsCurrentScope() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "z/Selected.java"); //$NON-NLS-1$
		AtomicInteger searchCalls= new AtomicInteger();
		ContainerFlowScopeSearch search= search(
				Map.of(),
				(method, monitor) -> new ContainerFlowScopeSearch.MethodFamily(List.of(method), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) -> {
					searchCalls.incrementAndGet();
					return new RelatedCompilationUnitSearch.Result(List.of(), true, List.of());
				});

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of()),
				List.of(selected),
				List.of(selected),
				null);

		assertTrue(result.complete());
		assertEquals(List.of(selected), result.compilationUnits());
		assertTrue(result.rejectionReasons().isEmpty());
		assertEquals(0, searchCalls.get());
	}

	@Test
	void missingJavaHandleFailsClosedWithoutInventingATarget() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "Selected.java"); //$NON-NLS-1$
		AtomicInteger searchCalls= new AtomicInteger();
		ContainerFlowScopeSearch search= search(
				Map.of(),
				(method, monitor) -> new ContainerFlowScopeSearch.MethodFamily(List.of(method), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) -> {
					searchCalls.incrementAndGet();
					return new RelatedCompilationUnitSearch.Result(List.copyOf(initial), true, List.of());
				});
		SearchSeed seed= new SearchSeed(
				"return:method", //$NON-NLS-1$
				SearchKind.METHOD_CALLERS,
				"", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"", //$NON-NLS-1$
				-1,
				"Find callers"); //$NON-NLS-1$

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(seed)),
				List.of(selected),
				List.of(selected),
				null);

		assertFalse(result.complete());
		assertEquals(List.of(selected), result.compilationUnits());
		assertTrue(result.rejectionReasons().stream()
				.anyMatch(reason -> reason.contains("no Java-model handle"))); //$NON-NLS-1$
		assertEquals(0, searchCalls.get());
	}

	@Test
	void wrongResolvedElementTypeFailsClosed() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "Selected.java"); //$NON-NLS-1$
		IMethod method= proxy(IMethod.class, "method", selected); //$NON-NLS-1$
		ContainerFlowScopeSearch search= search(
				Map.of("field", method), //$NON-NLS-1$
				(candidate, monitor) -> new ContainerFlowScopeSearch.MethodFamily(List.of(candidate), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) ->
						new RelatedCompilationUnitSearch.Result(List.copyOf(initial), true, List.of()));
		SearchSeed seed= new SearchSeed(
				"field:values", //$NON-NLS-1$
				SearchKind.FIELD_REFERENCES,
				"binding", //$NON-NLS-1$
				"owner", //$NON-NLS-1$
				"field", //$NON-NLS-1$
				-1,
				"Find field references"); //$NON-NLS-1$

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(seed)),
				List.of(selected),
				List.of(selected),
				null);

		assertFalse(result.complete());
		assertTrue(result.rejectionReasons().stream()
				.anyMatch(reason -> reason.contains("does not resolve to an IField"))); //$NON-NLS-1$
	}

	@Test
	void duplicateMethodSeedsDelegateOneExactTarget() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "Selected.java"); //$NON-NLS-1$
		IMethod method= proxy(IMethod.class, "method", selected); //$NON-NLS-1$
		List<IJavaElement> capturedTargets= new ArrayList<>();
		ContainerFlowScopeSearch search= search(
				Map.of("method", method), //$NON-NLS-1$
				(candidate, monitor) -> new ContainerFlowScopeSearch.MethodFamily(List.of(candidate), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) -> {
					capturedTargets.addAll(targets);
					return new RelatedCompilationUnitSearch.Result(List.copyOf(initial), true, List.of());
				});
		SearchSeed callers= methodSeed(SearchKind.METHOD_CALLERS, "method"); //$NON-NLS-1$
		SearchSeed declaration= methodSeed(SearchKind.METHOD_DECLARATION, "method"); //$NON-NLS-1$

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(callers, declaration)),
				List.of(selected),
				List.of(selected),
				null);

		assertTrue(result.complete());
		assertEquals(List.of(method), capturedTargets);
	}

	@Test
	void overrideFamilyMembersAreExpandedBeforeReferenceSearch() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "Selected.java"); //$NON-NLS-1$
		IMethod root= proxy(IMethod.class, "root", selected); //$NON-NLS-1$
		IMethod override= proxy(IMethod.class, "override", selected); //$NON-NLS-1$
		List<IJavaElement> capturedTargets= new ArrayList<>();
		ContainerFlowScopeSearch search= search(
				Map.of("root", root), //$NON-NLS-1$
				(method, monitor) -> new ContainerFlowScopeSearch.MethodFamily(
						List.of(override, method, override), true, List.of()),
				(projectArg, targets, initial, allowed, monitor) -> {
					capturedTargets.addAll(targets);
					return new RelatedCompilationUnitSearch.Result(List.copyOf(initial), true, List.of());
				});

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(
						methodSeed(SearchKind.METHOD_OVERRIDE_FAMILY, "root"))), //$NON-NLS-1$
				List.of(selected),
				List.of(selected),
				null);

		assertTrue(result.complete());
		assertEquals(List.of(override, root), capturedTargets);
	}

	@Test
	void incompleteFamilyReasonsArePreserved() throws Exception {
		IJavaProject project= proxy(IJavaProject.class, "project", null); //$NON-NLS-1$
		ICompilationUnit selected= unit(project, "Selected.java"); //$NON-NLS-1$
		IMethod method= proxy(IMethod.class, "method", selected); //$NON-NLS-1$
		ContainerFlowScopeSearch search= search(
				Map.of("method", method), //$NON-NLS-1$
				(candidate, monitor) -> new ContainerFlowScopeSearch.MethodFamily(
						List.of(candidate), false, List.of("Incomplete hierarchy")), //$NON-NLS-1$
				(projectArg, targets, initial, allowed, monitor) ->
						new RelatedCompilationUnitSearch.Result(List.copyOf(initial), true, List.of()));

		ContainerFlowScopeSearch.Result result= search.find(
				project,
				new ContainerFlowSearchPlan(List.of(
						methodSeed(SearchKind.METHOD_OVERRIDE_FAMILY, "method"))), //$NON-NLS-1$
				List.of(selected),
				List.of(selected),
				null);

		assertFalse(result.complete());
		assertEquals(List.of("Incomplete hierarchy"), result.rejectionReasons()); //$NON-NLS-1$
	}

	private static SearchSeed methodSeed(SearchKind kind, String handle) {
		return new SearchSeed(
				"parameter:method:0", //$NON-NLS-1$
				kind,
				"binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				handle,
				0,
				"Search method flow"); //$NON-NLS-1$
	}

	private static ContainerFlowScopeSearch search(
			Map<String, ? extends IJavaElement> elements,
			ContainerFlowScopeSearch.MethodFamilyResolver familyResolver,
			ContainerFlowScopeSearch.RelatedUnitFinder finder) {
		return new ContainerFlowScopeSearch(elements::get, familyResolver, finder);
	}

	private static ICompilationUnit unit(IJavaProject project, String handle) {
		return proxy(ICompilationUnit.class, handle, project);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, String handle, Object context) {
		return (T) Proxy.newProxyInstance(
				ContainerFlowScopeSearchTest.class.getClassLoader(),
				new Class<?>[] { type },
				(proxy, method, arguments) -> invoke(proxy, method, arguments, handle, context));
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
