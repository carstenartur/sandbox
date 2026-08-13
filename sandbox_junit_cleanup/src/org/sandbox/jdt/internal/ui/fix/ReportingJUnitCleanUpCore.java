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
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_AFTER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_BEFORE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;

import java.util.HashMap;
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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix.multifile.ExternalResourceRuleMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Analysis;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;

/** JUnit cleanup core that retains privacy-preserving planning evidence. */
public final class ReportingJUnitCleanUpCore extends JUnitCleanUpCore {

	private final Map<IJavaProject, String> diagnosticsByProject= new HashMap<>();

	/** Creates a reporting cleanup core without initial options. */
	public ReportingJUnitCleanUpCore() {
	}

	/** Creates a reporting cleanup core with the supplied cleanup options. */
	public ReportingJUnitCleanUpCore(Map<String, String> options) {
		super(options);
	}

	@Override
	protected MultiFileCleanUpPlanResult<JUnitMigrationPlan> createPlan(IJavaProject project,
			ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
		diagnosticsByProject.remove(project);
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= super.createPlan(project, compilationUnits, monitor);
		result= removeNoOpExternalResourceMigrations(project, compilationUnits, result, monitor);
		String plannerJson= result.diagnostics().toJson();
		if (isEnabled(JUnitMigrationOptions.BEST_EFFORT)) {
			Analysis analysis= getMigrationAnalysis(project);
			diagnosticsByProject.put(project, analysis.toJson(plannerJson));
		} else {
			diagnosticsByProject.put(project, plannerJson);
		}
		return result;
	}

	private static MultiFileCleanUpPlanResult<JUnitMigrationPlan> removeNoOpExternalResourceMigrations(
			IJavaProject project, ICompilationUnit[] compilationUnits,
			MultiFileCleanUpPlanResult<JUnitMigrationPlan> result, IProgressMonitor monitor) {
		JUnitMigrationPlan plan= result.plan();
		if (plan == null || plan.externalResourceRules().isEmpty()) {
			return result;
		}

		Set<String> plannedTypeKeys= new LinkedHashSet<>();
		for (ExternalResourceRuleMigration migration : plan.externalResourceRules()) {
			plannedTypeKeys.add(migration.resourceTypeBindingKey());
		}
		Set<String> locallyRewrittenTypeKeys= locallyRewrittenResourceTypes(project, compilationUnits,
				plannedTypeKeys, monitor);
		List<ExternalResourceRuleMigration> filtered= plan.externalResourceRules().stream()
				.filter(migration -> locallyRewrittenTypeKeys.contains(migration.resourceTypeBindingKey()))
				.toList();
		if (filtered.size() == plan.externalResourceRules().size()) {
			return result;
		}

		JUnitMigrationPlan normalized= new JUnitMigrationPlan(plan.selectedScope(), filtered,
				plan.junit3Hierarchies(), plan.testTypeInventory());
		int retainedEntries= filtered.size() + plan.junit3Hierarchies().size();
		return new MultiFileCleanUpPlanResult<>(normalized, result.status(),
				result.metrics().withRetainedPlanEntries(retainedEntries), result.diagnostics());
	}

	private static Set<String> locallyRewrittenResourceTypes(IJavaProject project,
			ICompilationUnit[] compilationUnits, Set<String> plannedTypeKeys, IProgressMonitor monitor) {
		Set<String> result= new LinkedHashSet<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(compilationUnits, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration node) {
						ITypeBinding binding= node.resolveBinding();
						String key= typeKey(binding);
						if (key != null && plannedTypeKeys.contains(key)
								&& requiresLocalExternalResourceRewrite(node, binding)) {
							result.add(key);
						}
						return true;
					}
				});
			}
		}, monitor);
		return result;
	}

	private static boolean requiresLocalExternalResourceRewrite(TypeDeclaration node, ITypeBinding binding) {
		ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
		if (superclass != null
				&& ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(superclass.getErasure().getQualifiedName())) {
			return true;
		}
		for (MethodDeclaration method : node.getMethods()) {
			if (!method.isConstructor() && method.parameters().isEmpty()) {
				String name= method.getName().getIdentifier();
				if (METHOD_BEFORE.equals(name) || METHOD_AFTER.equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String typeKey(ITypeBinding binding) {
		if (binding == null) {
			return null;
		}
		ITypeBinding declaration= binding.getErasure().getTypeDeclaration();
		return declaration == null ? null : declaration.getKey();
	}

	/** Returns the most recent structured diagnostics for a project. */
	public String getLastPlanningDiagnosticsJson(IJavaProject project) {
		return diagnosticsByProject.getOrDefault(project, ""); //$NON-NLS-1$
	}
}
