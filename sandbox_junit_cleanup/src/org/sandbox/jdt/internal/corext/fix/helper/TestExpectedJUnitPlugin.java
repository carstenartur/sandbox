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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate @Test(expected=Exception.class) to assertThrows().
 * Demonstrates TriggerPattern with placeholder bindings.
 */
public class TestExpectedJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected List<Pattern> getPatterns() {
		return List.of(
			// Matches @Test(expected=SomeException.class)
			new Pattern("@Test(expected=$exceptionType)", PatternKind.ANNOTATION, ORG_JUNIT_TEST)
		);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		
		Annotation testAnnotation = junitHolder.getAnnotation();
		
		// Get the exception type from placeholder binding
		TypeLiteral exceptionType = (TypeLiteral) junitHolder.getBinding("$exceptionType");
		
		if (exceptionType == null) {
			return;
		}
		
		// Get the method declaration
		MethodDeclaration method = ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		if (method == null) {
			return;
		}
		
		Block methodBody = method.getBody();
		if (methodBody == null) {
			return;
		}
		
		@SuppressWarnings("unchecked")
		List<Statement> statements = methodBody.statements();
		
		// Create assertThrows method invocation
		MethodInvocation assertThrowsCall = ast.newMethodInvocation();
		assertThrowsCall.setName(ast.newSimpleName(METHOD_ASSERT_THROWS));
		
		// Add the exception class as the first argument
		TypeLiteral exceptionClass = (TypeLiteral) ASTNodes.copySubtree(ast, exceptionType);
		assertThrowsCall.arguments().add(exceptionClass);
		
		// Create lambda expression for the method body
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);
		
		Block lambdaBody = ast.newBlock();
		
		// Copy all statements from the original method body into the lambda
		for (Statement stmt : statements) {
			Statement copiedStmt = (Statement) ASTNodes.copySubtree(ast, stmt);
			lambdaBody.statements().add(copiedStmt);
		}
		
		lambda.setBody(lambdaBody);
		assertThrowsCall.arguments().add(lambda);
		
		// Create the new expression statement with assertThrows
		ExpressionStatement assertThrowsStatement = ast.newExpressionStatement(assertThrowsCall);
		
		// Remove all existing statements from the method body
		for (int i = statements.size() - 1; i >= 0; i--) {
			rewriter.remove(statements.get(i), group);
		}
		
		// Add the assertThrows statement as the only statement in the method
		rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(assertThrowsStatement, group);
		
		// Replace @Test(expected=...) with @Test
		MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST));
		ASTNodes.replaceButKeepComment(rewriter, testAnnotation, newAnnotation, group);
		
		// Update imports
		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHOD_ASSERT_THROWS, false);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@Test
				public void testException() {
					assertThrows(IllegalArgumentException.class, () -> {
						throw new IllegalArgumentException();
					});
				}
				"""; //$NON-NLS-1$
		}
		return """
			@Test(expected = IllegalArgumentException.class)
			public void testException() {
				throw new IllegalArgumentException();
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Test(expected)"; //$NON-NLS-1$
	}
}
