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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

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
		private CoreException planningCoreFailure;
		private RuntimeException planningRuntimeFailure;
		private IJavaProject failingFixProject;
		private CoreException fixFailure;
		private CoreException postConditionFailure;

		@Override
		protected MultiFileCleanUpPlanResult<TestPlan> createPlan(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
			if (planningCoreFailure != null) {
				throw planningCoreFailure;
			}
			if (planningRuntimeFailure != null) {
				throw planningRuntimeFailure;
			}
			plannedUnits= compilationUnits;
			if (planningStatus.hasFatalError()) {
				return new MultiFileCleanUpPlanResult<>(null, planningStatus);
			}
			return new MultiFileCleanUpPlanResult<>(new TestPlan(project, List.of(compilationUnits)), planningStatus);
		}

		@Override
		protected ICleanUpFix createFixForPlan(TestPlan plan, CleanUpContext context) throws CoreException {
			assertSame(plan.project(), context.getCompilationUnit().getJavaProject());
			if (plan.project() == failingFixProject && fixFailure != null) {
				throw fixFailure;
			}
			fixedUnits.add(context.getCompilationUnit());
			return null;
		}

		@Override
		protected RefactoringStatus checkPlanPostConditions(IProgressMonitor monitor) throws CoreException {
			if (postConditionFailure != null) {
				throw postConditionFailure;
			}
			return new RefactoringStatus();
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
	void clearsPreviousPlanWhenPlanningThrowsCoreException() throws CoreException {
		IJavaProject project= javaProject();
		ICompilationUnit unit= compilationUnit(project, "Changed.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();
		cleanUp.checkPreConditions(project, new ICompilationUnit[] { unit }, new NullProgressMonitor());
		assertNotNull(cleanUp.retainedPlan(project));
		cleanUp.planningCoreFailure= new CoreException(Status.error("Planning failed")); //$NON-NLS-1$

		assertThrows(CoreException.class, () -> cleanUp.checkPreConditions(project,
				new ICompilationUnit[] { unit }, new NullProgressMonitor()));

		assertNull(cleanUp.retainedPlan(project));
	}

	@Test
	void clearsPreviousPlanWhenPlanningIsCancelled() throws CoreException {
		IJavaProject project= javaProject();
		ICompilationUnit unit= compilationUnit(project, "Cancelled.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();
		cleanUp.checkPreConditions(project, new ICompilationUnit[] { unit }, new NullProgressMonitor());
		assertNotNull(cleanUp.retainedPlan(project));
		cleanUp.planningRuntimeFailure= new OperationCanceledException();

		assertThrows(OperationCanceledException.class, () -> cleanUp.checkPreConditions(project,
				new ICompilationUnit[] { unit }, new NullProgressMonitor()));

		assertNull(cleanUp.retainedPlan(project));
	}

	@Test
	void fixResolutionFailureClearsOnlyTheAffectedProjectPlan() throws CoreException {
		IJavaProject firstProject= javaProject();
		IJavaProject secondProject= javaProject();
		ICompilationUnit first= compilationUnit(firstProject, "First.java"); //$NON-NLS-1$
		ICompilationUnit second= compilationUnit(secondProject, "Second.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();
		cleanUp.checkPreConditions(firstProject, new ICompilationUnit[] { first }, new NullProgressMonitor());
		cleanUp.checkPreConditions(secondProject, new ICompilationUnit[] { second }, new NullProgressMonitor());
		cleanUp.failingFixProject= firstProject;
		cleanUp.fixFailure= new CoreException(Status.error("Planned binding is stale")); //$NON-NLS-1$

		assertThrows(CoreException.class, () -> cleanUp.createFix(new CleanUpContext(first, null)));

		assertNull(cleanUp.retainedPlan(firstProject));
		assertNotNull(cleanUp.retainedPlan(secondProject));
		cleanUp.createFix(new CleanUpContext(second, null));
		assertEquals(List.of(second), cleanUp.fixedUnits);
	}

	@Test
	void postConditionExceptionClearsPlansForEveryProject() throws CoreException {
		IJavaProject firstProject= javaProject();
		IJavaProject secondProject= javaProject();
		ICompilationUnit first= compilationUnit(firstProject, "First.java"); //$NON-NLS-1$
		ICompilationUnit second= compilationUnit(secondProject, "Second.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();
		cleanUp.checkPreConditions(firstProject, new ICompilationUnit[] { first }, new NullProgressMonitor());
		cleanUp.checkPreConditions(secondProject, new ICompilationUnit[] { second }, new NullProgressMonitor());
		cleanUp.postConditionFailure= new CoreException(Status.error("Postcondition failed")); //$NON-NLS-1$

		assertThrows(CoreException.class, () -> cleanUp.checkPostConditions(new NullProgressMonitor()));

		assertNull(cleanUp.retainedPlan(firstProject));
		assertNull(cleanUp.retainedPlan(secondProject));
	}

	@Test
	void keepsPlansAndFixesIsolatedByJavaProject() throws CoreException {
		IJavaProject firstProject= javaProject();
		IJavaProject secondProject= javaProject();
		ICompilationUnit first= compilationUnit(firstProject, "SameName.java"); //$NON-NLS-1$
		ICompilationUnit second= compilationUnit(secondProject, "SameName.java"); //$NON-NLS-1$
		TestCleanUp cleanUp= new TestCleanUp();

		cleanUp.checkPreConditions(firstProject, new ICompilationUnit[] { first }, new NullProgressMonitor());
		cleanUp.checkPreConditions(secondProject, new ICompilationUnit[] { second }, new NullProgressMonitor());

		assertEquals(List.of(first), cleanUp.retainedPlan(firstProject).units());
		assertEquals(List.of(second), cleanUp.retainedPlan(secondProject).units());
		cleanUp.createFix(new CleanUpContext(second, null));
		cleanUp.createFix(new CleanUpContext(first, null));
		assertEquals(List.of(second, first), cleanUp.fixedUnits);
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
