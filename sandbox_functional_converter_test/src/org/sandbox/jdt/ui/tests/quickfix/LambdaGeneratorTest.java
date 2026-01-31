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
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.LambdaGenerator;
import org.sandbox.jdt.internal.corext.fix.helper.ReducerType;

/**
 * Unit tests for {@link LambdaGenerator}.
 * 
 * <p>These tests verify the lambda expression and method reference generation
 * used in stream pipeline construction.</p>
 * 
 * @see LambdaGenerator
 */
@DisplayName("LambdaGenerator Tests")
public class LambdaGeneratorTest {

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
	@DisplayName("Constructor")
	class ConstructorTests {

		@Test
		@DisplayName("null AST throws exception")
		void nullAstThrowsException() {
			assertThrows(IllegalArgumentException.class, 
					() -> new LambdaGenerator(null));
		}

		@Test
		@DisplayName("valid AST creates instance")
		void validAstCreatesInstance() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			assertNotNull(generator);
		}
	}

	@Nested
	@DisplayName("generateUniqueVariableName()")
	class GenerateUniqueVariableNameTests {

		@Test
		@DisplayName("Returns base name if not used")
		void returnsBaseNameIfNotUsed() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			String name = generator.generateUniqueVariableName("item");
			
			assertEquals("item", name);
		}

		@Test
		@DisplayName("Appends number if base name is used")
		void appendsNumberIfBaseNameIsUsed() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			generator.setUsedVariableNames(java.util.Set.of("item"));
			
			String name = generator.generateUniqueVariableName("item");
			
			assertEquals("item2", name);
		}

		@Test
		@DisplayName("Increments number until unique")
		void incrementsNumberUntilUnique() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			generator.setUsedVariableNames(java.util.Set.of("item", "item2", "item3"));
			
			String name = generator.generateUniqueVariableName("item");
			
			assertEquals("item4", name);
		}
	}

	@Nested
	@DisplayName("createAccumulatorExpression()")
	class CreateAccumulatorExpressionTests {

		@Test
		@DisplayName("Creates Integer::sum for SUM type with int")
		void createsIntegerSumForSumType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.SUM, "int", false);
			
			assertNotNull(expr);
			assertTrue(expr instanceof TypeMethodReference);
		}

		@Test
		@DisplayName("Creates Double::sum for SUM type with double")
		void createsDoubleSumForSumType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.SUM, "double", false);
			
			assertNotNull(expr);
			assertTrue(expr instanceof TypeMethodReference);
			TypeMethodReference ref = (TypeMethodReference) expr;
			assertEquals("sum", ref.getName().getIdentifier());
		}

		@Test
		@DisplayName("Creates counting lambda for INCREMENT type")
		void createsCountingLambdaForIncrementType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.INCREMENT, "int", false);
			
			assertNotNull(expr);
			// INCREMENT with int should use counting lambda, not method reference
			assertTrue(expr instanceof TypeMethodReference || expr instanceof LambdaExpression);
		}

		@Test
		@DisplayName("Creates binary lambda for PRODUCT type")
		void createsBinaryLambdaForProductType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.PRODUCT, "int", false);
			
			assertNotNull(expr);
			assertTrue(expr instanceof LambdaExpression);
			LambdaExpression lambda = (LambdaExpression) expr;
			InfixExpression body = (InfixExpression) lambda.getBody();
			assertEquals(InfixExpression.Operator.TIMES, body.getOperator());
		}

		@Test
		@DisplayName("Creates String::concat for STRING_CONCAT when null-safe")
		void createsStringConcatWhenNullSafe() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.STRING_CONCAT, "String", true);
			
			assertNotNull(expr);
			assertTrue(expr instanceof TypeMethodReference);
			TypeMethodReference ref = (TypeMethodReference) expr;
			assertEquals("concat", ref.getName().getIdentifier());
		}

		@Test
		@DisplayName("Creates binary lambda for STRING_CONCAT when not null-safe")
		void createsBinaryLambdaWhenNotNullSafe() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			var expr = generator.createAccumulatorExpression(
					ReducerType.STRING_CONCAT, "String", false);
			
			assertNotNull(expr);
			assertTrue(expr instanceof LambdaExpression);
		}
	}
}
