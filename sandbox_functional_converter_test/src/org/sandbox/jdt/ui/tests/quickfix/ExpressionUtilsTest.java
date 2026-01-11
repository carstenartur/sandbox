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

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.ExpressionUtils;

/**
 * Unit tests for {@link ExpressionUtils}.
 * 
 * <p>These tests verify the expression manipulation utilities used in the
 * functional loop converter. They run without an Eclipse plugin environment.</p>
 * 
 * @see ExpressionUtils
 */
@DisplayName("ExpressionUtils Tests")
public class ExpressionUtilsTest {

	private static AST ast;

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
	}

	@Nested
	@DisplayName("needsParentheses()")
	class NeedsParenthesesTests {

		@Test
		@DisplayName("InfixExpression needs parentheses")
		void infixExpressionNeedsParentheses() {
			InfixExpression infix = ast.newInfixExpression();
			infix.setLeftOperand(ast.newSimpleName("a"));
			infix.setRightOperand(ast.newSimpleName("b"));
			infix.setOperator(InfixExpression.Operator.EQUALS);

			assertTrue(ExpressionUtils.needsParentheses(infix),
					"InfixExpression (a == b) should need parentheses when negated");
		}

		@Test
		@DisplayName("SimpleName does not need parentheses")
		void simpleNameDoesNotNeedParentheses() {
			SimpleName name = ast.newSimpleName("flag");

			assertFalse(ExpressionUtils.needsParentheses(name),
					"SimpleName should not need parentheses when negated");
		}

		@Test
		@DisplayName("null input throws exception")
		void nullInputThrowsException() {
			assertThrows(IllegalArgumentException.class, 
					() -> ExpressionUtils.needsParentheses(null));
		}
	}

	@Nested
	@DisplayName("createNegatedExpression()")
	class CreateNegatedExpressionTests {

		@Test
		@DisplayName("Negates simple name without parentheses")
		void negatesSimpleNameWithoutParentheses() {
			SimpleName name = ast.newSimpleName("flag");
			
			Expression negated = ExpressionUtils.createNegatedExpression(ast, name);
			
			assertNotNull(negated);
			assertTrue(negated instanceof PrefixExpression);
			PrefixExpression prefix = (PrefixExpression) negated;
			assertEquals(PrefixExpression.Operator.NOT, prefix.getOperator());
			// Operand should be SimpleName without parentheses
			assertTrue(prefix.getOperand() instanceof SimpleName);
		}

		@Test
		@DisplayName("Negates infix expression with parentheses")
		void negatesInfixExpressionWithParentheses() {
			InfixExpression infix = ast.newInfixExpression();
			infix.setLeftOperand(ast.newSimpleName("a"));
			infix.setRightOperand(ast.newSimpleName("b"));
			infix.setOperator(InfixExpression.Operator.EQUALS);
			
			Expression negated = ExpressionUtils.createNegatedExpression(ast, infix);
			
			assertNotNull(negated);
			assertTrue(negated instanceof PrefixExpression);
			PrefixExpression prefix = (PrefixExpression) negated;
			// Operand should be ParenthesizedExpression
			assertTrue(prefix.getOperand() instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression);
		}

		@Test
		@DisplayName("null AST throws exception")
		void nullAstThrowsException() {
			SimpleName name = ast.newSimpleName("flag");
			assertThrows(IllegalArgumentException.class, 
					() -> ExpressionUtils.createNegatedExpression(null, name));
		}

		@Test
		@DisplayName("null condition throws exception")
		void nullConditionThrowsException() {
			assertThrows(IllegalArgumentException.class, 
					() -> ExpressionUtils.createNegatedExpression(ast, null));
		}
	}

	@Nested
	@DisplayName("isIdentityMapping()")
	class IsIdentityMappingTests {

		@Test
		@DisplayName("SimpleName matching varName is identity")
		void simpleNameMatchingVarNameIsIdentity() {
			SimpleName name = ast.newSimpleName("item");
			
			assertTrue(ExpressionUtils.isIdentityMapping(name, "item"));
		}

		@Test
		@DisplayName("SimpleName not matching varName is not identity")
		void simpleNameNotMatchingVarNameIsNotIdentity() {
			SimpleName name = ast.newSimpleName("other");
			
			assertFalse(ExpressionUtils.isIdentityMapping(name, "item"));
		}

		@Test
		@DisplayName("InfixExpression is not identity")
		void infixExpressionIsNotIdentity() {
			InfixExpression infix = ast.newInfixExpression();
			infix.setLeftOperand(ast.newSimpleName("item"));
			infix.setRightOperand(ast.newNumberLiteral("2"));
			infix.setOperator(InfixExpression.Operator.TIMES);
			
			assertFalse(ExpressionUtils.isIdentityMapping(infix, "item"));
		}

		@Test
		@DisplayName("null varName returns false")
		void nullVarNameReturnsFalse() {
			SimpleName name = ast.newSimpleName("item");
			
			assertFalse(ExpressionUtils.isIdentityMapping(name, null));
		}
	}

	@Nested
	@DisplayName("isNegatedExpression()")
	class IsNegatedExpressionTests {

		@Test
		@DisplayName("PrefixExpression with NOT is negated")
		void prefixExpressionWithNotIsNegated() {
			PrefixExpression prefix = ast.newPrefixExpression();
			prefix.setOperator(PrefixExpression.Operator.NOT);
			prefix.setOperand(ast.newSimpleName("flag"));
			
			assertTrue(ExpressionUtils.isNegatedExpression(prefix));
		}

		@Test
		@DisplayName("PrefixExpression with MINUS is not negated")
		void prefixExpressionWithMinusIsNotNegated() {
			PrefixExpression prefix = ast.newPrefixExpression();
			prefix.setOperator(PrefixExpression.Operator.MINUS);
			prefix.setOperand(ast.newNumberLiteral("1"));
			
			assertFalse(ExpressionUtils.isNegatedExpression(prefix));
		}

		@Test
		@DisplayName("SimpleName is not negated")
		void simpleNameIsNotNegated() {
			SimpleName name = ast.newSimpleName("flag");
			
			assertFalse(ExpressionUtils.isNegatedExpression(name));
		}
	}

	@Nested
	@DisplayName("stripNegation()")
	class StripNegationTests {

		@Test
		@DisplayName("Strips negation from PrefixExpression with NOT")
		void stripsNegationFromPrefixExpression() {
			PrefixExpression prefix = ast.newPrefixExpression();
			prefix.setOperator(PrefixExpression.Operator.NOT);
			SimpleName operand = ast.newSimpleName("flag");
			prefix.setOperand(operand);
			
			Expression result = ExpressionUtils.stripNegation(prefix);
			
			assertSame(operand, result);
		}

		@Test
		@DisplayName("Returns original if not negated")
		void returnsOriginalIfNotNegated() {
			SimpleName name = ast.newSimpleName("flag");
			
			Expression result = ExpressionUtils.stripNegation(name);
			
			assertSame(name, result);
		}
	}
}
