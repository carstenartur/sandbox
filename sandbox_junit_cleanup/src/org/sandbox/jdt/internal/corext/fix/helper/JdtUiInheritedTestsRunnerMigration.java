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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNWITH;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_TEST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Migrates the custom JUnit 4 runner used by the Eclipse JDT UI refactoring
 * tests to ordinary Jupiter discovery without reintroducing inherited tests.
 *
 * <p>The JUnit 4 contract consists of three exact source types:</p>
 * <ul>
 *   <li>{@code org.eclipse.jdt.ui.tests.IgnoreInheritedTests};</li>
 *   <li>{@code org.eclipse.jdt.ui.tests.InheritedTestsFilter};</li>
 *   <li>{@code org.eclipse.jdt.ui.tests.CustomBaseRunner}.</li>
 * </ul>
 *
 * <p>The runner filters a subclass so that only test methods declared directly
 * by that subclass remain. A direct {@code @Disabled} replacement is not
 * equivalent because disabled tests are still discovered and counted. The
 * source-only migration materializes one unannotated delegating override for
 * each otherwise inherited test method. Jupiter removes overridden superclass
 * methods before evaluating test annotations, so those methods disappear from
 * the discovered test tree while direct Java calls retain their former
 * behavior.</p>
 *
 * <p>This adapter intentionally recognizes the complete current JDT UI source
 * contract and fails closed when the runner, filter, marker, or inherited test
 * shape differs. A future project-resource migration may instead generate a
 * globally registered JUnit Platform {@code PostDiscoveryFilter}; ordinary
 * cleanup fixes cannot create its ServiceLoader resource atomically.</p>
 */
public final class JdtUiInheritedTestsRunnerMigration {

	static final String CUSTOM_BASE_RUNNER= "org.eclipse.jdt.ui.tests.CustomBaseRunner"; //$NON-NLS-1$
	static final String IGNORE_INHERITED_TESTS= "org.eclipse.jdt.ui.tests.IgnoreInheritedTests"; //$NON-NLS-1$
	static final String INHERITED_TESTS_FILTER= "org.eclipse.jdt.ui.tests.InheritedTestsFilter"; //$NON-NLS-1$

	private static final String BLOCK_JUNIT4_CLASS_RUNNER= "org.junit.runners.BlockJUnit4ClassRunner"; //$NON-NLS-1$
	private static final String DESCRIPTION_TYPE= "org.junit.runner.Description"; //$NON-NLS-1$
	private static final String FILTER_TYPE= "org.junit.runner.manipulation.Filter"; //$NON-NLS-1$
	private static final String INHERITED_META_ANNOTATION= "java.lang.annotation.Inherited"; //$NON-NLS-1$
	private static final String RETENTION_META_ANNOTATION= "java.lang.annotation.Retention"; //$NON-NLS-1$
	private static final String RETENTION_POLICY= "java.lang.annotation.RetentionPolicy"; //$NON-NLS-1$
	private static final String TARGET_META_ANNOTATION= "java.lang.annotation.Target"; //$NON-NLS-1$
	private static final String ELEMENT_TYPE= "java.lang.annotation.ElementType"; //$NON-NLS-1$

	/** One inherited method that must be shadowed in the migrated subclass. */
	public record SuppressedMethod(String name, List<String> thrownExceptionTypes) {

		public SuppressedMethod {
			Objects.requireNonNull(name);
			thrownExceptionTypes= List.copyOf(thrownExceptionTypes);
		}
	}

	/** Local, rewrite-ready plan for one annotated JDT UI test subclass. */
	public record Plan(TypeDeclaration type, Annotation markerAnnotation,
			List<SuppressedMethod> suppressedMethods) {

		public Plan {
			Objects.requireNonNull(type);
			Objects.requireNonNull(markerAnnotation);
			suppressedMethods= List.copyOf(suppressedMethods);
		}
	}

	/** Fail-closed assessment used by both runner diagnostics and rewriting. */
	public record Assessment(boolean eligible, String reasonCode, String explanation, Plan plan) {

		static Assessment accepted(Plan plan) {
			return new Assessment(true, "SUPPORTED_JDT_UI_INHERITED_TEST_FILTER", //$NON-NLS-1$
					"The JDT UI runner only removes inherited tests and can be materialized as unannotated overrides.", //$NON-NLS-1$
					plan);
		}

		static Assessment rejected(String reasonCode, String explanation) {
			return new Assessment(false, reasonCode, explanation, null);
		}
	}

	private record Variable(String name, ITypeBinding type) {
	}

	private record MethodCollection(List<SuppressedMethod> methods, String rejection) {
		MethodCollection {
			methods= List.copyOf(methods);
		}
	}

	private JdtUiInheritedTestsRunnerMigration() {
		throw new UnsupportedOperationException("Utility class"); //$NON-NLS-1$
	}

	/** Returns whether the exact JDT UI runner contract can be migrated safely. */
	public static Assessment assess(Annotation runWith, String runnerQualifiedName) {
		ASTNode parent= runWith == null ? null : runWith.getParent();
		TypeDeclaration type= parent != null
				&& parent.getNodeType() == ASTNode.TYPE_DECLARATION
						? (TypeDeclaration) parent
						: null;
		return assess(type, runnerQualifiedName);
	}

	/** Returns whether the exact JDT UI runner contract can be migrated safely. */
	public static Assessment assess(TypeDeclaration type, String runnerQualifiedName) {
		if (type == null || !CUSTOM_BASE_RUNNER.equals(runnerQualifiedName)) {
			return Assessment.rejected("NOT_JDT_UI_INHERITED_TEST_RUNNER", //$NON-NLS-1$
					"The runner is not the supported JDT UI inherited-test runner."); //$NON-NLS-1$
		}

		SingleMemberAnnotation runWith= runWithAnnotation(type);
		if (runWith == null || !(runWith.getValue() instanceof TypeLiteral literal)) {
			return Assessment.rejected("MALFORMED_JDT_UI_RUNNER_ANNOTATION", //$NON-NLS-1$
					"The JDT UI runner annotation is not a binding-resolved @RunWith type literal."); //$NON-NLS-1$
		}
		ITypeBinding runnerBinding= erasure(literal.getType().resolveBinding());
		if (runnerBinding == null || !CUSTOM_BASE_RUNNER.equals(runnerBinding.getQualifiedName())) {
			return Assessment.rejected("UNRESOLVED_JDT_UI_RUNNER", //$NON-NLS-1$
					"The JDT UI custom runner binding could not be resolved exactly."); //$NON-NLS-1$
		}

		Annotation marker= declaredAnnotation(type, IGNORE_INHERITED_TESTS);
		if (marker == null) {
			return Assessment.rejected("MISSING_IGNORE_INHERITED_TESTS", //$NON-NLS-1$
					"The test subclass does not declare the JDT UI inherited-test marker."); //$NON-NLS-1$
		}
		ITypeBinding markerBinding= erasure(marker.resolveTypeBinding());
		if (!isRuntimeTypeMarker(markerBinding)) {
			return Assessment.rejected("INVALID_IGNORE_INHERITED_TESTS", //$NON-NLS-1$
					"The marker must remain a binding-resolved @Inherited, @Retention(RUNTIME), @Target(TYPE) annotation."); //$NON-NLS-1$
		}

		String runnerRejection= verifyRunnerAndFilterContract(runnerBinding);
		if (runnerRejection != null) {
			return Assessment.rejected("JDT_UI_RUNNER_CONTRACT_CHANGED", runnerRejection); //$NON-NLS-1$
		}

		ITypeBinding testType= erasure(type.resolveBinding());
		if (testType == null) {
			return Assessment.rejected("UNRESOLVED_JDT_UI_TEST_TYPE", //$NON-NLS-1$
					"The annotated test subclass binding could not be resolved."); //$NON-NLS-1$
		}
		if (Modifier.isAbstract(testType.getModifiers()) || !hasDirectTestMethod(testType)) {
			return Assessment.rejected("NO_DIRECT_JDT_UI_TEST", //$NON-NLS-1$
					"The JDT UI runner adapter requires a concrete subclass with at least one directly declared test."); //$NON-NLS-1$
		}
		MethodCollection collected= collectSuppressedMethods(testType);
		if (collected.rejection() != null) {
			return Assessment.rejected("UNSUPPORTED_INHERITED_TEST_METHOD", collected.rejection()); //$NON-NLS-1$
		}
		return Assessment.accepted(new Plan(type, marker, collected.methods()));
	}

	/** Applies the local source-only migration prepared by {@link #assess}. */
	public static void rewrite(TextEditGroup group, ASTRewrite rewrite, AST ast,
			ImportRewrite imports, Annotation runWith, Plan plan) {
		ListRewrite modifiers= rewrite.getListRewrite(plan.type(), TypeDeclaration.MODIFIERS2_PROPERTY);
		modifiers.remove(runWith, group);
		modifiers.remove(plan.markerAnnotation(), group);

		ListRewrite body= rewrite.getListRewrite(plan.type(), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (SuppressedMethod suppressed : plan.suppressedMethods()) {
			MethodDeclaration method= ast.newMethodDeclaration();
			method.setName(ast.newSimpleName(suppressed.name()));
			method.setReturnType2(ast.newPrimitiveType(org.eclipse.jdt.core.dom.PrimitiveType.VOID));

			Annotation override= ast.newMarkerAnnotation();
			override.setTypeName(ast.newSimpleName("Override")); //$NON-NLS-1$
			method.modifiers().add(override);
			method.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));
			for (String thrownType : suppressed.thrownExceptionTypes()) {
				method.thrownExceptionTypes().add(ast.newSimpleType(ast.newName(imports.addImport(thrownType))));
			}

			SuperMethodInvocation invocation= ast.newSuperMethodInvocation();
			invocation.setName(ast.newSimpleName(suppressed.name()));
			Block methodBody= ast.newBlock();
			methodBody.statements().add(ast.newExpressionStatement(invocation));
			method.setBody(methodBody);
			body.insertLast(method, group);
		}

		imports.removeImport(ORG_JUNIT_RUNWITH);
		imports.removeImport(CUSTOM_BASE_RUNNER);
		imports.removeImport(IGNORE_INHERITED_TESTS);
	}

	private static String verifyRunnerAndFilterContract(ITypeBinding runnerBinding) {
		if (!hasDirectSuperclass(runnerBinding, BLOCK_JUNIT4_CLASS_RUNNER)) {
			return "CustomBaseRunner no longer directly extends BlockJUnit4ClassRunner."; //$NON-NLS-1$
		}
		TypeDeclaration runner= sourceDeclaration(runnerBinding);
		if (runner == null || runner.getFields().length != 0 || runner.getTypes().length != 0) {
			return "CustomBaseRunner must be editable source without fields or nested types."; //$NON-NLS-1$
		}
		MethodDeclaration[] methods= runner.getMethods();
		if (methods.length != 1 || !methods[0].isConstructor()) {
			return "CustomBaseRunner must contain only its constructor."; //$NON-NLS-1$
		}
		MethodDeclaration constructor= methods[0];
		Block body= constructor.getBody();
		if (body == null || body.statements().size() != 2
				|| !(body.statements().get(0) instanceof SuperConstructorInvocation)
				|| !(body.statements().get(1) instanceof TryStatement tryStatement)) {
			return "CustomBaseRunner constructor no longer has the supported super-plus-filter shape."; //$NON-NLS-1$
		}
		if (tryStatement.getFinally() != null || !tryStatement.resources().isEmpty()
				|| tryStatement.getBody().statements().size() != 1
				|| !(tryStatement.getBody().statements().get(0) instanceof ExpressionStatement expressionStatement)
				|| !(expressionStatement.getExpression() instanceof MethodInvocation filterCall)
				|| !"filter".equals(filterCall.getName().getIdentifier()) //$NON-NLS-1$
				|| filterCall.arguments().size() != 1
				|| !(filterCall.arguments().get(0) instanceof ClassInstanceCreation creation)) {
			return "CustomBaseRunner no longer installs exactly one source filter."; //$NON-NLS-1$
		}
		ITypeBinding filterBinding= erasure(creation.resolveTypeBinding());
		if (filterBinding == null || !INHERITED_TESTS_FILTER.equals(filterBinding.getQualifiedName())) {
			return "CustomBaseRunner no longer installs InheritedTestsFilter."; //$NON-NLS-1$
		}
		for (Object catchObject : tryStatement.catchClauses()) {
			CatchClause catchClause= (CatchClause) catchObject;
			if (catchClause.getBody().statements().size() != 1
					|| !(catchClause.getBody().statements().get(0) instanceof ThrowStatement)) {
				return "CustomBaseRunner catch handling contains additional behavior."; //$NON-NLS-1$
			}
		}
		return verifyFilterContract(filterBinding);
	}

	private static String verifyFilterContract(ITypeBinding filterBinding) {
		if (!hasDirectSuperclass(filterBinding, FILTER_TYPE)) {
			return "InheritedTestsFilter no longer directly extends JUnit 4 Filter."; //$NON-NLS-1$
		}
		TypeDeclaration filter= sourceDeclaration(filterBinding);
		if (filter == null || filter.getFields().length != 0 || filter.getTypes().length != 0) {
			return "InheritedTestsFilter must be editable source without fields or nested types."; //$NON-NLS-1$
		}
		MethodDeclaration shouldRun= null;
		for (MethodDeclaration method : filter.getMethods()) {
			if (!method.isConstructor() && "shouldRun".equals(method.getName().getIdentifier())) { //$NON-NLS-1$
				shouldRun= method;
			}
		}
		if (shouldRun == null || shouldRun.parameters().size() != 1 || shouldRun.getBody() == null) {
			return "InheritedTestsFilter.shouldRun(Description) is missing."; //$NON-NLS-1$
		}
		IMethodBinding shouldRunBinding= shouldRun.resolveBinding();
		if (shouldRunBinding == null || shouldRunBinding.getParameterTypes().length != 1
				|| !DESCRIPTION_TYPE.equals(erasureName(shouldRunBinding.getParameterTypes()[0]))) {
			return "InheritedTestsFilter.shouldRun no longer accepts JUnit Description."; //$NON-NLS-1$
		}

		List<?> statements= shouldRun.getBody().statements();
		if (statements.size() != 4
				|| !(statements.get(0) instanceof VariableDeclarationStatement classDeclaration)
				|| !(statements.get(1) instanceof VariableDeclarationStatement methodDeclaration)
				|| !(statements.get(2) instanceof IfStatement ifStatement)
				|| !(statements.get(3) instanceof ReturnStatement finalReturn)
				|| !isBooleanLiteral(finalReturn.getExpression(), true)) {
			return "InheritedTestsFilter.shouldRun no longer has the supported four-statement contract."; //$NON-NLS-1$
		}

		Variable classVariable= variableInitializedBy(classDeclaration, "getTestClass"); //$NON-NLS-1$
		Variable methodVariable= variableInitializedBy(methodDeclaration, "getMethodName"); //$NON-NLS-1$
		if (classVariable == null || methodVariable == null
				|| !"java.lang.Class".equals(erasureName(classVariable.type())) //$NON-NLS-1$
				|| !"java.lang.String".equals(erasureName(methodVariable.type()))) { //$NON-NLS-1$
			return "InheritedTestsFilter no longer derives class and method name from Description."; //$NON-NLS-1$
		}
		if (!isMarkerCondition(ifStatement.getExpression(), classVariable.name())
				|| ifStatement.getElseStatement() != null
				|| !(ifStatement.getThenStatement() instanceof Block thenBlock)
				|| thenBlock.statements().size() != 1
				|| !(thenBlock.statements().get(0) instanceof TryStatement tryStatement)
				|| !isDeclaredMethodReturn(tryStatement, classVariable.name(), methodVariable.name())) {
			return "InheritedTestsFilter no longer excludes methods not declared by the annotated subclass."; //$NON-NLS-1$
		}
		return null;
	}

	private static Variable variableInitializedBy(VariableDeclarationStatement declaration, String methodName) {
		if (declaration.fragments().size() != 1
				|| !(declaration.fragments().get(0) instanceof VariableDeclarationFragment fragment)
				|| !(fragment.getInitializer() instanceof MethodInvocation invocation)
				|| !methodName.equals(invocation.getName().getIdentifier())) {
			return null;
		}
		return new Variable(fragment.getName().getIdentifier(), declaration.getType().resolveBinding());
	}

	private static boolean isMarkerCondition(Expression expression, String classVariable) {
		if (!(expression instanceof MethodInvocation invocation)
				|| !"isAnnotationPresent".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| !(invocation.getExpression() instanceof SimpleName receiver)
				|| !classVariable.equals(receiver.getIdentifier())
				|| invocation.arguments().size() != 1
				|| !(invocation.arguments().get(0) instanceof TypeLiteral markerLiteral)) {
			return false;
		}
		return IGNORE_INHERITED_TESTS.equals(erasureName(markerLiteral.getType().resolveBinding()));
	}

	private static boolean isDeclaredMethodReturn(TryStatement tryStatement, String classVariable,
			String methodVariable) {
		if (tryStatement.getFinally() != null || !tryStatement.resources().isEmpty()
				|| tryStatement.getBody().statements().size() != 1
				|| !(tryStatement.getBody().statements().get(0) instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof InfixExpression comparison)
				|| comparison.getOperator() != InfixExpression.Operator.NOT_EQUALS
				|| !(comparison.getLeftOperand() instanceof MethodInvocation getDeclaredMethod)
				|| !(comparison.getRightOperand() instanceof NullLiteral)
				|| !"getDeclaredMethod".equals(getDeclaredMethod.getName().getIdentifier()) //$NON-NLS-1$
				|| !(getDeclaredMethod.getExpression() instanceof SimpleName receiver)
				|| !classVariable.equals(receiver.getIdentifier())
				|| getDeclaredMethod.arguments().size() != 1
				|| !(getDeclaredMethod.arguments().get(0) instanceof SimpleName methodName)
				|| !methodVariable.equals(methodName.getIdentifier())
				|| tryStatement.catchClauses().isEmpty()) {
			return false;
		}
		for (Object catchObject : tryStatement.catchClauses()) {
			CatchClause catchClause= (CatchClause) catchObject;
			if (catchClause.getBody().statements().size() != 1
					|| !(catchClause.getBody().statements().get(0) instanceof ReturnStatement catchReturn)
					|| !isBooleanLiteral(catchReturn.getExpression(), false)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isBooleanLiteral(Expression expression, boolean expected) {
		return expression instanceof BooleanLiteral literal && literal.booleanValue() == expected;
	}

	private static MethodCollection collectSuppressedMethods(ITypeBinding type) {
		Set<String> shadowedSignatures= Arrays.stream(type.getDeclaredMethods())
				.map(JdtUiInheritedTestsRunnerMigration::signature)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		List<SuppressedMethod> methods= new ArrayList<>();
		for (ITypeBinding superType= erasure(type.getSuperclass()); superType != null;
				superType= erasure(superType.getSuperclass())) {
			for (IMethodBinding method : superType.getDeclaredMethods()) {
				IMethodBinding declaration= method.getMethodDeclaration();
				if (!shadowedSignatures.add(signature(declaration)) || !isTestMethod(declaration)) {
					continue;
				}
				int modifiers= declaration.getModifiers();
				if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
						|| Modifier.isFinal(modifiers) || Modifier.isAbstract(modifiers)
						|| Modifier.isSynchronized(modifiers) || Modifier.isStrictfp(modifiers)
						|| Modifier.isNative(modifiers) || declaration.isSynthetic()
						|| declaration.getParameterTypes().length != 0
						|| declaration.getTypeParameters().length != 0
						|| !"void".equals(declaration.getReturnType().getName())) { //$NON-NLS-1$
					return new MethodCollection(List.of(),
							"Inherited test " + declaration.getDeclaringClass().getQualifiedName() + '#' //$NON-NLS-1$
									+ declaration.getName()
									+ " cannot be hidden by a semantics-preserving public delegating override."); //$NON-NLS-1$
				}
				List<String> thrownTypes= Arrays.stream(declaration.getExceptionTypes())
						.map(JdtUiInheritedTestsRunnerMigration::erasureName)
						.filter(name -> name != null && !name.isBlank())
						.toList();
				methods.add(new SuppressedMethod(declaration.getName(), thrownTypes));
			}
		}
		return new MethodCollection(methods, null);
	}

	private static boolean isTestMethod(IMethodBinding method) {
		for (IAnnotationBinding annotation : method.getAnnotations()) {
			String name= erasureName(annotation.getAnnotationType());
			if (ORG_JUNIT_TEST.equals(name) || ORG_JUNIT_JUPITER_TEST.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static String signature(IMethodBinding method) {
		return method.getName() + Arrays.stream(method.getParameterTypes())
				.map(JdtUiInheritedTestsRunnerMigration::erasureName)
				.collect(Collectors.joining(",", "(", ")")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static SingleMemberAnnotation runWithAnnotation(TypeDeclaration type) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof SingleMemberAnnotation annotation
					&& ORG_JUNIT_RUNWITH.equals(annotationName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	private static Annotation declaredAnnotation(TypeDeclaration type, String qualifiedName) {
		for (Object modifier : type.modifiers()) {
			if (modifier instanceof Annotation annotation && qualifiedName.equals(annotationName(annotation))) {
				return annotation;
			}
		}
		return null;
	}

	private static String annotationName(Annotation annotation) {
		ITypeBinding binding= erasure(annotation.resolveTypeBinding());
		return binding == null ? annotation.getTypeName().getFullyQualifiedName() : binding.getQualifiedName();
	}

	private static boolean isRuntimeTypeMarker(ITypeBinding type) {
		return type != null
				&& hasAnnotation(type, INHERITED_META_ANNOTATION)
				&& hasEnumAnnotationValue(type, RETENTION_META_ANNOTATION, RETENTION_POLICY, "RUNTIME") //$NON-NLS-1$
				&& hasEnumAnnotationValue(type, TARGET_META_ANNOTATION, ELEMENT_TYPE, "TYPE"); //$NON-NLS-1$
	}

	private static boolean hasAnnotation(ITypeBinding type, String qualifiedName) {
		for (IAnnotationBinding annotation : type.getAnnotations()) {
			if (qualifiedName.equals(erasureName(annotation.getAnnotationType()))) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasEnumAnnotationValue(ITypeBinding type, String annotationType,
			String enumType, String constantName) {
		for (IAnnotationBinding annotation : type.getAnnotations()) {
			if (!annotationType.equals(erasureName(annotation.getAnnotationType()))) {
				continue;
			}
			for (IMemberValuePairBinding pair : annotation.getDeclaredMemberValuePairs()) {
				if ("value".equals(pair.getName()) //$NON-NLS-1$
						&& containsEnumConstant(pair.getValue(), enumType, constantName)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsEnumConstant(Object value, String enumType, String constantName) {
		if (value instanceof Object[] values) {
			return Arrays.stream(values).anyMatch(candidate -> containsEnumConstant(candidate, enumType, constantName));
		}
		return value instanceof IVariableBinding variable
				&& variable.isEnumConstant()
				&& constantName.equals(variable.getName())
				&& enumType.equals(erasureName(variable.getDeclaringClass()));
	}

	private static boolean hasDirectTestMethod(ITypeBinding type) {
		return Arrays.stream(type.getDeclaredMethods())
				.map(IMethodBinding::getMethodDeclaration)
				.anyMatch(JdtUiInheritedTestsRunnerMigration::isTestMethod);
	}

	private static boolean hasDirectSuperclass(ITypeBinding type, String qualifiedName) {
		ITypeBinding superclass= erasure(type.getSuperclass());
		return superclass != null && qualifiedName.equals(superclass.getQualifiedName());
	}

	private static TypeDeclaration sourceDeclaration(ITypeBinding binding) {
		IJavaElement element= erasure(binding).getJavaElement();
		if (!(element instanceof IType sourceType)) {
			return null;
		}
		ICompilationUnit unit= sourceType.getCompilationUnit();
		if (unit == null) {
			return null;
		}
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(unit.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		parser.setStatementsRecovery(false);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		String expectedKey= erasure(binding).getKey();
		String expectedName= erasure(binding).getQualifiedName();
		TypeDeclaration[] result= new TypeDeclaration[1];
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding candidate= erasure(node.resolveBinding());
				if (candidate != null && (expectedKey.equals(candidate.getKey())
						|| expectedName.equals(candidate.getQualifiedName()))) {
					result[0]= node;
					return false;
				}
				return result[0] == null;
			}
		});
		return result[0];
	}

	private static ITypeBinding erasure(ITypeBinding binding) {
		return binding == null ? null : binding.getErasure();
	}

	private static String erasureName(ITypeBinding binding) {
		ITypeBinding erased= erasure(binding);
		return erased == null ? null : erased.getQualifiedName();
	}
}
