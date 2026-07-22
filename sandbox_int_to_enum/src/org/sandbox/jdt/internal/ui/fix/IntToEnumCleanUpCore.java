/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions.PROJECT_WIDE;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.INT_TO_ENUM_CLEANUP;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.IntToEnumCleanUpFix_refactor;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.IntToEnumCleanUp_description;
import static org.sandbox.jdt.internal.ui.fix.MultiFixMessages.IntToEnumCleanUp_project_wide_description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.internal.corext.fix.IntToEnumFixCore;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMultiFilePlanner;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumScopeCandidateDetector;

/** Core cleanup implementation that converts integer constants to enums. */
public class IntToEnumCleanUpCore extends AbstractPlannedMultiFileCleanUp<IntEnumMigrationPlan> {

	private final Map<IJavaProject, Set<String>> pendingExpandedScopes= new HashMap<>();

	public IntToEnumCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public IntToEnumCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(INT_TO_ENUM_CLEANUP) && !computeFixSet().isEmpty();
	}

	@Override
	protected MultiFileCleanUpPlanResult<IntEnumMigrationPlan> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		if (!isEnabled(INT_TO_ENUM_CLEANUP) || computeFixSet().isEmpty()) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		if (!isEnabled(PROJECT_WIDE)) {
			SelectedCompilationUnitPlan selectedScope= SelectedCompilationUnitPlan.of(project, compilationUnits);
			return MultiFileCleanUpPlanResult.success(new IntEnumMigrationPlan(selectedScope, List.of()));
		}
		return IntEnumMultiFilePlanner.create(project, compilationUnits, monitor);
	}

	@Override
	protected ICleanUpFix createFixForPlan(IntEnumMigrationPlan plan, CleanUpContext context) throws CoreException {
		if (!plan.contains(context.getCompilationUnit())) {
			return null;
		}
		CompilationUnit compilationUnit= context.getAST();
		if (compilationUnit == null) {
			return null;
		}

		EnumSet<IntToEnumFixCore> computeFixSet= computeFixSet();
		if (!isEnabled(INT_TO_ENUM_CLEANUP) || computeFixSet.isEmpty()) {
			return null;
		}

		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesProcessed= new HashSet<>();
		plan.addOperationsFor(context.getCompilationUnit(), compilationUnit, operations, nodesProcessed);
		computeFixSet.forEach(i -> i.findOperations(compilationUnit, operations, nodesProcessed));

		if (operations.isEmpty()) {
			return null;
		}

		CompilationUnitRewriteOperationWithSourceRange[] array= operations.toArray(
				new CompilationUnitRewriteOperationWithSourceRange[0]);
		return new CompilationUnitRewriteOperationsFixCore(IntToEnumCleanUpFix_refactor, compilationUnit, array);
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		if (!isEnabled(INT_TO_ENUM_CLEANUP) || !isEnabled(PROJECT_WIDE)) {
			return List.of();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}

		Set<String> currentHandles= handles(currentScope);
		Set<String> pendingScope= pendingExpandedScopes.remove(project);
		if (pendingScope != null && currentHandles.containsAll(pendingScope)) {
			return List.of();
		}
		if (!IntEnumScopeCandidateDetector.containsCandidate(project, currentScope, monitor)) {
			return List.of();
		}

		List<ICompilationUnit> projectUnits= JavaProjectCompilationUnits.collect(project, currentScope,
				SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS);
		Set<String> projectHandles= handles(projectUnits);
		if (!currentHandles.containsAll(projectHandles)) {
			pendingExpandedScopes.put(project, projectHandles);
		}
		return projectUnits;
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(INT_TO_ENUM_CLEANUP)) {
			result.add(Messages.format(IntToEnumCleanUp_description,
					new Object[] { String.join(",", computeFixSet().stream() //$NON-NLS-1$
							.map(IntToEnumFixCore::toString)
							.collect(Collectors.toList())) }));
			if (isEnabled(PROJECT_WIDE)) {
				result.add(IntToEnumCleanUp_project_wide_description);
			}
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder sb= new StringBuilder();
		EnumSet<IntToEnumFixCore> computeFixSet= computeFixSet();
		EnumSet.allOf(IntToEnumFixCore.class).forEach(e -> sb.append(e.getPreview(computeFixSet.contains(e))));
		return sb.toString();
	}

	private EnumSet<IntToEnumFixCore> computeFixSet() {
		EnumSet<IntToEnumFixCore> fixSet= EnumSet.noneOf(IntToEnumFixCore.class);
		if (isEnabled(INT_TO_ENUM_CLEANUP)) {
			fixSet= EnumSet.allOf(IntToEnumFixCore.class);
		}
		return fixSet;
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		if (units == null || units.isEmpty()) {
			return Set.of();
		}
		return units.stream()
				.filter(java.util.Objects::nonNull)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());
	}
}
