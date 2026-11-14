/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.sandbox.jdt.cleanup.multifile.ClosedSourceReturnMigrationFix;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan;
import org.sandbox.jdt.container.api.ReturnPublicationPolicy;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedSourceContainerMigrationService;

/** Offers an explicit publication policy for a source-closed return and its length-only callers. */
public final class ReturnPublicationRefactoring extends Refactoring {
	private final ICompilationUnit producer;
	private final ClosedSourceContainerMigrationService service= new ClosedSourceContainerMigrationService();
	private ClosedSourceReturnMigrationPlan originalPlan;
	private Map<ICompilationUnit, String> originals= Map.of();
	private ReturnPublicationPolicy selected;

	public ReturnPublicationRefactoring(ICompilationUnit producer) {
		this.producer= Objects.requireNonNull(producer);
	}

	public void select(ReturnPublicationPolicy policy) {
		selected= Objects.requireNonNull(policy);
	}

	public boolean supportsCopy() {
		if (!JavaModelUtil.is10OrHigher(producer.getJavaProject())) {
			return false;
		}
		try {
			var list= producer.getJavaProject().findType("java.util.List"); //$NON-NLS-1$
			return list != null && Arrays.stream(list.getMethods()).anyMatch(method ->
					"copyOf".equals(method.getElementName()) && method.getNumberOfParameters() == 1); //$NON-NLS-1$
		} catch (JavaModelException unavailable) {
			return false;
		}
	}

	@Override
	public String getName() {
		return "Refactor array return publication"; //$NON-NLS-1$
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor monitor) throws CoreException {
		if (originalPlan != null) {
			return validateSource(monitor);
		}
		var units= new LinkedHashSet<>(List.of(producer));
		ClosedSourceContainerMigrationService.Discovery discovery;
		do {
			discovery= service.discover(producer.getJavaProject(), units, monitor);
			if (!discovery.candidateFound() || !discovery.complete()) {
				return RefactoringStatus.createFatalErrorStatus("Select the file containing a closed append-array return and its resolved source callers."); //$NON-NLS-1$
			}
		} while (units.addAll(discovery.requiredUnits()));
		var result= service.plan(producer.getJavaProject(), units.toArray(ICompilationUnit[]::new), monitor);
		if (!(result.plan().orElse(null) instanceof ClosedSourceReturnMigrationPlan plan)) {
			return RefactoringStatus.createFatalErrorStatus("A closed return and every caller must be proven. " + result.message()); //$NON-NLS-1$
		}
		var sources= new LinkedHashMap<ICompilationUnit, String>();
		for (var unit : units) {
			if (plan.affectedCompilationUnitHandles().contains(unit.getPrimary().getHandleIdentifier())) {
				sources.put(unit, unit.getSource());
			}
		}
		originals= Map.copyOf(sources);
		originalPlan= plan;
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor monitor) throws CoreException {
		if (selected == null) {
			return RefactoringStatus.createFatalErrorStatus("Choose the publication contract before preview."); //$NON-NLS-1$
		}
		if (selected == ReturnPublicationPolicy.REJECT_NULLS && !supportsCopy()) {
			return RefactoringStatus.createFatalErrorStatus("List.copyOf requires at least Java 10."); //$NON-NLS-1$
		}
		return validateSource(monitor);
	}

	private RefactoringStatus validateSource(IProgressMonitor monitor) throws CoreException {
		for (var entry : originals.entrySet()) {
			if (!entry.getKey().exists() || !entry.getValue().equals(entry.getKey().getSource())) {
				return stale();
			}
		}
		var fresh= service.plan(producer.getJavaProject(), originals.keySet().toArray(ICompilationUnit[]::new), monitor);
		return originalPlan != null && fresh.plan().filter(originalPlan::equals).isPresent() ? new RefactoringStatus() : stale();
	}

	private static RefactoringStatus stale() {
		return RefactoringStatus.createFatalErrorStatus("The producer or its callers changed. Reopen the preview."); //$NON-NLS-1$
	}

	@Override
	public Change createChange(IProgressMonitor monitor) throws CoreException {
		requireValid(checkFinalConditions(monitor));
		var policy= selected;
		List<CompilationUnitChange> children= new ArrayList<>();
		try {
			for (String handle : originalPlan.affectedCompilationUnitHandles()) {
				var unit= (ICompilationUnit) JavaCore.create(handle);
				var parser= ASTParser.newParser(AST.getJLSLatest());
				parser.setSource(unit);
				parser.setResolveBindings(true);
				var root= (CompilationUnit) parser.createAST(monitor);
				var fix= ClosedSourceReturnMigrationFix.create(unit, root, originalPlan, policy);
				if (fix == null) {
					throw new CoreException(Status.error("A required return or consumer edit is missing.")); //$NON-NLS-1$
				}
				children.add(fix.createChange(monitor));
			}
			return new CompositeChange(getName(), children.toArray(Change[]::new)) {
				@Override
				public RefactoringStatus isValid(IProgressMonitor progress) throws CoreException {
					var status= super.isValid(progress);
					status.merge(validateAll(progress));
					return status;
				}

				private RefactoringStatus validateAll(IProgressMonitor progress) throws CoreException {
					if (selected != policy || getChildren().length != children.size() || children.stream().anyMatch(change ->
							!change.isEnabled() || Arrays.stream(change.getTextEditChangeGroups()).anyMatch(group -> !group.isEnabled()))) {
						return RefactoringStatus.createFatalErrorStatus("All return and caller edits must be applied together."); //$NON-NLS-1$
					}
					return checkFinalConditions(progress);
				}

				@Override
				public Change perform(IProgressMonitor progress) throws CoreException {
					requireValid(validateAll(progress));
					return super.perform(progress);
				}
			};
		} catch (CoreException | RuntimeException exception) {
			children.forEach(Change::dispose);
			throw exception;
		}
	}

	private static void requireValid(RefactoringStatus status) throws CoreException {
		if (status.hasError()) {
			throw new CoreException(Status.error(status.getMessageMatchingSeverity(status.getSeverity())));
		}
	}
}
