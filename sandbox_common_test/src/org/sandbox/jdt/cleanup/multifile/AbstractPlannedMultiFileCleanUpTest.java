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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

class AbstractPlannedMultiFileCleanUpTest {

	private record TestPlan(IJavaProject project, List<ICompilationUnit> units) {
	}

	private static final class TestCleanUp extends AbstractPlannedMultiFileCleanUp<TestPlan> {
		private final List<ICompilationUnit> fixedUnits= new ArrayList<>();
		private ICompilationUnit[] plannedUnits;
		private RefactoringStatus planningStatus= new RefactoringStatus();

		@Override
		protected MultiFileCleanUpPlanResult<TestPlan> createPlan(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) {
			plannedUnits= compilationUnits;
			if (planningStatus.hasFatalError()) {
				return new MultiFileCleanUpPlanResult<>(null, planningStatus);
			}
			return new MultiFileCleanUpPlanResult<>(new TestPlan(project, List.of(compilationUnits)), planningStatus);
		}

		@Override
		protected ICleanUpFix createFixForPlan(TestPlan plan, CleanUpContext context) {
			assertSame(plan.project(), context.getCompilationUnit().getJavaProject());
			fixedUnits.add(context.getCompilationUnit());
			return null;
		}

		TestPlan retainedPlan(IJavaProject project) {
			return getPlan(project);
		}
	}

	@Test
	void plansAllUnitsBeforeCreatingPerFileFixes() throws CoreException {
		IJavaProject project= javaProject();
		ICompilationUnit first= compilationUnit(project, "First.java"); //$NON-NLS-1$
		ICompilationUnit second= compilationUnit(project, "Second.java"); //$NON-NLS-1$
		ICompilationUnit[] selected= { first, second };
		TestCleanUp cleanUp= new TestCleanUp();

		RefactoringStatus status= cleanUp.checkPreConditions(project, selected, new NullProgressMonitor());

		assertFalse(status.hasError());
		assertEquals(List.of(first, second), List.of(cleanUp.plannedUnits));
		assertEquals(List.of(first, second), cleanUp.retainedPlan(project).units());

		cleanUp.createFix(new CleanUpContext(first, null));
		cleanUp.createFix(new CleanUpContext(second, null));
		assertEquals(List.of(first, second), cleanUp.fixedUnits);

		cleanUp.checkPostConditions(new NullProgressMonitor());
		assertNull(cleanUp.retainedPlan(project));
	}

	@Test
	void doesNotRetainPlanAfterFatalPlanningStatus() throws CoreException {
		IJavaProject project= javaProject();
		ICompilationUnit unit= compilationUnit(project, "Broken.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();
		cleanUp.planningStatus.addFatalError("Cannot build plan"); //$NON-NLS-1$

		RefactoringStatus status= cleanUp.checkPreConditions(project, new ICompilationUnit[] { unit },
				new NullProgressMonitor());

		assertEquals(RefactoringStatus.FATAL, status.getSeverity());
		assertNull(cleanUp.retainedPlan(project));
		assertNull(cleanUp.createFix(new CleanUpContext(unit, null)));
	}

	@Test
	void defaultScopeExpansionIsEmpty() throws CoreException {
		IJavaProject project= javaProject();
		ICompilationUnit unit= compilationUnit(project, "Only.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();

		Collection<ICompilationUnit> expanded= cleanUp.expandCleanUpScope(project, List.of(unit),
				new NullProgressMonitor());

		assertEquals(List.of(), List.copyOf(expanded));
	}

	private static IJavaProject javaProject() {
		return (IJavaProject) Proxy.newProxyInstance(AbstractPlannedMultiFileCleanUpTest.class.getClassLoader(),
				new Class<?>[] { IJavaProject.class }, (proxy, method, arguments) -> defaultValue(proxy, method.getName(), arguments));
	}

	private static ICompilationUnit compilationUnit(IJavaProject project, String name) {
		return (ICompilationUnit) Proxy.newProxyInstance(AbstractPlannedMultiFileCleanUpTest.class.getClassLoader(),
				new Class<?>[] { ICompilationUnit.class }, (proxy, method, arguments) -> {
					switch (method.getName()) {
						case "getJavaProject": //$NON-NLS-1$
							return project;
						case "getElementName": //$NON-NLS-1$
						case "getHandleIdentifier": //$NON-NLS-1$
						case "toString": //$NON-NLS-1$
							return name;
						default:
							return defaultValue(proxy, method.getName(), arguments);
					}
				});
	}

	private static Object defaultValue(Object proxy, String methodName, Object[] arguments) {
		if ("hashCode".equals(methodName)) { //$NON-NLS-1$
			return System.identityHashCode(proxy);
		}
		if ("equals".equals(methodName)) { //$NON-NLS-1$
			return proxy == arguments[0];
		}
		return null;
	}
}
