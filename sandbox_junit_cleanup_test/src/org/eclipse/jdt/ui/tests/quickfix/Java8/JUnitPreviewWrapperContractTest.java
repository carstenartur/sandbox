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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUp;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Guards the optional patched-JDT preview contract on the registered JUnit wrapper. */
public class JUnitPreviewWrapperContractTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setUp() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void registeredWrapperExposesTheReflectivePreviewContract() throws Exception {
		JUnitCleanUp cleanup= new JUnitCleanUp();
		Method method= cleanup.getClass().getMethod(
				"getCoordinatedCleanUpPreview", IJavaProject.class); //$NON-NLS-1$

		assertEquals(Collection.class, method.getReturnType());
		assertTrue(cleanup.getCoordinatedCleanUpPreview(
				context.getSourceFolder().getJavaProject()).isEmpty(),
				"A wrapper without a completed plan must expose an empty preview rather than fail"); //$NON-NLS-1$
	}

	@Test
	public void externalResourcePlanIsExposedAsOneAtomicCandidate() throws Exception {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() throws Throwable {
					}

					@Override
					protected void after() {
					}
				}
				""", false, null);
		ICompilationUnit consumer= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class MyTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
		JUnitCleanUp cleanup= new JUnitCleanUp(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE));
		NullProgressMonitor monitor= new NullProgressMonitor();

		try {
			var status= cleanup.checkPreConditions(resource.getJavaProject(),
					new ICompilationUnit[] { resource, consumer }, monitor);
			assertTrue(!status.hasFatalError(), () -> "JUnit planning failed: " + status); //$NON-NLS-1$

			Collection<Map<String, Object>> previews=
					cleanup.getCoordinatedCleanUpPreview(resource.getJavaProject());
			assertEquals(1, previews.size());
			Map<String, Object> preview= previews.iterator().next();
			assertTrue(preview.get("id").toString().contains("external-resource:test.SharedResource")); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(preview.get("name").toString().contains("Migrates 1 instance rule field")); //$NON-NLS-1$ //$NON-NLS-2$

			List<?> units= (List<?>) preview.get("compilationUnits"); //$NON-NLS-1$
			Set<String> unitNames= units.stream().map(ICompilationUnit.class::cast)
					.map(ICompilationUnit::getElementName).collect(Collectors.toSet());
			assertEquals(Set.of("SharedResource.java", "MyTest.java"), unitNames); //$NON-NLS-1$ //$NON-NLS-2$

			List<?> details= (List<?>) preview.get("details"); //$NON-NLS-1$
			assertTrue(details.contains(
					"Selection is atomic: all required source changes are applied together or not at all.")); //$NON-NLS-1$
		} finally {
			cleanup.checkPostConditions(monitor);
		}
	}
}
