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
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

/**
 * One shared fail-closed contract for the local JUnit 4 Parameterized rewrite
 * and its project-planning diagnostics.
 */
public final class ParameterizedMigrationEligibility {

	private static final String PARAMETER_ANNOTATION=
			"org.junit.runners.Parameterized.Parameter"; //$NON-NLS-1$
	private static final String JAVA_LANG_OBJECT= "java.lang.Object"; //$NON-NLS-1$
	private static final String JAVA_UTIL_ARRAYS= "java.util.Arrays"; //$NON-NLS-1$

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
		return parameterizedRunnerAnnotation(type) != null;
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
		MethodDeclaration provider= null;
		MethodDeclaration constructor= null;
		for (MethodDeclaration method : type.getMethods()) {
			if (method.isConstructor()) {
				constructors++;
				constructor= method;
				continue;
			}
			if (hasParametersAnnotation(method)) {
				providers++;
				provider= method;
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
		if (!(parameterizedRunnerAnnotation(type) instanceof SingleMemberAnnotation)) {
			return rejected("PARAMETERIZED_RUNNER_SYNTAX_UNSUPPORTED", //$NON-NLS-1$
					"The local rewrite currently supports only @RunWith(Parameterized.class), not the explicit value = syntax."); //$NON-NLS-1$
		}
		if (provider == null || !hasSupportedProviderShape(provider)) {
			return rejected("PARAMETERIZED_PROVIDER_BODY_UNSUPPORTED", //$NON-NLS-1$
					"The local @Parameters provider is not the supported static Arrays.asList(new Object[][] { ... }) form and cannot be rewritten safely."); //$NON-NLS-1$
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
				"One local provider with a supported body and one parameterized constructor form the supported local rewrite contract."); //$NON-NLS-1$
	}

	private static Annotation parameterizedRunnerAnnotation(TypeDeclaration type) {
		Objects.requireNonNull(type);
		for (Object modifier : type.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding annotationBinding= annotation.resolveTypeBinding();
			Expression value= runnerValue(annotation);
			if (annotationBinding == null
					|| !ORG_JUNIT_RUNWITH.equals(annotationBinding.getQualifiedName())
					|| !(value instanceof TypeLiteral literal)) {
				continue;
			}
			ITypeBinding runnerBinding= literal.getType().resolveBinding();
			if (runnerBinding != null && ORG_JUNIT_RUNNERS_PARAMETERIZED
					.equals(runnerBinding.getErasure().getQualifiedName())) {
				return annotation;
			}
		}
		return null;
	}

	private static boolean hasSupportedProviderShape(MethodDeclaration provider) {
		if (!Modifier.isStatic(provider.getModifiers())
				|| !provider.parameters().isEmpty()
				|| !provider.typeParameters().isEmpty()
				|| !provider.thrownExceptionTypes().isEmpty()
				|| provider.getBody() == null
				|| provider.getBody().statements().size() != 1) {
			return false;
		}
		Statement statement= (Statement) provider.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof MethodInvocation invocation)
				|| !"asList".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| invocation.arguments().size() != 1) {
			return false;
		}
		IMethodBinding methodBinding= invocation.resolveMethodBinding();
		ITypeBinding declaringType= methodBinding == null
				? null : methodBinding.getDeclaringClass();
		if (declaringType == null || !JAVA_UTIL_ARRAYS.equals(
				declaringType.getErasure().getQualifiedName())) {
			return false;
		}
		Expression argument= (Expression) invocation.arguments().get(0);
		if (!(argument instanceof ArrayCreation arrayCreation)
				|| arrayCreation.getInitializer() == null
				|| arrayCreation.getInitializer().expressions().isEmpty()) {
			return false;
		}
		ArrayType arrayType= arrayCreation.getType();
		ITypeBinding elementType= arrayType.getElementType().resolveBinding();
		if (arrayType.getDimensions() != 2 || elementType == null
				|| !JAVA_LANG_OBJECT.equals(
						elementType.getErasure().getQualifiedName())) {
			return false;
		}
		for (Object row : arrayCreation.getInitializer().expressions()) {
			if (row instanceof ArrayInitializer) {
				continue;
			}
			if (row instanceof ArrayCreation nested
					&& nested.getInitializer() != null) {
				continue;
			}
			return false;
		}
		return true;
	}

	private static Expression runnerValue(Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation single) {
			return single.getValue();
		}
		if (annotation instanceof NormalAnnotation normal) {
			for (Object value : normal.values()) {
				if (value instanceof MemberValuePair pair
						&& "value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
					return pair.getValue();
				}
		}
		return null;
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
