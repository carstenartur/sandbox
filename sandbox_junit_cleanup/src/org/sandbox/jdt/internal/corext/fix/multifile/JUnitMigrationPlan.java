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

import java.util.List;
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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;

/** Immutable project plan for coordinated JUnit migration edits. */
public record JUnitMigrationPlan(SelectedCompilationUnitPlan selectedScope,
		List<ExternalResourceRuleMigration> externalResourceRules) {

	/** Defensively copies plan data. */
	public JUnitMigrationPlan {
		Objects.requireNonNull(selectedScope);
		externalResourceRules= List.copyOf(externalResourceRules);
	}

	/** Returns whether the compilation unit belongs to this cleanup run. */
	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	/** Returns whether the plan contains coordinated cross-file work. */
	public boolean hasCoordinatedChanges() {
		return !externalResourceRules.isEmpty();
	}

	/**
	 * Resolves and adds the operations belonging to the current compilation unit.
	 * Resolution uses current bindings so stale plans abort the entire cleanup.
	 */
	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		String unitHandle= unit.getHandleIdentifier();
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
			throw stalePlan(unit, expectedFieldKeys, expectedTypeKeys, resolved);
		}
		nodesProcessed.addAll(resolved.fields().keySet());
		nodesProcessed.addAll(resolved.resourceTypes().keySet());
		operations.add(new JUnitMultiFileRewriteOperation(resolved));
	}

	private static JUnitMultiFileRewriteOperation.ResolvedEdits resolve(CompilationUnit root,
			List<ExternalResourceRuleMigration> fieldMigrations,
			List<ExternalResourceRuleMigration> typeMigrations, Set<String> expectedFieldKeys,
			Set<String> expectedTypeKeys) {
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

	private static CoreException stalePlan(ICompilationUnit unit, Set<String> expectedFieldKeys,
			Set<String> expectedTypeKeys, JUnitMultiFileRewriteOperation.ResolvedEdits resolved) {
		String message= "The coordinated JUnit migration plan is stale for " + unit.getElementName() //$NON-NLS-1$
				+ ". Expected fields " + expectedFieldKeys + " and types " + expectedTypeKeys //$NON-NLS-1$ //$NON-NLS-2$
				+ ", but resolved fields " + resolved.fieldKeys() + " and types " + resolved.typeKeys(); //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup", message)); //$NON-NLS-1$
	}
}
