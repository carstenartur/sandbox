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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for {@link NodeMatcher}.
 * 
 * <p>Tests all methods including statement matchers, expression matchers,
 * generic type matchers, terminal operations, and utility methods.</p>
 * 
 * @see NodeMatcher
 */
@DisplayName("NodeMatcher Tests")
public class NodeMatcherTest {

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
		@DisplayName("on() creates NodeMatcher instance")
		void testOn_createsNodeMatcher() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = extractFirstStatement(cu);
			NodeMatcher<Statement> matcher = NodeMatcher.on(stmt);
			
			assertNotNull(matcher);
			assertEquals(stmt, matcher.getNode());
		}

		@Test
		@DisplayName("isHandled() returns false initially")
		void testIsHandled_returnsFalseInitially() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = extractFirstStatement(cu);
			NodeMatcher<Statement> matcher = NodeMatcher.on(stmt);
			
			assertFalse(matcher.isHandled());
		}
	}

	@Nested
	@DisplayName("Statement Type Matchers")
	class StatementTypeMatcherTests {

		@Test
		@DisplayName("ifVariableDeclaration matches VariableDeclarationStatement")
		void testIfVariableDeclaration_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(varDecl)
				.ifVariableDeclaration(vd -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifVariableDeclarationMatching with predicate")
		void testIfVariableDeclarationMatching_withPredicate() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						String s = "test";
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(varDecl)
				.ifVariableDeclarationMatching(
					vd -> vd.getType().toString().equals("int"),
					vd -> called.set(true)
				);
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifIfStatement matches IfStatement")
		void testIfIfStatement_matches() {
			String source = """
				public class Test {
					void test() {
						if (true) { }
					}
				}
				""";
			cu = parseSource(source);
			
			IfStatement ifStmt = findFirstNodeOfType(cu, IfStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(ifStmt)
				.ifIfStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifIfStatementWithoutElse matches if without else")
		void testIfIfStatementWithoutElse_matches() {
			String source = """
				public class Test {
					void test() {
						if (true) { }
					}
				}
				""";
			cu = parseSource(source);
			
			IfStatement ifStmt = findFirstNodeOfType(cu, IfStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(ifStmt)
				.ifIfStatementWithoutElse(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifIfStatementWithElse matches if with else")
		void testIfIfStatementWithElse_matches() {
			String source = """
				public class Test {
					void test() {
						if (true) { } else { }
					}
				}
				""";
			cu = parseSource(source);
			
			IfStatement ifStmt = findFirstNodeOfType(cu, IfStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(ifStmt)
				.ifIfStatementWithElse(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifExpressionStatement matches ExpressionStatement")
		void testIfExpressionStatement_matches() {
			String source = """
				public class Test {
					void test() {
						System.out.println();
					}
				}
				""";
			cu = parseSource(source);
			
			ExpressionStatement exprStmt = findFirstNodeOfType(cu, ExpressionStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(exprStmt)
				.ifExpressionStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifReturnStatement matches ReturnStatement")
		void testIfReturnStatement_matches() {
			String source = """
				public class Test {
					int test() {
						return 5;
					}
				}
				""";
			cu = parseSource(source);
			
			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(returnStmt)
				.ifReturnStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifContinueStatement matches ContinueStatement")
		void testIfContinueStatement_matches() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							continue;
						}
					}
				}
				""";
			cu = parseSource(source);
			
			ContinueStatement continueStmt = findFirstNodeOfType(cu, ContinueStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(continueStmt)
				.ifContinueStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifBreakStatement matches BreakStatement")
		void testIfBreakStatement_matches() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							break;
						}
					}
				}
				""";
			cu = parseSource(source);
			
			BreakStatement breakStmt = findFirstNodeOfType(cu, BreakStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(breakStmt)
				.ifBreakStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifThrowStatement matches ThrowStatement")
		void testIfThrowStatement_matches() {
			String source = """
				public class Test {
					void test() {
						throw new RuntimeException();
					}
				}
				""";
			cu = parseSource(source);
			
			ThrowStatement throwStmt = findFirstNodeOfType(cu, ThrowStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(throwStmt)
				.ifThrowStatement(stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifBlock matches Block")
		void testIfBlock_matches() {
			String source = """
				public class Test {
					void test() {
						{ }
					}
				}
				""";
			cu = parseSource(source);
			
			MethodDeclaration method = findFirstNodeOfType(cu, MethodDeclaration.class);
			Block block = method.getBody();
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Statement) block)
				.ifBlock(b -> called.set(true));
			
			assertTrue(called.get());
		}
	}

	@Nested
	@DisplayName("Expression Type Matchers")
	class ExpressionTypeMatcherTests {

		@Test
		@DisplayName("ifAssignment matches Assignment")
		void testIfAssignment_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Assignment assignment = findFirstNodeOfType(cu, Assignment.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) assignment)
				.ifAssignment(a -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifAssignmentWithOperator matches specific operator")
		void testIfAssignmentWithOperator_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x += 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Assignment assignment = findFirstNodeOfType(cu, Assignment.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) assignment)
				.ifAssignmentWithOperator(Assignment.Operator.PLUS_ASSIGN, a -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifMethodInvocation matches MethodInvocation")
		void testIfMethodInvocation_matches() {
			String source = """
				public class Test {
					void test() {
						toString();
					}
				}
				""";
			cu = parseSource(source);
			
			MethodInvocation methodInv = findFirstNodeOfType(cu, MethodInvocation.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) methodInv)
				.ifMethodInvocation(mi -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifMethodInvocationNamed matches by method name")
		void testIfMethodInvocationNamed_matches() {
			String source = """
				public class Test {
					void test() {
						toString();
					}
				}
				""";
			cu = parseSource(source);
			
			MethodInvocation methodInv = findFirstNodeOfType(cu, MethodInvocation.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) methodInv)
				.ifMethodInvocationNamed("toString", mi -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifPostfixExpression matches PostfixExpression")
		void testIfPostfixExpression_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x++;
					}
				}
				""";
			cu = parseSource(source);
			
			PostfixExpression postfix = findFirstNodeOfType(cu, PostfixExpression.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) postfix)
				.ifPostfixExpression(p -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifPostfixIncrementOrDecrement matches increment")
		void testIfPostfixIncrementOrDecrement_matchesIncrement() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x++;
					}
				}
				""";
			cu = parseSource(source);
			
			PostfixExpression postfix = findFirstNodeOfType(cu, PostfixExpression.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) postfix)
				.ifPostfixIncrementOrDecrement(p -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifPrefixExpression matches PrefixExpression")
		void testIfPrefixExpression_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						++x;
					}
				}
				""";
			cu = parseSource(source);
			
			PrefixExpression prefix = findFirstNodeOfType(cu, PrefixExpression.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) prefix)
				.ifPrefixExpression(p -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifPrefixIncrementOrDecrement matches decrement")
		void testIfPrefixIncrementOrDecrement_matchesDecrement() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						--x;
					}
				}
				""";
			cu = parseSource(source);
			
			PrefixExpression prefix = findFirstNodeOfType(cu, PrefixExpression.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) prefix)
				.ifPrefixIncrementOrDecrement(p -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifSimpleName matches SimpleName")
		void testIfSimpleName_matches() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			SimpleName simpleName = findFirstNodeOfType(cu, SimpleName.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Expression) simpleName)
				.ifSimpleName(sn -> called.set(true));
			
			assertTrue(called.get());
		}
	}

	@Nested
	@DisplayName("Generic Type Matchers")
	class GenericTypeMatcherTests {

		@Test
		@DisplayName("ifType matches by class type")
		void testIfType_matches() {
			String source = """
				public class Test {
					void test() {
						return;
					}
				}
				""";
			cu = parseSource(source);
			
			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Statement) returnStmt)
				.ifType(ReturnStatement.class, stmt -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifTypeMatching with predicate")
		void testIfTypeMatching_withPredicate() {
			String source = """
				public class Test {
					int test() {
						return 5;
					}
				}
				""";
			cu = parseSource(source);
			
			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on((Statement) returnStmt)
				.ifTypeMatching(
					ReturnStatement.class,
					stmt -> stmt.getExpression() != null,
					stmt -> called.set(true)
				);
			
			assertTrue(called.get());
		}
	}

	@Nested
	@DisplayName("Terminal Operations")
	class TerminalOperationTests {

		@Test
		@DisplayName("orElse executes when no matcher handled")
		void testOrElse_executesWhenNotHandled() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = extractFirstStatement(cu);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(stmt)
				.ifReturnStatement(s -> {}) // Won't match
				.orElse(s -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("orElse does not execute when already handled")
		void testOrElse_doesNotExecuteWhenHandled() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			AtomicBoolean firstCalled = new AtomicBoolean(false);
			AtomicBoolean orElseCalled = new AtomicBoolean(false);
			
			NodeMatcher.on(varDecl)
				.ifVariableDeclaration(vd -> firstCalled.set(true))
				.orElse(s -> orElseCalled.set(true));
			
			assertTrue(firstCalled.get());
			assertFalse(orElseCalled.get());
		}

		@Test
		@DisplayName("orElseDo executes runnable when not handled")
		void testOrElseDo_executesWhenNotHandled() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = extractFirstStatement(cu);
			AtomicBoolean called = new AtomicBoolean(false);
			
			NodeMatcher.on(stmt)
				.ifReturnStatement(s -> {})
				.orElseDo(() -> called.set(true));
			
			assertTrue(called.get());
		}

		@Test
		@DisplayName("orElseGet returns Optional when not handled")
		void testOrElseGet_returnsOptionalWhenNotHandled() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			Statement stmt = extractFirstStatement(cu);
			
			var result = NodeMatcher.on(stmt)
				.ifReturnStatement(s -> {})
				.orElseGet(s -> "result");
			
			assertTrue(result.isPresent());
			assertEquals("result", result.get());
		}

		@Test
		@DisplayName("orElseGet returns empty when already handled")
		void testOrElseGet_returnsEmptyWhenHandled() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			var result = NodeMatcher.on(varDecl)
				.ifVariableDeclaration(vd -> {})
				.orElseGet(s -> "result");
			
			assertFalse(result.isPresent());
		}
	}

	@Nested
	@DisplayName("Utility Methods")
	class UtilityMethodTests {

		@Test
		@DisplayName("isControlFlowStatement returns true for return")
		void testIsControlFlowStatement_returnTrue() {
			String source = """
				public class Test {
					void test() {
						return;
					}
				}
				""";
			cu = parseSource(source);
			
			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			
			assertTrue(NodeMatcher.on(returnStmt).isControlFlowStatement());
		}

		@Test
		@DisplayName("isControlFlowStatement returns true for continue")
		void testIsControlFlowStatement_continueTrue() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							continue;
						}
					}
				}
				""";
			cu = parseSource(source);
			
			ContinueStatement continueStmt = findFirstNodeOfType(cu, ContinueStatement.class);
			
			assertTrue(NodeMatcher.on(continueStmt).isControlFlowStatement());
		}

		@Test
		@DisplayName("isControlFlowStatement returns true for break")
		void testIsControlFlowStatement_breakTrue() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							break;
						}
					}
				}
				""";
			cu = parseSource(source);
			
			BreakStatement breakStmt = findFirstNodeOfType(cu, BreakStatement.class);
			
			assertTrue(NodeMatcher.on(breakStmt).isControlFlowStatement());
		}

		@Test
		@DisplayName("isControlFlowStatement returns true for throw")
		void testIsControlFlowStatement_throwTrue() {
			String source = """
				public class Test {
					void test() {
						throw new RuntimeException();
					}
				}
				""";
			cu = parseSource(source);
			
			ThrowStatement throwStmt = findFirstNodeOfType(cu, ThrowStatement.class);
			
			assertTrue(NodeMatcher.on(throwStmt).isControlFlowStatement());
		}

		@Test
		@DisplayName("isControlFlowStatement returns false for variable declaration")
		void testIsControlFlowStatement_returnFalse() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			assertFalse(NodeMatcher.on(varDecl).isControlFlowStatement());
		}

		@Test
		@DisplayName("isAssignmentStatement returns true for assignment")
		void testIsAssignmentStatement_returnTrue() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			// Find the second statement (the assignment)
			MethodDeclaration method = findFirstNodeOfType(cu, MethodDeclaration.class);
			ExpressionStatement exprStmt = (ExpressionStatement) method.getBody().statements().get(1);
			
			assertTrue(NodeMatcher.on((Statement) exprStmt).isAssignmentStatement());
		}

		@Test
		@DisplayName("isAssignmentStatement returns false for non-assignment")
		void testIsAssignmentStatement_returnFalse() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			assertFalse(NodeMatcher.on(varDecl).isAssignmentStatement());
		}

		@Test
		@DisplayName("getAssignment returns Optional with assignment")
		void testGetAssignment_returnsOptionalWithAssignment() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			MethodDeclaration method = findFirstNodeOfType(cu, MethodDeclaration.class);
			ExpressionStatement exprStmt = (ExpressionStatement) method.getBody().statements().get(1);
			
			var result = NodeMatcher.on((Statement) exprStmt).getAssignment();
			
			assertTrue(result.isPresent());
			assertInstanceOf(Assignment.class, result.get());
		}

		@Test
		@DisplayName("getAssignment returns empty for non-assignment")
		void testGetAssignment_returnsEmpty() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			var result = NodeMatcher.on(varDecl).getAssignment();
			
			assertFalse(result.isPresent());
		}

		@Test
		@DisplayName("getExpression returns Optional with expression")
		void testGetExpression_returnsOptionalWithExpression() {
			String source = """
				public class Test {
					void test() {
						toString();
					}
				}
				""";
			cu = parseSource(source);
			
			ExpressionStatement exprStmt = findFirstNodeOfType(cu, ExpressionStatement.class);
			
			var result = NodeMatcher.on((Statement) exprStmt).getExpression();
			
			assertTrue(result.isPresent());
			assertInstanceOf(Expression.class, result.get());
		}

		@Test
		@DisplayName("getExpression returns empty for non-expression statement")
		void testGetExpression_returnsEmpty() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			var result = NodeMatcher.on(varDecl).getExpression();
			
			assertFalse(result.isPresent());
		}
	}

	@Nested
	@DisplayName("Composite Matchers")
	class CompositeMatcherTests {

		@Test
		@DisplayName("ifBlockWithSingleStatement matches Block with single matching statement")
		void testIfBlockWithSingleStatement_matches() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							{
								continue;
							}
						}
					}
				}
				""";
			cu = parseSource(source);

			// Find the inner block (not the method body block or while body)
			org.eclipse.jdt.core.dom.WhileStatement whileStmt = findFirstNodeOfType(cu, org.eclipse.jdt.core.dom.WhileStatement.class);
			Block innerBlock = (Block) ((Block) whileStmt.getBody()).statements().get(0);

			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on((Statement) innerBlock)
				.ifBlockWithSingleStatement(ContinueStatement.class,
					cs -> cs.getLabel() == null,
					cs -> called.set(true));

			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifBlockWithSingleStatement does not match Block with multiple statements")
		void testIfBlockWithSingleStatement_noMatchMultiple() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						int y = 1;
					}
				}
				""";
			cu = parseSource(source);

			MethodDeclaration method = findFirstNodeOfType(cu, MethodDeclaration.class);
			Block body = method.getBody();
			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on((Statement) body)
				.ifBlockWithSingleStatement(VariableDeclarationStatement.class,
					vd -> true,
					vd -> called.set(true));

			assertFalse(called.get());
		}

		@Test
		@DisplayName("ifThenStatementIs matches direct statement in then-branch")
		void testIfThenStatementIs_directMatch() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							if (true) continue;
						}
					}
				}
				""";
			cu = parseSource(source);

			IfStatement ifStmt = findFirstNodeOfType(cu, IfStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on((Statement) ifStmt)
				.ifThenStatementIs(ContinueStatement.class,
					cs -> cs.getLabel() == null,
					cs -> called.set(true));

			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifThenStatementIs matches wrapped statement in Block")
		void testIfThenStatementIs_wrappedInBlock() {
			String source = """
				public class Test {
					void test() {
						while (true) {
							if (true) {
								continue;
							}
						}
					}
				}
				""";
			cu = parseSource(source);

			IfStatement ifStmt = findFirstNodeOfType(cu, IfStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on((Statement) ifStmt)
				.ifThenStatementIs(ContinueStatement.class,
					cs -> cs.getLabel() == null,
					cs -> called.set(true));

			assertTrue(called.get());
		}

		@Test
		@DisplayName("ifThenStatementIs does not match non-IfStatement node")
		void testIfThenStatementIs_nonIfStatement() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);

			Statement stmt = extractFirstStatement(cu);
			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on(stmt)
				.ifThenStatementIs(ContinueStatement.class,
					cs -> true,
					cs -> called.set(true));

			assertFalse(called.get());
		}

		@Test
		@DisplayName("ifTypeMapping maps and consumes result")
		void testIfTypeMapping_mapsResult() {
			String source = """
				public class Test {
					int test() {
						return 42;
					}
				}
				""";
			cu = parseSource(source);

			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);
			String[] result = new String[1];

			NodeMatcher.on((Statement) returnStmt)
				.ifTypeMapping(ReturnStatement.class,
					rs -> rs.getExpression() != null ? rs.getExpression().toString() : null,
					r -> { result[0] = r; called.set(true); });

			assertTrue(called.get());
			assertEquals("42", result[0]);
		}

		@Test
		@DisplayName("ifTypeMapping does not consume when mapper returns null")
		void testIfTypeMapping_nullResult() {
			String source = """
				public class Test {
					void test() {
						return;
					}
				}
				""";
			cu = parseSource(source);

			ReturnStatement returnStmt = findFirstNodeOfType(cu, ReturnStatement.class);
			AtomicBoolean called = new AtomicBoolean(false);

			NodeMatcher.on((Statement) returnStmt)
				.ifTypeMapping(ReturnStatement.class,
					rs -> rs.getExpression(), // null for void return
					r -> called.set(true));

			assertFalse(called.get());
		}
	}

	@Nested
	@DisplayName("Static Utility Methods")
	class StaticUtilityTests {

		@Test
		@DisplayName("matchAll processes all ASTNode elements in list")
		void testMatchAll_processesAll() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
						System.out.println(x);
					}
				}
				""";
			cu = parseSource(source);

			MethodDeclaration method = findFirstNodeOfType(cu, MethodDeclaration.class);
			Block body = method.getBody();

			AtomicInteger count = new AtomicInteger(0);

			NodeMatcher.matchAll(body.statements(), m -> m
				.ifVariableDeclaration(vd -> count.incrementAndGet())
				.ifExpressionStatement(es -> count.incrementAndGet()));

			assertEquals(3, count.get());
		}
	}

	@Nested
	@DisplayName("Chaining and State Management")
	class ChainingTests {

		@Test
		@DisplayName("Chaining stops after first match")
		void testChaining_stopsAfterFirstMatch() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			AtomicBoolean firstCalled = new AtomicBoolean(false);
			AtomicBoolean secondCalled = new AtomicBoolean(false);
			
			NodeMatcher.on(varDecl)
				.ifVariableDeclaration(vd -> firstCalled.set(true))
				.ifVariableDeclaration(vd -> secondCalled.set(true));
			
			assertTrue(firstCalled.get());
			assertFalse(secondCalled.get());
		}

		@Test
		@DisplayName("isHandled() returns true after match")
		void testIsHandled_returnsTrueAfterMatch() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			VariableDeclarationStatement varDecl = findFirstNodeOfType(cu, VariableDeclarationStatement.class);
			
			NodeMatcher<VariableDeclarationStatement> matcher = NodeMatcher.on(varDecl);
			assertFalse(matcher.isHandled());
			
			matcher.ifVariableDeclaration(vd -> {});
			assertTrue(matcher.isHandled());
		}
	}

	// Helper methods

	private Statement extractFirstStatement(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = (MethodDeclaration) type.bodyDeclarations().get(0);
		Block body = method.getBody();
		return (Statement) body.statements().get(0);
	}

	@SuppressWarnings("unchecked")
	private <T extends ASTNode> T findFirstNodeOfType(CompilationUnit cu, Class<T> nodeType) {
		final Object[] result = new Object[1];
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (result[0] == null && nodeType.isInstance(node)) {
					result[0] = node;
				}
			}
		});
		return (T) result[0];
	}
}
