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
package org.sandbox.jdt.ui.tests.quickfix.rules;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.junit.JUnitCore;
import org.osgi.framework.Bundle;

/**
 * Adds bundles from the running Eclipse target platform to a temporary Java
 * project's classpath.
 * <p>
 * Workbench, SWTBot and cleanup tests should use the real target-platform API
 * whenever binding identity or inherited signatures are part of the behavior
 * under test. Source stubs with the same qualified names are not equivalent:
 * they can hide missing dependencies and do not prove the binding contract used
 * by real JDT Core or JDT UI projects.
 * </p>
 * <p>
 * Eclipse packages the JUnit libraries behind JDT classpath containers rather
 * than one OSGi bundle per library package. The well-known JUnit package
 * identifiers below therefore resolve through the official JDT containers so
 * fixtures see the same nested libraries as ordinary Eclipse Java projects.
 * </p>
 */
public final class EclipseBundleClasspath {

	private static final Map<String, IPath> JUNIT_LIBRARY_CONTAINERS= Map.of(
			"org.junit", JUnitCore.JUNIT4_CONTAINER_PATH, //$NON-NLS-1$
			"org.junit.jupiter.api", JUnitCore.JUNIT5_CONTAINER_PATH, //$NON-NLS-1$
			"org.apiguardian.api", JUnitCore.JUNIT5_CONTAINER_PATH, //$NON-NLS-1$
			"org.opentest4j", JUnitCore.JUNIT5_CONTAINER_PATH, //$NON-NLS-1$
			"org.junit.platform.commons", JUnitCore.JUNIT5_CONTAINER_PATH); //$NON-NLS-1$

	private EclipseBundleClasspath() {
	}

	/**
	 * Adds all named bundles and their installed fragments. Well-known JUnit
	 * library package identifiers use the corresponding Eclipse JUnit container.
	 *
	 * @param javaProject the temporary Java project
	 * @param bundleSymbolicNames exact bundle symbolic names or supported JUnit
	 *                            library package identifiers
	 * @throws CoreException if a dependency cannot be resolved or added
	 */
	public static void addBundles(IJavaProject javaProject, String... bundleSymbolicNames) throws CoreException {
		for (String bundleSymbolicName : bundleSymbolicNames) {
			IPath junitContainer= JUNIT_LIBRARY_CONTAINERS.get(bundleSymbolicName);
			if (junitContainer != null) {
				addToClasspath(javaProject, JavaCore.newContainerEntry(junitContainer));
			} else {
				addBundle(javaProject, bundleSymbolicName);
			}
		}
	}

	/**
	 * Adds one bundle and its installed fragments.
	 *
	 * @param javaProject the temporary Java project
	 * @param bundleSymbolicName the exact bundle symbolic name
	 * @throws CoreException if the bundle cannot be resolved or added
	 */
	public static void addBundle(IJavaProject javaProject, String bundleSymbolicName) throws CoreException {
		Bundle bundle= Platform.getBundle(bundleSymbolicName);
		if (bundle == null) {
			throw new CoreException(Status.error("Bundle not found: " + bundleSymbolicName)); //$NON-NLS-1$
		}

		addBundleFile(javaProject, bundle, bundleSymbolicName);
		Bundle[] fragments= Platform.getFragments(bundle);
		if (fragments != null) {
			for (Bundle fragment : fragments) {
				addBundleFile(javaProject, fragment, fragment.getSymbolicName());
			}
		}
	}

	private static void addBundleFile(IJavaProject javaProject, Bundle bundle, String displayName)
			throws CoreException {
		File bundleFile;
		try {
			bundleFile= FileLocator.getBundleFile(bundle);
		} catch (IOException exception) {
			throw new CoreException(Status.error("Cannot locate bundle file: " + displayName, exception)); //$NON-NLS-1$
		}
		if (bundleFile == null || !bundleFile.exists()) {
			throw new CoreException(Status.error("Bundle file does not exist: " + displayName)); //$NON-NLS-1$
		}

		File classpathFile= bundleFile;
		if (bundleFile.isDirectory()) {
			File binDirectory= new File(bundleFile, "bin"); //$NON-NLS-1$
			if (binDirectory.isDirectory()) {
				classpathFile= binDirectory;
			}
		}
		IPath path= Path.fromOSString(classpathFile.getAbsolutePath());
		addToClasspath(javaProject, JavaCore.newLibraryEntry(path, null, null));
	}

	private static void addToClasspath(IJavaProject javaProject, IClasspathEntry entry)
			throws JavaModelException {
		IClasspathEntry[] current= javaProject.getRawClasspath();
		for (IClasspathEntry existing : current) {
			if (existing.equals(entry)) {
				return;
			}
		}

		IClasspathEntry[] updated= new IClasspathEntry[current.length + 1];
		System.arraycopy(current, 0, updated, 0, current.length);
		updated[current.length]= entry;
		javaProject.setRawClasspath(updated, null);
	}
}
