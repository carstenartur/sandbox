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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Optimizes JUnit assertions by converting generic assertions to more specific ones.
 * 
 * Examples:
 * - assertTrue(a == b) → assertEquals(a, b)
 * - assertTrue(obj == null) → assertNull(obj)
 * - assertTrue(!condition) → assertFalse(condition)
 * - assertTrue(a.equals(b)) → assertEquals(a, b)
 */
public class AssertOptimizationJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		
		// Find assertTrue calls
		HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, "assertTrue", compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processAssertion(fixcore, operations, visited, aholder, true));
		HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertTrue", compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processAssertion(fixcore, operations, visited, aholder, true));
		
		// Find assertFalse calls
		HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, "assertFalse", compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processAssertion(fixcore, operations, visited, aholder, false));
		HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertFalse", compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processAssertion(fixcore, operations, visited, aholder, false));
	}

	private boolean processAssertion(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, boolean isTrue) {
		
		if (!(node instanceof MethodInvocation)) {
			return false;
		}
		
		MethodInvocation mi = (MethodInvocation) node;
		List<?> arguments = mi.arguments();
		
		if (arguments.isEmpty()) {
			return false;
		}
		
		// Get the condition expression (may be first or second argument depending on whether message is present)
		Expression condition = null;
		Expression message = null;
		
		if (arguments.size() == 1) {
			condition = (Expression) arguments.get(0);
		} else if (arguments.size() == 2) {
			// Check if first argument is String (message), otherwise it's condition
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
		
		if (condition == null || !canOptimize(condition)) {
			return false;
		}
		
		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
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
		if (!(junitHolder.minv instanceof MethodInvocation)) {
			return;
		}
		
		MethodInvocation mi = junitHolder.getMethodInvocation();
		List<?> arguments = mi.arguments();
		
		if (arguments.isEmpty()) {
			return;
		}
		
		// Determine if this is assertTrue or assertFalse
		String methodName = mi.getName().getIdentifier();
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
				if (message != null) {
					argsRewrite.replace((ASTNode) condition, rewriter.createCopyTarget(prefix.getOperand()), group);
				} else {
					argsRewrite.replace((ASTNode) condition, rewriter.createCopyTarget(prefix.getOperand()), group);
				}
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
			boolean leftIsNull = ASTNodes.isNullLiteral(left);
			boolean rightIsNull = ASTNodes.isNullLiteral(right);
			
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
