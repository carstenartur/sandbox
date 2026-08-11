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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Fail-closed recognition of common JUnit 3 compatibility members that become
 * redundant after an ordinary Jupiter migration.
 *
 * <p>The accepted shapes deliberately have no user-visible construction state or
 * custom control flow: a constructor may only delegate its optional test name
 * to {@code TestCase}, a self suite may only select its declaring class, and a
 * lifecycle super call may only invoke the empty {@code TestCase} hook from the
 * matching lifecycle method.</p>
 */
public final class JUnit3LegacyShape {

	/** Fully qualified JUnit 3 base type. */
	public static final String JUNIT3_TEST_CASE= "junit.framework.TestCase"; //$NON-NLS-1$

	private static final String JAVA_LANG_STRING= "java.lang.String"; //$NON-NLS-1$

	private JUnit3LegacyShape() {
	}

	/** Returns whether the type directly extends {@code junit.framework.TestCase}. */
	public static boolean directlyExtendsTestCase(TypeDeclaration type) {
		if (type == null || type.getSuperclassType() == null) {
			return false;
		}
		ITypeBinding binding= type.resolveBinding();
		ITypeBinding superclass= binding == null ? null : binding.getSuperclass();
		if (superclass != null && !superclass.isRecovered()) {
			return JUNIT3_TEST_CASE.equals(superclass.getErasure().getQualifiedName());
		}
		return isWrittenType(type.getSuperclassType(), JUNIT3_TEST_CASE);
	}

	/**
	 * Returns whether an explicit constructor can be removed without changing any
	 * user-defined construction behavior.
	 */
	public static boolean isRemovableConstructor(MethodDeclaration method) {
		if (method == null || !method.isConstructor() || hasAnnotation(method)
				|| !method.typeParameters().isEmpty() || !method.thrownExceptionTypes().isEmpty()
				|| method.getReceiverType() != null) {
			return false;
		}
		Block body= method.getBody();
		if (body == null) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<Statement> statements= body.statements();
		if (method.parameters().isEmpty()) {
			return statements.isEmpty()
					|| statements.size() == 1 && statements.get(0) instanceof SuperConstructorInvocation invocation
							&& invocation.arguments().isEmpty() && invocation.typeArguments().isEmpty();
		}
		if (method.parameters().size() != 1 || statements.size() != 1
				|| !(method.parameters().get(0) instanceof SingleVariableDeclaration parameter)
				|| parameter.isVarargs() || !isString(parameter.getType())
				|| !(statements.get(0) instanceof SuperConstructorInvocation invocation)
				|| !invocation.typeArguments().isEmpty() || invocation.arguments().size() != 1) {
			return false;
		}
		Expression argument= (Expression) invocation.arguments().get(0);
		return argument instanceof SimpleName name
				&& parameter.getName().getIdentifier().equals(name.getIdentifier());
	}

	/**
	 * Returns whether {@code suite()} is the ordinary redundant JUnit 3 wrapper
	 * {@code new TestSuite(ThisType.class)}.
	 */
	public static boolean isSelfSuite(MethodDeclaration method, TypeDeclaration owner) {
		if (method == null || owner == null) {
			return false;
		}
		JUnit3SuiteModel.Result model= JUnit3SuiteModel.analyze(method);
		if (!model.supported() || model.selectedTypes().size() != 1) {
			return false;
		}
		String selected= model.selectedTypes().get(0);
		ITypeBinding binding= owner.resolveBinding();
		String qualifiedName= binding == null || binding.isRecovered() ? null : binding.getQualifiedName();
		String ownerSimpleName= owner.getName().getIdentifier();
		return ownerSimpleName.equals(selected) || qualifiedName != null && qualifiedName.equals(selected)
				|| ownerSimpleName.equals(simpleName(selected));
	}

	/**
	 * Returns whether a type is a pure suite aggregator, optionally retaining a
	 * harmless direct {@code TestCase} superclass, delegating constructors and one
	 * static initialization block. The block is migrated to one
	 * {@code @BeforeSuite} method by the suite cleanup.
	 */
	public static boolean isPureSuiteAggregator(TypeDeclaration owner, MethodDeclaration suite) {
		if (owner == null || suite == null || !(owner.getParent() instanceof CompilationUnit root)
				|| root.types().size() != 1 || owner.isInterface() || owner.getFields().length != 0
				|| owner.getTypes().length != 0 || !JUnit3SuiteModel.analyze(suite).supported()
				|| !hasSupportedSuiteInitializer(owner)) {
			return false;
		}
		if (owner.getSuperclassType() != null && !directlyExtendsTestCase(owner)) {
			return false;
		}
		for (MethodDeclaration method : owner.getMethods()) {
			if (method != suite && !isRemovableConstructor(method)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns the single static initializer of a pure suite aggregator, or
	 * {@code null} when none exists or the initializer shape is unsupported.
	 */
	public static Initializer suiteInitializer(TypeDeclaration owner) {
		List<Initializer> initializers= initializers(owner);
		if (initializers.size() != 1 || !Modifier.isStatic(initializers.get(0).getModifiers())) {
			return null;
		}
		return initializers.get(0);
	}

	/**
	 * Returns whether an invocation is a redundant direct call to the matching
	 * {@code TestCase.setUp()} or {@code TestCase.tearDown()} hook.
	 */
	public static boolean isRedundantLifecycleSuperCall(SuperMethodInvocation invocation) {
		if (invocation == null || !invocation.arguments().isEmpty() || !invocation.typeArguments().isEmpty()
				|| !(invocation.getParent() instanceof ExpressionStatement)) {
			return false;
		}
		String name= invocation.getName().getIdentifier();
		if (!("setUp".equals(name) || "tearDown".equals(name))) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		MethodDeclaration enclosing= enclosingMethod(invocation);
		if (enclosing == null || !name.equals(enclosing.getName().getIdentifier())) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		ITypeBinding declaring= binding == null ? null : binding.getDeclaringClass();
		return declaring != null && JUNIT3_TEST_CASE.equals(declaring.getErasure().getQualifiedName());
	}

	private static boolean hasSupportedSuiteInitializer(TypeDeclaration owner) {
		List<Initializer> initializers= initializers(owner);
		return initializers.size() <= 1
				&& initializers.stream().allMatch(initializer -> Modifier.isStatic(initializer.getModifiers()));
	}

	private static List<Initializer> initializers(TypeDeclaration owner) {
		if (owner == null) {
			return List.of();
		}
		return owner.bodyDeclarations().stream()
				.filter(Initializer.class::isInstance)
				.map(Initializer.class::cast)
				.toList();
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
		}
		return null;
	}

	private static boolean hasAnnotation(MethodDeclaration method) {
		return method.modifiers().stream().anyMatch(Annotation.class::isInstance);
	}

	private static boolean isString(Type type) {
		ITypeBinding binding= type.resolveBinding();
		return binding != null && !binding.isRecovered()
				? JAVA_LANG_STRING.equals(binding.getErasure().getQualifiedName())
				: isWrittenType(type, JAVA_LANG_STRING);
	}

	private static boolean isWrittenType(Type type, String qualifiedName) {
		String written= type.toString();
		return qualifiedName.equals(written) || simpleName(qualifiedName).equals(written);
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}
}
