/*
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

/**
 * Fixture-local classpath setup for JUnit migration tests that need both the
 * legacy source container and the destination libraries produced by the cleanup.
 */
public final class JUnitMigrationFixtureClasspath {

	private static final String JUPITER_TEST= "org.junit.jupiter.api.Test"; //$NON-NLS-1$
	private static final String SUITE_API= "org.junit.platform.suite.api.Suite"; //$NON-NLS-1$
	private JUnitMigrationFixtureClasspath() {
	}

	public static IPackageFragmentRoot createJUnit4And5Root(AbstractEclipseJava context, String... requiredTypes)
			throws CoreException {
		return createRoot(context, JUnitCore.JUNIT4_CONTAINER_PATH, requiredTypes);
	}

	public static IPackageFragmentRoot createJUnit3And5Root(AbstractEclipseJava context, String... requiredTypes)
			throws CoreException {
		return createRoot(context, JUnitCore.JUNIT3_CONTAINER_PATH, requiredTypes);
	}

	private static IPackageFragmentRoot createRoot(AbstractEclipseJava context, org.eclipse.core.runtime.IPath sourceContainer,
			String... requiredTypes) throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(sourceContainer);
		IJavaProject javaProject= context.getJavaProject();
		AbstractEclipseJava.addToClasspath(javaProject, JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		Set<String> typesToVerify= new LinkedHashSet<>();
		typesToVerify.add(JUPITER_TEST);
		for (String requiredType : requiredTypes) {
			typesToVerify.add(requiredType);
			if (SUITE_API.equals(requiredType)) {
				EclipseBundleClasspath.addBundles(javaProject, "junit-platform-suite-api"); //$NON-NLS-1$
			}
		}
		for (String type : typesToVerify) {
			assertNotNull(javaProject.findType(type), type);
		}
		return root;
	}
}
