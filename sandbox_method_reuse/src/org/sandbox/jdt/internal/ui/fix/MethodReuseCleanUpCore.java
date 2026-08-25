/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.METHOD_REUSE_CLEANUP;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES;

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseMultiFilePlanner;

/** Cleanup that delegates exact duplicate static method implementations. */
public class MethodReuseCleanUpCore extends AbstractPlannedMultiFileCleanUp<MethodReuseMigrationPlan> {

	public MethodReuseCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public MethodReuseCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(requireAST(), false, false, null);
	}

	public boolean requireAST() {
		return isEnabled(METHOD_REUSE_CLEANUP) || isEnabled(METHOD_REUSE_INLINE_SEQUENCES);
	}

	@Override
	protected MultiFileCleanUpPlanResult<MethodReuseMigrationPlan> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		if (!requireAST()) {
			return MultiFileCleanUpPlanResult.noPlan();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			return MethodReuseMultiFilePlanner.create(project, compilationUnits, monitor);
		}
		return MultiFileCleanUpPlanResult.success(new MethodReuseMigrationPlan(
				SelectedCompilationUnitPlan.of(project, compilationUnits), List.of()));
	}

	@Override
	protected ICleanUpFix createFixForPlan(MethodReuseMigrationPlan plan, CleanUpContext context)
			throws CoreException {
		if (!plan.contains(context.getCompilationUnit()) || context.getAST() == null || !requireAST()) {
			return null;
		}
		CompilationUnit compilationUnit= context.getAST();
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();
		Set<ASTNode> nodesProcessed= new HashSet<>();

		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			plan.addOperationsFor(context.getCompilationUnit(), compilationUnit, operations, nodesProcessed);
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			MethodReuseCleanUpFixCore.INLINE_SEQUENCES.findOperations(compilationUnit, operations, nodesProcessed);
		}
		if (operations.isEmpty()) {
			return null;
		}
		return new CompilationUnitRewriteOperationsFixCore("Method Reuse Cleanup", compilationUnit, //$NON-NLS-1$
				operations.toArray(new CompilationUnitRewriteOperation[0]));
	}

	@Override
	protected Collection<ICompilationUnit> discoverAdditionalCompilationUnits(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		if (!isEnabled(METHOD_REUSE_CLEANUP)) {
			return List.of();
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		Set<String> currentHandles= currentScope.stream()
				.filter(java.util.Objects::nonNull)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());
		return JavaProjectCompilationUnits.collect(project, currentScope, SourceRootPolicy.COMPLETE_PROJECT).stream()
				.filter(unit -> !currentHandles.contains(unit.getPrimary().getHandleIdentifier()))
				.toList();
	}

	@Override
	public String[] getStepDescriptions() {
		List<String> result= new ArrayList<>();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			result.add("Replace exact duplicate static method implementations with calls to an existing method"); //$NON-NLS-1$
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			result.add("Replace inline code sequences with method calls"); //$NON-NLS-1$
		}
		return result.toArray(new String[0]);
	}

	@Override
	public String getPreview() {
		StringBuilder preview= new StringBuilder();
		if (isEnabled(METHOD_REUSE_CLEANUP)) {
			preview.append("""
				final class CanonicalNames {
				    static String normalize(String value) {
				        return value.trim();
				    }
				}
				final class Names {
				    static String clean(String input) {
				        return CanonicalNames.normalize(input);
				    }
				}
				"""); //$NON-NLS-1$
		} else {
			preview.append("""
				final class CanonicalNames {
				    static String normalize(String value) {
				        return value.trim();
				    }
				}
				final class Names {
				    static String clean(String input) {
				        return input.trim();
				    }
				}
				"""); //$NON-NLS-1$
		}
		if (isEnabled(METHOD_REUSE_INLINE_SEQUENCES)) {
			preview.append("""
				void printUser(String first, String last) {
				    String name = formatName(first, last);
				    System.out.println(name);
				}
				"""); //$NON-NLS-1$
		} else {
			preview.append("""
				void printUser(String first, String last) {
				    String name = first.trim() + " " + last.trim();
				    System.out.println(name);
				}
				"""); //$NON-NLS-1$
		}
		return preview.toString();
	}
}
