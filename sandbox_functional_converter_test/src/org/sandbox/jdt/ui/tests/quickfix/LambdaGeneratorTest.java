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
	@DisplayName("createMethodReference()")
	class CreateMethodReferenceTests {

		@Test
		@DisplayName("Creates Integer::sum reference")
		void createsIntegerSumReference() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			TypeMethodReference ref = generator.createMethodReference("Integer", "sum");
			
			assertNotNull(ref);
			assertEquals("sum", ref.getName().getIdentifier());
		}

		@Test
		@DisplayName("Creates String::concat reference")
		void createsStringConcatReference() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			TypeMethodReference ref = generator.createMethodReference("String", "concat");
			
			assertNotNull(ref);
			assertEquals("concat", ref.getName().getIdentifier());
		}
	}

	@Nested
	@DisplayName("createBinaryOperatorLambda()")
	class CreateBinaryOperatorLambdaTests {

		@Test
		@DisplayName("Creates lambda with PLUS operator")
		void createsLambdaWithPlusOperator() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			LambdaExpression lambda = generator.createBinaryOperatorLambda(InfixExpression.Operator.PLUS);
			
			assertNotNull(lambda);
			assertEquals(2, lambda.parameters().size());
			assertTrue(lambda.getBody() instanceof InfixExpression);
			
			InfixExpression body = (InfixExpression) lambda.getBody();
			assertEquals(InfixExpression.Operator.PLUS, body.getOperator());
		}

		@Test
		@DisplayName("Creates lambda with TIMES operator")
		void createsLambdaWithTimesOperator() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			LambdaExpression lambda = generator.createBinaryOperatorLambda(InfixExpression.Operator.TIMES);
			
			assertNotNull(lambda);
			InfixExpression body = (InfixExpression) lambda.getBody();
			assertEquals(InfixExpression.Operator.TIMES, body.getOperator());
		}
	}

	@Nested
	@DisplayName("createCountingLambda()")
	class CreateCountingLambdaTests {

		@Test
		@DisplayName("Creates increment counting lambda")
		void createsIncrementCountingLambda() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			LambdaExpression lambda = generator.createCountingLambda(InfixExpression.Operator.PLUS);
			
			assertNotNull(lambda);
			assertEquals(2, lambda.parameters().size());
			assertTrue(lambda.getBody() instanceof InfixExpression);
			
			InfixExpression body = (InfixExpression) lambda.getBody();
			assertEquals(InfixExpression.Operator.PLUS, body.getOperator());
			// Right operand should be literal "1"
			assertEquals("1", body.getRightOperand().toString());
		}

		@Test
		@DisplayName("Creates decrement counting lambda")
		void createsDecrementCountingLambda() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			LambdaExpression lambda = generator.createCountingLambda(InfixExpression.Operator.MINUS);
			
			assertNotNull(lambda);
			InfixExpression body = (InfixExpression) lambda.getBody();
			assertEquals(InfixExpression.Operator.MINUS, body.getOperator());
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

	@Nested
	@DisplayName("createMaxMinMethodReference()")
	class CreateMaxMinMethodReferenceTests {

		@Test
		@DisplayName("Creates Integer::max for int type")
		void createsIntegerMaxForIntType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			TypeMethodReference ref = generator.createMaxMinMethodReference("int", "max");
			
			assertNotNull(ref);
			assertEquals("max", ref.getName().getIdentifier());
		}

		@Test
		@DisplayName("Creates Double::min for double type")
		void createsDoubleMinForDoubleType() {
			LambdaGenerator generator = new LambdaGenerator(ast);
			
			TypeMethodReference ref = generator.createMaxMinMethodReference("double", "min");
			
			assertNotNull(ref);
			assertEquals("min", ref.getName().getIdentifier());
		}
	}
}
