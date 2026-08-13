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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Fail-closed assessment shared by strict and best-effort runner analysis.
 *
 * <p>The assessment mirrors the source shapes the existing runner rewriters can
 * actually transform. A runner name alone is never sufficient for complex
 * Enclosed, Theories, Categories, or Parameterized migrations.</p>
 */
public final class RunWithMigrationEligibility {

	private RunWithMigrationEligibility() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/** Returns whether this exact runner and source shape can be migrated. */
	public static boolean canMigrate(TypeDeclaration type, String qualifiedRunnerName) {
		if (type == null || qualifiedRunnerName == null || qualifiedRunnerName.isBlank()) {
			return false;
		}
		if (ORG_JUNIT_RUNNERS_PARAMETERIZED.equals(qualifiedRunnerName)) {
			return ParameterizedMigrationEligibility.assess(type).eligible();
		}

		Annotation runWith= runWithAnnotation(type);
		if (!(runWith instanceof SingleMemberAnnotation single)
				|| !(single.getValue() instanceof TypeLiteral literal)) {
			return false;
		}
		ITypeBinding runnerBinding= literal.getType().resolveBinding();
		if (runnerBinding == null
				|| !qualifiedRunnerName.equals(runnerBinding.getErasure().getQualifiedName())) {
			return false;
		}

		if (ORG_JUNIT_SUITE.equals(qualifiedRunnerName)
				|| ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER.equals(qualifiedRunnerName)
				|| ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER.equals(qualifiedRunnerName)
				|| ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER.equals(qualifiedRunnerName)
				|| ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER.equals(qualifiedRunnerName)) {
			return true;
		}
		if (ORG_JUNIT_EXPERIMENTAL_RUNNERS_ENCLOSED.equals(qualifiedRunnerName)) {
			return hasMigratableNestedType(type);
		}
		if (ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORIES.equals(qualifiedRunnerName)) {
			return hasClosedTheoriesShape(type);
		}
		if (ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORIES.equals(qualifiedRunnerName)) {
			return hasSuiteClasses(type);
		}
		return false;
	}

	private static Annotation runWithAnnotation(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof Annotation annotation
					&& isAnnotation(annotation, ORG_JUNIT_RUNWITH, "RunWith")) { //$NON-NLS-1$
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasMigratableNestedType(TypeDeclaration type) {
		for (TypeDeclaration nested : type.getTypes()) {
			if (Modifier.isStatic(nested.getModifiers()) && hasTestMethod(nested)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasTestMethod(TypeDeclaration type) {
		for (MethodDeclaration method : type.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation
						&& (isAnnotation(annotation, ORG_JUNIT_TEST, "Test") //$NON-NLS-1$
								|| isAnnotation(annotation, ORG_JUNIT_JUPITER_TEST, "Test"))) { //$NON-NLS-1$
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasClosedTheoriesShape(TypeDeclaration type) {
		int dataPointFields= 0;
		int theoryMethods= 0;
		for (FieldDeclaration field : type.getFields()) {
			for (Object modifier : field.modifiers()) {
				if (modifier instanceof Annotation annotation
						&& isAnnotation(annotation, ORG_JUNIT_EXPERIMENTAL_THEORIES_DATAPOINTS,
								"DataPoints")) { //$NON-NLS-1$
					dataPointFields++;
					if (field.fragments().size() != 1
							|| !(field.fragments().get(0) instanceof VariableDeclarationFragment fragment)
							|| !(fragment.getInitializer() instanceof ArrayInitializer)) {
						return false;
					}
				}
			}
		}
		for (MethodDeclaration method : type.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation
						&& isAnnotation(annotation, ORG_JUNIT_EXPERIMENTAL_THEORIES_THEORY,
								"Theory")) { //$NON-NLS-1$
					theoryMethods++;
				}
			}
		}
		return dataPointFields == 1 && theoryMethods == 1;
	}

	private static boolean hasSuiteClasses(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof SingleMemberAnnotation annotation
					&& isAnnotation(annotation, ORG_JUNIT_SUITE_SUITECLASSES, "SuiteClasses")) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private static boolean isAnnotation(Annotation annotation, String qualifiedName, String simpleName) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		if (binding != null) {
			return qualifiedName.equals(binding.getQualifiedName());
		}
		String sourceName= annotation.getTypeName().getFullyQualifiedName();
		return simpleName.equals(sourceName) || qualifiedName.equals(sourceName)
				|| sourceName.endsWith('.' + simpleName);
	}
}
