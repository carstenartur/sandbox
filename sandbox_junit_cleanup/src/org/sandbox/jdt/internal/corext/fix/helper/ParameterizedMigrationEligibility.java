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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNNERS_PARAMETERIZED;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNWITH;

import java.util.Objects;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

/**
 * One shared fail-closed contract for the local JUnit 4 Parameterized rewrite
 * and its project-planning diagnostics.
 */
public final class ParameterizedMigrationEligibility {

	private static final String PARAMETER_ANNOTATION=
			"org.junit.runners.Parameterized.Parameter"; //$NON-NLS-1$

	/** Immutable eligibility decision with a stable diagnostic reason code. */
	public record Assessment(boolean eligible, String reasonCode,
			String explanation) {

		/** Validate and normalize an assessment. */
		public Assessment {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private ParameterizedMigrationEligibility() {
	}

	/**
	 * Return whether a type declares the JUnit 4 Parameterized runner.
	 *
	 * @param type
	 *            source type
	 * @return {@code true} when bindings prove
	 *         {@code @RunWith(Parameterized.class)}
	 */
	public static boolean hasParameterizedRunner(TypeDeclaration type) {
		Objects.requireNonNull(type);
		for (Object modifier : type.modifiers()) {
			if (!(modifier instanceof SingleMemberAnnotation annotation)) {
				continue;
			}
			ITypeBinding annotationBinding= annotation.resolveTypeBinding();
			if (annotationBinding == null
					|| !ORG_JUNIT_RUNWITH.equals(annotationBinding.getQualifiedName())
					|| !(annotation.getValue() instanceof TypeLiteral literal)) {
				continue;
			}
			ITypeBinding runnerBinding= literal.getType().resolveBinding();
			if (runnerBinding != null && ORG_JUNIT_RUNNERS_PARAMETERIZED
					.equals(runnerBinding.getErasure().getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Assess the exact local source contract understood by the existing rewrite.
	 *
	 * @param type
	 *            Parameterized test type
	 * @return eligibility or one stable fail-closed reason
	 */
	public static Assessment assess(TypeDeclaration type) {
		Objects.requireNonNull(type);
		int providers= 0;
		int constructors= 0;
		MethodDeclaration constructor= null;
		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor()) {
				constructors++;
				constructor= method;
				continue;
			}
			if (hasParametersAnnotation(method)) {
				providers++;
			}
		}
		if (providers == 0) {
			return rejected("PARAMETERIZED_PROVIDER_NOT_LOCAL", //$NON-NLS-1$
					"No local @Parameters provider is declared; inherited or external providers require a coordinated migration."); //$NON-NLS-1$
		}
		if (providers > 1) {
			return rejected("PARAMETERIZED_MULTIPLE_LOCAL_PROVIDERS", //$NON-NLS-1$
					"More than one local @Parameters provider is declared, so a unique MethodSource cannot be selected safely."); //$NON-NLS-1$
		}
		if (usesFieldInjection(type)) {
			return rejected("PARAMETERIZED_FIELD_INJECTION", //$NON-NLS-1$
					"@Parameterized.Parameter field injection is not represented by the constructor-based local rewrite."); //$NON-NLS-1$
		}
		if (constructors != 1) {
			return rejected("PARAMETERIZED_CONSTRUCTOR_NOT_UNIQUE", //$NON-NLS-1$
					"Exactly one explicit constructor is required before constructor parameters can become test-method parameters."); //$NON-NLS-1$
		}
		if (constructor == null || constructor.parameters().isEmpty()) {
			return rejected("PARAMETERIZED_CONSTRUCTOR_HAS_NO_PARAMETERS", //$NON-NLS-1$
					"The unique constructor has no parameters to propagate to ParameterizedTest methods."); //$NON-NLS-1$
		}
		return new Assessment(true, "PARAMETERIZED_LOCAL_CONTRACT", //$NON-NLS-1$
				"One local provider and one parameterized constructor form the supported local rewrite contract."); //$NON-NLS-1$
	}

	private static boolean hasParametersAnnotation(MethodDeclaration method) {
		for (Object modifier : method.modifiers()) {
			if (modifier instanceof Annotation annotation
					&& isAnnotation(annotation,
							ORG_JUNIT_RUNNERS_PARAMETERIZED_PARAMETERS,
							"Parameters")) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private static boolean usesFieldInjection(TypeDeclaration type) {
		for (FieldDeclaration field : type.getFields()) {
			for (Object modifier : field.modifiers()) {
				if (modifier instanceof Annotation annotation
						&& isAnnotation(annotation, PARAMETER_ANNOTATION,
								"Parameter")) { //$NON-NLS-1$
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isAnnotation(Annotation annotation,
			String qualifiedName, String simpleName) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		if (binding != null) {
			return qualifiedName.equals(binding.getQualifiedName());
		}
		String sourceName= annotation.getTypeName().getFullyQualifiedName();
		return simpleName.equals(sourceName) || qualifiedName.equals(sourceName);
	}

	private static Assessment rejected(String reasonCode,
			String explanation) {
		return new Assessment(false, reasonCode, explanation);
	}
}
