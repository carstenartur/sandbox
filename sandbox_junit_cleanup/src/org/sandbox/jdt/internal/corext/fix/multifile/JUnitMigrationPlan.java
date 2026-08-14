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

import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_AFTER_EACH;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_ASSERTION_MESSAGE_FIRST;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_ASSERTION_QUALIFY;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_BEFORE_EACH;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_HIERARCHY_TYPE;
import static org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.ROLE_TEST_METHOD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.InvocationKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.InvocationMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.TypeMigration;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.cleanup.PlanAwareHintFileFixCore;

/** Immutable project plan for coordinated JUnit migration edits. */
public record JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
		List<ExternalResourceRuleMigration> externalResourceRules,
		List<JUnit3HierarchyMigration> junit3Hierarchies,
		JUnitTestTypeInventory testTypeInventory,
		Set<String> junit4CompatibleExternalResourceTypes) {

	private static final String JUNIT3_HINT_RESOURCE=
			"org/sandbox/jdt/internal/corext/fix/hints/junit3-hierarchy-to-jupiter.sandbox-hint"; //$NON-NLS-1$
	private static final String FACT_REMOVE_TEST_CASE_SUPERCLASS= "removeTestCaseSuperclass"; //$NON-NLS-1$
	private static final String FACT_TEST_ORDER= "testOrder"; //$NON-NLS-1$
	private static final String FACT_REMOVE_OVERRIDE= "removeOverride"; //$NON-NLS-1$

	public JUnitMigrationPlan {
		Objects.requireNonNull(selectedScope);
		externalResourceRules= List.copyOf(externalResourceRules);
		junit3Hierarchies= List.copyOf(junit3Hierarchies);
		testTypeInventory= Objects.requireNonNull(testTypeInventory);
		junit4CompatibleExternalResourceTypes= Set.copyOf(junit4CompatibleExternalResourceTypes);
	}

	public JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
			List<ExternalResourceRuleMigration> externalResourceRules,
			List<JUnit3HierarchyMigration> junit3Hierarchies,
			JUnitTestTypeInventory testTypeInventory) {
		this(selectedScope, externalResourceRules, junit3Hierarchies, testTypeInventory, Set.of());
	}

	public JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
			List<ExternalResourceRuleMigration> externalResourceRules) {
		this(selectedScope, externalResourceRules, List.of(), new JUnitTestTypeInventory(List.of()));
	}

	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	public boolean hasCoordinatedChanges() {
		return !externalResourceRules.isEmpty() || !junit3Hierarchies.isEmpty();
	}

	/**
	 * Retains the JUnit 4 {@code ExternalResource} contract for every fixture used
	 * by a strict-mode compilation unit that cannot be migrated atomically.
	 */
	public JUnitMigrationPlan withJUnit4CompatibilityForBlockedRuleUnits(Set<String> blockedRuleUnitHandles) {
		if (blockedRuleUnitHandles == null || blockedRuleUnitHandles.isEmpty()
				|| externalResourceRules.isEmpty()) {
			return this;
		}
		Set<String> compatibleTypes= externalResourceRules.stream()
				.filter(migration -> blockedRuleUnitHandles.contains(migration.ruleCompilationUnitHandle()))
				.map(ExternalResourceRuleMigration::resourceTypeBindingKey)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (compatibleTypes.isEmpty()) {
			return this;
		}
		compatibleTypes.addAll(junit4CompatibleExternalResourceTypes);
		if (compatibleTypes.equals(junit4CompatibleExternalResourceTypes)) {
			return this;
		}
		return new JUnitMigrationPlan(selectedScope, externalResourceRules, junit3Hierarchies,
				testTypeInventory, compatibleTypes);
	}

	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		String unitHandle= unit.getPrimary().getHandleIdentifier();
		addExternalResourceOperations(unit, unitHandle, root, operations, nodesProcessed);
		addJUnit3HierarchyOperations(unit, unitHandle, root, operations, nodesProcessed);
	}

	private void addExternalResourceOperations(ICompilationUnit unit, String unitHandle, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
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
		JUnitMultiFileRewriteOperation.ResolvedEdits resolved= resolveExternalResources(root, fieldMigrations,
				typeMigrations, expectedFieldKeys, expectedTypeKeys,
				junit4CompatibleExternalResourceTypes);
		if (!resolved.fieldKeys().equals(expectedFieldKeys) || !resolved.typeKeys().equals(expectedTypeKeys)) {
			throw staleExternalResourcePlan(unit, expectedFieldKeys, expectedTypeKeys, resolved);
		}
		nodesProcessed.addAll(resolved.fields().keySet());
		nodesProcessed.addAll(resolved.resourceTypes().keySet());
		operations.add(new JUnitMultiFileRewriteOperation(resolved));
	}

	private void addJUnit3HierarchyOperations(ICompilationUnit unit, String unitHandle, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		List<TypeMigration> plannedTypes= junit3Hierarchies.stream()
				.flatMap(hierarchy -> hierarchy.types().stream())
				.filter(type -> unitHandle.equals(type.compilationUnitHandle()))
				.toList();
		if (plannedTypes.isEmpty()) {
			return;
		}

		Map<String, TypeMigration> expectedTypes= plannedTypes.stream()
				.collect(Collectors.toMap(TypeMigration::typeBindingKey, type -> type));
		Set<String> expectedMethodKeys= plannedTypes.stream().flatMap(type -> type.methods().stream())
				.map(MethodMigration::methodBindingKey).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<NodeKey, InvocationMigration> expectedInvocations= new LinkedHashMap<>();
		for (TypeMigration type : plannedTypes) {
			for (InvocationMigration invocation : type.invocations()) {
				NodeKey key= NodeKey.invocation(invocation.methodBindingKey(), invocation.sourceStart(),
						invocation.sourceLength());
				expectedInvocations.put(key, invocation);
			}
		}

		Map<String, TypeDeclaration> resolvedTypes= new LinkedHashMap<>();
		Map<String, MethodDeclaration> resolvedMethods= new LinkedHashMap<>();
		Map<NodeKey, MethodInvocation> resolvedInvocations= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				String key= typeKey(node.resolveBinding());
				if (key != null && expectedTypes.containsKey(key)) {
					resolvedTypes.put(key, node);
				}
				return true;
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= node.resolveBinding();
				String key= binding == null ? null : binding.getMethodDeclaration().getKey();
				if (key != null && expectedMethodKeys.contains(key)) {
					resolvedMethods.put(key, node);
				}
				return true;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				NodeKey key= NodeKey.from(node);
				if (key != null && expectedInvocations.containsKey(key)) {
					resolvedInvocations.put(key, node);
				}
				return true;
			}
		});
		if (!resolvedTypes.keySet().equals(expectedTypes.keySet())
				|| !resolvedMethods.keySet().equals(expectedMethodKeys)
				|| !resolvedInvocations.keySet().equals(expectedInvocations.keySet())) {
			throw staleJUnit3Plan(unit, expectedTypes.keySet(), expectedMethodKeys, expectedInvocations.keySet(),
					resolvedTypes.keySet(), resolvedMethods.keySet(), resolvedInvocations.keySet());
		}

		SemanticRewritePlan.Builder plan= SemanticRewritePlan.builder("junit3-hierarchy"); //$NON-NLS-1$
		Set<NodeKey> expectedHintTargets= new LinkedHashSet<>();
		for (TypeMigration planned : plannedTypes) {
			NodeKey typeKey= NodeKey.type(planned.typeBindingKey());
			plan.add(typeKey, ROLE_HIERARCHY_TYPE)
					.putBoolean(typeKey, FACT_REMOVE_TEST_CASE_SUPERCLASS,
							planned.removeTestCaseSuperclass());
			if (planned.removeTestCaseSuperclass()) {
				expectedHintTargets.add(typeKey);
			}
			for (MethodMigration method : planned.methods()) {
				NodeKey key= NodeKey.method(method.methodBindingKey());
				plan.add(key, methodRole(method.kind()));
				if (method.kind() == MethodKind.TEST) {
					if (method.executionOrder() <= 0) {
						throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", //$NON-NLS-1$
								"The planned JUnit 3 test order is missing for " + method.methodBindingKey())); //$NON-NLS-1$
					}
					plan.putInteger(key, FACT_TEST_ORDER, method.executionOrder());
				} else {
					plan.putBoolean(key, FACT_REMOVE_OVERRIDE, method.removeOverride());
				}
				expectedHintTargets.add(key);
			}
			for (InvocationMigration invocation : planned.invocations()) {
				NodeKey key= NodeKey.invocation(invocation.methodBindingKey(), invocation.sourceStart(),
						invocation.sourceLength());
				plan.add(key, invocation.kind() == InvocationKind.MESSAGE_FIRST
						? ROLE_ASSERTION_MESSAGE_FIRST : ROLE_ASSERTION_QUALIFY);
				expectedHintTargets.add(key);
			}
			nodesProcessed.add(resolvedTypes.get(planned.typeBindingKey()));
			planned.methods().stream().map(MethodMigration::methodBindingKey)
					.map(resolvedMethods::get).forEach(nodesProcessed::add);
			planned.invocations().stream()
					.map(invocation -> NodeKey.invocation(invocation.methodBindingKey(), invocation.sourceStart(),
							invocation.sourceLength()))
					.map(resolvedInvocations::get).forEach(nodesProcessed::add);
		}

		Set<NodeKey> covered= PlanAwareHintFileFixCore.findOperationsFromContent(root, loadJUnit3HintProgram(),
				plan.build(), unit.getJavaProject().getOptions(true), operations, nodesProcessed);
		if (!covered.equals(expectedHintTargets)) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", //$NON-NLS-1$
					"The plan-aware JUnit hint program covered " + covered + " but expected " //$NON-NLS-1$ //$NON-NLS-2$
							+ expectedHintTargets));
		}
	}

	private static String methodRole(MethodKind kind) {
		return switch (kind) {
		case TEST -> ROLE_TEST_METHOD;
		case BEFORE_EACH -> ROLE_BEFORE_EACH;
		case AFTER_EACH -> ROLE_AFTER_EACH;
		};
	}

	private static String loadJUnit3HintProgram() throws CoreException {
		try (InputStream stream= JUnitMigrationPlan.class.getClassLoader().getResourceAsStream(JUNIT3_HINT_RESOURCE)) {
			if (stream == null) {
				throw new IOException("Missing resource " + JUNIT3_HINT_RESOURCE); //$NON-NLS-1$
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", //$NON-NLS-1$
					"Cannot load plan-aware JUnit 3 hint program", e)); //$NON-NLS-1$
		}
	}

	private static JUnitMultiFileRewriteOperation.ResolvedEdits resolveExternalResources(CompilationUnit root,
			List<ExternalResourceRuleMigration> fieldMigrations,
			List<ExternalResourceRuleMigration> typeMigrations, Set<String> expectedFieldKeys,
			Set<String> expectedTypeKeys, Set<String> junit4CompatibleResourceTypes) {
		JUnitMultiFileRewriteOperation.ResolvedEdits.Builder builder= JUnitMultiFileRewriteOperation.ResolvedEdits.builder(root);
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
					builder.addResourceType(node, key, migration.classRule(),
							junit4CompatibleResourceTypes.contains(key));
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

	private static CoreException staleJUnit3Plan(ICompilationUnit unit, Set<String> expectedTypeKeys,
			Set<String> expectedMethodKeys, Set<NodeKey> expectedInvocationKeys, Set<String> resolvedTypeKeys,
			Set<String> resolvedMethodKeys, Set<NodeKey> resolvedInvocationKeys) {
		String message= "The coordinated JUnit 3 hierarchy plan is stale for " + unit.getElementName() //$NON-NLS-1$
				+ ". Expected types " + expectedTypeKeys + ", methods " + expectedMethodKeys //$NON-NLS-1$ //$NON-NLS-2$
				+ " and invocations " + expectedInvocationKeys + ", but resolved types " + resolvedTypeKeys //$NON-NLS-1$ //$NON-NLS-2$
				+ ", methods " + resolvedMethodKeys + " and invocations " + resolvedInvocationKeys; //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}
}
