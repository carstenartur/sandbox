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
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Plugin to migrate JUnit 4 {@code @Test(expected=...)} to assertThrows(). */
@CleanupPattern(value = "@Test(expected=$ex)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_TEST, cleanupId = "cleanup.junit.test.expected", description = "Migrate @Test(expected=...) to assertThrows()", displayName = "JUnit 4 @Test(expected) → JUnit 5 assertThrows()")
public class TestExpectedJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		ASTNode node = match.getMatchedNode();
		if (!(node instanceof NormalAnnotation)) {
			return null;
		}
		NormalAnnotation annotation = (NormalAnnotation) node;
		MemberValuePair expectedPair = null;
		for (Object obj : annotation.values()) {
			MemberValuePair pair = (MemberValuePair) obj;
			String name= pair.getName().getIdentifier();
			if ("timeout".equals(name)) { //$NON-NLS-1$
				return null;
			}
			if ("expected".equals(name)) { //$NON-NLS-1$
				expectedPair = pair;
			}
		}
		if (expectedPair == null || !(expectedPair.getValue() instanceof TypeLiteral)) {
			return null;
		}
		JunitHolder holder = new JunitHolder();
		holder.setMinv(annotation);
		holder.setAdditionalInfo(expectedPair);
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		NormalAnnotation testAnnotation = (NormalAnnotation) junitHolder.getAnnotation();
		MemberValuePair expectedPair = (MemberValuePair) junitHolder.getAdditionalInfo();
		if (expectedPair == null || !(expectedPair.getValue() instanceof TypeLiteral expectedTypeLiteral)) {
			return;
		}
		MethodDeclaration method = ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		if (method == null || method.getBody() == null) {
			return;
		}
		Block methodBody = method.getBody();
		List<Statement> statements = methodBody.statements();
		MethodInvocation assertThrowsCall = ast.newMethodInvocation();
		assertThrowsCall.setName(ast.newSimpleName(METHOD_ASSERT_THROWS));
		TypeLiteral exceptionClass = (TypeLiteral) ASTNode.copySubtree(ast, expectedTypeLiteral);
		assertThrowsCall.arguments().add(exceptionClass);
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);
		Block lambdaBody = ast.newBlock();
		for (Statement stmt : statements) {
			Statement copiedStmt = (Statement) ASTNode.copySubtree(ast, stmt);
			lambdaBody.statements().add(copiedStmt);
		}
		lambda.setBody(lambdaBody);
		assertThrowsCall.arguments().add(lambda);
		ExpressionStatement assertThrowsStatement = ast.newExpressionStatement(assertThrowsCall);
		for (int i = statements.size() - 1; i >= 0; i--) {
			rewriter.remove(statements.get(i), group);
		}
		rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY).insertLast(assertThrowsStatement, group);
		List<MemberValuePair> testValues = testAnnotation.values();
		int remainingParams = 0;
		for (MemberValuePair pair : testValues) {
			if (!"expected".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
				remainingParams++;
			}
		}
		if (remainingParams == 0) {
			MarkerAnnotation markerTestAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_TEST);
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			rewriter.remove(expectedPair, group);
		}
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
