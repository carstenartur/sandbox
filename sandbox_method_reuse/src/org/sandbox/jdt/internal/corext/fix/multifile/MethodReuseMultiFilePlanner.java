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
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningLimits;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.cleanup.multifile.SourceRootPolicy;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.MethodDescriptor;

/** Builds deterministic plans for exact cross-file static-method delegation. */
public final class MethodReuseMultiFilePlanner {

	private static final String CLEANUP_ID= "method-reuse"; //$NON-NLS-1$
	private static final Comparator<MethodDescriptor> METHOD_ORDER=
			Comparator.comparing(MethodDescriptor::declaringTypeQualifiedName)
					.thenComparing(MethodDescriptor::methodName)
					.thenComparing(MethodDescriptor::methodBindingKey);

	private MethodReuseMultiFilePlanner() {
	}

	/** Creates a fail-closed plan for the complete editable source scope. */
	public static MultiFileCleanUpPlanResult<MethodReuseMigrationPlan> create(IJavaProject project,
			ICompilationUnit[] selectedUnits, IProgressMonitor monitor) throws CoreException {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		SelectedCompilationUnitPlan selectedScope= SelectedCompilationUnitPlan.of(project, selectedUnits);
		List<ICompilationUnit> allowedUnits= JavaProjectCompilationUnits.collect(project, Arrays.asList(selectedUnits),
				SourceRootPolicy.COMPLETE_PROJECT);
		Set<String> allowedHandles= handles(allowedUnits);
		boolean complete= selectedScope.compilationUnitHandles().containsAll(allowedHandles);
		if (!complete) {
			MethodReuseMigrationPlan emptyPlan= new MethodReuseMigrationPlan(selectedScope, List.of());
			return MultiFileCleanUpPlanResult.success(emptyPlan, new RefactoringStatus(),
					MultiFilePlanningMetrics.empty(), diagnostics(selectedUnits, false, List.of()));
		}

		long planningStarted= System.nanoTime();
		MultiFilePlanningBudget.Assessment budget= MultiFilePlanningBudget.assess(selectedUnits,
				MultiFilePlanningLimits.fromSystemProperties(), monitor);
		if (!budget.mayProceed()) {
			return new MultiFileCleanUpPlanResult<>(null, budget.status(), budget.metrics(),
					diagnostics(selectedUnits, true, List.of()));
		}

		long parseStarted= System.nanoTime();
		Map<String, CompilationUnit> roots= parse(project, selectedUnits, monitor);
		long parseNanos= System.nanoTime() - parseStarted;
		if (!roots.keySet().containsAll(selectedScope.compilationUnitHandles())) {
			MethodReuseMigrationPlan emptyPlan= new MethodReuseMigrationPlan(selectedScope, List.of());
			return MultiFileCleanUpPlanResult.success(emptyPlan, budget.status(), budget.metrics(),
					diagnostics(selectedUnits, false, List.of()));
		}

		Map<String, ICompilationUnit> unitsByHandle= new LinkedHashMap<>();
		for (ICompilationUnit unit : selectedUnits) {
			unitsByHandle.put(unit.getPrimary().getHandleIdentifier(), unit.getPrimary());
		}
		List<MethodDescriptor> methods= discover(unitsByHandle, roots, monitor);
		Map<String, List<MethodDescriptor>> groups= new LinkedHashMap<>();
		for (MethodDescriptor method : methods) {
			groups.computeIfAbsent(method.groupKey(), ignored -> new ArrayList<>()).add(method);
		}

		List<MethodReuseCandidate> candidates= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> candidateDiagnostics= new ArrayList<>();
		for (List<MethodDescriptor> group : groups.values()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			group.sort(METHOD_ORDER);
			long distinctUnits= group.stream().map(MethodDescriptor::compilationUnitHandle).distinct().count();
			if (distinctUnits < 2) {
				continue;
			}
			MethodDescriptor target= group.stream().filter(MethodDescriptor::targetEligible)
					.min(METHOD_ORDER).orElse(null);
			if (target == null) {
				continue;
			}
			for (MethodDescriptor duplicate : group) {
				if (duplicate.methodBindingKey().equals(target.methodBindingKey())
						|| duplicate.compilationUnitHandle().equals(target.compilationUnitHandle())) {
					continue;
				}
				String candidateId= duplicate.candidateId() + "->" + target.candidateId(); //$NON-NLS-1$
				MethodReuseCandidate candidate= new MethodReuseCandidate(candidateId,
						target.compilationUnitHandle(), target.methodBindingKey(),
						target.declaringTypeQualifiedName(), target.methodName(), duplicate.compilationUnitHandle(),
						duplicate.methodBindingKey(), duplicate.signatureKey(), duplicate.fingerprint());
				candidates.add(candidate);
				candidateDiagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId,
						duplicate.compilationUnitHandle(),
						"Replace the exact duplicate implementation of " + duplicate.declaringTypeQualifiedName() //$NON-NLS-1$
								+ '.' + duplicate.methodName() + " with a call to " //$NON-NLS-1$
								+ target.declaringTypeQualifiedName() + '.' + target.methodName() + '.',
						List.of(target.compilationUnitHandle())));
			}
		}
		candidates.sort(Comparator.comparing(MethodReuseCandidate::duplicateCompilationUnitHandle)
				.thenComparing(MethodReuseCandidate::duplicateMethodBindingKey));
		MultiFilePlanningMetrics metrics= budget.metrics()
				.withDurations(parseNanos, System.nanoTime() - planningStarted)
				.withRetainedPlanEntries(candidates.size());
		return MultiFileCleanUpPlanResult.success(new MethodReuseMigrationPlan(selectedScope, candidates),
				budget.status(), metrics, diagnostics(selectedUnits, true, candidateDiagnostics));
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static List<MethodDescriptor> discover(Map<String, ICompilationUnit> unitsByHandle,
			Map<String, CompilationUnit> roots, IProgressMonitor monitor) {
		List<MethodDescriptor> result= new ArrayList<>();
		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			ICompilationUnit unit= unitsByHandle.get(entry.getKey());
			if (unit == null) {
				continue;
			}
			CompilationUnit root= entry.getValue();
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodDeclaration method) {
					MultiFilePlanningBudget.checkCanceled(monitor);
					MethodDescriptor descriptor= MethodReuseSemanticSupport.describe(unit, root, method);
					if (descriptor != null) {
						result.add(descriptor);
					}
					return true;
				}
			});
		}
		result.sort(METHOD_ORDER);
		return result;
	}

	private static MultiFileCleanUpDiagnostics diagnostics(ICompilationUnit[] selectedUnits, boolean complete,
			List<MultiFileCandidateDiagnostic> candidates) {
		List<String> selectedHandles= Arrays.stream(selectedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.sorted().toList();
		MultiFileScopeDiagnostic scope= complete
				? new MultiFileScopeDiagnostic(selectedHandles, List.of(), "CLOSED_EDITABLE_SOURCE_SCOPE", //$NON-NLS-1$
						"Every editable source compilation unit in the selected Java project was analysed.", true) //$NON-NLS-1$
				: new MultiFileScopeDiagnostic(selectedHandles, List.of(), "INCOMPLETE_EDITABLE_SOURCE_SCOPE", //$NON-NLS-1$
						"The cleanup did not receive every editable source compilation unit required for method reuse.", false); //$NON-NLS-1$
		return new MultiFileCleanUpDiagnostics(CLEANUP_ID, scope, candidates);
	}

	private static Set<String> handles(List<ICompilationUnit> units) {
		Set<String> result= new LinkedHashSet<>();
		for (ICompilationUnit unit : units) {
			if (unit != null) {
				result.add(unit.getPrimary().getHandleIdentifier());
			}
		return result;
	}
}
