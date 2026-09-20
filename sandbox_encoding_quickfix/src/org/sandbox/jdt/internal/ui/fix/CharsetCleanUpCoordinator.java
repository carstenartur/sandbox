/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.triggerpattern.cleanup.CharsetConstructorMigration;
import org.sandbox.jdt.triggerpattern.cleanup.CheckedExceptionMigration;

/** Reuses the common lifecycle; only explicitly selected/discovered files can be changed. */
final class CharsetCleanUpCoordinator extends AbstractPlannedMultiFileCleanUp<CharsetCleanUpCoordinator.Run> {
    record Run(CheckedExceptionMigration.Plan exceptions) { }
    private final UseExplicitEncodingCleanUpCore owner;

    CharsetCleanUpCoordinator(UseExplicitEncodingCleanUpCore owner) {
        this.owner= owner;
    }

    boolean isPrepared(IJavaProject project) { return getPlan(project) != null; }

    @Override protected MultiFileCleanUpPlanResult<Run> createPlan(IJavaProject project,
            ICompilationUnit[] units, IProgressMonitor monitor) throws CoreException {
        CheckedExceptionMigration.Plan plan= owner.requireAST() ? parseAndPlan(project, List.of(units), monitor) : null;
        RefactoringStatus status= new RefactoringStatus();
        if (plan == null) return MultiFileCleanUpPlanResult.success(new Run(null));
        try {
            plan.requireComplete();
        } catch (CoreException conflict) {
            status.addFatalError(conflict.getStatus().getMessage());
            return new MultiFileCleanUpPlanResult<>(null, status);
        }
        List<String> affected= plan.affectedCompilationUnits().stream().sorted().toList();
        var scope= new MultiFileScopeDiagnostic(List.of(units).stream().map(ICompilationUnit::getHandleIdentifier).toList(),
                List.of(), "EXPLICIT_SELECTED_SCOPE", "Charset calls and their source exception contracts are changed together.", true); //$NON-NLS-1$ //$NON-NLS-2$
        var candidate= MultiFileCandidateDiagnostic.transformed("charset-exceptions", affected.get(0), //$NON-NLS-1$
                "Use typed Charset constructors and adapt checked exceptions", affected.subList(1, affected.size())); //$NON-NLS-1$
        return MultiFileCleanUpPlanResult.success(new Run(plan), status, MultiFilePlanningMetrics.empty(),
                new MultiFileCleanUpDiagnostics("sandbox.encoding", scope, List.of(candidate))); //$NON-NLS-1$
    }

    @Override protected ICleanUpFix createFixForPlan(Run plan, CleanUpContext context) throws CoreException {
        return owner.createEncodingFix(context, plan.exceptions());
    }

    @Override protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
            Collection<ICompilationUnit> selected, IProgressMonitor monitor) throws CoreException {
        if (!owner.requireAST()) return List.of();
        var plan= parseAndPlan(project, selected, monitor);
        if (plan == null) return List.of();
        List<ICompilationUnit> additions= new ArrayList<>();
        for (String handle : plan.requiredCompilationUnits()) {
            if (JavaCore.create(handle) instanceof ICompilationUnit unit && unit.exists()
                    && project.equals(unit.getJavaProject()) && !selected.contains(unit)) additions.add(unit);
        }
        additions.sort(Comparator.comparing(ICompilationUnit::getHandleIdentifier));
        return additions;
    }

    private static CheckedExceptionMigration.Plan parseAndPlan(IJavaProject project,
            Collection<ICompilationUnit> units, IProgressMonitor monitor) throws CoreException {
        if (units.isEmpty()) return null;
        List<CompilationUnit> roots= new ArrayList<>();
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setProject(project);
        parser.setResolveBindings(true);
        parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
            @Override public void acceptAST(ICompilationUnit unit, CompilationUnit root) { roots.add(root); }
        }, monitor);
        if (monitor != null && monitor.isCanceled()) throw new OperationCanceledException();
        if (roots.size() != units.size()) {
            throw new CoreException(Status.error("Not all selected compilation units could be parsed")); //$NON-NLS-1$
        }
        return CharsetConstructorMigration.plan(roots, monitor);
    }
}
