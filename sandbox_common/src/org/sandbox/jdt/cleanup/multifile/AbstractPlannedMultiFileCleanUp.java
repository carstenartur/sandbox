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
import java.util.Map;

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
 * <p>Plans must not retain AST nodes from the planning parser. Previous cleanups
 * may change working copies before this cleanup receives its current AST. Store
 * Java model handles, binding keys, signatures, and semantic edit descriptions,
 * then resolve them again in {@link #createFixForPlan(Object, CleanUpContext)}.</p>
 *
 * @param <P> immutable plan type
 */
public abstract class AbstractPlannedMultiFileCleanUp<P> extends AbstractCleanUp
		implements IMultiFileCleanUpScopeProvider {

	private final Map<IJavaProject, P> plansByProject= new HashMap<>();

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
		plansByProject.remove(project);
		MultiFileCleanUpPlanResult<P> result;
		try {
			result= createPlan(project, compilationUnits.clone(), monitor);
		} catch (CoreException | RuntimeException e) {
			plansByProject.remove(project);
			throw e;
		}
		if (!result.status().hasFatalError() && result.plan() != null) {
			plansByProject.put(project, result.plan());
		}
		return result.status();
	}

	@Override
	public final ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		ICompilationUnit unit= context.getCompilationUnit();
		if (unit == null) {
			return null;
		}
		P plan= plansByProject.get(unit.getJavaProject());
		return plan == null ? null : createFixForPlan(plan, context);
	}

	@Override
	public final RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		try {
			return checkPlanPostConditions(monitor);
		} finally {
			plansByProject.clear();
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
		Collection<ICompilationUnit> result= discoverAdditionalCompilationUnits(project,
				Collections.unmodifiableCollection(currentScope), monitor);
		return result == null ? Collections.emptyList() : result;
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
		return plansByProject.get(project);
	}
}
