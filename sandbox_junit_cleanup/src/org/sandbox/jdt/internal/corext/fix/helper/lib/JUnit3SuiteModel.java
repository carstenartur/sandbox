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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Fail-closed model of a JUnit 3 {@code public static Test suite()} or
 * {@code public static TestSuite suite()} aggregator.
 *
 * <p>Only aggregators whose selected classes and their order are provable from
 * the source are modelled. Everything else is reported as a precise rejection so
 * that the migration can leave the aggregator untouched instead of guessing.
 *
 * <p>Supported shapes:
 * <pre>
 * public static Test suite() { return new TestSuite(A.class, B.class); }
 *
 * public static Test suite() {
 *     TestSuite suite= new TestSuite("name");
 *     suite.addTestSuite(A.class);
 *     suite.addTest(new TestSuite(B.class));
 *     suite.addTest(C.suite());
 *     return suite;
 * }
 * </pre>
 */
public final class JUnit3SuiteModel {

	/** Fully qualified name of the JUnit 3 suite type. */
	public static final String JUNIT3_TEST_SUITE= "junit.framework.TestSuite"; //$NON-NLS-1$

	/** Fully qualified name of the JUnit 3 test interface. */
	public static final String JUNIT3_TEST= "junit.framework.Test"; //$NON-NLS-1$

	/** Precise, stable reason why an aggregator cannot be modelled. */
	public record Rejection(String reasonCode, String explanation) {
		public Rejection {
			reasonCode= Objects.requireNonNull(reasonCode);
			explanation= Objects.requireNonNull(explanation);
		}
	}

	/**
	 * Analysis outcome of one {@code suite()} declaration.
	 *
	 * @param selectedTypes source-visible names of the selected test classes, in declaration order
	 * @param rejection the rejection when the aggregator is not modelled, otherwise {@code null}
	 */
	public record Result(List<String> selectedTypes, Rejection rejection) {
		public Result {
			selectedTypes= List.copyOf(selectedTypes);
		}

		public boolean supported() {
			return rejection == null;
		}

		static Result rejected(String reasonCode, String explanation) {
			return new Result(List.of(), new Rejection(reasonCode, explanation));
		}

		static Result supported(List<String> selectedTypes) {
			return new Result(selectedTypes, null);
		}
	}

	private JUnit3SuiteModel() {
	}

	/**
	 * Returns whether the declaration has a supported JUnit 3 suite-builder signature.
	 *
	 * @param method method declaration to inspect
	 * @return {@code true} for {@code public static Test suite()} or
	 *         {@code public static TestSuite suite()}
	 */
	public static boolean isSuiteBuilder(MethodDeclaration method) {
		if (method == null || method.isConstructor() || !method.parameters().isEmpty()
				|| !Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
				|| !"suite".equals(method.getName().getIdentifier())) { //$NON-NLS-1$
			return false;
		}
		Type returnType= method.getReturnType2();
		return returnType != null && isJUnit3Type(returnType, JUNIT3_TEST, JUNIT3_TEST_SUITE);
	}

	/**
	 * Models the selected classes of a JUnit 3 suite aggregator.
	 *
	 * @param method a {@code public static Test suite()} or
	 *            {@code public static TestSuite suite()} declaration
	 * @return the ordered selected classes, or a precise rejection
	 */
	public static Result analyze(MethodDeclaration method) {
		if (!isSuiteBuilder(method)) {
			return Result.rejected("NOT_A_JUNIT3_SUITE_BUILDER", //$NON-NLS-1$
					"The method does not declare a public static Test or TestSuite suite() contract."); //$NON-NLS-1$
		}
		Block body= method.getBody();
		if (body == null) {
			return Result.rejected("UNRESOLVED_JUNIT3_SUITE_BODY", //$NON-NLS-1$
					"The suite() declaration has no source body to model."); //$NON-NLS-1$
		}
		@SuppressWarnings("unchecked")
		List<Statement> statements= body.statements();
		if (statements.size() == 1 && statements.get(0) instanceof ReturnStatement singleReturn) {
			return analyzeDirectReturn(singleReturn);
		}
		return analyzeAccumulatingSuite(statements);
	}

	private static Result analyzeDirectReturn(ReturnStatement statement) {
		if (!(statement.getExpression() instanceof ClassInstanceCreation creation)
				|| !isTestSuiteCreation(creation)) {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() result is not a directly constructed junit.framework.TestSuite."); //$NON-NLS-1$
		}
		List<String> selected= new ArrayList<>();
		Result rejection= collectCreationArguments(creation, selected);
		if (rejection != null) {
			return rejection;
		}
		return finish(selected);
	}

	private static Result analyzeAccumulatingSuite(List<Statement> statements) {
		if (statements.size() < 2) {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body does not declare and return one local junit.framework.TestSuite."); //$NON-NLS-1$
		}
		if (!(statements.get(0) instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !isJUnit3Type(declaration.getType(), JUNIT3_TEST_SUITE)) {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body does not start with a single local junit.framework.TestSuite declaration."); //$NON-NLS-1$
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) declaration.fragments().get(0);
		String suiteName= fragment.getName().getIdentifier();
		List<String> selected= new ArrayList<>();
		if (fragment.getInitializer() instanceof ClassInstanceCreation creation
				&& isTestSuiteCreation(creation)) {
			Result rejection= collectCreationArguments(creation, selected);
			if (rejection != null) {
				return rejection;
			}
		} else {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The local suite variable is not initialized with a directly constructed TestSuite."); //$NON-NLS-1$
		}

		for (Statement statement : statements.subList(1, statements.size() - 1)) {
			Result rejection= collectAddition(statement, suiteName, selected);
			if (rejection != null) {
				return rejection;
			}
		}

		Statement last= statements.get(statements.size() - 1);
		if (!(last instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof SimpleName returned)
				|| !suiteName.equals(returned.getIdentifier())) {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body does not return the declared local suite variable."); //$NON-NLS-1$
		}
		return finish(selected);
	}

	private static Result collectAddition(Statement statement, String suiteName, List<String> selected) {
		if (!(statement instanceof ExpressionStatement expressionStatement)
				|| !(expressionStatement.getExpression() instanceof MethodInvocation invocation)) {
			return Result.rejected("ORDER_DEPENDENT_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body contains statements other than suite additions, so its composition is not provable."); //$NON-NLS-1$
		}
		if (!(invocation.getExpression() instanceof SimpleName receiver)
				|| !suiteName.equals(receiver.getIdentifier())) {
			return Result.rejected("ORDER_DEPENDENT_JUNIT3_SUITE", //$NON-NLS-1$
					"A suite() statement does not add to the declared local suite variable."); //$NON-NLS-1$
		}
		String invoked= invocation.getName().getIdentifier();
		@SuppressWarnings("unchecked")
		List<Expression> arguments= invocation.arguments();
		if ("addTestSuite".equals(invoked) && arguments.size() == 1) { //$NON-NLS-1$
			return addTypeLiteral(arguments.get(0), selected);
		}
		if ("addTest".equals(invoked) && arguments.size() == 1) { //$NON-NLS-1$
			return addTestArgument(arguments.get(0), selected);
		}
		return Result.rejected("CUSTOM_JUNIT3_SUITE_COMPOSITION", //$NON-NLS-1$
				"The suite() body calls " + invoked + "(), which has no provable @SelectClasses equivalent."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Result addTestArgument(Expression argument, List<String> selected) {
		if (argument instanceof ClassInstanceCreation creation) {
			if (!isTestSuiteCreation(creation)) {
				return Result.rejected("CUSTOM_JUNIT3_SUITE_DECORATOR", //$NON-NLS-1$
						"The suite() body wraps tests in a decorator, whose setup must be migrated to an explicit extension first."); //$NON-NLS-1$
			}
			return collectCreationArguments(creation, selected);
		}
		if (argument instanceof MethodInvocation nested && "suite".equals(nested.getName().getIdentifier()) //$NON-NLS-1$
				&& nested.arguments().isEmpty() && nested.getExpression() instanceof Name qualifier) {
			// A nested aggregator stays selectable by class; the platform resolves it
			// through its own @Suite or through the vintage engine.
			selected.add(qualifier.getFullyQualifiedName());
			return null;
		}
		return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
				"The suite() body adds a test whose identity is not provable from the source."); //$NON-NLS-1$
	}

	private static Result collectCreationArguments(ClassInstanceCreation creation, List<String> selected) {
		@SuppressWarnings("unchecked")
		List<Expression> arguments= creation.arguments();
		if (creation.getAnonymousClassDeclaration() != null) {
			return Result.rejected("CUSTOM_JUNIT3_SUITE_DECORATOR", //$NON-NLS-1$
					"The suite() body creates an anonymous TestSuite subclass with custom behavior."); //$NON-NLS-1$
		}
		for (Expression argument : arguments) {
			if (argument instanceof StringLiteral) {
				// The suite display name has no @SelectClasses equivalent and is dropped.
				continue;
			}
			Result rejection= addTypeLiteral(argument, selected);
			if (rejection != null) {
				return rejection;
			}
		}
		return null;
	}

	private static Result addTypeLiteral(Expression argument, List<String> selected) {
		if (!(argument instanceof TypeLiteral literal)) {
			return Result.rejected("DYNAMIC_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body selects tests through a value that is not a class literal."); //$NON-NLS-1$
		}
		selected.add(literal.getType().toString());
		return null;
	}

	private static Result finish(List<String> selected) {
		if (selected.isEmpty()) {
			return Result.rejected("EMPTY_JUNIT3_SUITE", //$NON-NLS-1$
					"The suite() body does not select any test class."); //$NON-NLS-1$
		}
		Set<String> unique= new LinkedHashSet<>(selected);
		if (unique.size() != selected.size()) {
			return Result.rejected("DUPLICATED_JUNIT3_SUITE_ENTRY", //$NON-NLS-1$
					"The suite() body selects the same test class more than once, which @SelectClasses cannot reproduce."); //$NON-NLS-1$
		}
		return Result.supported(selected);
	}

	private static boolean isTestSuiteCreation(ClassInstanceCreation creation) {
		return isJUnit3Type(creation.getType(), JUNIT3_TEST_SUITE);
	}

	private static boolean isJUnit3Type(Type type, String... qualifiedNames) {
		if (type == null) {
			return false;
		}
		ITypeBinding binding= type.resolveBinding();
		if (binding != null && !binding.isRecovered()) {
			String name= binding.getErasure().getQualifiedName();
			for (String candidate : qualifiedNames) {
				if (candidate.equals(name)) {
					return true;
				}
			}
			return false;
		}
		String written= type.toString();
		for (String candidate : qualifiedNames) {
			if (candidate.equals(written) || simpleName(candidate).equals(written)) {
				return true;
			}
		}
		return false;
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}
}
