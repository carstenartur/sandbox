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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.GeneratedNameCollisionPolicy;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;

/** Rejects prospective generated enum names against every fresh selected AST. */
public final class IntEnumGeneratedNameValidator {

	private static final String CLEANUP_ID= "int-to-enum"; //$NON-NLS-1$

	private IntEnumGeneratedNameValidator() {
	}

	/**
	 * Filters colliding candidates before preview changes are created.
	 *
	 * @param project current Java project
	 * @param units complete selected source scope
	 * @param result semantic planner result
	 * @param monitor progress monitor
	 * @return planner result containing only collision-free candidates
	 * @throws CoreException if fresh AST creation fails
	 */
	public static MultiFileCleanUpPlanResult<IntEnumMigrationPlan> validate(IJavaProject project,
			ICompilationUnit[] units, MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result,
			IProgressMonitor monitor) throws CoreException {
		IntEnumMigrationPlan plan= result.plan();
		if (plan == null || plan.candidates().isEmpty()) {
			return result;
		}
		Map<String, CompilationUnit> roots= parse(project, units, monitor);
		List<IntEnumCandidate> accepted= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (IntEnumCandidate candidate : plan.candidates()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			List<String> collisionExplanations= new ArrayList<>();
			for (CompilationUnit root : roots.values()) {
				GeneratedNameCollisionPolicy.Assessment assessment= GeneratedNameCollisionPolicy.assess(root,
						candidate.ownerTypeBindingKey(), candidate.ownerTypeQualifiedName(), candidate.enumTypeName());
				if (!assessment.available()) {
					collisionExplanations.add(assessment.explanation());
				}
			}
			String candidateId= candidateId(candidate);
			List<String> relatedUnits= relatedUnits(candidate);
			if (collisionExplanations.isEmpty()) {
				accepted.add(candidate);
				diagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId,
						candidate.ownerCompilationUnitHandle(),
						"Migrates the closed integer-state flow to nested enum " + candidate.enumTypeName() + '.', //$NON-NLS-1$
						relatedUnits));
			} else {
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId,
						candidate.ownerCompilationUnitHandle(), "GENERATED_NAME_COLLISION", //$NON-NLS-1$
						String.join("; ", collisionExplanations), relatedUnits)); //$NON-NLS-1$
			}
		}
		IntEnumMigrationPlan validatedPlan= new IntEnumMigrationPlan(plan.selectedScope(), accepted);
		MultiFileCleanUpDiagnostics validatedDiagnostics= new MultiFileCleanUpDiagnostics(CLEANUP_ID,
				result.diagnostics().scope(), diagnostics);
		return MultiFileCleanUpPlanResult.success(validatedPlan, result.status(),
				result.metrics().withRetainedPlanEntries(accepted.size()), validatedDiagnostics);
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

	private static String candidateId(IntEnumCandidate candidate) {
		return candidate.ownerTypeQualifiedName() + '#' + candidate.enumTypeName() + ':' + candidate.parameterIndex();
	}

	private static List<String> relatedUnits(IntEnumCandidate candidate) {
		Set<String> handles= new LinkedHashSet<>();
		handles.add(candidate.ownerCompilationUnitHandle());
		handles.addAll(candidate.expectedReferenceCountsByUnit().keySet());
		handles.addAll(candidate.expectedCallCountsByUnit().keySet());
		return List.copyOf(handles);
	}
}
