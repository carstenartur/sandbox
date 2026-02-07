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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTestAnnotationParameterPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @Test(expected=...) to JUnit 5 assertThrows().
 * 
 * Transforms:
 * <pre>
 * {@literal @}Test(expected = IllegalArgumentException.class)
 * public void testException() {
 *     // code that throws
 * }
 * </pre>
 * 
 * To:
 * <pre>
 * {@literal @}Test
 * public void testException() {
 *     assertThrows(IllegalArgumentException.class, () -> {
 *         // code that throws
 *     });
 * }
 * </pre>
 */
public class TestExpectedJUnitPlugin extends AbstractTestAnnotationParameterPlugin {

	@Override
	protected String getParameterName() {
		return "expected";
	}

	@Override
	protected boolean validateParameter(MemberValuePair pair) {
		// Only process TypeLiteral values (e.g., Exception.class)
		return pair.getValue() instanceof TypeLiteral;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		NormalAnnotation testAnnotation = (NormalAnnotation) junitHolder.getAnnotation();
		MemberValuePair expectedPair = (MemberValuePair) junitHolder.getAdditionalInfo();
		
		if (expectedPair == null) {
			return;
		}
		
		Expression expectedValue = expectedPair.getValue();
		if (!(expectedValue instanceof TypeLiteral)) {
			// Can't handle non-TypeLiteral expected values
			return;
		}
		
		TypeLiteral expectedTypeLiteral = (TypeLiteral) expectedValue;
		
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
		TypeLiteral exceptionClass = (TypeLiteral) ASTNode.copySubtree(ast, expectedTypeLiteral);
		assertThrowsCall.arguments().add(exceptionClass);
		
		// Create lambda expression for the method body
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);
		
		Block lambdaBody = ast.newBlock();
		
		// Copy all statements from the original method body into the lambda
		for (Statement stmt : statements) {
			Statement copiedStmt = (Statement) ASTNode.copySubtree(ast, stmt);
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
		
		// Remove the expected parameter from @Test annotation
		// If expected is the only parameter remaining, replace with marker annotation
		@SuppressWarnings("unchecked")
		List<MemberValuePair> testValues = testAnnotation.values();
		
		// Count how many parameters will remain after removing expected
		// (need to account for other parameters that might be removed by other plugins like timeout)
		int remainingParams = 0;
		for (MemberValuePair pair : testValues) {
			String paramName = pair.getName().getIdentifier();
			// Count parameters that are not expected and not timeout (which is handled by TestTimeoutJUnitPlugin)
			if (!"expected".equals(paramName) && !"timeout".equals(paramName)) {
				remainingParams++;
			}
		}
		
		if (remainingParams == 0) {
			// No other meaningful parameters remain, convert to marker annotation @Test
			MarkerAnnotation markerTestAnnotation = ast.newMarkerAnnotation();
			markerTestAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST));
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			rewriter.remove(expectedPair, group);
		}
		
		// Add imports - order matters: remove old import first, then add new imports
		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHOD_ASSERT_THROWS, false);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import static org.junit.jupiter.api.Assertions.assertThrows;
					
					import org.junit.jupiter.api.Test;
					
					@Test
					public void testException() {
						assertThrows(IllegalArgumentException.class, () -> {
							throw new IllegalArgumentException("Expected");
						});
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Test;
				
				@Test(expected = IllegalArgumentException.class)
				public void testException() {
					throw new IllegalArgumentException("Expected");
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "TestExpected"; //$NON-NLS-1$
	}
}
