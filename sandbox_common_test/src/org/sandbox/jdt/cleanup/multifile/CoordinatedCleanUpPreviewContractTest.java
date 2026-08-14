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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

class CoordinatedCleanUpPreviewContractTest {

	private record TestPlan() {
	}

	private static final class TestCleanUp extends AbstractPlannedMultiFileCleanUp<TestPlan> {
		private final MultiFileCleanUpDiagnostics diagnostics;

		TestCleanUp(MultiFileCleanUpDiagnostics diagnostics) {
			this.diagnostics= diagnostics;
		}

		@Override
		protected MultiFileCleanUpPlanResult<TestPlan> createPlan(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) {
			return MultiFileCleanUpPlanResult.success(new TestPlan(), new RefactoringStatus(),
					MultiFilePlanningMetrics.empty(), diagnostics);
		}

		@Override
		protected ICleanUpFix createFixForPlan(TestPlan plan, CleanUpContext context) {
			return null;
		}
	}

	@Test
	void exposesOneAtomicPreviewForEachTransformedCandidate() throws CoreException {
		IJavaProject project= javaProject("preview"); //$NON-NLS-1$
		ICompilationUnit owner= compilationUnit(project, "owner", "Owner.java"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit caller= compilationUnit(project, "caller", "Caller.java"); //$NON-NLS-1$ //$NON-NLS-2$
		MultiFileCleanUpDiagnostics diagnostics= diagnostics(List.of(
				MultiFileCandidateDiagnostic.transformed("candidate", "owner", //$NON-NLS-1$ //$NON-NLS-2$
						"Migrate the integer state and every caller to an enum", List.of("owner", "caller")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				MultiFileCandidateDiagnostic.rejected("rejected", "owner", "EXTERNAL_CALLER", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						"An external caller prevents a safe migration", List.of("owner")))); //$NON-NLS-1$ //$NON-NLS-2$
		TestCleanUp cleanUp= new TestCleanUp(diagnostics);

		cleanUp.checkPreConditions(project, new ICompilationUnit[] { owner, caller }, new NullProgressMonitor());
		Collection<Map<String, Object>> previews= cleanUp.getCoordinatedCleanUpPreview(project);

		assertEquals(1, previews.size());
		Map<String, Object> preview= previews.iterator().next();
		assertEquals("test-cleanup:candidate", preview.get("id")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Migrate the integer state and every caller to an enum", preview.get("name")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(CleanUpImpact.PROJECT_CLOSED.compatibilityStatement(), preview.get("description")); //$NON-NLS-1$
		List<?> units= (List<?>) preview.get("compilationUnits"); //$NON-NLS-1$
		assertEquals(2, units.size());
		assertSame(owner, units.get(0));
		assertSame(caller, units.get(1));
		List<?> details= (List<?>) preview.get("details"); //$NON-NLS-1$
		assertTrue(details.contains(
				"Selection is atomic: all required source changes are applied together or not at all.")); //$NON-NLS-1$
		assertTrue(details.contains("Affected source files: 2")); //$NON-NLS-1$

		cleanUp.checkPostConditions(new NullProgressMonitor());
		assertTrue(cleanUp.getCoordinatedCleanUpPreview(project).isEmpty());
	}

	@Test
	void failsClosedWhenCandidateRefersOutsideTheProvenExecutionScope() throws CoreException {
		IJavaProject project= javaProject("preview"); //$NON-NLS-1$
		ICompilationUnit owner= compilationUnit(project, "owner", "Owner.java"); //$NON-NLS-1$ //$NON-NLS-2$
		MultiFileCleanUpDiagnostics diagnostics= diagnostics(List.of(
				MultiFileCandidateDiagnostic.transformed("candidate", "owner", //$NON-NLS-1$ //$NON-NLS-2$
						"Migrate all callers", List.of("missing-caller")))); //$NON-NLS-1$ //$NON-NLS-2$
		TestCleanUp cleanUp= new TestCleanUp(diagnostics);
		cleanUp.checkPreConditions(project, new ICompilationUnit[] { owner }, new NullProgressMonitor());

		CoreException exception= assertThrows(CoreException.class,
				() -> cleanUp.getCoordinatedCleanUpPreview(project));

		assertTrue(exception.getMessage().contains("outside the proven execution scope")); //$NON-NLS-1$
	}

	@Test
	void omitsRejectedCandidatesAndRunsWithoutPatchedJdt() throws CoreException {
		IJavaProject project= javaProject("preview"); //$NON-NLS-1$
		ICompilationUnit owner= compilationUnit(project, "owner", "Owner.java"); //$NON-NLS-1$ //$NON-NLS-2$
		MultiFileCleanUpDiagnostics diagnostics= diagnostics(List.of(
				MultiFileCandidateDiagnostic.rejected("candidate", "owner", "UNSUPPORTED", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						"The candidate is not safe", List.of("owner")))); //$NON-NLS-1$ //$NON-NLS-2$
		TestCleanUp cleanUp= new TestCleanUp(diagnostics);
		cleanUp.checkPreConditions(project, new ICompilationUnit[] { owner }, new NullProgressMonitor());

		assertTrue(cleanUp.getCoordinatedCleanUpPreview(project).isEmpty());
	}

	private static MultiFileCleanUpDiagnostics diagnostics(List<MultiFileCandidateDiagnostic> candidates) {
		return new MultiFileCleanUpDiagnostics("test-cleanup", //$NON-NLS-1$
				new MultiFileScopeDiagnostic(List.of("owner"), List.of("caller"), //$NON-NLS-1$ //$NON-NLS-2$
						"CLOSED_SOURCE_SCOPE", //$NON-NLS-1$
						"The selected and discovered units form a closed migration scope.", true), //$NON-NLS-1$
				candidates);
	}

	private static IJavaProject javaProject(String name) {
		return (IJavaProject) Proxy.newProxyInstance(CoordinatedCleanUpPreviewContractTest.class.getClassLoader(),
				new Class<?>[] { IJavaProject.class }, (proxy, method, arguments) -> {
					if ("getElementName".equals(method.getName()) || "toString".equals(method.getName())) { //$NON-NLS-1$ //$NON-NLS-2$
						return name;
					}
					return defaultValue(proxy, method, arguments);
				});
	}

	private static ICompilationUnit compilationUnit(IJavaProject project, String handle, String name) {
		return (ICompilationUnit) Proxy.newProxyInstance(CoordinatedCleanUpPreviewContractTest.class.getClassLoader(),
				new Class<?>[] { ICompilationUnit.class }, (proxy, method, arguments) -> {
					switch (method.getName()) {
						case "getJavaProject": //$NON-NLS-1$
							return project;
						case "getPrimary": //$NON-NLS-1$
							return proxy;
						case "getHandleIdentifier": //$NON-NLS-1$
							return handle;
						case "getElementName": //$NON-NLS-1$
						case "toString": //$NON-NLS-1$
							return name;
						case "exists": //$NON-NLS-1$
							return Boolean.TRUE;
						default:
							return defaultValue(proxy, method, arguments);
					}
				});
	}

	private static Object defaultValue(Object proxy, Method method, Object[] arguments) {
		if ("hashCode".equals(method.getName())) { //$NON-NLS-1$
			return System.identityHashCode(proxy);
		}
		if ("equals".equals(method.getName())) { //$NON-NLS-1$
			return proxy == arguments[0];
		}
		Class<?> returnType= method.getReturnType();
		if (returnType == boolean.class) {
			return Boolean.FALSE;
		}
		if (returnType == int.class) {
			return Integer.valueOf(0);
		}
		return null;
	}
}
