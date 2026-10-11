/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;

import org.sandbox.jdt.cleanup.multifile.RelatedCompilationUnitSearch;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitScopeCandidateDetector.SearchSeeds;

/** Optional stderr observations of decisions already made by the normal scope path. */
final class JUnitScopeDiagnostics {
	private static final String PROPERTY= "sandbox.junit.scope.diagnostics"; //$NON-NLS-1$
	private static final Expansion DISABLED= new Expansion(null, null, null);

	private JUnitScopeDiagnostics() {
	}

	private static boolean enabled() {
		try {
			return Boolean.getBoolean(PROPERTY);
		} catch (RuntimeException error) {
			diagnosticError("property", error); //$NON-NLS-1$
			return false;
		}
	}

	static Expansion expansion(IJavaProject project, Set<String> selected, Set<String> pending) {
		return enabled() ? new Expansion(project, selected, pending) : DISABLED;
	}

	static final class Expansion {
		private final IJavaProject project;
		private final Set<String> selected;
		private final Set<String> pending;
		private List<SearchSeeds> seeds;
		private SearchSeeds merged;

		private Expansion(IJavaProject project, Set<String> selected, Set<String> pending) {
			this.project= project;
			this.selected= selected;
			this.pending= pending;
		}

		void seeds(SearchSeeds standard, SearchSeeds junit3, SearchSeeds lifecycle, SearchSeeds helper,
				SearchSeeds combined) {
			if (project != null) {
				seeds= List.of(standard, junit3, lifecycle, helper);
				merged= combined;
			}
		}

		void finish(String branch, Collection<IJavaProject> projects, Collection<IJavaProject> readOnly,
				Collection<ICompilationUnit> allowed, Collection<ICompilationUnit> required,
				RelatedCompilationUnitSearch.Result related) {
			if (project == null) {
				return;
			}
			try {
				StringBuilder text= new StringBuilder("[sandbox.junit.scope] expansion projectLabel=") //$NON-NLS-1$
						.append(label(project)).append(" branch=").append(branch) //$NON-NLS-1$
						.append(" selected=").append(sorted(selected)).append(" pending=").append(sorted(pending)); //$NON-NLS-1$ //$NON-NLS-2$
				if (seeds != null) {
					List<String> names= List.of("standard", "junit3", "lifecycle", "helper"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					for (int i= 0; i < seeds.size(); i++) {
						SearchSeeds seed= seeds.get(i);
						text.append(' ').append(names.get(i)).append("={candidate=").append(seed.candidateFound()) //$NON-NLS-1$
								.append(",complete=").append(seed.complete()).append(",elements=").append(labels(seed.elements())) //$NON-NLS-1$ //$NON-NLS-2$
								.append(",direct=").append(labels(seed.directCompilationUnits())).append('}'); //$NON-NLS-1$
					}
					text.append(" mergedCandidate=").append(merged.candidateFound()).append(" mergedComplete=").append(merged.complete()); //$NON-NLS-1$ //$NON-NLS-2$
				}
				text.append(" projectLabels=").append(labels(projects)).append(" readOnlyLabels=").append(labels(readOnly)) //$NON-NLS-1$ //$NON-NLS-2$
						.append(" allowedLabels=").append(labels(allowed)).append(" requiredLabels=").append(labels(required)); //$NON-NLS-1$ //$NON-NLS-2$
				if (related != null) {
					text.append(" relatedComplete=").append(related.complete()).append(" relatedUnitLabels=").append(labels(related.compilationUnits())) //$NON-NLS-1$ //$NON-NLS-2$
							.append(" relatedReasons=").append(related.rejectionReasons()); //$NON-NLS-1$
				}
				System.err.println(text);
			} catch (RuntimeException error) {
				diagnosticError("expansion", error); //$NON-NLS-1$
			}
		}
	}

	static void consumed(IJavaProject project, ICompilationUnit[] units, Set<String> selected,
			Set<String> expected, Set<String> pending, Boolean decision, String branch) {
		if (!enabled()) {
			return;
		}
		try {
			Set<String> missing= expected == null || selected == null ? null : new TreeSet<>(expected);
			if (missing != null) {
				missing.removeAll(selected);
			}
			System.err.println("[sandbox.junit.scope] consume projectLabel=" + label(project) //$NON-NLS-1$
					+ " branch=" + branch + " decision=" + decision + " selected=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ (selected == null ? "not-computed" : sorted(selected)) //$NON-NLS-1$
					+ " expected=" + sorted(expected) + " pending=" + sorted(pending) + " missingExpected=" + missing //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					+ " unitLabels=" + labels(List.of(units))); //$NON-NLS-1$
		} catch (RuntimeException error) {
			diagnosticError("consume", error); //$NON-NLS-1$
		}
	}

	private static List<String> labels(Collection<? extends IJavaElement> elements) {
		if (elements == null) {
			return null;
		}
		List<String> result= new ArrayList<>();
		for (IJavaElement element : elements) {
			result.add(label(element));
		}
		Collections.sort(result);
		return result;
	}

	private static String label(IJavaElement element) {
		StringBuilder result= new StringBuilder();
		// Do not create handle mementos: a root memento can resolve its classpath entry.
		for (IJavaElement current= element; current != null; current= current.getParent()) {
			if (result.length() > 0) {
				result.append('/');
			}
			result.append(current.getClass().getName()).append('(').append(current.getElementName()).append(')');
		}
		return result.toString();
	}

	private static void diagnosticError(String phase, RuntimeException error) {
		System.err.println("[sandbox.junit.scope] diagnostic-error phase=" + phase //$NON-NLS-1$
				+ " type=" + error.getClass().getName()); //$NON-NLS-1$
	}

	private static Set<String> sorted(Set<String> handles) {
		return handles == null ? null : new TreeSet<>(handles);
	}
}
