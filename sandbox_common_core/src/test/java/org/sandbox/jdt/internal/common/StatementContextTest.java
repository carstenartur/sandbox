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
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for {@link StatementContext}.
 * 
 * <p>Tests all methods including factory methods, position queries,
 * navigation methods, list operations, and conditional helpers.</p>
 * 
 * @see StatementContext
 */
@DisplayName("StatementContext Tests")
public class StatementContextTest {

	private CompilationUnit cu;

	@BeforeEach
	void setUp() {
		// Common setup if needed
	}

	private CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		java.util.Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setEnvironment(new String[] {}, new String[] {}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Test.java");
		return (CompilationUnit) parser.createAST(null);
	}

	@Nested
	@DisplayName("Factory Methods")
	class FactoryMethodTests {

		@Test
		@DisplayName("forSingle creates context for single statement")
		void testForSingle_createsContext() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertNotNull(ctx);
			assertEquals(stmt, ctx.getStatement());
			assertEquals(0, ctx.getIndex());
			assertEquals(1, ctx.getTotalCount());
			assertTrue(ctx.isOnly());
		}

		@Test
		@DisplayName("forStatement creates context with position info")
		void testForStatement_createsContextWithPosition() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			
			assertEquals(statements.get(1), ctx.getStatement());
			assertEquals(1, ctx.getIndex());
			assertEquals(3, ctx.getTotalCount());
		}

		@Test
		@DisplayName("forEachInBlock processes all statements")
		void testForEachInBlock_processesAllStatements() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			AtomicInteger count = new AtomicInteger(0);
			
			StatementContext.forEachInBlock(block, (stmt, ctx) -> {
				count.incrementAndGet();
				assertEquals(count.get() - 1, ctx.getIndex());
			});
			
			assertEquals(3, count.get());
		}

		@Test
		@DisplayName("processBlock allows early termination")
		void testProcessBlock_earlyTermination() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			AtomicInteger count = new AtomicInteger(0);
			
			var result = StatementContext.processBlock(block, (stmt, ctx) -> {
				count.incrementAndGet();
				if (ctx.getIndex() == 1) {
					return java.util.Optional.of("found");
				}
				return java.util.Optional.empty();
			});
			
			assertEquals(2, count.get()); // Only processed first 2 statements
			assertTrue(result.isPresent());
			assertEquals("found", result.get());
		}

		@Test
		@DisplayName("processBlock returns empty when no match")
		void testProcessBlock_returnsEmpty() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			
			var result = StatementContext.processBlock(block, (stmt, ctx) -> {
				return java.util.Optional.empty();
			});
			
			assertFalse(result.isPresent());
		}
	}

	@Nested
	@DisplayName("Position Queries")
	class PositionQueryTests {

		@Test
		@DisplayName("isFirst returns true for first statement")
		void testIsFirst_returnsTrueForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			assertTrue(ctx.isFirst());
		}

		@Test
		@DisplayName("isFirst returns false for non-first statement")
		void testIsFirst_returnsFalseForNonFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			assertFalse(ctx.isFirst());
		}

		@Test
		@DisplayName("isLast returns true for last statement")
		void testIsLast_returnsTrueForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			assertTrue(ctx.isLast());
		}

		@Test
		@DisplayName("isOnly returns true for single statement")
		void testIsOnly_returnsTrueForSingle() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertTrue(ctx.isOnly());
		}

		@Test
		@DisplayName("isMiddle returns true for middle statement")
		void testIsMiddle_returnsTrueForMiddle() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			assertTrue(ctx.isMiddle());
		}

		@Test
		@DisplayName("isMiddle returns false for first statement")
		void testIsMiddle_returnsFalseForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			assertFalse(ctx.isMiddle());
		}

		@Test
		@DisplayName("getRemainingCount returns correct count")
		void testGetRemainingCount_returnsCorrectCount() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			assertEquals(2, ctx.getRemainingCount());
		}

		@Test
		@DisplayName("hasNext returns true when there are more statements")
		void testHasNext_returnsTrueWhenMore() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			assertTrue(ctx.hasNext());
		}

		@Test
		@DisplayName("hasNext returns false for last statement")
		void testHasNext_returnsFalseForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			assertFalse(ctx.hasNext());
		}

		@Test
		@DisplayName("hasPrevious returns true when there are previous statements")
		void testHasPrevious_returnsTrueWhenPrevious() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			assertTrue(ctx.hasPrevious());
		}

		@Test
		@DisplayName("hasPrevious returns false for first statement")
		void testHasPrevious_returnsFalseForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			assertFalse(ctx.hasPrevious());
		}
	}

	@Nested
	@DisplayName("Navigation Methods")
	class NavigationTests {

		@Test
		@DisplayName("getNextStatement returns next statement")
		void testGetNextStatement_returnsNext() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			var next = ctx.getNextStatement();
			
			assertTrue(next.isPresent());
			assertEquals(statements.get(1), next.get());
		}

		@Test
		@DisplayName("getNextStatement returns empty for last statement")
		void testGetNextStatement_returnsEmptyForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertFalse(ctx.getNextStatement().isPresent());
		}

		@Test
		@DisplayName("getPreviousStatement returns previous statement")
		void testGetPreviousStatement_returnsPrevious() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			var prev = ctx.getPreviousStatement();
			
			assertTrue(prev.isPresent());
			assertEquals(statements.get(0), prev.get());
		}

		@Test
		@DisplayName("getPreviousStatement returns empty for first statement")
		void testGetPreviousStatement_returnsEmptyForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertFalse(ctx.getPreviousStatement().isPresent());
		}

		@Test
		@DisplayName("getStatementAt returns statement at positive offset")
		void testGetStatementAt_positiveOffset() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			var result = ctx.getStatementAt(2);
			
			assertTrue(result.isPresent());
			assertEquals(statements.get(2), result.get());
		}

		@Test
		@DisplayName("getStatementAt returns statement at negative offset")
		void testGetStatementAt_negativeOffset() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(2), 2, statements);
			var result = ctx.getStatementAt(-2);
			
			assertTrue(result.isPresent());
			assertEquals(statements.get(0), result.get());
		}

		@Test
		@DisplayName("getStatementAt returns empty for out of bounds")
		void testGetStatementAt_outOfBounds() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertFalse(ctx.getStatementAt(1).isPresent());
			assertFalse(ctx.getStatementAt(-1).isPresent());
		}
	}

	@Nested
	@DisplayName("List Operations")
	class ListOperationTests {

		@Test
		@DisplayName("getRemainingStatements returns statements after current")
		void testGetRemainingStatements_returnsAfterCurrent() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			List<Statement> remaining = ctx.getRemainingStatements();
			
			assertEquals(2, remaining.size());
			assertEquals(statements.get(1), remaining.get(0));
			assertEquals(statements.get(2), remaining.get(1));
		}

		@Test
		@DisplayName("getRemainingStatements returns empty for last statement")
		void testGetRemainingStatements_emptyForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertTrue(ctx.getRemainingStatements().isEmpty());
		}

		@Test
		@DisplayName("getPrecedingStatements returns statements before current")
		void testGetPrecedingStatements_returnsBeforeCurrent() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
						int z = 15;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(2), 2, statements);
			List<Statement> preceding = ctx.getPrecedingStatements();
			
			assertEquals(2, preceding.size());
			assertEquals(statements.get(0), preceding.get(0));
			assertEquals(statements.get(1), preceding.get(1));
		}

		@Test
		@DisplayName("getPrecedingStatements returns empty for first statement")
		void testGetPrecedingStatements_emptyForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertTrue(ctx.getPrecedingStatements().isEmpty());
		}
	}

	@Nested
	@DisplayName("Conditional Helpers")
	class ConditionalHelperTests {

		@Test
		@DisplayName("ifLast executes for last statement")
		void testIfLast_executesForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifLast(stmt -> captured.add(stmt));
			
			assertEquals(1, captured.size());
			assertEquals(statements.get(1), captured.get(0));
		}

		@Test
		@DisplayName("ifLast does not execute for non-last statement")
		void testIfLast_doesNotExecuteForNonLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifLast(stmt -> captured.add(stmt));
			
			assertTrue(captured.isEmpty());
		}

		@Test
		@DisplayName("ifNotLast executes for non-last statement")
		void testIfNotLast_executesForNonLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifNotLast(stmt -> captured.add(stmt));
			
			assertEquals(1, captured.size());
		}

		@Test
		@DisplayName("ifFirst executes for first statement")
		void testIfFirst_executesForFirst() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifFirst(stmt -> captured.add(stmt));
			
			assertEquals(1, captured.size());
		}

		@Test
		@DisplayName("ifOnly executes for only statement")
		void testIfOnly_executesForOnly() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifOnly(s -> captured.add(s));
			
			assertEquals(1, captured.size());
		}
	}

	@Nested
	@DisplayName("Matcher Integration")
	class MatcherIntegrationTests {

		@Test
		@DisplayName("matcher returns NodeMatcher for statement")
		void testMatcher_returnsNodeMatcher() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			NodeMatcher<Statement> matcher = ctx.matcher();
			
			assertNotNull(matcher);
			assertEquals(stmt, matcher.getNode());
		}

		@Test
		@DisplayName("nextMatches returns true when predicate matches")
		void testNextMatches_returnsTrueWhenMatches() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			
			assertTrue(ctx.nextMatches(stmt -> stmt instanceof VariableDeclarationStatement));
		}

		@Test
		@DisplayName("nextMatches returns false when predicate does not match")
		void testNextMatches_returnsFalseWhenNoMatch() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			
			assertFalse(ctx.nextMatches(stmt -> stmt instanceof ReturnStatement));
		}

		@Test
		@DisplayName("nextIs returns true for matching type")
		void testNextIs_returnsTrueForMatchingType() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			
			assertTrue(ctx.nextIs(VariableDeclarationStatement.class));
		}

		@Test
		@DisplayName("nextIs with predicate returns true when both match")
		void testNextIsWithPredicate_returnsTrueWhenBothMatch() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						String s = "test";
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(0), 0, statements);
			
			assertTrue(ctx.nextIs(VariableDeclarationStatement.class, 
				vd -> vd.getType().toString().contains("String")));
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("Single statement is first, last, and only")
		void testSingleStatement_isFirstLastOnly() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertTrue(ctx.isFirst());
			assertTrue(ctx.isLast());
			assertTrue(ctx.isOnly());
			assertFalse(ctx.isMiddle());
		}

		@Test
		@DisplayName("getRemainingCount is zero for last statement")
		void testGetRemainingCount_zeroForLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = getFirstStatementFromMethod(cu);
			StatementContext ctx = StatementContext.forSingle(stmt);
			
			assertEquals(0, ctx.getRemainingCount());
		}

		@Test
		@DisplayName("Context chaining with ifLast")
		void testContextChaining_withIfLast() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			Block block = getMethodBody(cu);
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			
			StatementContext ctx = StatementContext.forStatement(statements.get(1), 1, statements);
			List<Statement> captured = new ArrayList<>();
			
			ctx.ifLast(stmt -> captured.add(stmt))
			   .ifFirst(stmt -> captured.add(stmt));
			
			// Should only execute ifLast since it's the last statement
			assertEquals(1, captured.size());
		}
	}

	// Helper methods

	private Statement getFirstStatementFromMethod(CompilationUnit cu) {
		Block block = getMethodBody(cu);
		@SuppressWarnings("unchecked")
		List<Statement> statements = block.statements();
		return statements.get(0);
	}

	private Block getMethodBody(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = (MethodDeclaration) type.bodyDeclarations().get(0);
		return method.getBody();
	}
}
