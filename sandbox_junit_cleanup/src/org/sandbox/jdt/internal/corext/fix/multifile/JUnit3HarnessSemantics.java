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
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;

/**
 * Stable fail-closed taxonomy for JUnit 3 execution semantics that are not
 * equivalent to ordinary annotation-driven Jupiter tests.
 */
final class JUnit3HarnessSemantics {

	private static final String JAVA_LANG_STRING= "java.lang.String"; //$NON-NLS-1$
	private static final String JUNIT_FRAMEWORK_TEST= "junit.framework.Test"; //$NON-NLS-1$
	private static final String JUNIT_FRAMEWORK_TEST_RESULT= "junit.framework.TestResult"; //$NON-NLS-1$

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
		if ("suite".equals(name) && isSuiteBuilder(method)) { //$NON-NLS-1$
			JUnit3SuiteModel.Result model= JUnit3SuiteModel.analyze(method);
			if (model.supported()) {
				return Optional.of(new Rejection("MODELLED_JUNIT3_SUITE_BUILDER", //$NON-NLS-1$
						"The hierarchy declares a modellable suite(); migrate the aggregator to @Suite and @SelectClasses before the hierarchy itself.")); //$NON-NLS-1$
			}
			return Optional.of(new Rejection("CUSTOM_JUNIT3_SUITE_BUILDER", //$NON-NLS-1$
					"The hierarchy declares suite(), so test composition, nesting, duplication or ordering must be migrated as an explicit suite model (" //$NON-NLS-1$
							+ model.rejection().reasonCode() + ")." )); //$NON-NLS-1$
		}
		if ("runTest".equals(name) && isRunTestHook(method)) { //$NON-NLS-1$
			return Optional.of(new Rejection("CUSTOM_JUNIT3_TEST_SELECTION", //$NON-NLS-1$
					"The hierarchy overrides runTest(), so runtime test selection is not equivalent to method-name discovery.")); //$NON-NLS-1$
		}
		if ("runBare".equals(name) && isRunBareHook(method)) { //$NON-NLS-1$
			return Optional.of(new Rejection("CUSTOM_JUNIT3_LIFECYCLE_WRAPPER", //$NON-NLS-1$
					"The hierarchy overrides runBare(), so its setup/test/teardown wrapper and exception behavior require an explicit extension or invocation migration.")); //$NON-NLS-1$
		}
		if (("createResult".equals(name) && isCreateResultHook(method)) //$NON-NLS-1$
				|| ("countTestCases".equals(name) && isCountTestCasesHook(method))) { //$NON-NLS-1$
			return Optional.of(new Rejection("CUSTOM_JUNIT3_RESULT_MODEL", //$NON-NLS-1$
					"The hierarchy customizes JUnit 3 result creation or test counting, which has no ordinary Jupiter annotation equivalent.")); //$NON-NLS-1$
		}
		if (("getName".equals(name) && isGetNameHook(method)) //$NON-NLS-1$
				|| ("setName".equals(name) && isSetNameHook(method))) { //$NON-NLS-1$
			return Optional.of(new Rejection("NAMED_JUNIT3_TEST_CONTRACT", //$NON-NLS-1$
					"The hierarchy customizes the mutable JUnit 3 test name used for selected-method execution and reporting.")); //$NON-NLS-1$
		}
		if ("run".equals(name) && isRunHook(method)) { //$NON-NLS-1$
			return Optional.of(new Rejection("CUSTOM_JUNIT3_RUNNER_INTEGRATION", //$NON-NLS-1$
					"The hierarchy overrides run(TestResult), so listener, result or execution delegation semantics require an explicit framework migration.")); //$NON-NLS-1$
		}
		return Optional.empty();
	}

	private static boolean isNamedTestConstructor(MethodDeclaration method) {
		return hasParameters(method, JAVA_LANG_STRING);
	}

	private static boolean isSuiteBuilder(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, JUNIT_FRAMEWORK_TEST)
				&& hasParameters(method);
	}

	private static boolean isRunTestHook(MethodDeclaration method) {
		return isProtectedOrPublic(method)
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, "void") //$NON-NLS-1$
				&& hasParameters(method);
	}

	private static boolean isRunBareHook(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, "void") //$NON-NLS-1$
				&& hasParameters(method);
	}

	private static boolean isCreateResultHook(MethodDeclaration method) {
		return isProtectedOrPublic(method)
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, JUNIT_FRAMEWORK_TEST_RESULT)
				&& hasParameters(method);
	}

	private static boolean isCountTestCasesHook(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, "int") //$NON-NLS-1$
				&& hasParameters(method);
	}

	private static boolean isGetNameHook(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, JAVA_LANG_STRING)
				&& hasParameters(method);
	}

	private static boolean isSetNameHook(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, "void") //$NON-NLS-1$
				&& hasParameters(method, JAVA_LANG_STRING);
	}

	private static boolean isRunHook(MethodDeclaration method) {
		return Modifier.isPublic(method.getModifiers())
				&& !Modifier.isStatic(method.getModifiers())
				&& hasReturnType(method, "void") //$NON-NLS-1$
				&& hasParameters(method, JUNIT_FRAMEWORK_TEST_RESULT);
	}

	private static boolean isProtectedOrPublic(MethodDeclaration method) {
		return Modifier.isProtected(method.getModifiers())
				|| Modifier.isPublic(method.getModifiers());
	}

	private static boolean hasReturnType(MethodDeclaration method, String qualifiedName) {
		IMethodBinding binding= method.resolveBinding();
		if (binding != null) {
			return isType(binding.getReturnType(), qualifiedName);
		}
		return isType(method.getReturnType2(), qualifiedName);
	}

	private static boolean hasParameters(MethodDeclaration method, String... qualifiedNames) {
		IMethodBinding binding= method.resolveBinding();
		if (binding != null) {
			ITypeBinding[] parameters= binding.getParameterTypes();
			if (parameters.length != qualifiedNames.length) {
				return false;
			}
			for (int index= 0; index < parameters.length; index++) {
				if (!isType(parameters[index], qualifiedNames[index])) {
					return false;
				}
			}
			return true;
		}
		if (method.parameters().size() != qualifiedNames.length) {
			return false;
		}
		for (int index= 0; index < qualifiedNames.length; index++) {
			if (!(method.parameters().get(index) instanceof SingleVariableDeclaration parameter)
					|| parameter.isVarargs()
					|| !isType(parameter.getType(), qualifiedNames[index])) {
				return false;
			}
		}
		return true;
	}

	private static boolean isType(ITypeBinding binding, String qualifiedName) {
		return binding != null && qualifiedName.equals(binding.getErasure().getQualifiedName());
	}

	private static boolean isType(Type type, String qualifiedName) {
		if (type == null) {
			return false;
		}
		ITypeBinding binding= type.resolveBinding();
		if (binding != null) {
			return isType(binding, qualifiedName);
		}
		String sourceType= type.toString();
		int lastDot= qualifiedName.lastIndexOf('.');
		String simpleName= lastDot < 0 ? qualifiedName : qualifiedName.substring(lastDot + 1);
		return qualifiedName.equals(sourceType) || simpleName.equals(sourceType);
	}
}
