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
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLOSED_SOURCE_PARAMETER_MIGRATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.ClosedSourceParameterMigrationFix;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedSourceContainerMigrationService;

/**
 * Planned multi-file execution surface for closed-source container signature
 * migrations. It is intentionally separate from {@link ContainerCleanUpCore}, whose
 * rules are locally executable and save-action compatible.
 */
public final class CoordinatedContainerCleanUpCore
		extends AbstractPlannedMultiFileCleanUp<ClosedSourceParameterMigrationPlan> {

	private static final String CLEANUP_ID=
			"sandbox_container_cleanup.closed_source_parameter_migration"; //$NON-NLS-1$

	private final ClosedSourceContainerMigrationService migrationService=
			new ClosedSourceContainerMigrationService();

	public CoordinatedContainerCleanUpCore() {
	}

	public CoordinatedContainerCleanUpCore(Map<String, String> options) {
		super(options);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(isActive(), false, false, null);
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		if (!isActive()) {
			return List.of();
		}
		ClosedSourceContainerMigrationService.Discovery discovery=
				migrationService.discover(project, currentScope, monitor);
		if (!discovery.candidateFound() || !discovery.complete()
				|| containsAll(currentScope, discovery.requiredUnits())) {
			return List.of();
		}
		return discovery.requiredUnits();
	}

	@Override
	protected MultiFileCleanUpPlanResult<ClosedSourceParameterMigrationPlan> createPlan(
			IJavaProject project, ICompilationUnit[] compilationUnits, IProgressMonitor monitor)
			throws CoreException {
		if (!isActive()) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		ClosedSourceContainerMigrationService.Planning planning=
				migrationService.plan(project, compilationUnits, monitor);
		if (!planning.candidateFound()) {
			return MultiFileCleanUpPlanResult.noPlan();
		}

		List<String> relatedHandles= planning.complete()
				? new ArrayList<>(planning.plan().orElseThrow().affectedCompilationUnitHandles())
				: new ArrayList<>(planning.requiredHandles());
		relatedHandles.remove(planning.ownerHandle());
		MultiFileCandidateDiagnostic candidate= planning.complete()
				? MultiFileCandidateDiagnostic.transformed(
						planning.candidateId(), planning.ownerHandle(), planning.message(), relatedHandles)
				: MultiFileCandidateDiagnostic.rejected(
						planning.candidateId(), planning.ownerHandle(), planning.reasonCode(),
						planning.message(), relatedHandles);
		MultiFileCleanUpDiagnostics diagnostics= new MultiFileCleanUpDiagnostics(
				CLEANUP_ID, MultiFileScopeDiagnostic.empty(), List.of(candidate));
		RefactoringStatus status= new RefactoringStatus();
		if (!planning.complete()) {
			status.addInfo("Coordinated container migration was not applied: " //$NON-NLS-1$
					+ planning.reasonCode() + ": " + planning.message()); //$NON-NLS-1$
			return new MultiFileCleanUpPlanResult<>(
					null, status, MultiFilePlanningMetrics.empty(), diagnostics);
		}
		return MultiFileCleanUpPlanResult.success(
				planning.plan().orElseThrow(), status,
				MultiFilePlanningMetrics.empty(), diagnostics);
	}

	@Override
	protected ICleanUpFix createFixForPlan(ClosedSourceParameterMigrationPlan plan,
			CleanUpContext context) throws CoreException {
		ICompilationUnit unit= context.getCompilationUnit();
		if (unit == null || context.getAST() == null
				|| !plan.affectedCompilationUnitHandles().contains(primaryHandle(unit))) {
			return null;
		}
		return ClosedSourceParameterMigrationFix.create(unit, context.getAST(), plan);
	}

	/** Returns the most recent privacy-preserving planning diagnostics for the project. */
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		MultiFileCleanUpDiagnostics diagnostics= getPlanningDiagnostics(project);
		return CLEANUP_ID.equals(diagnostics.cleanupId()) ? diagnostics.toJson() : ""; //$NON-NLS-1$
	}

	@Override
	public String[] getStepDescriptions() {
		return isActive()
				? new String[] {
						"Migrate one proven closed append-array flow and its complete parameter override family atomically" //$NON-NLS-1$
				}
				: new String[0];
	}

	@Override
	public String getPreview() {
		return isActive()
				? "// Project-closed migration: preview and selection are atomic across every required source file.\n" //$NON-NLS-1$
						+ "// Unsupported, stale, binary or incomplete flows remain unchanged with diagnostics.\n" //$NON-NLS-1$
				: "// Closed-source parameter migration is disabled.\n"; //$NON-NLS-1$
	}

	private static boolean containsAll(Collection<ICompilationUnit> currentScope,
			Collection<ICompilationUnit> requiredUnits) {
		Set<String> currentHandles= new HashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null) {
				currentHandles.add(primaryHandle(unit));
			}
		}
		for (ICompilationUnit unit : requiredUnits) {
			if (unit != null && !currentHandles.contains(primaryHandle(unit))) {
				return false;
			}
		}
		return true;
	}

	private static String primaryHandle(ICompilationUnit unit) {
		ICompilationUnit primary= unit.getPrimary();
		return (primary == null ? unit : primary).getHandleIdentifier();
	}

	private boolean isActive() {
		return isEnabled(CLEANUP) && isEnabled(CLOSED_SOURCE_PARAMETER_MIGRATION);
	}
}
