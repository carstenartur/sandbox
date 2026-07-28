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
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningBudget;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;

/** Binding-based immutable planning for JUnit 4 SuiteClasses relationships. */
final class JUnitSuitePlanner {

	record Result(List<JUnitSuiteMigration> migrations, List<MultiFileCandidateDiagnostic> diagnostics) {
		Result {
			migrations= List.copyOf(migrations);
			diagnostics= List.copyOf(diagnostics);
		}
	}

	private record Target(String typeKey, String compilationUnitHandle) {
	}

	private JUnitSuitePlanner() {
	}

	static Result create(Map<String, CompilationUnit> rootsByHandle, SelectedCompilationUnitPlan selectedScope,
			RefactoringStatus status, IProgressMonitor monitor) {
		List<JUnitSuiteMigration> migrations= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>();
		for (Map.Entry<String, CompilationUnit> entry : rootsByHandle.entrySet()) {
			MultiFilePlanningBudget.checkCanceled(monitor);
			String suiteUnitHandle= entry.getKey();
			for (Object typeObject : entry.getValue().types()) {
				if (typeObject instanceof TypeDeclaration type) {
					collectType(type, suiteUnitHandle, selectedScope, migrations, diagnostics, status, monitor);
				}
			}
		}
		return new Result(migrations, diagnostics);
	}

	private static void collectType(TypeDeclaration type, String suiteUnitHandle,
			SelectedCompilationUnitPlan selectedScope, List<JUnitSuiteMigration> migrations,
			List<MultiFileCandidateDiagnostic> diagnostics, RefactoringStatus status, IProgressMonitor monitor) {
		MultiFilePlanningBudget.checkCanceled(monitor);
		Annotation annotation= suiteClassesAnnotation(type);
		if (annotation != null) {
			planSuite(type, annotation, suiteUnitHandle, selectedScope, migrations, diagnostics, status);
		}
		for (TypeDeclaration nested : type.getTypes()) {
			collectType(nested, suiteUnitHandle, selectedScope, migrations, diagnostics, status, monitor);
		}
	}

	private static void planSuite(TypeDeclaration type, Annotation annotation, String suiteUnitHandle,
			SelectedCompilationUnitPlan selectedScope, List<JUnitSuiteMigration> migrations,
			List<MultiFileCandidateDiagnostic> diagnostics, RefactoringStatus status) {
		ITypeBinding suiteBinding= type.resolveBinding();
		String suiteKey= JUnitMigrationPlan.typeKey(suiteBinding);
		String suiteName= suiteBinding == null || suiteBinding.getQualifiedName().isEmpty()
				? type.getName().getIdentifier() : suiteBinding.getQualifiedName();
		String candidateId= "suite:" + suiteName; //$NON-NLS-1$
		List<Target> targets= targets(annotation);
		if (suiteKey == null || targets.isEmpty()) {
			String message= "JUnit suite " + suiteName //$NON-NLS-1$
					+ " has unresolved SuiteClasses source targets and cannot be migrated safely."; //$NON-NLS-1$
			status.addFatalError(message);
			diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, suiteUnitHandle,
					"UNRESOLVED_SUITE_TARGET", message, List.of(suiteUnitHandle))); //$NON-NLS-1$
			return;
		}
		List<String> targetHandles= targets.stream().map(Target::compilationUnitHandle).toList();
		List<String> omitted= targetHandles.stream()
				.filter(handle -> !selectedScope.compilationUnitHandles().contains(handle)).distinct().toList();
		List<String> relatedHandles= new ArrayList<>();
		relatedHandles.add(suiteUnitHandle);
		relatedHandles.addAll(targetHandles);
		if (!omitted.isEmpty()) {
			String message= "JUnit suite " + suiteName //$NON-NLS-1$
					+ " references source tests outside the selected cleanup scope: " + omitted; //$NON-NLS-1$
			status.addFatalError(message);
			diagnostics.add(MultiFileCandidateDiagnostic.rejected(candidateId, suiteUnitHandle,
					"INCOMPLETE_SUITE_SCOPE", message, relatedHandles)); //$NON-NLS-1$
			return;
		}
		migrations.add(new JUnitSuiteMigration(suiteUnitHandle, suiteKey,
				targets.stream().map(Target::typeKey).toList(), targetHandles));
		diagnostics.add(MultiFileCandidateDiagnostic.transformed(candidateId, suiteUnitHandle,
				"Migrates a JUnit suite together with " + targets.size() + " referenced source test type(s).", //$NON-NLS-1$ //$NON-NLS-2$
				relatedHandles));
	}

	private static Annotation suiteClassesAnnotation(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				ITypeBinding binding= annotation.resolveTypeBinding();
				if (binding != null && ORG_JUNIT_SUITE_SUITECLASSES.equals(binding.getQualifiedName())) {
					return annotation;
				}
			}
		}
		return null;
	}

	private static List<Target> targets(Annotation annotation) {
		Expression value= value(annotation);
		if (value == null) {
			return List.of();
		}
		List<Target> targets= new ArrayList<>();
		return collect(value, targets) ? List.copyOf(targets) : List.of();
	}

	private static Expression value(Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation single) {
			return single.getValue();
		}
		if (annotation instanceof NormalAnnotation normal) {
			for (Object valueObject : normal.values()) {
				MemberValuePair pair= (MemberValuePair) valueObject;
				if ("value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
					return pair.getValue();
				}
			}
		}
		return null;
	}

	private static boolean collect(Expression expression, List<Target> targets) {
		if (expression instanceof TypeLiteral literal) {
			Target target= target(literal.getType().resolveBinding());
			if (target == null) {
				return false;
			}
			targets.add(target);
			return true;
		}
		if (expression instanceof ArrayInitializer initializer) {
			for (Object expressionObject : initializer.expressions()) {
				if (!(expressionObject instanceof Expression nested) || !collect(nested, targets)) {
					return false;
				}
			}
			return !targets.isEmpty();
		}
		return false;
	}

	private static Target target(ITypeBinding binding) {
		ITypeBinding declaration= binding == null ? null : binding.getErasure().getTypeDeclaration();
		String key= JUnitMigrationPlan.typeKey(declaration);
		IJavaElement element= declaration == null ? null : declaration.getJavaElement();
		ICompilationUnit unit= element instanceof IType type ? type.getCompilationUnit() : null;
		return key == null || unit == null || !unit.exists()
				? null : new Target(key, unit.getPrimary().getHandleIdentifier());
	}
}
