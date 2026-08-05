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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.sandbox.jdt.container.api.ContainerFlowSearchPlan;

class ContainerFlowScopeSearchArgumentValidationTest {

	private static final ContainerFlowSearchPlan EMPTY_PLAN=
			new ContainerFlowSearchPlan(List.of());
	private static final IJavaProject PROJECT= proxyProject();
	private static final List<ICompilationUnit> EMPTY_SCOPE= List.of();

	@Test
	void rejectsNullProjectBeforeStartingAWorkspaceSearch() {
		NullPointerException exception= assertThrows(NullPointerException.class,
				() -> ContainerFlowScopeSearch.findRelatedUnits(
						null, EMPTY_PLAN, EMPTY_SCOPE, EMPTY_SCOPE, null));

		assertEquals("project", exception.getMessage()); //$NON-NLS-1$
	}

	@Test
	void rejectsNullPlanBeforeStartingAWorkspaceSearch() {
		NullPointerException exception= assertThrows(NullPointerException.class,
				() -> ContainerFlowScopeSearch.findRelatedUnits(
						PROJECT, null, EMPTY_SCOPE, EMPTY_SCOPE, null));

		assertEquals("plan", exception.getMessage()); //$NON-NLS-1$
	}

	@Test
	void rejectsNullCurrentScopeBeforeStartingAWorkspaceSearch() {
		NullPointerException exception= assertThrows(NullPointerException.class,
				() -> ContainerFlowScopeSearch.findRelatedUnits(
						PROJECT, EMPTY_PLAN, null, EMPTY_SCOPE, null));

		assertEquals("currentScope", exception.getMessage()); //$NON-NLS-1$
	}

	@Test
	void rejectsNullAllowedUnitsBeforeStartingAWorkspaceSearch() {
		NullPointerException exception= assertThrows(NullPointerException.class,
				() -> ContainerFlowScopeSearch.findRelatedUnits(
						PROJECT, EMPTY_PLAN, EMPTY_SCOPE, null, null));

		assertEquals("allowedUnits", exception.getMessage()); //$NON-NLS-1$
	}

	private static IJavaProject proxyProject() {
		return (IJavaProject) Proxy.newProxyInstance(
				ContainerFlowScopeSearchArgumentValidationTest.class.getClassLoader(),
				new Class<?>[] { IJavaProject.class },
				(proxy, method, arguments) -> null);
	}
}
