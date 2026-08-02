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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;

/**
 * Resolves the source compilation units related to binding-derived Java elements.
 *
 * <p>The search is workspace-wide so references outside the selected project or
 * in binary inputs are visible as safety failures. Only accurate matches in the
 * explicitly allowed source units of the owning project are admitted to the
 * returned closure. Any unresolved, external, generated, derived, output, or
 * otherwise excluded match makes the result incomplete.</p>
 */
public final class RelatedCompilationUnitSearch {

	/** Stable result of one related-source search. */
	public record Result(List<ICompilationUnit> compilationUnits, boolean complete,
			List<String> rejectionReasons) {
		public Result {
			compilationUnits= List.copyOf(compilationUnits);
			rejectionReasons= List.copyOf(rejectionReasons);
		}
	}

	/** Package-visible deterministic match classifier used by common-layer tests. */
	static final class MatchAccumulator {
		private final Set<String> projectHandles;
		private final Map<String, ICompilationUnit> allowedByHandle;
		private final Map<String, ICompilationUnit> resultByHandle= new LinkedHashMap<>();
		private final Set<String> reasons= new LinkedHashSet<>();

		MatchAccumulator(IJavaProject project, Collection<ICompilationUnit> initialUnits,
				Collection<ICompilationUnit> allowedUnits) {
			this(List.of(project), initialUnits, allowedUnits);
		}

		MatchAccumulator(Collection<IJavaProject> projects, Collection<ICompilationUnit> initialUnits,
				Collection<ICompilationUnit> allowedUnits) {
			this.projectHandles= projectHandles(projects);
			allowedByHandle= byHandle(allowedUnits);
			addUnits(projectHandles, initialUnits, allowedByHandle, resultByHandle, reasons,
					"The initial cleanup scope contains a source unit outside the permitted root policy."); //$NON-NLS-1$
		}

		void addTarget(IJavaElement target) {
			ICompilationUnit declarationUnit= compilationUnit(target);
			if (declarationUnit == null) {
				reasons.add("A search target is declared outside an editable source compilation unit."); //$NON-NLS-1$
				return;
			}
			addUnit(projectHandles, declarationUnit, allowedByHandle, resultByHandle, reasons,
					"A search-target declaration is outside the permitted source-root policy."); //$NON-NLS-1$
		}

		void accept(Object element, int accuracy) {
			if (accuracy != SearchMatch.A_ACCURATE) {
				reasons.add("JDT reported an inaccurate reference match."); //$NON-NLS-1$
				return;
			}
			if (!(element instanceof IJavaElement javaElement)) {
				reasons.add("A reference match has no Java-model element."); //$NON-NLS-1$
				return;
			}
			ICompilationUnit unit= compilationUnit(javaElement);
			if (unit == null) {
				reasons.add("A reference exists in a binary or otherwise non-source element."); //$NON-NLS-1$
				return;
			}
			addUnit(projectHandles, unit, allowedByHandle, resultByHandle, reasons,
					"A reference exists outside the permitted project source-root policy."); //$NON-NLS-1$
		}

		void reject(String reason) {
			reasons.add(reason);
		}

		Result finish() {
			return result(resultByHandle, reasons);
		}
	}

	private RelatedCompilationUnitSearch() {
	}

	/**
	 * Finds source units containing references to the supplied Java elements.
	 *
	 * @param project project that owns the coordinated cleanup
	 * @param targets binding-derived fields, methods, or types to search for
	 * @param initialUnits units explicitly selected or already admitted
	 * @param allowedUnits complete source-root-policy allow-list
	 * @param monitor progress monitor, may be {@code null}
	 * @return deterministic related-unit closure and completeness status
	 * @throws CoreException if the JDT search engine fails
	 */
	public static Result findReferences(IJavaProject project,
			Collection<? extends IJavaElement> targets,
			Collection<ICompilationUnit> initialUnits,
			Collection<ICompilationUnit> allowedUnits,
			IProgressMonitor monitor) throws CoreException {
		return findReferences(project == null ? List.<IJavaProject>of() : List.of(project), targets, initialUnits,
				allowedUnits, monitor);
	}

	/**
	 * Finds source units containing references to the supplied Java elements across
	 * several coordinated projects, for example a fixture bundle and the test bundles
	 * that consume it. References found outside those projects make the result
	 * incomplete so that a partial cross-bundle migration is never planned.
	 *
	 * @param projects coordinated Java projects that may be modified
	 * @param targets binding-derived fields, methods, or types to search for
	 * @param initialUnits units explicitly selected or already admitted
	 * @param allowedUnits complete source-root-policy allow-list across all projects
	 * @param monitor progress monitor, may be {@code null}
	 * @return deterministic related-unit closure and completeness status
	 * @throws CoreException if the JDT search engine fails
	 */
	public static Result findReferences(Collection<IJavaProject> projects,
			Collection<? extends IJavaElement> targets,
			Collection<ICompilationUnit> initialUnits,
			Collection<ICompilationUnit> allowedUnits,
			IProgressMonitor monitor) throws CoreException {
		checkCanceled(monitor);
		if (projects == null || projects.isEmpty() || targets == null || targets.isEmpty()) {
			return new Result(normalize(initialUnits), false,
					List.of("No binding-derived search target is available.")); //$NON-NLS-1$
		}

		MatchAccumulator accumulator= new MatchAccumulator(projects, initialUnits, allowedUnits);
		SearchPattern combinedPattern= null;
		for (IJavaElement target : targets) {
			checkCanceled(monitor);
			if (target == null || !target.exists()) {
				accumulator.reject("A binding-derived search target is missing or no longer exists."); //$NON-NLS-1$
				continue;
			}
			accumulator.addTarget(target);
			SearchPattern pattern= SearchPattern.createPattern(target, IJavaSearchConstants.REFERENCES);
			if (pattern == null) {
				accumulator.reject("JDT could not create an exact reference-search pattern."); //$NON-NLS-1$
				continue;
			}
			combinedPattern= combinedPattern == null
					? pattern
					: SearchPattern.createOrPattern(combinedPattern, pattern);
		}
		if (combinedPattern == null) {
			return accumulator.finish();
		}

		SearchRequestor requestor= new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) {
				accumulator.accept(match.getElement(), match.getAccuracy());
			}
		};
		new SearchEngine().search(combinedPattern,
				new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
				SearchEngine.createWorkspaceScope(), requestor, monitor);
		checkCanceled(monitor);
		return accumulator.finish();
	}

	private static Result result(Map<String, ICompilationUnit> resultByHandle, Set<String> reasons) {
		List<ICompilationUnit> units= new ArrayList<>(resultByHandle.values());
		units.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return new Result(units, reasons.isEmpty(), List.copyOf(reasons));
	}

	private static Map<String, ICompilationUnit> byHandle(Collection<ICompilationUnit> units) {
		Map<String, ICompilationUnit> result= new LinkedHashMap<>();
		if (units == null) {
			return result;
		}
		for (ICompilationUnit unit : units) {
			if (unit != null && unit.exists()) {
				ICompilationUnit primary= unit.getPrimary();
				result.put(primary.getHandleIdentifier(), primary);
			}
		}
		return result;
	}

	private static List<ICompilationUnit> normalize(Collection<ICompilationUnit> units) {
		List<ICompilationUnit> result= new ArrayList<>(byHandle(units).values());
		result.sort(Comparator.comparing(IJavaElement::getHandleIdentifier));
		return List.copyOf(result);
	}

	private static void addUnits(Set<String> projectHandles, Collection<ICompilationUnit> units,
			Map<String, ICompilationUnit> allowedByHandle,
			Map<String, ICompilationUnit> resultByHandle, Set<String> reasons, String rejectionReason) {
		if (units == null) {
			return;
		}
		for (ICompilationUnit unit : units) {
			addUnit(projectHandles, unit, allowedByHandle, resultByHandle, reasons, rejectionReason);
		}
	}

	private static void addUnit(Set<String> projectHandles, ICompilationUnit unit,
			Map<String, ICompilationUnit> allowedByHandle,
			Map<String, ICompilationUnit> resultByHandle, Set<String> reasons, String rejectionReason) {
		if (unit == null || !unit.exists()) {
			reasons.add(rejectionReason);
			return;
		}
		ICompilationUnit primary= unit.getPrimary();
		String handle= primary.getHandleIdentifier();
		ICompilationUnit allowed= allowedByHandle.get(handle);
		IJavaProject owner= primary.getJavaProject();
		if (owner == null || !projectHandles.contains(owner.getHandleIdentifier()) || allowed == null) {
			reasons.add(rejectionReason);
			return;
		}
		resultByHandle.put(handle, allowed);
	}

	private static Set<String> projectHandles(Collection<IJavaProject> projects) {
		Set<String> handles= new LinkedHashSet<>();
		if (projects == null) {
			return handles;
		}
		for (IJavaProject project : projects) {
			if (project != null && project.exists()) {
				handles.add(project.getHandleIdentifier());
			}
		}
		return handles;
	}

	private static ICompilationUnit compilationUnit(IJavaElement element) {
		if (element == null) {
			return null;
		}
		IJavaElement ancestor= element.getAncestor(IJavaElement.COMPILATION_UNIT);
		return ancestor instanceof ICompilationUnit unit ? unit.getPrimary() : null;
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
