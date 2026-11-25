/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.LibStandardNames;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Optimizes JUnit assertions by converting generic assertions to more specific
 * ones and correcting parameter order (expected/actual).
 * 
 * Examples: - assertTrue(a == b) → assertEquals(a, b) - assertTrue(obj == null)
 * → assertNull(obj) - assertTrue(!condition) → assertFalse(condition) -
 * assertTrue(a.equals(b)) → assertEquals(a, b) - assertEquals(getActual(),
 * EXPECTED) → assertEquals(EXPECTED, getActual())
 */
public class AssertOptimizationJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * Assertion methods that have expected/actual parameter order. First parameter
	 * should be expected (constant/literal), second should be actual (computed).
	 */
	// assertLinesMatch interprets only its expected lines as patterns or skip markers.
	private static final Set<String> METHODS_WITH_EXPECTED_ACTUAL = Set.of("assertEquals", "assertNotEquals", //$NON-NLS-1$ //$NON-NLS-2$
			"assertArrayEquals", "assertSame", "assertNotSame", "assertIterableEquals"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private static final Set<String> SYMMETRIC_VALUE_TYPES= Set.of(LibStandardNames.JAVA_LANG_STRING,
			LibStandardNames.JAVA_LANG_INTEGER, LibStandardNames.JAVA_LANG_LONG, LibStandardNames.JAVA_LANG_DOUBLE,
			"java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short", "java.lang.Float", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"java.lang.Class"); //$NON-NLS-1$

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();

		// NOTE: We only process JUnit 5 (Assertions) calls here.
		// JUnit 4 (Assert) calls are handled by AssertJUnitPlugin which does migration.
		// The optimization for JUnit 4 assertions should be done within the migration
		// itself.

		// Find assertTrue and assertFalse calls for optimization (JUnit 5)
		HelperVisitorFactory.forMethodCalls(ORG_JUNIT_JUPITER_API_ASSERTIONS, Set.of("assertTrue", "assertFalse"))
				.in(compilationUnit).excluding(nodesprocessed).processEach(dataHolder, (visited, aholder) -> {
					if (visited instanceof MethodInvocation mi) {
						boolean isTrue = "assertTrue".equals(mi.getName().getIdentifier());
						return processAssertion(fixcore, operations, visited, aholder, isTrue);
					}
					return true;
				});

		// Find assertion calls with expected/actual parameters for parameter order
		// correction (JUnit 5)
		HelperVisitorFactory.forMethodCalls(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHODS_WITH_EXPECTED_ACTUAL)
				.in(compilationUnit).excluding(nodesprocessed).processEach(dataHolder, (visited, aholder) -> {
					if (visited instanceof MethodInvocation) {
						return processParameterOrder(fixcore, operations, visited, aholder);
					}
					return true;
				});
	}

	private boolean processAssertion(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, boolean isTrue) {

		if (!(node instanceof MethodInvocation)) {
			return true; // Continue processing other nodes
		}

		MethodInvocation mi = (MethodInvocation) node;
		List<?> arguments = mi.arguments();

		if (arguments.isEmpty()) {
			return true; // Continue processing other nodes
		}

		// This helper only visits Jupiter assertions, whose condition comes first.
		Expression condition= arguments.size() <= 2 ? (Expression) arguments.get(0) : null;

		if (condition == null || !canOptimize(condition)) {
			return true; // Continue processing other nodes
		}

		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
	}

	/**
	 * Processes assertion method calls to check if parameters need to be swapped.
	 * Swaps parameters if the second parameter is a constant but the first is not.
	 */
	private boolean processParameterOrder(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		if (node instanceof MethodInvocation invocation && canSwapArguments(invocation)) {
			return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
		}
		return true;
	}

	private static boolean canSwapArguments(MethodInvocation invocation) {
		List<?> arguments= invocation.arguments();
		if (arguments.size() < 2) {
			return false;
		}
		String name= invocation.getName().getIdentifier();
		boolean identityAssertion= "assertSame".equals(name) || "assertNotSame".equals(name); //$NON-NLS-1$ //$NON-NLS-2$
		Expression expected= (Expression) arguments.get(0);
		Expression actual= (Expression) arguments.get(1);
		return !isStableArgument(expected, identityAssertion) && isStableArgument(actual, identityAssertion)
				&& (identityAssertion || hasSymmetricComparison(name, expected, actual));
	}

	private static boolean hasSymmetricComparison(String method, Expression expected, Expression actual) {
		// Jupiter dispatches equals on the expected value, including array and iterable elements.
		return switch (method) {
		case "assertArrayEquals" -> isSymmetricArray(expected.resolveTypeBinding()) && isSymmetricArray(actual.resolveTypeBinding()); //$NON-NLS-1$
		case "assertIterableEquals" -> isSymmetricValue(standardListElement(expected)) && isSymmetricValue(standardListElement(actual)); //$NON-NLS-1$
		default -> isSymmetricValue(expected.resolveTypeBinding()) && isSymmetricValue(actual.resolveTypeBinding());
		};
	}

	private static boolean isSymmetricArray(ITypeBinding type) {
		return type != null && type.isArray() && isSymmetricValue(type.getElementType());
	}

	private static boolean isSymmetricValue(ITypeBinding type) {
		return type != null && !type.isRecovered() && (type.isPrimitive() || type.isNullType() || type.isEnum()
				|| SYMMETRIC_VALUE_TYPES.contains(type.getQualifiedName())
				|| "java.lang.Class".equals(type.getErasure().getQualifiedName())); //$NON-NLS-1$
	}

	private static ITypeBinding standardListElement(Expression expression) {
		if (!(ASTNodes.getUnparenthesedExpression(expression) instanceof MethodInvocation invocation)) {
			return null;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (!isStandardListFactory(invocation, binding)) {
			// Iterable<T> alone says nothing about observable iterator callbacks. A nonvirtual
			// local helper with a sole JDK factory return has the same known implementation.
			if (binding == null || binding.isRecovered()
					|| !(Modifier.isPrivate(binding.getModifiers()) || Modifier.isStatic(binding.getModifiers()))
					|| !(invocation.getRoot() instanceof CompilationUnit root)
					|| !(root.findDeclaringNode(binding.getMethodDeclaration()) instanceof MethodDeclaration declaration)
					|| declaration.getBody() == null || declaration.getBody().statements().size() != 1
					|| !(declaration.getBody().statements().getFirst() instanceof ReturnStatement result)
					|| !(result.getExpression() instanceof MethodInvocation factory)) {
				return null;
			}
			binding= factory.resolveMethodBinding();
			if (!isStandardListFactory(factory, binding)) {
				return null;
			}
		}
		ITypeBinding[] arguments= binding.getReturnType().getTypeArguments();
		return arguments.length == 1 ? arguments[0] : null;
	}

	private static boolean isStableArgument(Expression expression, boolean identityAssertion) {
		if (expression instanceof ParenthesizedExpression parentheses) {
			return isStableArgument(parentheses.getExpression(), identityAssertion);
		}
		if (expression instanceof NullLiteral || expression instanceof TypeLiteral || isCompileTimeConstant(expression)) {
			return true;
		}
		if (identityAssertion && expression instanceof SimpleName name && isLocalStaticFinalReference(name)) {
			return true;
		}
		if (expression instanceof ArrayCreation array && array.getInitializer() != null) {
			return isStableArgument(array.getInitializer(), false);
		}
		if (expression instanceof ArrayInitializer initializer) {
			List<?> elements= initializer.expressions();
			return elements.stream().allMatch(element -> isStableArgument((Expression) element, false));
		}
		return expression instanceof MethodInvocation invocation && isStableLibraryCall(invocation);
	}

	private static boolean isCompileTimeConstant(Expression expression) {
		// A field access through an expression can evaluate its receiver even for a constant field.
		return !(expression instanceof FieldAccess || expression instanceof SuperFieldAccess)
				&& (!(expression instanceof QualifiedName name) || name.getQualifier().resolveBinding() instanceof ITypeBinding)
				&& expression.resolveConstantExpressionValue() != null;
	}

	private static boolean isLocalStaticFinalReference(SimpleName name) {
		if (!(name.resolveBinding() instanceof IVariableBinding field) || field.isRecovered() || !field.isField()
				|| !Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) {
			return false;
		}
		// Reading a final reference from one of its own class's methods does not initialize another class.
		MethodDeclaration method= ASTNodes.getParent(name, MethodDeclaration.class);
		var binding= method == null ? null : method.resolveBinding();
		return binding != null && !binding.isRecovered() && binding.getDeclaringClass().isEqualTo(field.getDeclaringClass());
	}

	private static boolean isStableLibraryCall(MethodInvocation invocation) {
		var binding= invocation.resolveMethodBinding();
		if (binding == null || binding.isRecovered()) {
			return false;
		}
		List<?> arguments= invocation.arguments();
		if (invocation.getExpression() instanceof StringLiteral) {
			return LibStandardNames.JAVA_LANG_STRING.equals(binding.getDeclaringClass().getQualifiedName())
					&& "getBytes".equals(binding.getName()) && arguments.isEmpty(); //$NON-NLS-1$
		}
		return isStandardListFactory(invocation, binding)
				&& arguments.stream().allMatch(argument -> isCompileTimeConstant((Expression) argument));
	}

	private static boolean isStandardListFactory(MethodInvocation invocation, IMethodBinding binding) {
		if (binding == null || binding.isRecovered() || !(invocation.getExpression() instanceof Name qualifier)
				|| !(qualifier.resolveBinding() instanceof ITypeBinding)) {
			return false;
		}
		String owner= binding.getMethodDeclaration().getDeclaringClass().getQualifiedName();
		String method= binding.getName();
		return ("java.util.List".equals(owner) && "of".equals(method)) //$NON-NLS-1$ //$NON-NLS-2$
				|| ("java.util.Arrays".equals(owner) && "asList".equals(method)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private boolean canOptimize(Expression condition) {
		// Check for prefix expression (!condition)
		if (condition instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) condition;
			return prefix.getOperator() == PrefixExpression.Operator.NOT;
		}

		// Check for infix expression (==, !=)
		if (condition instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression) condition;
			InfixExpression.Operator op = infix.getOperator();
			return op == InfixExpression.Operator.EQUALS || op == InfixExpression.Operator.NOT_EQUALS;
		}

		// Check for .equals() method call
		if (condition instanceof MethodInvocation) {
			MethodInvocation methodInv = (MethodInvocation) condition;
			return LibStandardNames.METHOD_EQUALS.equals(methodInv.getName().getIdentifier()) && methodInv.arguments().size() == 1;
		}

		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore fixcore, ReferenceHolder<Integer, JunitHolder> hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		MethodInvocation invocation= hit.get(hit.size() - 1).getMethodInvocation();
		super.rewrite(fixcore, hit, cuRewrite, group);
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		SimpleName replacement= (SimpleName) rewrite.get(invocation, MethodInvocation.NAME_PROPERTY);
		if (invocation.getExpression() != null || replacement.getIdentifier().equals(invocation.getName().getIdentifier())) {
			return;
		}
		ImportRewrite imports= cuRewrite.getImportRewrite();
		String method= replacement.getIdentifier();
		String reference= imports.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, method, false,
				new ContextSensitiveImportRewriteContext(invocation, imports));
		int separator= reference.lastIndexOf('.');
		if (separator >= 0) {
			rewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY,
					invocation.getAST().newName(reference.substring(0, separator)), group);
		} else {
			cuRewrite.getImportRemover().registerAddedStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, method, false);
		}
		// Retain the original static import when an unchanged call still uses it.
		cuRewrite.getImportRemover().registerRemovedNode(invocation.getName());
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (!(junitHolder.getMinv() instanceof MethodInvocation)) {
			return;
		}

		MethodInvocation mi = junitHolder.getMethodInvocation();
		List<?> arguments = mi.arguments();

		if (arguments.isEmpty()) {
			return;
		}

		String methodName = mi.getName().getIdentifier();

		// Check if this is a parameter order correction case
		if (METHODS_WITH_EXPECTED_ACTUAL.contains(methodName)) {
			swapParametersIfNeeded(mi, rewriter, group);
			return;
		}

		// Handle assertTrue/assertFalse optimization
		boolean isTrue = "assertTrue".equals(methodName);

		Expression condition= (Expression) arguments.get(0);
		Expression message= arguments.size() == 2 ? (Expression) arguments.get(1) : null;

		if (condition == null) {
			return;
		}

		// Handle prefix expression: assertTrue(!x) → assertFalse(x)
		if (condition instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) condition;
			if (prefix.getOperator() == PrefixExpression.Operator.NOT) {
				// Flip assertTrue/assertFalse and remove negation
				String newMethodName = isTrue ? "assertFalse" : "assertTrue";
				rewriter.set(mi, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(newMethodName), group);

				// Replace condition with the operand (removing the !)
				ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);
				argsRewrite.replace((ASTNode) condition, rewriter.createCopyTarget(prefix.getOperand()), group);
			}
			return;
		}

		// Handle infix expression: ==, !=
		if (condition instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression) condition;
			InfixExpression.Operator op = infix.getOperator();
			Expression left = infix.getLeftOperand();
			Expression right = infix.getRightOperand();

			// Check for null comparisons
			boolean leftIsNull = left instanceof NullLiteral;
			boolean rightIsNull = right instanceof NullLiteral;

			if (leftIsNull || rightIsNull) {
				Expression nonNullExpr = leftIsNull ? right : left;
				String newMethodName = null;

				if (op == InfixExpression.Operator.EQUALS) {
					// assertTrue(x == null) → assertNull(x)
					// assertFalse(x == null) → assertNotNull(x)
					newMethodName = isTrue ? "assertNull" : "assertNotNull";
				} else if (op == InfixExpression.Operator.NOT_EQUALS) {
					// assertTrue(x != null) → assertNotNull(x)
					// assertFalse(x != null) → assertNull(x)
					newMethodName = isTrue ? "assertNotNull" : "assertNull";
				}

				if (newMethodName != null) {
					rewriter.set(mi, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(newMethodName), group);
					ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);

					argsRewrite.replace(condition, rewriter.createCopyTarget(nonNullExpr), group);
				}
			} else {
				// Handle equality checks between non-null values
				boolean isPrimitiveComparison = isPrimitiveComparison(left, right);
				String newMethodName = null;

				if (op == InfixExpression.Operator.EQUALS) {
					if (isPrimitiveComparison) {
						// assertTrue(a == b) → assertEquals(a, b)
						// assertFalse(a == b) → assertNotEquals(a, b)
						newMethodName = isTrue ? "assertEquals" : "assertNotEquals";
					} else {
						// Objects: assertTrue(obj1 == obj2) → assertSame(obj1, obj2)
						// assertFalse(obj1 == obj2) → assertNotSame(obj1, obj2)
						newMethodName = isTrue ? "assertSame" : "assertNotSame";
					}
				} else if (op == InfixExpression.Operator.NOT_EQUALS) {
					if (isPrimitiveComparison) {
						// assertTrue(a != b) → assertNotEquals(a, b)
						// assertFalse(a != b) → assertEquals(a, b)
						newMethodName = isTrue ? "assertNotEquals" : "assertEquals";
					} else {
						// Objects: assertTrue(obj1 != obj2) → assertNotSame(obj1, obj2)
						// assertFalse(obj1 != obj2) → assertSame(obj1, obj2)
						newMethodName = isTrue ? "assertNotSame" : "assertSame";
					}
				}

				if (newMethodName != null) {
					rewriter.set(mi, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(newMethodName), group);
					ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);

					// Replace condition with two separate arguments
					argsRewrite.remove((ASTNode) condition, group);
					if (message != null) {
						// Message comes last in JUnit 5
						argsRewrite.insertBefore(rewriter.createCopyTarget(left), (ASTNode) message, group);
						argsRewrite.insertBefore(rewriter.createCopyTarget(right), (ASTNode) message, group);
					} else {
						argsRewrite.insertFirst(rewriter.createCopyTarget(left), group);
						argsRewrite.insertLast(rewriter.createCopyTarget(right), group);
					}
				}
			}
			return;
		}

		// Handle .equals() method call
		if (condition instanceof MethodInvocation) {
			MethodInvocation methodInv = (MethodInvocation) condition;
			if ("equals".equals(methodInv.getName().getIdentifier()) && methodInv.arguments().size() == 1) {
				Expression receiver = methodInv.getExpression();
				Expression argument = (Expression) methodInv.arguments().get(0);

				// assertTrue(a.equals(b)) → assertEquals(b, a)
				// assertFalse(a.equals(b)) → assertNotEquals(b, a)
				String newMethodName = isTrue ? "assertEquals" : "assertNotEquals";
				rewriter.set(mi, MethodInvocation.NAME_PROPERTY, ast.newSimpleName(newMethodName), group);

				ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);
				argsRewrite.remove((ASTNode) condition, group);

				if (message != null) {
					// JUnit 5: expected, actual, message
					argsRewrite.insertBefore(rewriter.createCopyTarget(argument), (ASTNode) message, group);
					if (receiver != null) {
						argsRewrite.insertBefore(rewriter.createCopyTarget(receiver), (ASTNode) message, group);
					}
				} else {
					argsRewrite.insertFirst(rewriter.createCopyTarget(argument), group);
					if (receiver != null) {
						argsRewrite.insertLast(rewriter.createCopyTarget(receiver), group);
					}
				}
			}
		}
	}

	/**
	 * Swaps expected/actual parameters if they are in the wrong order. Expected
	 * (constant) should come first, actual (computed) should come second.
	 */
	private void swapParametersIfNeeded(MethodInvocation invocation, ASTRewrite rewriter, TextEditGroup group) {
		if (!canSwapArguments(invocation)) {
			return;
		}
		List<?> arguments= invocation.arguments();
		Expression expected= (Expression) arguments.get(0);
		Expression actual= (Expression) arguments.get(1);
		ListRewrite argsRewrite= rewriter.getListRewrite(invocation, MethodInvocation.ARGUMENTS_PROPERTY);
		argsRewrite.replace(expected, rewriter.createCopyTarget(actual), group);
		argsRewrite.replace(actual, rewriter.createCopyTarget(expected), group);
	}

	private boolean isPrimitiveComparison(Expression left, Expression right) {
		ITypeBinding leftType = left.resolveTypeBinding();
		ITypeBinding rightType = right.resolveTypeBinding();
		return (leftType != null && leftType.isPrimitive()) || (rightType != null && rightType.isPrimitive());
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assertions.assertEquals(5, result);
					Assertions.assertNull(obj);
					Assertions.assertFalse(condition);
					"""; //$NON-NLS-1$
		}
		return """
				Assertions.assertTrue(result == 5);
				Assertions.assertTrue(obj == null);
				Assertions.assertTrue(!condition);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "AssertOptimization"; //$NON-NLS-1$
	}
}
