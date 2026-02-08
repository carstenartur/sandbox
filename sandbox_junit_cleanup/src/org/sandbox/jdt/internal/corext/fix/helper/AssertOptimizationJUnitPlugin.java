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
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Optimizes JUnit assertions by converting generic assertions to more specific ones
 * and correcting parameter order (expected/actual).
 * 
 * Examples:
 * - assertTrue(a == b) → assertEquals(a, b)
 * - assertTrue(obj == null) → assertNull(obj)
 * - assertTrue(!condition) → assertFalse(condition)
 * - assertTrue(a.equals(b)) → assertEquals(a, b)
 * - assertEquals(getActual(), EXPECTED) → assertEquals(EXPECTED, getActual())
 */
public class AssertOptimizationJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * Assertion methods that have expected/actual parameter order.
	 * First parameter should be expected (constant/literal), second should be actual (computed).
	 */
	private static final Set<String> METHODS_WITH_EXPECTED_ACTUAL = Set.of(
		"assertEquals",
		"assertNotEquals",
		"assertArrayEquals",
		"assertSame",
		"assertNotSame",
		"assertIterableEquals",  // JUnit 5 only
		"assertLinesMatch"       // JUnit 5 only
	);

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		
		// NOTE: We only process JUnit 5 (Assertions) calls here.
		// JUnit 4 (Assert) calls are handled by AssertJUnitPlugin which does migration.
		// The optimization for JUnit 4 assertions should be done within the migration itself.
		
		// Find assertTrue and assertFalse calls for optimization (JUnit 5)
		HelperVisitor.forMethodCalls(ORG_JUNIT_JUPITER_API_ASSERTIONS, Set.of("assertTrue", "assertFalse"))
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof MethodInvocation mi) {
					boolean isTrue = "assertTrue".equals(mi.getName().getIdentifier());
					return processAssertion(fixcore, operations, visited, aholder, isTrue);
				}
				return true;
			});
		
		// Find assertion calls with expected/actual parameters for parameter order correction (JUnit 5)
		HelperVisitor.forMethodCalls(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHODS_WITH_EXPECTED_ACTUAL)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
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
		
		// Get the condition expression (may be first or second argument depending on whether message is present)
		Expression condition = null;
		
		if (arguments.size() == 1) {
			condition = (Expression) arguments.get(0);
		} else if (arguments.size() == 2) {
			// Check if first argument is String (message), otherwise it's condition
			Expression firstArg = (Expression) arguments.get(0);
			ITypeBinding firstArgType = firstArg.resolveTypeBinding();
			if (firstArgType != null && "java.lang.String".equals(firstArgType.getQualifiedName())) {
				// First arg is message, second is condition
				condition = (Expression) arguments.get(1);
			} else {
				condition = firstArg;
			}
		}
		
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
		
		if (!(node instanceof MethodInvocation)) {
			return true; // Continue processing other nodes
		}
		
		MethodInvocation mi = (MethodInvocation) node;
		List<?> arguments = mi.arguments();
		
		// Need at least 2 arguments (expected, actual) or 3 with message
		if (arguments.size() < 2) {
			return true; // Continue processing other nodes
		}
		
		// Get first two arguments (they might be expected/actual or message/expected depending on JUnit version)
		Expression first = (Expression) arguments.get(0);
		Expression second = (Expression) arguments.get(1);
		
		// Check if first argument is a String message (JUnit 4 style)
		ITypeBinding firstType = first.resolveTypeBinding();
		boolean firstIsMessage = firstType != null && "java.lang.String".equals(firstType.getQualifiedName());
		
		Expression expectedParam;
		Expression actualParam;
		
		if (firstIsMessage && arguments.size() >= 3) {
			// JUnit 4: message, expected, actual
			expectedParam = (Expression) arguments.get(1);
			actualParam = (Expression) arguments.get(2);
		} else {
			// JUnit 5: expected, actual [, message]
			expectedParam = first;
			actualParam = second;
		}
		
		// If expected is not constant but actual is constant, we need to swap
		if (!isConstantExpression(expectedParam) && isConstantExpression(actualParam)) {
			return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
		}
		
		return true; // Continue processing other nodes
	}

	/**
	 * Determines if an expression is a constant value.
	 * Constants include literals, final fields, enums, class literals, array literals,
	 * and collection factory methods with constant arguments.
	 */
	private boolean isConstantExpression(Expression expr) {
		if (expr == null) {
			return false;
		}
		
		// Literals
		if (expr instanceof NumberLiteral || expr instanceof StringLiteral || 
			expr instanceof BooleanLiteral || expr instanceof CharacterLiteral ||
			expr instanceof NullLiteral || expr instanceof TypeLiteral) {
			return true;
		}
		
		// Final fields and enum constants
		if (expr instanceof SimpleName) {
			SimpleName name = (SimpleName) expr;
			IVariableBinding binding = (IVariableBinding) name.resolveBinding();
			if (binding != null && binding.isField()) {
				int modifiers = binding.getModifiers();
				return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) || binding.isEnumConstant();
			}
		}
		
		// Qualified names (e.g., MyClass.CONSTANT)
		if (expr instanceof QualifiedName) {
			QualifiedName qname = (QualifiedName) expr;
			IVariableBinding binding = (IVariableBinding) qname.resolveBinding();
			if (binding != null && binding.isField()) {
				int modifiers = binding.getModifiers();
				return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) || binding.isEnumConstant();
			}
		}
		
		// Field access (e.g., Status.ACTIVE)
		if (expr instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess) expr;
			IVariableBinding binding = fieldAccess.resolveFieldBinding();
			if (binding != null) {
				int modifiers = binding.getModifiers();
				return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) || binding.isEnumConstant();
			}
		}
		
		// Array creation with initializer containing only constants
		if (expr instanceof ArrayCreation) {
			ArrayCreation arrayCreation = (ArrayCreation) expr;
			ArrayInitializer initializer = arrayCreation.getInitializer();
			if (initializer != null) {
				List<?> expressions = initializer.expressions();
				return expressions.stream().allMatch(e -> isConstantExpression((Expression) e));
			}
		}
		
		// Array initializer
		if (expr instanceof ArrayInitializer) {
			ArrayInitializer initializer = (ArrayInitializer) expr;
			List<?> expressions = initializer.expressions();
			return expressions.stream().allMatch(e -> isConstantExpression((Expression) e));
		}
		
		// Collection factory methods: List.of(...), Set.of(...), Arrays.asList(...), Map.of(...)
		if (expr instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) expr;
			String methodName = mi.getName().getIdentifier();
			Expression receiver = mi.getExpression();
			
			if (methodName.equals("of") || methodName.equals("asList")) {
				if (receiver instanceof SimpleName) {
					String receiverName = ((SimpleName) receiver).getIdentifier();
					if (receiverName.equals("List") || receiverName.equals("Set") || 
						receiverName.equals("Arrays") || receiverName.equals("Map")) {
						List<?> arguments = mi.arguments();
						return arguments.stream().allMatch(arg -> isConstantExpression((Expression) arg));
					}
				}
			}
			
			// Method call on string literal: "test".getBytes()
			if (receiver instanceof StringLiteral) {
				return true;
			}
		}
		
		return false;
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
			return "equals".equals(methodInv.getName().getIdentifier()) && methodInv.arguments().size() == 1;
		}
		
		return false;
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
		
		// Get the condition and message
		Expression condition = null;
		Expression message = null;
		
		if (arguments.size() == 1) {
			condition = (Expression) arguments.get(0);
		} else if (arguments.size() == 2) {
			Expression firstArg = (Expression) arguments.get(0);
			ITypeBinding firstArgType = firstArg.resolveTypeBinding();
			if (firstArgType != null && "java.lang.String".equals(firstArgType.getQualifiedName())) {
				message = firstArg;
				condition = (Expression) arguments.get(1);
			} else {
				condition = firstArg;
				message = (Expression) arguments.get(1);
			}
		}
		
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
					
					// Create new argument list
					argsRewrite.remove((ASTNode) condition, group);
					if (message != null) {
						argsRewrite.insertLast(rewriter.createCopyTarget(nonNullExpr), group);
					} else {
						argsRewrite.insertFirst(rewriter.createCopyTarget(nonNullExpr), group);
					}
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
	 * Swaps expected/actual parameters if they are in the wrong order.
	 * Expected (constant) should come first, actual (computed) should come second.
	 */
	private void swapParametersIfNeeded(MethodInvocation mi, ASTRewrite rewriter, TextEditGroup group) {
		List<?> arguments = mi.arguments();
		
		if (arguments.size() < 2) {
			return;
		}
		
		Expression first = (Expression) arguments.get(0);
		Expression second = (Expression) arguments.get(1);
		
		// Check if first argument is a String message (JUnit 4 style)
		ITypeBinding firstType = first.resolveTypeBinding();
		boolean firstIsMessage = firstType != null && "java.lang.String".equals(firstType.getQualifiedName());
		
		Expression expectedParam;
		Expression actualParam;
		int expectedIndex;
		int actualIndex;
		
		if (firstIsMessage && arguments.size() >= 3) {
			// JUnit 4: message, expected, actual
			expectedParam = (Expression) arguments.get(1);
			actualParam = (Expression) arguments.get(2);
			expectedIndex = 1;
			actualIndex = 2;
		} else {
			// JUnit 5: expected, actual [, message]
			expectedParam = first;
			actualParam = second;
			expectedIndex = 0;
			actualIndex = 1;
		}
		
		// If expected is not constant but actual is constant, swap them
		if (!isConstantExpression(expectedParam) && isConstantExpression(actualParam)) {
			ListRewrite argsRewrite = rewriter.getListRewrite(mi, MethodInvocation.ARGUMENTS_PROPERTY);
			Expression newExpected = (Expression) rewriter.createCopyTarget(actualParam);
			Expression newActual = (Expression) rewriter.createCopyTarget(expectedParam);
			argsRewrite.replace((ASTNode) arguments.get(expectedIndex), newExpected, group);
			argsRewrite.replace((ASTNode) arguments.get(actualIndex), newActual, group);
		}
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
