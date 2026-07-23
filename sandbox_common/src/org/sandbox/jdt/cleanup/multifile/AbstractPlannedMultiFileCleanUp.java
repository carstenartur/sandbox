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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

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
				return new MultiFileScopeDiagnostic(selectedHandles, existing.addedCompilationUnitHandles(),
						existing.reasonCode(), existing.explanation(), existing.complete());
			}
			Set<String> allAdded= new LinkedHashSet<>(existing.addedCompilationUnitHandles());
			allAdded.addAll(addedHandles);
			return new MultiFileScopeDiagnostic(selectedHandles, allAdded, "RELATED_SOURCE_CLOSURE", //$NON-NLS-1$
					"Related source compilation units were added to close coordinated cleanup references.", //$NON-NLS-1$
					existing.complete());
		}

		private static void addHandles(Set<String> target, Collection<ICompilationUnit> units) {
			if (units == null) {
				return;
			}
			for (ICompilationUnit unit : units) {
				if (unit != null) {
					target.add(unit.getPrimary().getHandleIdentifier());
				}
			}
		}
	}

	private final Object lifecycleLock= new Object();

	private final Map<IJavaProject, P> plansByProject= new HashMap<>();
	private final Map<IJavaProject, MultiFilePlanningMetrics> metricsByProject= new HashMap<>();
	private final Map<IJavaProject, MultiFileCleanUpDiagnostics> diagnosticsByProject= new HashMap<>();
	private final Map<IJavaProject, ScopeAccumulator> scopesByProject= new HashMap<>();

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

	private void clearPlanningState(IJavaProject project) {
		plansByProject.remove(project);
		metricsByProject.remove(project);
		diagnosticsByProject.remove(project);
	}

	private void clearProjectState(IJavaProject project) {
		clearPlanningState(project);
		scopesByProject.remove(project);
	}
}
