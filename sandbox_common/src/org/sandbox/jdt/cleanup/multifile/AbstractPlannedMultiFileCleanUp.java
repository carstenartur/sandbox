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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;

/**
 * Base class for cleanups that first build one immutable semantic plan for all
 * selected compilation units and then emit the local part of that plan for each
 * {@link CleanUpContext}.
 *
 * <p>This uses the existing {@code ICleanUp} lifecycle. Eclipse calls
 * {@code checkPreConditions} once with all selected compilation units in a Java
 * project and subsequently calls {@code createFix} on the same cleanup instance
 * for every target. The ordinary cleanup refactoring already combines all local
 * changes into one preview, apply operation, and undo.</p>
 *
 * <p>The JDT cleanup orchestrator invokes one cleanup instance sequentially.
 * Alternative callers must not assume that project plans can be built or
 * resolved concurrently on the same instance. All lifecycle entry points that
 * can access retained plan state are therefore serialized on a private lock.
 * The lock is deliberately not the publicly reachable cleanup instance or class
 * monitor. An instance may be reused after {@link #checkPostConditions(IProgressMonitor)}
 * has completed and cleared all retained state.</p>
 *
 * <p>Overridable planning, scope-discovery, fix-resolution, and postcondition
 * hooks execute while that private lifecycle lock is held. Implementations must
 * therefore remain synchronous and must not wait for another thread to call a
 * lifecycle method on the same cleanup instance.</p>
 *
 * <p>Plans must not retain AST nodes from the planning parser. Previous cleanups
 * may change working copies before this cleanup receives its current AST. Store
 * Java model handles, binding keys, signatures, and semantic edit descriptions,
 * then resolve them again in {@link #createFixForPlan(Object, CleanUpContext)}.</p>
 *
 * @param <P> immutable plan type
 */
public abstract class AbstractPlannedMultiFileCleanUp<P> extends AbstractCleanUp
		implements IMultiFileCleanUpScopeProvider {

	private static final String UNKNOWN_CLEANUP_ID= "unknown"; //$NON-NLS-1$
	private static final String PREVIEW_ID= "id"; //$NON-NLS-1$
	private static final String PREVIEW_NAME= "name"; //$NON-NLS-1$
	private static final String PREVIEW_DESCRIPTION= "description"; //$NON-NLS-1$
	private static final String PREVIEW_COMPILATION_UNITS= "compilationUnits"; //$NON-NLS-1$
	private static final String PREVIEW_DETAILS= "details"; //$NON-NLS-1$
	private static final String ATOMIC_SELECTION_DETAIL=
			"Selection is atomic: all required source changes are applied together or not at all."; //$NON-NLS-1$

	private static final class ScopeAccumulator {
		private final Set<String> selectedHandles= new LinkedHashSet<>();
		private final Set<String> addedHandles= new LinkedHashSet<>();

		void record(Collection<ICompilationUnit> currentScope, Collection<ICompilationUnit> additions) {
			if (selectedHandles.isEmpty()) {
				addHandles(selectedHandles, currentScope);
			}
			Set<String> discovered= new LinkedHashSet<>();
			addHandles(discovered, additions);
			discovered.removeAll(selectedHandles);
			addedHandles.addAll(discovered);
		}

		MultiFileScopeDiagnostic merge(MultiFileScopeDiagnostic existing) {
			if (addedHandles.isEmpty()) {
				return new MultiFileScopeDiagnostic(List.copyOf(selectedHandles),
						existing.addedCompilationUnitHandles(), existing.reasonCode(), existing.explanation(),
						existing.complete());
			}
			Set<String> allAdded= new LinkedHashSet<>(existing.addedCompilationUnitHandles());
			allAdded.addAll(addedHandles);
			return new MultiFileScopeDiagnostic(List.copyOf(selectedHandles), List.copyOf(allAdded),
					"RELATED_SOURCE_CLOSURE", //$NON-NLS-1$
					"Related source compilation units were added to close coordinated cleanup references.", //$NON-NLS-1$
					existing.complete());
		}

		private static void addHandles(Set<String> target, Collection<ICompilationUnit> units) {
			if (units == null) {
				return;
			}
			for (ICompilationUnit unit : units) {
				if (unit == null) {
					continue;
				}
				ICompilationUnit primary= unit.getPrimary();
				String handle= (primary == null ? unit : primary).getHandleIdentifier();
				if (handle != null) {
					target.add(handle);
				}
			}
		}
	}

	private final Object lifecycleLock= new Object();

	private final Map<IJavaProject, P> plansByProject= new HashMap<>();
	private final Map<IJavaProject, MultiFilePlanningMetrics> metricsByProject= new HashMap<>();
	private final Map<IJavaProject, MultiFileCleanUpDiagnostics> diagnosticsByProject= new HashMap<>();
	private final Map<IJavaProject, ScopeAccumulator> scopesByProject= new HashMap<>();
	private final Map<IJavaProject, Map<String, ICompilationUnit>> compilationUnitsByProject= new HashMap<>();

	/** Creates a base class without options. */
	protected AbstractPlannedMultiFileCleanUp() {
	}

	/** Creates a base class with cleanup options. */
	protected AbstractPlannedMultiFileCleanUp(Map<String, String> settings) {
		super(settings);
	}

	/**
	 * Analyses all selected units and creates the immutable run plan.
	 *
	 * @param project Java project being cleaned
	 * @param compilationUnits complete selected scope for the project
	 * @param monitor progress monitor
	 * @return plan and diagnostics
	 * @throws CoreException if analysis cannot be completed
	 */
	protected abstract MultiFileCleanUpPlanResult<P> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException;

	/**
	 * Creates the local fix for the current compilation unit from the common plan.
	 *
	 * @param plan immutable project-wide plan
	 * @param context current context, potentially containing a fresh AST after
	 *                earlier cleanups
	 * @return local fix or {@code null}
	 * @throws CoreException if a planned edit can no longer be resolved safely
	 */
	protected abstract ICleanUpFix createFixForPlan(P plan, CleanUpContext context) throws CoreException;

	@Override
	public final RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits,
			IProgressMonitor monitor) throws CoreException {
		synchronized (lifecycleLock) {
			clearPlanningState(project);
			MultiFileCleanUpPlanResult<P> result;
			try {
				result= createPlan(project, compilationUnits.clone(), monitor);
			} catch (CoreException | RuntimeException e) {
				clearProjectState(project);
				throw e;
			}
			MultiFileCleanUpDiagnostics diagnostics= result.diagnostics();
			ScopeAccumulator scope= scopesByProject.remove(project);
			if (scope != null) {
				MultiFileScopeDiagnostic mergedScope= scope.merge(diagnostics.scope());
				String cleanupId= UNKNOWN_CLEANUP_ID.equals(diagnostics.cleanupId())
						? getClass().getSimpleName()
						: diagnostics.cleanupId();
				diagnostics= new MultiFileCleanUpDiagnostics(cleanupId, mergedScope, diagnostics.candidates());
			}
			metricsByProject.put(project, result.metrics());
			diagnosticsByProject.put(project, diagnostics);
			diagnostics.appendSummary(result.status());
			if (!result.status().hasFatalError() && result.plan() != null) {
				plansByProject.put(project, result.plan());
				compilationUnitsByProject.put(project, indexCompilationUnits(compilationUnits));
			}
			return result.status();
		}
	}

	@Override
	public final ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		synchronized (lifecycleLock) {
			ICompilationUnit unit= context.getCompilationUnit();
			if (unit == null) {
				return null;
			}
			IJavaProject project= unit.getJavaProject();
			P plan= plansByProject.get(project);
			if (plan == null) {
				return null;
			}
			try {
				return createFixForPlan(plan, context);
			} catch (CoreException | RuntimeException e) {
				clearProjectState(project);
				throw e;
			}
		}
	}

	@Override
	public final RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		synchronized (lifecycleLock) {
			try {
				return checkPlanPostConditions(monitor);
			} finally {
				plansByProject.clear();
				metricsByProject.clear();
				diagnosticsByProject.clear();
				scopesByProject.clear();
				compilationUnitsByProject.clear();
			}
		}
	}

	/**
	 * Hook for consumer-specific postcondition checks.
	 *
	 * @param monitor progress monitor
	 * @return postcondition status
	 * @throws CoreException if validation fails unexpectedly
	 */
	protected RefactoringStatus checkPlanPostConditions(IProgressMonitor monitor) throws CoreException {
		return new RefactoringStatus();
	}

	@Override
	public final Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		synchronized (lifecycleLock) {
			Collection<ICompilationUnit> result= discoverAdditionalCompilationUnits(project,
					Collections.unmodifiableCollection(currentScope), monitor);
			Collection<ICompilationUnit> normalized= result == null ? Collections.emptyList() : result;
			scopesByProject.computeIfAbsent(project, ignored -> new ScopeAccumulator()).record(currentScope, normalized);
			return normalized;
		}
	}

	/**
	 * Returns the optional dependency-free metadata consumed by the patched JDT
	 * Cleanup preview. Every transformed diagnostic becomes one candidate-level
	 * atomic selection unit. Rejected candidates are deliberately omitted.
	 *
	 * <p>The method is public and uses only JDT model objects plus JDK collection
	 * types so an unpatched Eclipse product can continue loading this cleanup; it
	 * simply never invokes the optional contract.</p>
	 *
	 * @param project current Java project
	 * @return immutable collection of candidate metadata maps
	 * @throws CoreException if a transformed candidate refers to a unit outside the
	 *                       proven execution scope
	 */
	public final Collection<Map<String, Object>> getCoordinatedCleanUpPreview(IJavaProject project)
			throws CoreException {
		synchronized (lifecycleLock) {
			if (!plansByProject.containsKey(project)) {
				return List.of();
			}
			MultiFileCleanUpDiagnostics diagnostics= diagnosticsByProject.get(project);
			Map<String, ICompilationUnit> unitsByHandle= compilationUnitsByProject.get(project);
			if (diagnostics == null || unitsByHandle == null) {
				return List.of();
			}
			String cleanupId= diagnostics.cleanupId();
			if (cleanupId.isBlank() || UNKNOWN_CLEANUP_ID.equals(cleanupId)) {
				cleanupId= getClass().getName();
			}
			List<Map<String, Object>> result= new ArrayList<>();
			for (MultiFileCandidateDiagnostic candidate : diagnostics.candidates()) {
				if (candidate.outcome() != MultiFileCandidateOutcome.TRANSFORMED) {
					continue;
				}
				if (candidate.candidateId().isBlank()) {
					throw invalidPreviewCandidate(cleanupId, "has a blank candidate id"); //$NON-NLS-1$
				}
				Set<ICompilationUnit> units= new LinkedHashSet<>();
				addPreviewCompilationUnit(units, unitsByHandle, candidate.ownerCompilationUnitHandle(),
						cleanupId, candidate.candidateId());
				for (String relatedHandle : candidate.relatedCompilationUnitHandles()) {
					addPreviewCompilationUnit(units, unitsByHandle, relatedHandle, cleanupId,
							candidate.candidateId());
				}
				String candidateName= candidate.message().isBlank()
						? "Coordinated cleanup candidate" //$NON-NLS-1$
						: candidate.message();
				List<String> details= new ArrayList<>();
				details.add(ATOMIC_SELECTION_DETAIL);
				if (!diagnostics.scope().explanation().isBlank()) {
					details.add(diagnostics.scope().explanation());
				}
				details.add("Affected source files: " + units.size()); //$NON-NLS-1$
				details.add(diagnostics.impact().compatibilityStatement());

				Map<String, Object> preview= new LinkedHashMap<>();
				preview.put(PREVIEW_ID, cleanupId + ':' + candidate.candidateId());
				preview.put(PREVIEW_NAME, candidateName);
				preview.put(PREVIEW_DESCRIPTION, diagnostics.impact().compatibilityStatement());
				preview.put(PREVIEW_COMPILATION_UNITS, List.copyOf(units));
				preview.put(PREVIEW_DETAILS, List.copyOf(new LinkedHashSet<>(details)));
				result.add(Collections.unmodifiableMap(preview));
			}
			return List.copyOf(result);
		}
	}

	/**
	 * Optional target-scope discovery used by a patched cleanup orchestrator.
	 * The unpatched Eclipse cleanup framework simply never calls this method.
	 *
	 * @param project current Java project
	 * @param currentScope immutable current target scope
	 * @param monitor progress monitor
	 * @return related compilation units, or an empty collection
	 * @throws CoreException if discovery cannot be completed safely
	 */
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		return Collections.emptyList();
	}

	/**
	 * Returns the currently retained plan for tests and specialised subclasses.
	 *
	 * @param project Java project
	 * @return plan or {@code null}
	 */
	protected final P getPlan(IJavaProject project) {
		synchronized (lifecycleLock) {
			return plansByProject.get(project);
		}
	}

	/**
	 * Returns metrics from the current project's most recent planning attempt.
	 * Metrics remain available through fix creation and postcondition checks and are
	 * cleared with the rest of the lifecycle state.
	 *
	 * @param project Java project
	 * @return retained metrics or empty metrics when no planning run exists
	 */
	protected final MultiFilePlanningMetrics getPlanningMetrics(IJavaProject project) {
		synchronized (lifecycleLock) {
			return metricsByProject.getOrDefault(project, MultiFilePlanningMetrics.empty());
		}
	}

	/**
	 * Returns structured diagnostics from the current project's most recent planning
	 * attempt. Diagnostics remain available through fix creation and postcondition
	 * checks and are cleared with the rest of the lifecycle state.
	 *
	 * @param project Java project
	 * @return retained diagnostics or empty diagnostics when no planning run exists
	 */
	protected final MultiFileCleanUpDiagnostics getPlanningDiagnostics(IJavaProject project) {
		synchronized (lifecycleLock) {
			return diagnosticsByProject.getOrDefault(project, MultiFileCleanUpDiagnostics.empty());
		}
	}

	private static Map<String, ICompilationUnit> indexCompilationUnits(ICompilationUnit[] compilationUnits) {
		Map<String, ICompilationUnit> result= new LinkedHashMap<>();
		for (ICompilationUnit unit : compilationUnits) {
			if (unit == null) {
				continue;
			}
			ICompilationUnit primary= unit.getPrimary();
			ICompilationUnit normalized= primary == null ? unit : primary;
			String handle= normalized.getHandleIdentifier();
			if (handle != null && !handle.isBlank()) {
				result.put(handle, normalized);
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private static void addPreviewCompilationUnit(Set<ICompilationUnit> result,
			Map<String, ICompilationUnit> unitsByHandle, String handle, String cleanupId,
			String candidateId) throws CoreException {
		if (handle == null || handle.isBlank()) {
			throw invalidPreviewCandidate(cleanupId,
					"candidate " + candidateId + " contains a blank compilation-unit handle"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		ICompilationUnit unit= unitsByHandle.get(handle);
		if (unit == null) {
			throw invalidPreviewCandidate(cleanupId,
					"candidate " + candidateId + " refers to a compilation unit outside the proven execution scope"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		result.add(unit);
	}

	private static CoreException invalidPreviewCandidate(String cleanupId, String detail) {
		return new CoreException(Status.error("Invalid coordinated cleanup preview for " //$NON-NLS-1$
				+ cleanupId + ": " + detail)); //$NON-NLS-1$
	}

	private void clearPlanningState(IJavaProject project) {
		plansByProject.remove(project);
		metricsByProject.remove(project);
		diagnosticsByProject.remove(project);
		compilationUnitsByProject.remove(project);
	}

	private void clearProjectState(IJavaProject project) {
		clearPlanningState(project);
		scopesByProject.remove(project);
	}
}
