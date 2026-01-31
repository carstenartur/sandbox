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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation;
import org.sandbox.jdt.internal.corext.fix.helper.SideEffectChecker;

/**
 * Unit tests for {@link SideEffectChecker}.
 * 
 * <p>These tests verify the side-effect checking logic used to determine
 * whether a statement is safe to include in a stream pipeline.</p>
 * 
 * @see SideEffectChecker
 */
@DisplayName("SideEffectChecker Tests")
public class SideEffectCheckerTest {

	private static AST ast;
	private static SideEffectChecker checker;

	@BeforeAll
	static void setUp() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource("class Dummy {}".toCharArray());
		parser.setUnitName("Dummy");
		parser.createAST(null);
		ast = AST.newAST(AST.getJLSLatest());
		checker = new SideEffectChecker();
	}

	@Nested
	@DisplayName("isSafeSideEffect()")
	class IsSafeSideEffectTests {

		@Test
		@DisplayName("Method invocation is safe")
		void methodInvocationIsSafe() {
			// Create: System.out.println("test")
			MethodInvocation println = ast.newMethodInvocation();
			println.setName(ast.newSimpleName("println"));
			println.arguments().add(ast.newStringLiteral());
			
			ExpressionStatement stmt = ast.newExpressionStatement(println);
			List<ProspectiveOperation> ops = new ArrayList<>();
			
			assertTrue(checker.isSafeSideEffect(stmt, "item", ops),
					"Method invocation should be safe");
		}

		@Test
		@DisplayName("Assignment to current variable is unsafe")
		void assignmentToCurrentVariableIsUnsafe() {
			// Create: item = "newValue"
			Assignment assignment = ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName("item"));
			assignment.setRightHandSide(ast.newStringLiteral());
			assignment.setOperator(Assignment.Operator.ASSIGN);
			
			ExpressionStatement stmt = ast.newExpressionStatement(assignment);
			List<ProspectiveOperation> ops = new ArrayList<>();
			
			assertFalse(checker.isSafeSideEffect(stmt, "item", ops),
					"Assignment to current variable should be unsafe");
		}

		@Test
		@DisplayName("Assignment to different variable is unsafe")
		void assignmentToDifferentVariableIsUnsafe() {
			// Create: other = "value"
			Assignment assignment = ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName("other"));
			assignment.setRightHandSide(ast.newStringLiteral());
			assignment.setOperator(Assignment.Operator.ASSIGN);
			
			ExpressionStatement stmt = ast.newExpressionStatement(assignment);
			List<ProspectiveOperation> ops = new ArrayList<>();
			
			assertFalse(checker.isSafeSideEffect(stmt, "item", ops),
					"Assignment to external variable should be unsafe");
		}

		@Test
		@DisplayName("null statement is unsafe")
		void nullStatementIsUnsafe() {
			List<ProspectiveOperation> ops = new ArrayList<>();
			
			assertFalse(checker.isSafeSideEffect(null, "item", ops),
					"null statement should be unsafe");
		}
	}
}
