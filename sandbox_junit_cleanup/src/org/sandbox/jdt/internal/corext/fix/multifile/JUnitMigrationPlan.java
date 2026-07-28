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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_SUITE_SUITECLASSES;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;

/** Immutable project plan for coordinated JUnit migration edits. */
public record JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
		List<ExternalResourceRuleMigration> externalResourceRules, List<JUnitSuiteMigration> suiteMigrations) {

	private record SuiteTarget(String typeBindingKey, String compilationUnitHandle) {
	}

	private record ResolvedSuite(String suiteTypeBindingKey, List<String> targetTypeKeys,
			List<String> targetCompilationUnitHandles) {
	}

	/** Compatibility constructor for ExternalResource-only callers. */
	public JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
			List<ExternalResourceRuleMigration> externalResourceRules) {
		this(selectedScope, externalResourceRules, List.of());
	}

	/** Defensively copies plan data. */
	public JUnitMigrationPlan {
		Objects.requireNonNull(selectedScope);
		externalResourceRules= List.copyOf(externalResourceRules);
		suiteMigrations= List.copyOf(suiteMigrations);
	}

	/** Returns whether the compilation unit belongs to this cleanup run. */
	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	/** Returns whether the plan contains coordinated cross-file work. */
	public boolean hasCoordinatedChanges() {
		return !externalResourceRules.isEmpty() || !suiteMigrations.isEmpty();
	}

	/**
	 * Re-resolves planned relationships and adds the operations belonging to the
	 * current compilation unit. A stale suite relationship aborts before local
	 * JUnit plugins are allowed to emit rewrites.
	 */
	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		String unitHandle= unit.getPrimary().getHandleIdentifier();
		List<JUnitSuiteMigration> suites= suiteMigrations.stream()
				.filter(migration -> unitHandle.equals(migration.suiteCompilationUnitHandle())).toList();
		validateSuites(unit, root, suites);

		List<ExternalResourceRuleMigration> fieldMigrations= externalResourceRules.stream()
				.filter(migration -> unitHandle.equals(migration.ruleCompilationUnitHandle())).toList();
		List<ExternalResourceRuleMigration> typeMigrations= externalResourceRules.stream()
				.filter(migration -> unitHandle.equals(migration.resourceCompilationUnitHandle())).toList();
		if (fieldMigrations.isEmpty() && typeMigrations.isEmpty()) {
			return;
		}

		Set<String> expectedFieldKeys= fieldMigrations.stream().map(ExternalResourceRuleMigration::fieldBindingKey)
				.collect(Collectors.toSet());
		Set<String> expectedTypeKeys= typeMigrations.stream().map(ExternalResourceRuleMigration::resourceTypeBindingKey)
				.collect(Collectors.toSet());
		JUnitMultiFileRewriteOperation.ResolvedEdits resolved= resolve(root, fieldMigrations, typeMigrations,
				expectedFieldKeys, expectedTypeKeys);
		if (!resolved.fieldKeys().equals(expectedFieldKeys) || !resolved.typeKeys().equals(expectedTypeKeys)) {
			throw staleExternalResourcePlan(unit, expectedFieldKeys, expectedTypeKeys, resolved);
		}
		nodesProcessed.addAll(resolved.fields().keySet());
		nodesProcessed.addAll(resolved.resourceTypes().keySet());
		operations.add(new JUnitMultiFileRewriteOperation(resolved));
	}

	private static void validateSuites(ICompilationUnit unit, CompilationUnit root,
			List<JUnitSuiteMigration> expectedSuites) throws CoreException {
		if (expectedSuites.isEmpty()) {
			return;
		}
		Map<String, ResolvedSuite> resolvedByType= resolveSuites(root, expectedSuites);
		for (JUnitSuiteMigration expected : expectedSuites) {
			ResolvedSuite resolved= resolvedByType.get(expected.suiteTypeBindingKey());
			if (resolved == null
					|| !expected.referencedTypeBindingKeys().equals(resolved.targetTypeKeys())
					|| !expected.referencedCompilationUnitHandles().equals(
							resolved.targetCompilationUnitHandles())) {
				throw staleSuitePlan(unit, expected, resolved);
			}
		}
	}

	private static Map<String, ResolvedSuite> resolveSuites(CompilationUnit root,
			List<JUnitSuiteMigration> expectedSuites) {
		Set<String> expectedTypeKeys= expectedSuites.stream()
				.map(JUnitSuiteMigration::suiteTypeBindingKey).collect(Collectors.toSet());
		Map<String, ResolvedSuite> result= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				String suiteTypeKey= typeKey(node.resolveBinding());
				if (suiteTypeKey == null || !expectedTypeKeys.contains(suiteTypeKey)) {
					return true;
				}
				Annotation annotation= suiteClassesAnnotation(node);
				List<SuiteTarget> targets= annotation == null ? List.of() : suiteTargets(annotation);
				result.put(suiteTypeKey, new ResolvedSuite(suiteTypeKey,
						targets.stream().map(SuiteTarget::typeBindingKey).toList(),
						targets.stream().map(SuiteTarget::compilationUnitHandle).toList()));
				return true;
			}
		});
		return result;
	}

	private static Annotation suiteClassesAnnotation(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof Annotation annotation && isSuiteClasses(annotation)) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean isSuiteClasses(Annotation annotation) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		if (binding != null) {
			return ORG_JUNIT_SUITE_SUITECLASSES.equals(binding.getQualifiedName());
		}
		String name= annotation.getTypeName().getFullyQualifiedName();
		int separator= name.lastIndexOf('.');
		return "SuiteClasses".equals(separator < 0 ? name : name.substring(separator + 1)); //$NON-NLS-1$
	}

	private static List<SuiteTarget> suiteTargets(Annotation annotation) {
		Expression expression= suiteValue(annotation);
		if (expression == null) {
			return List.of();
		}
		List<SuiteTarget> result= new ArrayList<>();
		if (!collectSuiteTargets(expression, result)) {
			return List.of();
		}
		return List.copyOf(result);
	}

	private static Expression suiteValue(Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation single) {
			return single.getValue();
		}
		if (annotation instanceof NormalAnnotation normal) {
			for (Object value : normal.values()) {
				MemberValuePair pair= (MemberValuePair) value;
				if ("value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
					return pair.getValue();
				}
			}
		}
		return null;
	}

	private static boolean collectSuiteTargets(Expression expression, List<SuiteTarget> targets) {
		if (expression instanceof TypeLiteral literal) {
			SuiteTarget target= suiteTarget(literal.getType().resolveBinding());
			if (target == null) {
				return false;
			}
			targets.add(target);
			return true;
		}
		if (expression instanceof ArrayInitializer initializer) {
			for (Object value : initializer.expressions()) {
				if (!(value instanceof Expression nested) || !collectSuiteTargets(nested, targets)) {
					return false;
				}
			}
			return !targets.isEmpty();
		}
		return false;
	}

	private static SuiteTarget suiteTarget(ITypeBinding binding) {
		ITypeBinding declaration= binding == null ? null : binding.getErasure().getTypeDeclaration();
		String key= typeKey(declaration);
		IJavaElement element= declaration == null ? null : declaration.getJavaElement();
		ICompilationUnit unit= element instanceof IType type ? type.getCompilationUnit() : null;
		if (key == null || unit == null || !unit.exists()) {
			return null;
		}
		return new SuiteTarget(key, unit.getPrimary().getHandleIdentifier());
	}

	private static JUnitMultiFileRewriteOperation.ResolvedEdits resolve(CompilationUnit root,
			List<ExternalResourceRuleMigration> fieldMigrations,
			List<ExternalResourceRuleMigration> typeMigrations, Set<String> expectedFieldKeys,
			Set<String> expectedTypeKeys) {
		JUnitMultiFileRewriteOperation.ResolvedEdits.Builder builder=
				JUnitMultiFileRewriteOperation.ResolvedEdits.builder(root);
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration node) {
				for (Object fragmentObject : node.fragments()) {
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
					IVariableBinding binding= fragment.resolveBinding();
					String key= binding == null ? null : binding.getVariableDeclaration().getKey();
					if (key != null && expectedFieldKeys.contains(key)) {
						ExternalResourceRuleMigration migration= fieldMigrations.stream()
								.filter(candidate -> key.equals(candidate.fieldBindingKey())).findFirst().orElseThrow();
						builder.addField(node, key, migration.classRule(), findRuleAnnotation(node, migration.classRule()));
					}
				}
				return true;
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding binding= node.resolveBinding();
				String key= typeKey(binding);
				if (key != null && expectedTypeKeys.contains(key)) {
					ExternalResourceRuleMigration migration= typeMigrations.stream()
							.filter(candidate -> key.equals(candidate.resourceTypeBindingKey())).findFirst().orElseThrow();
					builder.addResourceType(node, key, migration.classRule());
				}
				return true;
			}
		});
		return builder.build();
	}

	private static Annotation findRuleAnnotation(FieldDeclaration field, boolean classRule) {
		String expected= classRule ? "org.junit.ClassRule" : "org.junit.Rule"; //$NON-NLS-1$ //$NON-NLS-2$
		for (Object modifier : field.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && expected.equals(binding.getQualifiedName())) {
					return annotation;
				}
			}
		}
		return null;
	}

	static String typeKey(ITypeBinding binding) {
		if (binding == null) {
			return null;
		}
		ITypeBinding declaration= binding.getErasure().getTypeDeclaration();
		return declaration == null ? null : declaration.getKey();
	}

	private static CoreException staleExternalResourcePlan(ICompilationUnit unit, Set<String> expectedFieldKeys,
			Set<String> expectedTypeKeys, JUnitMultiFileRewriteOperation.ResolvedEdits resolved) {
		String message= "The coordinated JUnit migration plan is stale for " + unit.getElementName() //$NON-NLS-1$
				+ ". Expected fields " + expectedFieldKeys + " and types " + expectedTypeKeys //$NON-NLS-1$ //$NON-NLS-2$
				+ ", but resolved fields " + resolved.fieldKeys() + " and types " + resolved.typeKeys(); //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}

	private static CoreException staleSuitePlan(ICompilationUnit unit, JUnitSuiteMigration expected,
			ResolvedSuite resolved) {
		String actual= resolved == null ? "<missing suite annotation>" //$NON-NLS-1$
				: resolved.targetTypeKeys() + " in " + resolved.targetCompilationUnitHandles(); //$NON-NLS-1$
		String message= "The coordinated JUnit suite plan is stale for " + unit.getElementName() //$NON-NLS-1$
				+ ". Expected " + expected.referencedTypeBindingKeys() + " in " //$NON-NLS-1$ //$NON-NLS-2$
				+ expected.referencedCompilationUnitHandles() + ", but resolved " + actual; //$NON-NLS-1$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}
}
