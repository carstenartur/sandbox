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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_CLASS_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;

import java.util.ArrayList;
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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
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

/** Builds the source-wide JUnit migration plan before per-file rewrites start. */
public final class JUnitMultiFilePlanner {

	private static final String CLEANUP_ID= "junit-external-resource"; //$NON-NLS-1$

	private record ResourceType(String compilationUnitHandle, String typeBindingKey, String typeName) {
	}

	private record RuleField(String compilationUnitHandle, String fieldBindingKey, String resourceTypeKey,
			boolean classRule) {
	}

	private record MigrationResult(List<ExternalResourceRuleMigration> migrations,
			List<MultiFileCandidateDiagnostic> diagnostics) {
		MigrationResult {
			migrations= List.copyOf(migrations);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	private enum RuleKind {
		NONE,
		INSTANCE,
		CLASS
	}

	private JUnitMultiFilePlanner() {
	}

	/** Plans named ExternalResource migrations for a complete project selection. */
	public static MultiFileCleanUpPlanResult<JUnitMigrationPlan> create(IJavaProject project,
			ICompilationUnit[] selectedUnits, boolean migrateExternalResourceRules, IProgressMonitor monitor)
			throws CoreException {
		SelectedCompilationUnitPlan selectedScope= SelectedCompilationUnitPlan.of(project, selectedUnits);
		if (!migrateExternalResourceRules || selectedUnits.length == 0) {
			return MultiFileCleanUpPlanResult.success(new JUnitMigrationPlan(selectedScope, List.of()));
		}
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		List<ICompilationUnit> allProjectUnits= JavaProjectCompilationUnits.collect(project);
		Set<String> projectHandles= new LinkedHashSet<>();
		for (ICompilationUnit unit : allProjectUnits) {
			projectHandles.add(unit.getPrimary().getHandleIdentifier());
		}
		return create(project, selectedUnits, migrateExternalResourceRules,
				selectedScope.compilationUnitHandles().equals(projectHandles), monitor);
	}

	/**
	 * Plans named ExternalResource migrations after the caller has proved that the
	 * selected source units form a closed migration scope.
	 */
	public static MultiFileCleanUpPlanResult<JUnitMigrationPlan> create(IJavaProject project,
			ICompilationUnit[] selectedUnits, boolean migrateExternalResourceRules, boolean closedScope,
			IProgressMonitor monitor) throws CoreException {
		SelectedCompilationUnitPlan selectedScope= SelectedCompilationUnitPlan.of(project, selectedUnits);
		if (!migrateExternalResourceRules || selectedUnits.length == 0) {
			return MultiFileCleanUpPlanResult.success(new JUnitMigrationPlan(selectedScope, List.of()));
		}
		if (!closedScope) {
			MultiFileCleanUpDiagnostics diagnostics= diagnostics(selectedUnits, false, List.of());
			return MultiFileCleanUpPlanResult.success(new JUnitMigrationPlan(selectedScope, List.of()),
					new RefactoringStatus(), MultiFilePlanningMetrics.empty(), diagnostics);
		}
		MultiFilePlanningBudget.checkCanceled(monitor);

		long planningStarted= System.nanoTime();
		MultiFilePlanningBudget.Assessment budget= MultiFilePlanningBudget.assess(selectedUnits,
				MultiFilePlanningLimits.fromSystemProperties(), monitor);
		if (!budget.mayProceed()) {
			return new MultiFileCleanUpPlanResult<>(null, budget.status(), budget.metrics(),
					diagnostics(selectedUnits, true, List.of()));
		}
		long parseStarted= System.nanoTime();
		Map<String, CompilationUnit> rootsByHandle= parse(project, selectedUnits, monitor);
		long parseNanos= System.nanoTime() - parseStarted;
		MultiFilePlanningBudget.checkCanceled(monitor);
		Map<String, ResourceType> resourcesByTypeKey= collectResourceTypes(rootsByHandle, monitor);
		MultiFilePlanningBudget.checkCanceled(monitor);
		List<RuleField> ruleFields= collectRuleFields(rootsByHandle, resourcesByTypeKey, monitor);
		RefactoringStatus status= budget.status();
		MultiFilePlanningBudget.checkCanceled(monitor);
		MigrationResult migrationResult= createMigrations(ruleFields, resourcesByTypeKey, status, monitor);
		MultiFilePlanningMetrics metrics= budget.metrics()
				.withDurations(parseNanos, System.nanoTime() - planningStarted)
				.withRetainedPlanEntries(migrationResult.migrations().size());
		MultiFileCleanUpDiagnostics diagnostics= diagnostics(selectedUnits, true, migrationResult.diagnostics());
		if (status.hasFatalError()) {
			return new MultiFileCleanUpPlanResult<>(null, status, metrics, diagnostics);
		}
		return MultiFileCleanUpPlanResult.success(new JUnitMigrationPlan(selectedScope, migrationResult.migrations()),
				status, metrics, diagnostics);
	}

	private static MultiFileCleanUpDiagnostics diagnostics(ICompilationUnit[] selectedUnits, boolean complete,
			List<MultiFileCandidateDiagnostic> candidates) {
		List<String> selectedHandles= java.util.Arrays.stream(selectedUnits)
				.map(ICompilationUnit::getPrimary)
				.map(ICompilationUnit::getHandleIdentifier)
				.toList();
		MultiFileScopeDiagnostic scope= complete
				? new MultiFileScopeDiagnostic(selectedHandles, List.of(), "CLOSED_SOURCE_SCOPE", //$NON-NLS-1$
						"The selected compilation units form a closed ExternalResource migration scope.", true) //$NON-NLS-1$
				: new MultiFileScopeDiagnostic(selectedHandles, List.of(), "INCOMPLETE_SOURCE_SCOPE", //$NON-NLS-1$
						"The selected compilation units do not contain every required ExternalResource declaration and user.", //$NON-NLS-1$
						false);
		return new MultiFileCleanUpDiagnostics(CLEANUP_ID, scope, candidates);
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

	private static Map<String, ResourceType> collectResourceTypes(
			Map<String, CompilationUnit> rootsByHandle, IProgressMonitor monitor) {
		Map<String, ResourceType> result= new LinkedHashMap<>();
		for (Map.Entry<String, CompilationUnit> entry : rootsByHandle.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			String unitHandle= entry.getKey();
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					MultiFilePlanningBudget.checkCanceled(monitor);
					ITypeBinding binding= node.resolveBinding();
					if (directlyExtendsExternalResource(binding)) {
						String typeKey= JUnitMigrationPlan.typeKey(binding);
						if (typeKey != null) {
							result.put(typeKey, new ResourceType(unitHandle, typeKey, binding.getQualifiedName()));
						}
					}
					return true;
				}
			});
		}
		return result;
	}

	private static List<RuleField> collectRuleFields(Map<String, CompilationUnit> rootsByHandle,
			Map<String, ResourceType> resourcesByTypeKey, IProgressMonitor monitor) {
		List<RuleField> result= new ArrayList<>();
		for (Map.Entry<String, CompilationUnit> entry : rootsByHandle.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			String unitHandle= entry.getKey();
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(FieldDeclaration node) {
					MultiFilePlanningBudget.checkCanceled(monitor);
					RuleKind kind= ruleKind(node);
					boolean classRule= kind == RuleKind.CLASS;
					if (kind == RuleKind.NONE || node.fragments().size() != 1
							|| classRule != Modifier.isStatic(node.getModifiers())) {
						return true;
					}
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) node.fragments().get(0);
					IVariableBinding fieldBinding= fragment.resolveBinding();
					ITypeBinding resourceBinding= resourceTypeBinding(fragment, fieldBinding);
					String resourceTypeKey= JUnitMigrationPlan.typeKey(resourceBinding);
					ResourceType resource= resourcesByTypeKey.get(resourceTypeKey);
					if (fieldBinding == null || resource == null) {
						return true;
					}
					String fieldKey= fieldBinding.getVariableDeclaration().getKey();
					if (fieldKey != null) {
						result.add(new RuleField(unitHandle, fieldKey, resourceTypeKey, classRule));
					}
					return true;
				}
			});
		}
		return result;
	}

	private static MigrationResult createMigrations(List<RuleField> fields,
			Map<String, ResourceType> resourcesByTypeKey, RefactoringStatus status, IProgressMonitor monitor) {
		Map<String, List<RuleField>> fieldsByResourceType= new LinkedHashMap<>();
		for (RuleField field : fields) {
			fieldsByResourceType.computeIfAbsent(field.resourceTypeKey(), ignored -> new ArrayList<>()).add(field);
		}
		List<ExternalResourceRuleMigration> migrations= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (Map.Entry<String, List<RuleField>> entry : fieldsByResourceType.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			ResourceType resource= resourcesByTypeKey.get(entry.getKey());
			List<RuleField> resourceFields= entry.getValue();
			boolean hasRule= resourceFields.stream().anyMatch(field -> !field.classRule());
			boolean hasClassRule= resourceFields.stream().anyMatch(RuleField::classRule);
			List<String> relatedHandles= new ArrayList<>();
			relatedHandles.add(resource.compilationUnitHandle());
			resourceFields.stream().map(RuleField::compilationUnitHandle).forEach(relatedHandles::add);
			String candidateId= "external-resource:" + resource.typeName(); //$NON-NLS-1$
			if (hasRule && hasClassRule) {
				String message= "ExternalResource type " + resource.typeName() //$NON-NLS-1$
						+ " is used by both @Rule and @ClassRule fields; one callback lifecycle cannot safely represent both usages."; //$NON-NLS-1$
				status.addFatalError(message);
				diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId,
						resource.compilationUnitHandle(), "MIXED_RULE_LIFECYCLE", message, relatedHandles)); //$NON-NLS-1$
				continue;
			}
			for (RuleField field : resourceFields) {
				migrations.add(new ExternalResourceRuleMigration(field.compilationUnitHandle(), field.fieldBindingKey(),
						resource.compilationUnitHandle(), resource.typeBindingKey(), field.classRule()));
			}
			String lifecycle= hasClassRule ? "class" : "instance"; //$NON-NLS-1$ //$NON-NLS-2$
			diagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId,
					resource.compilationUnitHandle(), "Migrates " + resourceFields.size() + " " + lifecycle //$NON-NLS-1$ //$NON-NLS-2$
							+ " rule field(s) together with " + resource.typeName() + ".", relatedHandles)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return new MigrationResult(migrations, diagnostics);
	}

	private static RuleKind ruleKind(FieldDeclaration field) {
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding == null) {
					continue;
				}
				if (ORG_JUNIT_RULE.equals(binding.getQualifiedName())) {
					return RuleKind.INSTANCE;
				}
				if (ORG_JUNIT_CLASS_RULE.equals(binding.getQualifiedName())) {
					return RuleKind.CLASS;
				}
			}
		}
		return RuleKind.NONE;
	}

	private static ITypeBinding resourceTypeBinding(VariableDeclarationFragment fragment,
			IVariableBinding fieldBinding) {
		Expression initializer= fragment.getInitializer();
		if (initializer instanceof ClassInstanceCreation creation) {
			ITypeBinding binding= creation.resolveTypeBinding();
			if (binding != null) {
				return binding;
			}
		}
		return fieldBinding == null ? null : fieldBinding.getType();
	}

	private static boolean directlyExtendsExternalResource(ITypeBinding binding) {
		if (binding == null || binding.getSuperclass() == null) {
			return false;
		}
		return ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(binding.getSuperclass().getErasure().getQualifiedName());
	}
}
