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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
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
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 ErrorCollector rule to JUnit 5 assertAll.
 * 
 * Transforms:
 * - collector.checkThat(actual, matcher) → () -> assertThat(actual, matcher)
 * - collector.addError(throwable) → () -> { throw throwable; }
 * - collector.checkSucceeds(callable) → () -> callable.call()
 * 
 * All transformations are wrapped in assertAll() per test method.
 */
public class RuleErrorCollectorJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		HelperVisitorFactory.forField()
			.withAnnotation(ORG_JUNIT_RULE)
			.ofType(ORG_JUNIT_RULES_ERROR_COLLECTOR)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> processFoundNode(fixcore, operations, (FieldDeclaration) visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			// Return true to continue processing other fields
			return true;
		}
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding != null && ORG_JUNIT_RULES_ERROR_COLLECTOR.equals(binding.getQualifiedName())) {
			JunitHolder mh = new JunitHolder();
			mh.setMinv(node);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		// Return true to continue processing other fields
		return true;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration field = junitHolder.getFieldDeclaration();
		TypeDeclaration parentClass = ASTNodes.getParent(field, TypeDeclaration.class);

		VariableDeclarationFragment originalFragment = (VariableDeclarationFragment) field.fragments().get(0);
		String fieldName = originalFragment.getName().getIdentifier();

		// Remove the field declaration
		rewriter.remove(field, group);

		// Remove old imports
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_ERROR_COLLECTOR);

		// Add new imports
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertAll", false);

		// Transform all test methods that use the ErrorCollector field
		for (MethodDeclaration method : parentClass.getMethods()) {
			transformTestMethod(method, fieldName, rewriter, ast, group, importRewriter);
		}
	}

	private void transformTestMethod(MethodDeclaration method, String fieldName, ASTRewrite rewriter, AST ast,
			TextEditGroup group, ImportRewrite importRewriter) {
		Block methodBody = method.getBody();
		if (methodBody == null) {
			return;
		}

		List<Statement> statements = methodBody.statements();
		if (statements.isEmpty()) {
			return;
		}

		// Find all ErrorCollector method invocations in this method
		List<ErrorCollectorCall> errorCollectorCalls = findErrorCollectorCalls(statements, fieldName);

		if (errorCollectorCalls.isEmpty()) {
			// This method doesn't use the ErrorCollector field
			return;
		}

		// Create assertAll() call with lambda expressions for each error collector call
		MethodInvocation assertAllCall = ast.newMethodInvocation();
		assertAllCall.setName(ast.newSimpleName("assertAll"));

		// Create lambda expressions for each ErrorCollector call
		for (ErrorCollectorCall call : errorCollectorCalls) {
			LambdaExpression lambda = createLambdaForErrorCollectorCall(call, ast, importRewriter);
			assertAllCall.arguments().add(lambda);
		}

		// Create the new assertAll statement
		ExpressionStatement assertAllStatement = ast.newExpressionStatement(assertAllCall);

		// Remove all old ErrorCollector calls
		for (int i = errorCollectorCalls.size() - 1; i >= 0; i--) {
			ErrorCollectorCall call = errorCollectorCalls.get(i);
			rewriter.remove(call.statement, group);
		}

		// Insert the assertAll statement where the first ErrorCollector call was
		if (!errorCollectorCalls.isEmpty()) {
			ErrorCollectorCall firstCall = errorCollectorCalls.get(0);
			int insertIndex = statements.indexOf(firstCall.statement);
			rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertAt(assertAllStatement, insertIndex, group);
		}
	}

	private List<ErrorCollectorCall> findErrorCollectorCalls(List<Statement> statements, String fieldName) {
		List<ErrorCollectorCall> calls = new ArrayList<>();
		
		// Use ASTVisitor to find all ErrorCollector calls, including nested ones
		for (Statement stmt : statements) {
			stmt.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation invocation) {
					Expression expression = invocation.getExpression();
					if (expression instanceof SimpleName) {
						SimpleName receiver = (SimpleName) expression;
						if (fieldName.equals(receiver.getIdentifier())) {
							String methodName = invocation.getName().getIdentifier();
							if ("checkThat".equals(methodName) || "addError".equals(methodName) || "checkSucceeds".equals(methodName)) {
								// Find the parent statement that contains this invocation
								Statement parentStmt = findParentStatement(invocation);
								if (parentStmt != null) {
									calls.add(new ErrorCollectorCall(parentStmt, invocation, methodName));
								}
							}
						}
					}
					return super.visit(invocation);
				}
			});
		}

		return calls;
	}

	private Statement findParentStatement(ASTNode node) {
		ASTNode current = node;
		while (current != null && !(current instanceof Statement)) {
			current = current.getParent();
		}
		return (Statement) current;
	}

	private LambdaExpression createLambdaForErrorCollectorCall(ErrorCollectorCall call, AST ast, ImportRewrite importRewriter) {
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);

		MethodInvocation invocation = call.invocation;
		String methodName = call.methodName;

		if ("checkThat".equals(methodName)) {
			// checkThat(actual, matcher) → () -> assertThat(actual, matcher)
			// Use expression-body lambda for single-expression case
			
			// Create assertThat call with the same arguments
			MethodInvocation assertThatCall = ast.newMethodInvocation();
			assertThatCall.setName(ast.newSimpleName("assertThat"));
			
			// Copy arguments
			for (Object arg : invocation.arguments()) {
				assertThatCall.arguments().add(ASTNode.copySubtree(ast, (ASTNode) arg));
			}
			
			// Set expression body directly (no block)
			lambda.setBody(assertThatCall);
			
			// Add Hamcrest imports for assertThat
			importRewriter.addStaticImport("org.hamcrest.MatcherAssert", "assertThat", false);
		} else if ("addError".equals(methodName)) {
			// addError(throwable) → () -> { throw throwable; }
			// This requires a block body since throw is a statement, not an expression
			Block lambdaBody = ast.newBlock();
			
			ThrowStatement throwStmt = ast.newThrowStatement();
			// The argument is the throwable to throw
			Expression throwableArg = (Expression) invocation.arguments().get(0);
			throwStmt.setExpression((Expression) ASTNode.copySubtree(ast, throwableArg));
			
			lambdaBody.statements().add(throwStmt);
			lambda.setBody(lambdaBody);
		} else if ("checkSucceeds".equals(methodName)) {
			// checkSucceeds(callable) → () -> callable.call()
			// Use expression-body lambda for single-expression case
			
			// Create callable.call() invocation
			Expression callableArg = (Expression) invocation.arguments().get(0);
			MethodInvocation callInvocation = ast.newMethodInvocation();
			callInvocation.setExpression((Expression) ASTNode.copySubtree(ast, callableArg));
			callInvocation.setName(ast.newSimpleName("call"));
			
			// Set expression body directly (no block)
			lambda.setBody(callInvocation);
		}

		return lambda;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import static org.junit.jupiter.api.Assertions.assertAll;
					import static org.hamcrest.MatcherAssert.assertThat;
					import static org.hamcrest.CoreMatchers.equalTo;
					
					import org.junit.jupiter.api.Test;
					
					public class MyTest {
						@Test
						public void testMultipleErrors() {
							assertAll(
								() -> assertThat("value1", equalTo("expected1")),
								() -> assertThat("value2", equalTo("expected2")),
								() -> { throw new Throwable("error message"); }
							);
						}
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ErrorCollector;
				import static org.hamcrest.CoreMatchers.equalTo;
				
				public class MyTest {
					@Rule
					public ErrorCollector collector = new ErrorCollector();
					
					@Test
					public void testMultipleErrors() {
						collector.checkThat("value1", equalTo("expected1"));
						collector.checkThat("value2", equalTo("expected2"));
						collector.addError(new Throwable("error message"));
					}
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleErrorCollector"; //$NON-NLS-1$
	}

	/**
	 * Helper class to hold ErrorCollector call information
	 */
	private static class ErrorCollectorCall {
		final Statement statement;
		final MethodInvocation invocation;
		final String methodName;

		ErrorCollectorCall(Statement statement, MethodInvocation invocation, String methodName) {
			this.statement = statement;
			this.invocation = invocation;
			this.methodName = methodName;
		}
	}
}
