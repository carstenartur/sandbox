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

import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/**
 * Stable fail-closed taxonomy for JUnit 3 execution semantics that are not
 * equivalent to ordinary annotation-driven Jupiter tests.
 */
final class JUnit3HarnessSemantics {

	record Rejection(String reasonCode, String explanation) {
		Rejection {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	private JUnit3HarnessSemantics() {
	}

	/**
	 * Classify a method or constructor that changes the JUnit 3 harness contract.
	 *
	 * @param method
	 *            source declaration to inspect
	 * @return a precise rejection, or empty for an ordinary test/helper method
	 */
	static Optional<Rejection> rejection(MethodDeclaration method) {
		Objects.requireNonNull(method);
		if (method.isConstructor()) {
			if (isNamedTestConstructor(method)) {
				return Optional.of(new Rejection("NAMED_JUNIT3_TEST_CONSTRUCTION", //$NON-NLS-1$
						"The hierarchy constructs named JUnit 3 test instances through a String constructor; preserving selected method identity requires an explicit framework migration.")); //$NON-NLS-1$
			}
			return Optional.of(new Rejection("CUSTOM_JUNIT3_CONSTRUCTOR", //$NON-NLS-1$
					"The hierarchy declares an explicit constructor whose instantiation contract is not represented by ordinary Jupiter test discovery.")); //$NON-NLS-1$
		}

		String name= method.getName().getIdentifier();
		return switch (name) {
			case "suite" -> Optional.of(new Rejection( //$NON-NLS-1$
					"CUSTOM_JUNIT3_SUITE_BUILDER", //$NON-NLS-1$
					"The hierarchy declares suite(), so test composition, nesting, duplication or ordering must be migrated as an explicit suite model.")); //$NON-NLS-1$
			case "runTest" -> Optional.of(new Rejection( //$NON-NLS-1$
					"CUSTOM_JUNIT3_TEST_SELECTION", //$NON-NLS-1$
					"The hierarchy overrides runTest(), so runtime test selection is not equivalent to method-name discovery.")); //$NON-NLS-1$
			case "runBare" -> Optional.of(new Rejection( //$NON-NLS-1$
					"CUSTOM_JUNIT3_LIFECYCLE_WRAPPER", //$NON-NLS-1$
					"The hierarchy overrides runBare(), so its setup/test/teardown wrapper and exception behavior require an explicit extension or invocation migration.")); //$NON-NLS-1$
			case "createResult", "countTestCases" -> Optional.of(new Rejection( //$NON-NLS-1$ //$NON-NLS-2$
					"CUSTOM_JUNIT3_RESULT_MODEL", //$NON-NLS-1$
					"The hierarchy customizes JUnit 3 result creation or test counting, which has no ordinary Jupiter annotation equivalent.")); //$NON-NLS-1$
			case "getName", "setName" -> Optional.of(new Rejection( //$NON-NLS-1$ //$NON-NLS-2$
					"NAMED_JUNIT3_TEST_CONTRACT", //$NON-NLS-1$
					"The hierarchy customizes the mutable JUnit 3 test name used for selected-method execution and reporting.")); //$NON-NLS-1$
			case "run" -> Optional.of(new Rejection( //$NON-NLS-1$
					"CUSTOM_JUNIT3_RUNNER_INTEGRATION", //$NON-NLS-1$
					"The hierarchy overrides run(), so listener, result or execution delegation semantics require an explicit framework migration.")); //$NON-NLS-1$
			default -> Optional.empty();
		};
	}

	private static boolean isNamedTestConstructor(MethodDeclaration method) {
		IMethodBinding binding= method.resolveBinding();
		if (binding != null && binding.getParameterTypes().length == 1) {
			ITypeBinding parameter= binding.getParameterTypes()[0];
			return parameter != null && "java.lang.String" //$NON-NLS-1$
					.equals(parameter.getErasure().getQualifiedName());
		}
		if (method.parameters().size() != 1
				|| !(method.parameters().get(0) instanceof SingleVariableDeclaration parameter)
				|| parameter.isVarargs()) {
			return false;
		}
		String sourceType= parameter.getType().toString();
		return "String".equals(sourceType) || "java.lang.String".equals(sourceType); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
