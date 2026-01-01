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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
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
public class TestExpectedJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		HelperVisitor.callNormalAnnotationVisitor(ORG_JUNIT_TEST, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, NormalAnnotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		// Check if this @Test annotation has an "expected" parameter
		MemberValuePair expectedPair = null;
		Expression expectedValue = null;
		
		@SuppressWarnings("unchecked")
		List<MemberValuePair> values = node.values();
		for (MemberValuePair pair : values) {
			if ("expected".equals(pair.getName().getIdentifier())) {
				expectedPair = pair;
				expectedValue = pair.getValue();
				break;
			}
		}
		
		// Only process if we found an expected parameter
		if (expectedPair != null && expectedValue != null) {
			JunitHolder mh = new JunitHolder();
			mh.minv = node;
			mh.minvname = node.getTypeName().getFullyQualifiedName();
			mh.additionalInfo = expectedPair; // Store the expected pair for removal
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		
		return false;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		NormalAnnotation testAnnotation = (NormalAnnotation) junitHolder.getAnnotation();
		MemberValuePair expectedPair = (MemberValuePair) junitHolder.additionalInfo;
		
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
		// If expected is the only parameter, replace with marker annotation
		@SuppressWarnings("unchecked")
		List<MemberValuePair> testValues = testAnnotation.values();
		if (testValues.size() == 1 && testValues.get(0) == expectedPair) {
			// Only parameter is expected, so convert to marker annotation @Test
			MarkerAnnotation markerTestAnnotation = ast.newMarkerAnnotation();
			markerTestAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST));
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			// There are other parameters, just remove expected
			rewriter.remove(expectedPair, group);
		}
		
		// Update imports
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
