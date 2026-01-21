/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 ExpectedException rule to JUnit 5 assertThrows.
 */
public class RuleExpectedExceptionJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		HelperVisitor.callFieldDeclarationVisitor(ORG_JUNIT_RULE, ORG_JUNIT_RULES_EXPECTED_EXCEPTION, compilationUnit,
				dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh = new JunitHolder();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return false;
		}
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding != null && ORG_JUNIT_RULES_EXPECTED_EXCEPTION.equals(binding.getQualifiedName())) {
			mh.minv = node;
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return false;
	}

	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration field = junitHolder.getFieldDeclaration();
		TypeDeclaration parentClass = ASTNodes.getParent(field, TypeDeclaration.class);

		VariableDeclarationFragment originalFragment = (VariableDeclarationFragment) field.fragments().get(0);
		String fieldName = originalFragment.getName().getIdentifier();

		// Remove the field declaration
		rewriter.remove(field, group);

		// Remove old imports
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXPECTED_EXCEPTION);

		// Add new imports
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertThrows", false);

		// Transform all test methods that use the ExpectedException field
		for (MethodDeclaration method : parentClass.getMethods()) {
			transformTestMethod(method, fieldName, rewriter, ast, group, importRewriter, parentClass);
		}
	}

	private void transformTestMethod(MethodDeclaration method, String fieldName, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter, TypeDeclaration parentClass) {
		Block methodBody = method.getBody();
		if (methodBody == null) {
			return;
		}

		List<Statement> statements = methodBody.statements();
		if (statements.isEmpty()) {
			return;
		}

		// Find expect() and expectMessage() calls
		ExpectedExceptionInfo info = findExpectedExceptionCalls(statements, fieldName);

		if (info.expectCall == null) {
			// This method doesn't use the ExpectedException field
			return;
		}

		// Generate a unique variable name for the exception if we need to check the message or cause
		String exceptionVarName = null;
		if (info.expectMessageCall != null || info.expectCauseCall != null) {
			Collection<String> usedNames = getUsedVariableNames(method);
			exceptionVarName = generateUniqueVariableName("exception", usedNames);
		}

		// Create assertThrows call
		MethodInvocation assertThrowsCall = ast.newMethodInvocation();
		assertThrowsCall.setName(ast.newSimpleName("assertThrows"));

		// Add exception class as first argument
		Expression exceptionClass = (Expression) ASTNode.copySubtree(ast,
				(Expression) info.expectCall.arguments().get(0));
		assertThrowsCall.arguments().add(exceptionClass);

		// Create lambda with remaining statements
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);

		Block lambdaBody = ast.newBlock();

		// Copy all statements after the expect/expectMessage calls
		int startIndex = info.lastExpectStatementIndex + 1;
		if (startIndex >= statements.size()) {
			// Edge case: expect() is the last statement, no code to throw exception
			// This would create an empty lambda that never throws, causing test to fail
			// Skip transformation for this edge case
			return;
		}
		
		for (int i = startIndex; i < statements.size(); i++) {
			Statement stmt = statements.get(i);
			lambdaBody.statements().add(ASTNode.copySubtree(ast, stmt));
		}

		lambda.setBody(lambdaBody);
		assertThrowsCall.arguments().add(lambda);

		// Create the new statement
		Statement newStatement;
		if (exceptionVarName != null) {
			// Need to capture exception for message check
			// ExceptionType exceptionVar = assertThrows(ExceptionType.class, () -> { ... });
			VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
			fragment.setName(ast.newSimpleName(exceptionVarName));
			fragment.setInitializer(assertThrowsCall);

			VariableDeclarationStatement varDecl = ast.newVariableDeclarationStatement(fragment);
			// Extract the exception type name from the class literal
			String exceptionTypeName = extractExceptionTypeName(info.expectCall);
			varDecl.setType(ast.newSimpleType(ast.newName(exceptionTypeName)));

			newStatement = varDecl;
		} else {
			// No message check needed, just call assertThrows
			newStatement = ast.newExpressionStatement(assertThrowsCall);
		}

		// Remove old expect/expectMessage calls and statements after them
		for (int i = statements.size() - 1; i >= info.firstExpectStatementIndex; i--) {
			rewriter.remove(statements.get(i), group);
		}

		// Insert the new assertThrows statement
		rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(newStatement, group);

		// If there's a message expectation, add the assertion
		if (info.expectMessageCall != null && exceptionVarName != null) {
			Expression messageArg = (Expression) info.expectMessageCall.arguments().get(0);
			
			// Create: assertEquals("message", exception.getMessage());
			MethodInvocation getMessageCall = ast.newMethodInvocation();
			getMessageCall.setExpression(ast.newSimpleName(exceptionVarName));
			getMessageCall.setName(ast.newSimpleName("getMessage"));

			MethodInvocation assertEqualsCall = ast.newMethodInvocation();
			assertEqualsCall.setName(ast.newSimpleName("assertEquals"));
			assertEqualsCall.arguments().add(ASTNode.copySubtree(ast, messageArg));
			assertEqualsCall.arguments().add(getMessageCall);

			ExpressionStatement assertStatement = ast.newExpressionStatement(assertEqualsCall);
			rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(assertStatement, group);

			// Add assertEquals import
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertEquals", false);
		}
		
		// If there's a cause expectation, add the assertion
		if (info.expectCauseCall != null && exceptionVarName != null) {
			Expression causeArg = (Expression) info.expectCauseCall.arguments().get(0);
			Expression causeClass = extractCauseClass(causeArg);
			
			if (causeClass != null) {
				// Create: exception.getCause()
				MethodInvocation getCauseCall = ast.newMethodInvocation();
				getCauseCall.setExpression(ast.newSimpleName(exceptionVarName));
				getCauseCall.setName(ast.newSimpleName("getCause"));

				// Create: assertInstanceOf(CauseClass.class, exception.getCause());
				MethodInvocation assertInstanceOfCall = ast.newMethodInvocation();
				assertInstanceOfCall.setName(ast.newSimpleName("assertInstanceOf"));
				assertInstanceOfCall.arguments().add(ASTNode.copySubtree(ast, causeClass));
				assertInstanceOfCall.arguments().add(getCauseCall);

				ExpressionStatement assertStatement = ast.newExpressionStatement(assertInstanceOfCall);
				rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(assertStatement, group);

				// Add assertInstanceOf import
				importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertInstanceOf", false);
			}
		}
	}

	private ExpectedExceptionInfo findExpectedExceptionCalls(List<Statement> statements, String fieldName) {
		ExpectedExceptionInfo info = new ExpectedExceptionInfo();
		
		for (int i = 0; i < statements.size(); i++) {
			Statement stmt = statements.get(i);
			if (!(stmt instanceof ExpressionStatement)) {
				continue;
			}

			Expression expr = ((ExpressionStatement) stmt).getExpression();
			if (!(expr instanceof MethodInvocation)) {
				continue;
			}

			MethodInvocation invocation = (MethodInvocation) expr;
			Expression expression = invocation.getExpression();
			if (expression == null || !(expression instanceof SimpleName)) {
				continue;
			}

			SimpleName receiver = (SimpleName) expression;
			if (!fieldName.equals(receiver.getIdentifier())) {
				continue;
			}

			String methodName = invocation.getName().getIdentifier();
			if ("expect".equals(methodName)) {
				info.expectCall = invocation;
				if (info.firstExpectStatementIndex == -1) {
					info.firstExpectStatementIndex = i;
				}
				info.lastExpectStatementIndex = i;
			} else if ("expectMessage".equals(methodName)) {
				info.expectMessageCall = invocation;
				if (info.firstExpectStatementIndex == -1) {
					info.firstExpectStatementIndex = i;
				}
				info.lastExpectStatementIndex = i;
			} else if ("expectCause".equals(methodName)) {
				info.expectCauseCall = invocation;
				if (info.firstExpectStatementIndex == -1) {
					info.firstExpectStatementIndex = i;
				}
				info.lastExpectStatementIndex = i;
			}
		}

		return info;
	}

	private Expression extractCauseClass(Expression causeArg) {
		// Handle Hamcrest matchers like: org.hamcrest.Matchers.instanceOf(IllegalArgumentException.class)
		// or: org.hamcrest.Matchers.isA(IllegalArgumentException.class)
		if (causeArg instanceof MethodInvocation methodInv) {
			String methodName = methodInv.getName().getIdentifier();
			if (("instanceOf".equals(methodName) || "isA".equals(methodName)) && !methodInv.arguments().isEmpty()) {
				// Extract the class literal argument
				Expression arg = (Expression) methodInv.arguments().get(0);
				return arg;
			}
		}
		return null;
	}

	private String extractExceptionTypeName(MethodInvocation expectCall) {
		// The argument is typically a TypeLiteral like IllegalArgumentException.class
		if (!expectCall.arguments().isEmpty()) {
			Expression arg = (Expression) expectCall.arguments().get(0);
			
			// Use TypeLiteral API for robust type extraction
			if (arg instanceof TypeLiteral typeLiteral) {
				Type type = typeLiteral.getType();
				if (type != null) {
					ITypeBinding typeBinding = type.resolveBinding();
					if (typeBinding != null) {
						// Try qualified name first, fall back to simple name
						String qualifiedName = typeBinding.getQualifiedName();
						if (qualifiedName != null && !qualifiedName.isEmpty()) {
							return qualifiedName;
						}
						String name = typeBinding.getName();
						if (name != null && !name.isEmpty()) {
							return name;
						}
					}
					// Fallback: use the type's string representation
					return type.toString();
				}
			}
			
			// Fallback for non-TypeLiteral expressions
			String argStr = arg.toString();
			if (argStr.endsWith(".class")) {
				return argStr.substring(0, argStr.length() - ".class".length());
			}
		}
		return "Exception";
	}

	private String generateUniqueVariableName(String baseName, Collection<String> usedNames) {
		if (!usedNames.contains(baseName)) {
			return baseName;
		}
		int counter = 1;
		String candidateName;
		do {
			candidateName = baseName + counter;
			counter++;
		} while (usedNames.contains(candidateName));
		return candidateName;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import static org.junit.jupiter.api.Assertions.assertEquals;
					import static org.junit.jupiter.api.Assertions.assertThrows;
					
					import org.junit.jupiter.api.Test;
					
					public class MyTest {
						@Test
						public void testException() {
							IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
								throw new IllegalArgumentException("Invalid argument");
							});
							assertEquals("Invalid argument", exception.getMessage());
						}
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;
				
				public class MyTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();
					
					@Test
					public void testException() {
						thrown.expect(IllegalArgumentException.class);
						thrown.expectMessage("Invalid argument");
						throw new IllegalArgumentException("Invalid argument");
					}
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleExpectedException"; //$NON-NLS-1$
	}

	private static class ExpectedExceptionInfo {
		MethodInvocation expectCall;
		MethodInvocation expectMessageCall;
		MethodInvocation expectCauseCall;
		int firstExpectStatementIndex = -1;
		int lastExpectStatementIndex = -1;
	}
}
