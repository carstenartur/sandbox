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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Unified plugin that migrates ALL JUnit 4 @Test annotation variants to JUnit 5.
 * 
 * <p>Handles:</p>
 * <ul>
 *   <li>{@code @Test} → {@code @Test} (jupiter)</li>
 *   <li>{@code @Test(expected=X.class)} → {@code @Test} + {@code assertThrows(X.class, () -> {...})}</li>
 *   <li>{@code @Test(timeout=N)} → {@code @Test} + {@code @Timeout(N, unit=MILLISECONDS)}</li>
 *   <li>Combined: both transformations applied</li>
 * </ul>
 * 
 * <p>This replaces three separate V1 plugins:</p>
 * <ul>
 *   <li>TestJUnitPlugin</li>
 *   <li>TestExpectedJUnitPlugin</li>
 *   <li>TestTimeoutJUnitPlugin</li>
 * </ul>
 * 
 * @since 1.3.0
 */
@CleanupPattern(
    value = "@Test",
    kind = PatternKind.ANNOTATION,
    qualifiedType = ORG_JUNIT_TEST,
    cleanupId = "cleanup.junit.test",
    description = "Migrate @Test (including expected and timeout) to JUnit 5",
    displayName = "JUnit 4 @Test → JUnit 5 @Test (unified)"
)
public class TestJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		JunitHolder holder = super.createHolder(match);
		ASTNode node = match.getMatchedNode();
		
		// Analyze parameters for NormalAnnotation or SingleMemberAnnotation
		if (node instanceof NormalAnnotation normalAnnotation) {
			@SuppressWarnings("unchecked")
			List<MemberValuePair> values = normalAnnotation.values();
			for (MemberValuePair pair : values) {
				String name = pair.getName().getIdentifier();
				Expression value = pair.getValue();
				
				if ("expected".equals(name) && value instanceof TypeLiteral) {
					holder.expectedExceptionType = value;
				}
				if ("timeout".equals(name) && value instanceof NumberLiteral) {
					holder.timeoutValue = value;
				}
			}
		} else if (node instanceof SingleMemberAnnotation singleMemberAnnotation) {
			// SingleMemberAnnotation is not commonly used for @Test in JUnit 4
			// JUnit 4 @Test uses either no parameters (marker) or named parameters (normal)
			// This case is included for completeness but is not expected in practice
			Expression value = singleMemberAnnotation.getValue();
			if (value instanceof TypeLiteral) {
				holder.expectedExceptionType = value;
			}
		}
		
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		
		Annotation annotation = junitHolder.getAnnotation();
		
		// 1. Handle expected exception if present
		if (junitHolder.expectedExceptionType != null) {
			wrapBodyWithAssertThrows(annotation, junitHolder, rewriter, ast, importRewriter, group);
		}
		
		// 2. Handle timeout if present
		if (junitHolder.timeoutValue != null) {
			addTimeoutAnnotation(annotation, junitHolder, rewriter, ast, importRewriter, group);
		}
		
		// 3. Replace @Test annotation (convert to JUnit 5)
		replaceTestAnnotation(annotation, junitHolder, rewriter, ast, importRewriter, group);
	}

	/**
	 * Replaces the @Test annotation with JUnit 5 version.
	 * Removes expected/timeout parameters if they exist.
	 */
	private void replaceTestAnnotation(Annotation annotation, JunitHolder junitHolder,
			ASTRewrite rewriter, AST ast, ImportRewrite importRewriter, TextEditGroup group) {
		
		// Always replace with simple marker annotation since we handle expected/timeout separately
		MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST));
		ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
		
		// Update imports
		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
	}

	/**
	 * Wraps the method body with assertThrows().
	 * 
	 * <p>Before:</p>
	 * <pre>
	 * @Test(expected = IllegalArgumentException.class)
	 * public void testMethod() {
	 *     doSomething();
	 * }
	 * </pre>
	 * 
	 * <p>After:</p>
	 * <pre>
	 * @Test
	 * public void testMethod() {
	 *     assertThrows(IllegalArgumentException.class, () -> {
	 *         doSomething();
	 *     });
	 * }
	 * </pre>
	 */
	private void wrapBodyWithAssertThrows(Annotation annotation, JunitHolder junitHolder,
			ASTRewrite rewriter, AST ast, ImportRewrite importRewriter, TextEditGroup group) {
		
		// Find the method
		MethodDeclaration method = ASTNodes.getParent(annotation, MethodDeclaration.class);
		if (method == null || method.getBody() == null) {
			return;
		}
		
		Block originalBody = method.getBody();
		@SuppressWarnings("unchecked")
		List<Statement> statements = originalBody.statements();
		
		if (statements.isEmpty()) {
			return; // No statements to wrap
		}
		
		// Add import for Assertions
		importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, METHOD_ASSERT_THROWS, false);
		
		// Create: assertThrows(ExceptionType.class, () -> { ... })
		MethodInvocation assertThrows = ast.newMethodInvocation();
		assertThrows.setName(ast.newSimpleName(METHOD_ASSERT_THROWS));
		
		// Argument 1: Exception type
		TypeLiteral exceptionType = (TypeLiteral) ASTNodes.copySubtree(ast, junitHolder.expectedExceptionType);
		assertThrows.arguments().add(exceptionType);
		
		// Argument 2: Lambda with original body
		LambdaExpression lambda = ast.newLambdaExpression();
		lambda.setParentheses(true);
		
		// Copy all statements into lambda body
		Block lambdaBody = ast.newBlock();
		for (Statement stmt : statements) {
			Statement copy = (Statement) ASTNodes.copySubtree(ast, stmt);
			lambdaBody.statements().add(copy);
		}
		lambda.setBody(lambdaBody);
		
		assertThrows.arguments().add(lambda);
		
		// Create new body with only assertThrows
		Block newBody = ast.newBlock();
		ExpressionStatement assertThrowsStmt = ast.newExpressionStatement(assertThrows);
		newBody.statements().add(assertThrowsStmt);
		
		// Replace method body
		rewriter.replace(originalBody, newBody, group);
	}

	/**
	 * Adds @Timeout annotation to the method.
	 * 
	 * <p>Before:</p>
	 * <pre>
	 * @Test(timeout = 1000)
	 * public void testMethod() { }
	 * </pre>
	 * 
	 * <p>After:</p>
	 * <pre>
	 * @Test
	 * @Timeout(value = 1, unit = TimeUnit.SECONDS)
	 * public void testMethod() { }
	 * </pre>
	 */
	private void addTimeoutAnnotation(Annotation annotation, JunitHolder junitHolder,
			ASTRewrite rewriter, AST ast, ImportRewrite importRewriter, TextEditGroup group) {
		
		MethodDeclaration method = ASTNodes.getParent(annotation, MethodDeclaration.class);
		if (method == null) {
			return;
		}
		
		// Parse timeout value
		NumberLiteral timeoutLiteral = (NumberLiteral) junitHolder.timeoutValue;
		long timeoutMillis;
		try {
			timeoutMillis = Long.parseLong(timeoutLiteral.getToken());
		} catch (NumberFormatException e) {
			return; // Skip invalid timeout
		}
		
		// Determine best time unit for readability
		long timeoutValue;
		String timeUnit;
		if (timeoutMillis % 1000 == 0 && timeoutMillis >= 1000) {
			// Use SECONDS for better readability
			timeoutValue = timeoutMillis / 1000;
			timeUnit = "SECONDS";
		} else {
			// Use MILLISECONDS
			timeoutValue = timeoutMillis;
			timeUnit = "MILLISECONDS";
		}
		
		// Add imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TIMEOUT);
		importRewriter.addImport(JAVA_UTIL_CONCURRENT_TIME_UNIT);
		
		// Create @Timeout(value = timeoutValue, unit = TimeUnit.SECONDS or MILLISECONDS)
		NormalAnnotation timeoutAnnotation = ast.newNormalAnnotation();
		timeoutAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TIMEOUT));
		
		// value = N
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value"));
		valuePair.setValue(ast.newNumberLiteral(String.valueOf(timeoutValue)));
		timeoutAnnotation.values().add(valuePair);
		
		// unit = TimeUnit.MILLISECONDS or TimeUnit.SECONDS
		MemberValuePair unitPair = ast.newMemberValuePair();
		unitPair.setName(ast.newSimpleName("unit"));
		QualifiedName timeUnitName = ast.newQualifiedName(
			ast.newSimpleName("TimeUnit"),
			ast.newSimpleName(timeUnit)
		);
		unitPair.setValue(timeUnitName);
		timeoutAnnotation.values().add(unitPair);
		
		// Add annotation after @Test
		ListRewrite modifiersRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		modifiersRewrite.insertAfter(timeoutAnnotation, annotation, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@Test
				void testMethod() {
					assertThrows(IllegalArgumentException.class, () -> {
						doSomething();
					});
				}
				"""; //$NON-NLS-1$
		}
		return """
			@Test(expected = IllegalArgumentException.class)
			public void testMethod() {
				doSomething();
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Test (Unified TriggerPattern)"; //$NON-NLS-1$
	}
}
