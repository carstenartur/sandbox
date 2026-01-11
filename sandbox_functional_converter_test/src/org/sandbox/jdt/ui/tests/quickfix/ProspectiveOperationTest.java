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
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation;
import org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType;
import org.sandbox.jdt.internal.corext.fix.helper.ReducerType;
import org.sandbox.jdt.internal.corext.fix.helper.StreamConstants;

/**
 * Unit tests for {@link ProspectiveOperation}.
 * 
 * <p>These tests verify the behavior of ProspectiveOperation, which represents
 * individual stream operations extracted from loop bodies.</p>
 * 
 * @see ProspectiveOperation
 * @see OperationType
 * @see ReducerType
 */
@DisplayName("ProspectiveOperation Tests")
public class ProspectiveOperationTest {

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
	@DisplayName("OperationType Enum")
	class OperationTypeTests {

		@Test
		@DisplayName("All operation types are defined")
		void allOperationTypesAreDefined() {
			OperationType[] types = OperationType.values();
			assertTrue(types.length >= 7, "Should have at least 7 operation types");
			
			assertNotNull(OperationType.MAP);
			assertNotNull(OperationType.FOREACH);
			assertNotNull(OperationType.FILTER);
			assertNotNull(OperationType.REDUCE);
			assertNotNull(OperationType.ANYMATCH);
			assertNotNull(OperationType.NONEMATCH);
			assertNotNull(OperationType.ALLMATCH);
		}
	}

	@Nested
	@DisplayName("ReducerType Enum")
	class ReducerTypeTests {

		@Test
		@DisplayName("All reducer types are defined")
		void allReducerTypesAreDefined() {
			ReducerType[] types = ReducerType.values();
			assertTrue(types.length >= 8, "Should have at least 8 reducer types");
			
			assertNotNull(ReducerType.INCREMENT);
			assertNotNull(ReducerType.DECREMENT);
			assertNotNull(ReducerType.SUM);
			assertNotNull(ReducerType.PRODUCT);
			assertNotNull(ReducerType.STRING_CONCAT);
			assertNotNull(ReducerType.MAX);
			assertNotNull(ReducerType.MIN);
			assertNotNull(ReducerType.CUSTOM_AGGREGATE);
		}
	}

	@Nested
	@DisplayName("Constructor with Expression")
	class ExpressionConstructorTests {

		@Test
		@DisplayName("Creates FILTER operation from expression")
		void createsFilterOperationFromExpression() {
			SimpleName expr = ast.newSimpleName("condition");
			
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.FILTER);
			
			assertEquals(OperationType.FILTER, op.getOperationType());
		}

		@Test
		@DisplayName("Creates MAP operation with produced variable")
		void createsMapOperationWithProducedVariable() {
			InfixExpression expr = ast.newInfixExpression();
			expr.setLeftOperand(ast.newSimpleName("num"));
			expr.setRightOperand(ast.newNumberLiteral("2"));
			expr.setOperator(InfixExpression.Operator.TIMES);
			
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.MAP, "squared");
			
			assertEquals(OperationType.MAP, op.getOperationType());
			assertEquals("squared", op.getProducedVariableName());
		}
	}

	@Nested
	@DisplayName("OperationType.getMethodName()")
	class GetMethodNameTests {

		@Test
		@DisplayName("Returns 'map' for MAP operation")
		void returnsMapForMapOperation() {
			SimpleName expr = ast.newSimpleName("value");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.MAP);
			
			assertEquals(StreamConstants.MAP_METHOD, op.getOperationType().getMethodName());
			assertEquals("map", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'filter' for FILTER operation")
		void returnsFilterForFilterOperation() {
			SimpleName expr = ast.newSimpleName("condition");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.FILTER);
			
			assertEquals(StreamConstants.FILTER_METHOD, op.getOperationType().getMethodName());
			assertEquals("filter", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'forEachOrdered' for FOREACH operation")
		void returnsForEachOrderedForForeachOperation() {
			SimpleName expr = ast.newSimpleName("action");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.FOREACH);
			
			assertEquals(StreamConstants.FOR_EACH_ORDERED_METHOD, op.getOperationType().getMethodName());
			assertEquals("forEachOrdered", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'reduce' for REDUCE operation")
		void returnsReduceForReduceOperation() {
			SimpleName expr = ast.newSimpleName("value");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.REDUCE);
			
			assertEquals(StreamConstants.REDUCE_METHOD, op.getOperationType().getMethodName());
			assertEquals("reduce", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'anyMatch' for ANYMATCH operation")
		void returnsAnyMatchForAnymatchOperation() {
			SimpleName expr = ast.newSimpleName("condition");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.ANYMATCH);
			
			assertEquals(StreamConstants.ANY_MATCH_METHOD, op.getOperationType().getMethodName());
			assertEquals("anyMatch", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'noneMatch' for NONEMATCH operation")
		void returnsNoneMatchForNonematchOperation() {
			SimpleName expr = ast.newSimpleName("condition");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.NONEMATCH);
			
			assertEquals(StreamConstants.NONE_MATCH_METHOD, op.getOperationType().getMethodName());
			assertEquals("noneMatch", op.getOperationType().getMethodName());
		}

		@Test
		@DisplayName("Returns 'allMatch' for ALLMATCH operation")
		void returnsAllMatchForAllmatchOperation() {
			SimpleName expr = ast.newSimpleName("condition");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.ALLMATCH);
			
			assertEquals(StreamConstants.ALL_MATCH_METHOD, op.getOperationType().getMethodName());
			assertEquals("allMatch", op.getOperationType().getMethodName());
		}
	}

	@Nested
	@DisplayName("Accumulator and Null-Safe Properties")
	class PropertyTests {

		@Test
		@DisplayName("setAccumulatorType and getAccumulatorType work correctly")
		void accumulatorTypeWorksCorrectly() {
			SimpleName expr = ast.newSimpleName("sum");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.REDUCE);
			
			op.setAccumulatorType("int");
			assertEquals("int", op.getAccumulatorType());
			
			op.setAccumulatorType("double");
			assertEquals("double", op.getAccumulatorType());
		}

		@Test
		@DisplayName("setNullSafe and isNullSafe work correctly")
		void nullSafeWorksCorrectly() {
			SimpleName expr = ast.newSimpleName("str");
			ProspectiveOperation op = new ProspectiveOperation(expr, OperationType.REDUCE);
			
			assertFalse(op.isNullSafe(), "Default should be not null-safe");
			
			op.setNullSafe(true);
			assertTrue(op.isNullSafe());
			
			op.setNullSafe(false);
			assertFalse(op.isNullSafe());
		}
	}
}
