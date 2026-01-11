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
import org.eclipse.jdt.core.dom.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.StatementParsingContext;

/**
 * Unit tests for {@link StatementParsingContext}.
 * 
 * <p>These tests verify the context object used during statement parsing
 * in the handler chain.</p>
 * 
 * @see StatementParsingContext
 */
@DisplayName("StatementParsingContext Tests")
public class StatementParsingContextTest {

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
		@DisplayName("Creates context with all parameters")
		void createsContextWithAllParameters() {
			List<Statement> statements = new ArrayList<>();
			
			StatementParsingContext context = new StatementParsingContext(
					"item",           // loopVariableName
					"item",           // currentVariableName
					true,             // isLastStatement
					0,                // currentIndex
					statements,       // allStatements
					ast,              // ast
					null,             // ifAnalyzer
					null,             // reduceDetector
					false,            // isAnyMatchPattern
					false,            // isNoneMatchPattern
					false             // isAllMatchPattern
			);
			
			assertEquals("item", context.getLoopVariableName());
			assertEquals("item", context.getCurrentVariableName());
			assertTrue(context.isLastStatement());
			assertEquals(0, context.getCurrentIndex());
			assertSame(statements, context.getAllStatements());
			assertSame(ast, context.getAst());
			assertFalse(context.isAnyMatchPattern());
			assertFalse(context.isNoneMatchPattern());
			assertFalse(context.isAllMatchPattern());
		}
	}

	@Nested
	@DisplayName("Getters and Setters")
	class GettersAndSettersTests {

		@Test
		@DisplayName("setCurrentVariableName updates the variable name")
		void setCurrentVariableNameUpdatesValue() {
			StatementParsingContext context = new StatementParsingContext(
					"item", "item", true, 0, null, ast, null, null, false, false, false);
			
			assertEquals("item", context.getCurrentVariableName());
			
			context.setCurrentVariableName("newVar");
			
			assertEquals("newVar", context.getCurrentVariableName());
		}

		@Test
		@DisplayName("Match patterns are correctly returned")
		void matchPatternsAreCorrectlyReturned() {
			StatementParsingContext anyMatchContext = new StatementParsingContext(
					"item", "item", true, 0, null, ast, null, null, true, false, false);
			assertTrue(anyMatchContext.isAnyMatchPattern());
			assertFalse(anyMatchContext.isNoneMatchPattern());
			assertFalse(anyMatchContext.isAllMatchPattern());
			
			StatementParsingContext noneMatchContext = new StatementParsingContext(
					"item", "item", true, 0, null, ast, null, null, false, true, false);
			assertFalse(noneMatchContext.isAnyMatchPattern());
			assertTrue(noneMatchContext.isNoneMatchPattern());
			assertFalse(noneMatchContext.isAllMatchPattern());
			
			StatementParsingContext allMatchContext = new StatementParsingContext(
					"item", "item", true, 0, null, ast, null, null, false, false, true);
			assertFalse(allMatchContext.isAnyMatchPattern());
			assertFalse(allMatchContext.isNoneMatchPattern());
			assertTrue(allMatchContext.isAllMatchPattern());
		}
	}

	@Nested
	@DisplayName("forSingleStatement factory")
	class ForSingleStatementTests {

		@Test
		@DisplayName("Creates context for single statement")
		void createsContextForSingleStatement() {
			StatementParsingContext context = StatementParsingContext.forSingleStatement(
					"item", ast, null, null, false, false, false);
			
			assertEquals("item", context.getLoopVariableName());
			assertEquals("item", context.getCurrentVariableName());
			assertTrue(context.isLastStatement(), "Single statement should always be 'last'");
			assertEquals(0, context.getCurrentIndex());
			assertNull(context.getAllStatements());
		}

		@Test
		@DisplayName("Creates context with match patterns")
		void createsContextWithMatchPatterns() {
			StatementParsingContext context = StatementParsingContext.forSingleStatement(
					"item", ast, null, null, true, true, true);
			
			assertTrue(context.isAnyMatchPattern());
			assertTrue(context.isNoneMatchPattern());
			assertTrue(context.isAllMatchPattern());
		}
	}
}
