/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_TIMEOUT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.METHOD_ASSERT_THROWS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_ASSERTIONS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_TIMEOUT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_TEST;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_TEST;

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
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Coordinates migration of a JUnit 4 {@code @Test} annotation that declares
 * both {@code expected} and {@code timeout}.
 *
 * <p>A single operation must own the annotation because the ordinary timeout
 * and expected plugins deliberately share a processed-node set. Processing the
 * attributes independently would either skip one migration or schedule
 * conflicting replacements for the same annotation.</p>
 */
@CleanupPattern(value = "@Test(expected=$ex, timeout=$t)", kind = PatternKind.ANNOTATION,
		qualifiedType = ORG_JUNIT_TEST, cleanupId = "cleanup.junit.test.expected-timeout",
		description = "Migrate combined @Test(expected, timeout)",
		displayName = "JUnit 4 @Test(expected, timeout) → assertThrows() and @Timeout")
public class TestExpectedAndTimeoutJUnitPlugin extends TriggerPatternCleanupPlugin {

	private record Parameters(MemberValuePair expected, MemberValuePair timeout) {
	}

	@Override
	protected List<Pattern> getPatterns() {
		return List.of(
				new Pattern("@Test(expected=$ex, timeout=$t)", PatternKind.ANNOTATION, null, null, //$NON-NLS-1$
						ORG_JUNIT_TEST, null, null),
				new Pattern("@Test(timeout=$t, expected=$ex)", PatternKind.ANNOTATION, null, null, //$NON-NLS-1$
						ORG_JUNIT_TEST, null, null));
	}

	@Override
	protected JunitHolder createHolder(Match match) {
		if (!(match.getMatchedNode() instanceof NormalAnnotation annotation)) {
			return null;
		}
		MemberValuePair expected= pair(annotation, "expected"); //$NON-NLS-1$
		MemberValuePair timeout= pair(annotation, "timeout"); //$NON-NLS-1$
		if (expected == null || timeout == null || !(expected.getValue() instanceof TypeLiteral)
				|| !(timeout.getValue() instanceof NumberLiteral number) || !isLong(number.getToken())) {
			return null;
		}
		return new JunitHolder().setMinv(annotation).setAdditionalInfo(new Parameters(expected, timeout));
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		if (!(junitHolder.getAnnotation() instanceof NormalAnnotation testAnnotation)
				|| !(junitHolder.getAdditionalInfo() instanceof Parameters parameters)
				|| !(parameters.expected().getValue() instanceof TypeLiteral expectedType)
				|| !(parameters.timeout().getValue() instanceof NumberLiteral timeoutLiteral)) {
			return;
		}
		MethodDeclaration method= ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		Block methodBody= method == null ? null : method.getBody();
		if (methodBody == null) {
			return;
		}
		long timeoutMillis;
		try {
			timeoutMillis= Long.parseLong(timeoutLiteral.getToken());
		} catch (NumberFormatException exception) {
			return;
		}

		MethodInvocation assertThrows= ast.newMethodInvocation();
		assertThrows.setName(ast.newSimpleName(METHOD_ASSERT_THROWS));
		assertThrows.arguments().add(ASTNode.copySubtree(ast, expectedType));
		LambdaExpression lambda= ast.newLambdaExpression();
		lambda.setParentheses(true);
		Block lambdaBody= ast.newBlock();
		List<Statement> statements= methodBody.statements();
		for (Statement statement : statements) {
			lambdaBody.statements().add(ASTNode.copySubtree(ast, statement));
		}
		lambda.setBody(lambdaBody);
		assertThrows.arguments().add(lambda);
		ExpressionStatement assertion= ast.newExpressionStatement(assertThrows);
		ListRewrite bodyRewrite= rewriter.getListRewrite(methodBody, Block.STATEMENTS_PROPERTY);
		for (Statement statement : statements) {
			bodyRewrite.remove(statement, group);
		}
		bodyRewrite.insertLast(assertion, group);

		NormalAnnotation timeoutAnnotation= ast.newNormalAnnotation();
		timeoutAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TIMEOUT));
		MemberValuePair valuePair= ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value")); //$NON-NLS-1$
		boolean seconds= timeoutMillis >= 1000 && timeoutMillis % 1000 == 0;
		valuePair.setValue(ast.newNumberLiteral(Long.toString(seconds ? timeoutMillis / 1000 : timeoutMillis)));
		timeoutAnnotation.values().add(valuePair);
		MemberValuePair unitPair= ast.newMemberValuePair();
		unitPair.setName(ast.newSimpleName("unit")); //$NON-NLS-1$
		QualifiedName unit= ast.newQualifiedName(ast.newSimpleName("TimeUnit"), //$NON-NLS-1$
				ast.newSimpleName(seconds ? "SECONDS" : "MILLISECONDS")); //$NON-NLS-1$ //$NON-NLS-2$
		unitPair.setValue(unit);
		timeoutAnnotation.values().add(unitPair);
		ListRewrite modifiers= rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifiers.insertAfter(timeoutAnnotation, testAnnotation, group);

		MarkerAnnotation markerTest= AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_TEST);
		ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTest, group);

		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TIMEOUT);
		importRewriter.addImport("java.util.concurrent.TimeUnit"); //$NON-NLS-1$
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHOD_ASSERT_THROWS, false);
	}

	private static MemberValuePair pair(NormalAnnotation annotation, String name) {
		for (Object value : annotation.values()) {
			MemberValuePair pair= (MemberValuePair) value;
			if (name.equals(pair.getName().getIdentifier())) {
				return pair;
			}
		}
		return null;
	}

	private static boolean isLong(String token) {
		try {
			Long.parseLong(token);
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		return afterRefactoring
				? "@Test\n@Timeout(value = 1, unit = TimeUnit.SECONDS)\nvoid test() { assertThrows(Exception.class, () -> work()); }" //$NON-NLS-1$
				: "@Test(expected = Exception.class, timeout = 1000)\nvoid test() { work(); }"; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "TestExpectedAndTimeout"; //$NON-NLS-1$
	}
}
