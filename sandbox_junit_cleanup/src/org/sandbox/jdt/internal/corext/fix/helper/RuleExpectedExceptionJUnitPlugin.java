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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2025 hammer
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

import java.util.ArrayList;
import java.util.Collection;
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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Plugin to migrate JUnit 4 ExpectedException rule to JUnit 5 assertThrows.
 * <p>
 * Transforms patterns like:
 * <pre>
 * &#64;Rule
 * public ExpectedException thrown = ExpectedException.none();
 * 
 * &#64;Test
 * public void test() {
 *     thrown.expect(MyException.class);
 *     thrown.expectMessage("error");
 *     doSomething();
 * }
 * </pre>
 * to:
 * <pre>
 * &#64;Test
 * public void test() {
 *     MyException exception = assertThrows(MyException.class, () -> doSomething());
 *     assertEquals("error", exception.getMessage());
 * }
 * </pre>
 * </p>
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
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding != null && ORG_JUNIT_RULES_EXPECTED_EXCEPTION.equals(binding.getQualifiedName())) {
			mh.minv = node;
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return false;
	}

	@Override
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration field = junitHolder.getFieldDeclaration();
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) field.fragments().get(0);
		String fieldName = fragment.getName().getIdentifier();
		
		// Remove the field declaration
		rewriter.remove(field, group);
		
		// Remove JUnit 4 imports
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_EXPECTED_EXCEPTION);
		
		// Find the parent class to process test methods
		TypeDeclaration parentClass = ASTNodes.getParent(field, TypeDeclaration.class);
		if (parentClass == null) {
			return;
		}
		
		// Track whether we need static imports
		boolean needsAssertThrows = false;
		boolean needsAssertTrue = false;
		
		// First pass: check if any methods use ExpectedException to determine imports needed
		for (MethodDeclaration method : parentClass.getMethods()) {
			Block body = method.getBody();
			if (body == null) {
				continue;
			}
			
			ExpectedExceptionCollector collector = new ExpectedExceptionCollector(fieldName);
			body.accept(collector);
			
			if (collector.hasExpectations()) {
				needsAssertThrows = true;
				if (collector.expectedMessage != null) {
					needsAssertTrue = true;
				}
			}
			
			// Early exit once we know we need both imports
			if (needsAssertThrows && needsAssertTrue) {
				break;
			}
		}
		
		// Add JUnit 5 static imports only if needed
		if (needsAssertThrows) {
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertThrows", false);
		}
		if (needsAssertTrue) {
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assertTrue", false);
		}
		
		// Process each test method to find and transform ExpectedException usage
		for (MethodDeclaration method : parentClass.getMethods()) {
			processTestMethod(method, fieldName, rewriter, ast, group);
		}
	}

	/**
	 * Processes a test method to find and transform ExpectedException usage.
	 * 
	 * @param method the test method to process
	 * @param fieldName the name of the ExpectedException field
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 */
	private void processTestMethod(MethodDeclaration method, String fieldName, ASTRewrite rewriter, AST ast,
			TextEditGroup group) {
		Block body = method.getBody();
		if (body == null) {
			return;
		}

		ExpectedExceptionCollector collector = new ExpectedExceptionCollector(fieldName);
		body.accept(collector);

		if (!collector.hasExpectations()) {
			return;
		}

		// Find the remaining statements after expect/expectMessage
		List<Statement> remainingStatements = getRemainingStatements(body, collector.expectStatements);
		
		if (remainingStatements.isEmpty()) {
			return;
		}

		// Create assertThrows wrapper - this will handle removing all statements and adding new ones
		createAssertThrowsWrapper(remainingStatements, collector, rewriter, ast, group, body);
	}

	/**
	 * Gets the statements that should be wrapped in assertThrows.
	 * 
	 * @param body the method body
	 * @param expectStatements the expect/expectMessage statements to exclude
	 * @return list of statements to wrap
	 */
	@SuppressWarnings("unchecked")
	private List<Statement> getRemainingStatements(Block body, List<Statement> expectStatements) {
		List<Statement> remaining = new ArrayList<>();
		for (Object obj : body.statements()) {
			Statement stmt = (Statement) obj;
			if (!expectStatements.contains(stmt)) {
				remaining.add(stmt);
			}
		}
		return remaining;
	}

	/**
	 * Creates the assertThrows wrapper around the remaining statements.
	 * 
	 * @param statements the statements to wrap (non-expect statements)
	 * @param collector the collector with exception expectations
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param methodBody the method body containing the statements
	 */
	@SuppressWarnings("unchecked")
	private void createAssertThrowsWrapper(List<Statement> statements, ExpectedExceptionCollector collector,
			ASTRewrite rewriter, AST ast, TextEditGroup group, Block methodBody) {
		
		// Create lambda expression with the code that should throw
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(false);
		
		Block lambdaBody = ast.newBlock();
		for (Statement stmt : statements) {
			lambdaBody.statements().add(ASTNode.copySubtree(ast, stmt));
		}
		lambda.setBody(lambdaBody);
		
		// Create assertThrows method invocation (use assertThrows by default)
		MethodInvocation assertThrows = ast.newMethodInvocation();
		assertThrows.setName(ast.newSimpleName("assertThrows"));
		
		// Add exception class argument
		if (collector.expectedException != null) {
			assertThrows.arguments().add(ASTNode.copySubtree(ast, collector.expectedException));
		} else {
			// Default to Exception.class if no specific exception was expected
			TypeLiteral exceptionClass = ast.newTypeLiteral();
			exceptionClass.setType(ast.newSimpleType(ast.newSimpleName("Exception")));
			assertThrows.arguments().add(exceptionClass);
		}
		
		// Add lambda as second argument
		assertThrows.arguments().add(lambda);
		
		// Get list rewrite for method body
		ListRewrite listRewrite = rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY);
		
		// Remove expect/expectMessage statements
		for (Statement stmt : collector.expectStatements) {
			listRewrite.remove(stmt, group);
		}
		
		// Remove original statements (that will be wrapped in lambda)
		for (Statement stmt : statements) {
			listRewrite.remove(stmt, group);
		}
		
		// Add new statements to method body
		if (collector.expectedMessage != null) {
			// Need to capture exception to check message
			// Generate a unique variable name to avoid conflicts
			Collection<String> usedNames = getUsedVariableNames(methodBody);
			String exceptionVarName = "exception";
			int counter = 1;
			while (usedNames.contains(exceptionVarName)) {
				exceptionVarName = "exception" + counter;
				counter++;
			}
			
			VariableDeclarationFragment varFragment = ast.newVariableDeclarationFragment();
			varFragment.setName(ast.newSimpleName(exceptionVarName));
			varFragment.setInitializer(assertThrows);
			
			org.eclipse.jdt.core.dom.VariableDeclarationStatement varDecl = ast.newVariableDeclarationStatement(varFragment);
			
			// Determine exception type from expectedException
			String exceptionType = getExceptionTypeName(collector.expectedException);
			varDecl.setType(ast.newSimpleType(ast.newSimpleName(exceptionType)));
			
			// Add message assertion
			MethodInvocation messageAssertion = createMessageAssertion(ast, exceptionVarName, collector.expectedMessage);
			
			// Add new statements
			listRewrite.insertLast(varDecl, group);
			listRewrite.insertLast(ast.newExpressionStatement(messageAssertion), group);
		} else {
			// No message check needed, just wrap in assertThrows
			listRewrite.insertLast(ast.newExpressionStatement(assertThrows), group);
		}
	}

	/**
	 * Gets the exception type name from a class literal expression.
	 * 
	 * @param classLiteral the class literal expression (e.g., MyException.class)
	 * @return the exception type name
	 */
	private String getExceptionTypeName(Expression classLiteral) {
		if (classLiteral instanceof org.eclipse.jdt.core.dom.TypeLiteral) {
			org.eclipse.jdt.core.dom.TypeLiteral typeLiteral = (org.eclipse.jdt.core.dom.TypeLiteral) classLiteral;
			return typeLiteral.getType().toString();
		}
		return "Exception";
	}

	/**
	 * Creates an assertion to check the exception message.
	 * 
	 * @param ast the AST instance
	 * @param exceptionVarName the name of the exception variable
	 * @param expectedMessage the expected message expression
	 * @return the assertion method invocation
	 */
	@SuppressWarnings("unchecked")
	private MethodInvocation createMessageAssertion(AST ast, String exceptionVarName, Expression expectedMessage) {
		MethodInvocation assertion = ast.newMethodInvocation();
		assertion.setName(ast.newSimpleName("assertTrue"));
		
		// Create: exception.getMessage().contains(expectedMessage)
		MethodInvocation getMessage = ast.newMethodInvocation();
		getMessage.setExpression(ast.newSimpleName(exceptionVarName));
		getMessage.setName(ast.newSimpleName("getMessage"));
		
		MethodInvocation contains = ast.newMethodInvocation();
		contains.setExpression(getMessage);
		contains.setName(ast.newSimpleName("contains"));
		contains.arguments().add(ASTNode.copySubtree(ast, expectedMessage));
		
		assertion.arguments().add(contains);
		
		return assertion;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Test
					public void test() {
						IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
							doSomething();
						});
						assertTrue(exception.getMessage().contains("Invalid argument"));
					}
					"""; //$NON-NLS-1$
		}
		return """
				@Rule
				public ExpectedException thrown = ExpectedException.none();

				@Test
				public void test() {
					thrown.expect(IllegalArgumentException.class);
					thrown.expectMessage("Invalid argument");
					doSomething();
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleExpectedException"; //$NON-NLS-1$
	}

	/**
	 * Visitor to collect ExpectedException expectations from a test method.
	 */
	private static class ExpectedExceptionCollector extends ASTVisitor {
		private final String fieldName;
		private Expression expectedException;
		private Expression expectedMessage;
		private final List<Statement> expectStatements = new ArrayList<>();

		ExpectedExceptionCollector(String fieldName) {
			this.fieldName = fieldName;
		}

		@Override
		public boolean visit(ExpressionStatement node) {
			Expression expr = node.getExpression();
			if (expr instanceof MethodInvocation) {
				MethodInvocation invocation = (MethodInvocation) expr;
				Expression receiver = invocation.getExpression();
				
				if (receiver instanceof SimpleName) {
					SimpleName name = (SimpleName) receiver;
					if (fieldName.equals(name.getIdentifier())) {
						String methodName = invocation.getName().getIdentifier();
						
						if ("expect".equals(methodName) && !invocation.arguments().isEmpty()) {
							expectedException = (Expression) invocation.arguments().get(0);
							expectStatements.add(node);
						} else if ("expectMessage".equals(methodName) && !invocation.arguments().isEmpty()) {
							expectedMessage = (Expression) invocation.arguments().get(0);
							expectStatements.add(node);
						}
					}
				}
			}
			return super.visit(node);
		}

		boolean hasExpectations() {
			return !expectStatements.isEmpty();
		}
	}
}
