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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourceAttributes;

import org.eclipse.core.runtime.IPath;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/** Utility for obtaining stable, policy-aware source compilation-unit scopes. */
public final class JavaProjectCompilationUnits {

	private static final Set<String> GENERATED_SEGMENTS= Set.of(
			"generated", "generated-sources", "generated-test-sources", "generated-src", "src-gen"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	private static final Set<String> TEST_SEGMENTS= Set.of(
			"test", "tests", "testfixtures", "integrationtest", "integration-test"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	private JavaProjectCompilationUnits() {
	}

	/**
	 * Collects all editable production and test compilation units. Generated,
	 * derived, output, missing, and non-source roots are excluded by default.
	 *
	 * @param project Java project
	 * @return stable list of editable source compilation units
	 * @throws JavaModelException if the Java model cannot be read
	 */
	public static List<ICompilationUnit> collect(IJavaProject project) throws JavaModelException {
		return collect(project, List.of(), SourceRootPolicy.COMPLETE_PROJECT);
	}

	/**
	 * Collects compilation units according to an explicit coordinated-cleanup
	 * source-root policy. The result is sorted by Java element handle for
	 * deterministic planning.
	 *
	 * @param project Java project
	 * @param currentScope explicitly selected cleanup scope
	 * @param policy source-root expansion policy
	 * @return stable list of compilation units allowed by the policy
	 * @throws JavaModelException if the Java model cannot be read
	 */
	public static List<ICompilationUnit> collect(IJavaProject project,
			Collection<ICompilationUnit> currentScope, SourceRootPolicy policy) throws JavaModelException {
		if (project == null || policy == null) {
			return List.of();
		}
		Set<String> selectedRootHandles= selectedRootHandles(currentScope);
		boolean productionSelected= hasSelectedRootOfKind(project, selectedRootHandles, SourceRootKind.PRODUCTION);
		List<ICompilationUnit> result= new ArrayList<>();
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			SourceRootKind kind= classify(root);
			if (!kind.isEditableByDefault()
					|| !includes(policy, root.getHandleIdentifier(), kind, selectedRootHandles, productionSelected)) {
				continue;
			}
			collectRootUnits(root, result);
		}
		result.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return List.copyOf(result);
	}

	/**
	 * Collects compilation units across several Java projects, for example a fixture
	 * bundle together with the test bundles that consume it. Projects that cannot be
	 * edited are reported through {@link #readOnlyProjects(Collection)} and must be
	 * checked before a coordinated cleanup is planned.
	 *
	 * @param projects Java projects contributing to the coordinated scope
	 * @param currentScope explicitly selected cleanup scope
	 * @param policy source-root expansion policy
	 * @return stable list of compilation units allowed by the policy across all projects
	 * @throws JavaModelException if the Java model cannot be read
	 */
	public static List<ICompilationUnit> collect(Collection<IJavaProject> projects,
			Collection<ICompilationUnit> currentScope, SourceRootPolicy policy) throws JavaModelException {
		if (projects == null || policy == null) {
			return List.of();
		}
		Set<String> seen= new HashSet<>();
		List<ICompilationUnit> result= new ArrayList<>();
		for (IJavaProject project : projects) {
			for (ICompilationUnit unit : collect(project, currentScope, policy)) {
				if (seen.add(unit.getHandleIdentifier())) {
					result.add(unit);
				}
			}
		}
		result.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return List.copyOf(result);
	}

	/**
	 * Returns the Java projects that own the given compilation units, in stable order.
	 *
	 * @param units compilation units, may be {@code null}
	 * @return owning Java projects
	 */
	public static List<IJavaProject> owningProjects(Collection<ICompilationUnit> units) {
		if (units == null) {
			return List.of();
		}
		Map<String, IJavaProject> projects= new TreeMap<>();
		for (ICompilationUnit unit : units) {
			IJavaProject project= unit == null ? null : unit.getJavaProject();
			if (project != null && project.exists()) {
				projects.putIfAbsent(project.getHandleIdentifier(), project);
			}
		}
		return List.copyOf(projects.values());
	}

	/**
	 * Returns the transitive closure of Java projects that reference the given
	 * projects, including the given projects themselves. A coordinated cleanup of a
	 * shared test fixture must include every project that consumes it, otherwise the
	 * fixture would be migrated while its users keep the old API.
	 *
	 * @param projects seed Java projects, may be {@code null}
	 * @return seed projects and all transitively referencing Java projects
	 */
	public static List<IJavaProject> withReferencingProjects(Collection<IJavaProject> projects) {
		Map<String, IJavaProject> result= new TreeMap<>();
		if (projects == null) {
			return List.of();
		}
		Deque<IProject> pending= new ArrayDeque<>();
		for (IJavaProject project : projects) {
			if (project != null && project.exists()
					&& result.putIfAbsent(project.getHandleIdentifier(), project) == null) {
				pending.add(project.getProject());
			}
		}
		while (!pending.isEmpty()) {
			IProject current= pending.removeFirst();
			if (current == null || !current.isAccessible()) {
				continue;
			}
			for (IProject referencing : current.getReferencingProjects()) {
				if (referencing == null || !referencing.isAccessible()) {
					continue;
				}
				IJavaProject javaProject= JavaCore.create(referencing);
				if (javaProject == null || !javaProject.exists()
						|| result.putIfAbsent(javaProject.getHandleIdentifier(), javaProject) != null) {
					continue;
				}
				pending.add(referencing);
			}
		}
		return List.copyOf(result.values());
	}

	/**
	 * Returns whether a coordinated cleanup may write to the project. Target-platform
	 * bundles and other read-only or closed projects must abort the migration instead
	 * of being migrated partially.
	 *
	 * @param project Java project, may be {@code null}
	 * @return {@code true} if the project exists, is open and is writable
	 */
	public static boolean isEditable(IJavaProject project) {
		IProject resource= project == null ? null : project.getProject();
		if (resource == null || !resource.exists() || !resource.isAccessible()) {
			return false;
		}
		ResourceAttributes attributes= resource.getResourceAttributes();
		return attributes == null || !attributes.isReadOnly();
	}

	/**
	 * Returns the projects that must not be modified by a coordinated cleanup.
	 *
	 * @param projects Java projects to inspect, may be {@code null}
	 * @return read-only or inaccessible projects, in input order
	 */
	public static List<IJavaProject> readOnlyProjects(Collection<IJavaProject> projects) {
		if (projects == null) {
			return List.of();
		}
		List<IJavaProject> result= new ArrayList<>();
		for (IJavaProject project : projects) {
			if (!isEditable(project)) {
				result.add(project);
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Classifies a package-fragment root for cleanup scope decisions.
	 *
	 * @param root package-fragment root
	 * @return conservative source-root classification
	 * @throws JavaModelException if classpath metadata cannot be read
	 */
	public static SourceRootKind classify(IPackageFragmentRoot root) throws JavaModelException {
		if (root == null || !root.exists() || root.getKind() != IPackageFragmentRoot.K_SOURCE) {
			return SourceRootKind.EXCLUDED;
		}
		IResource resource= root.getResource();
		if (resource != null && resource.isDerived()) {
			return SourceRootKind.DERIVED;
		}
		IPath rootPath= root.getPath();
		if (isOutputRoot(root, rootPath)) {
			return SourceRootKind.OUTPUT;
		}
		if (containsSegment(rootPath, GENERATED_SEGMENTS)) {
			return SourceRootKind.GENERATED;
		}
		IClasspathEntry entry= root.getResolvedClasspathEntry();
		if (entry == null) {
			entry= root.getRawClasspathEntry();
		}
		if (entry == null) {
			return SourceRootKind.EXCLUDED;
		}
		if (entry.isTest()) {
			return SourceRootKind.TEST;
		}
		return containsSegment(rootPath, TEST_SEGMENTS) ? SourceRootKind.TEST : SourceRootKind.PRODUCTION;
	}

	private static boolean includes(SourceRootPolicy policy, String rootHandle, SourceRootKind kind,
			Set<String> selectedRootHandles, boolean productionSelected) {
		return switch (policy) {
			case EXPLICIT_SELECTED_ROOTS -> selectedRootHandles.contains(rootHandle);
			case TEST_ROOTS_AND_SELECTED_SUPPORT -> kind == SourceRootKind.TEST
					|| selectedRootHandles.contains(rootHandle);
			case PRODUCTION_WITH_DEPENDENT_TESTS -> productionSelected
					? kind == SourceRootKind.PRODUCTION || kind == SourceRootKind.TEST
					: selectedRootHandles.contains(rootHandle);
			case COMPLETE_PROJECT -> true;
		};
	}

	private static Set<String> selectedRootHandles(Collection<ICompilationUnit> currentScope) {
		if (currentScope == null || currentScope.isEmpty()) {
			return Set.of();
		}
		Set<String> handles= new HashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit == null) {
				continue;
			}
			IJavaElement ancestor= unit.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			if (ancestor instanceof IPackageFragmentRoot root) {
				handles.add(root.getHandleIdentifier());
			}
		}
		return Set.copyOf(handles);
	}

	private static boolean hasSelectedRootOfKind(IJavaProject project, Set<String> selectedRootHandles,
			SourceRootKind expected) throws JavaModelException {
		if (selectedRootHandles.isEmpty()) {
			return false;
		}
		for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
			if (selectedRootHandles.contains(root.getHandleIdentifier()) && classify(root) == expected) {
				return true;
			}
		}
		return false;
	}

	private static void collectRootUnits(IPackageFragmentRoot root, List<ICompilationUnit> result)
			throws JavaModelException {
		for (IJavaElement child : root.getChildren()) {
			if (child instanceof IPackageFragment fragment && fragment.exists()) {
				for (ICompilationUnit unit : fragment.getCompilationUnits()) {
					if (unit.exists()) {
						result.add(unit);
					}
				}
			}
		}
	}

	private static boolean isOutputRoot(IPackageFragmentRoot root, IPath rootPath) throws JavaModelException {
		if (rootPath == null) {
			return false;
		}
		IJavaProject project= root.getJavaProject();
		IPath projectOutput= project == null ? null : project.getOutputLocation();
		if (projectOutput != null && projectOutput.isPrefixOf(rootPath)) {
			return true;
		}
		IClasspathEntry rawEntry= root.getRawClasspathEntry();
		IPath entryOutput= rawEntry == null ? null : rawEntry.getOutputLocation();
		return entryOutput != null && entryOutput.isPrefixOf(rootPath);
	}

	private static boolean containsSegment(IPath path, Set<String> candidates) {
		if (path == null) {
			return false;
		}
		for (String segment : path.segments()) {
			if (candidates.contains(segment.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}
}
