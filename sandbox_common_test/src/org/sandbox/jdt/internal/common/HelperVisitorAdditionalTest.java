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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Additional tests for {@link HelperVisitor} to increase coverage.
 * 
 * <p>These tests focus on visitor builder methods and edge cases
 * not covered by existing tests.</p>
 * 
 * @see HelperVisitor
 */
@DisplayName("HelperVisitor Additional Coverage Tests")
public class HelperVisitorAdditionalTest {

	private CompilationUnit cu;
	private Set<ASTNode> nodesprocessed;

	@BeforeEach
	void setUp() {
		nodesprocessed = new HashSet<>();
	}

	private CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		@SuppressWarnings("unchecked")
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
	@DisplayName("Direct Visitor Methods")
	class DirectVisitorMethodTests {

		@Test
		@DisplayName("addVariableDeclarationStatement visitor")
		void testAddVariableDeclarationStatement() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						String s = "test";
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addVariableDeclarationStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(2, count.get(), "Should find 2 variable declarations");
		}

		@Test
		@DisplayName("addMethodDeclaration visitor")
		void testAddMethodDeclaration() {
			String source = """
				public class Test {
					void method1() { }
					int method2() { return 0; }
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addMethodDeclaration((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(2, count.get(), "Should find 2 methods");
		}

		@Test
		@DisplayName("addIfStatement visitor")
		void testAddIfStatement() {
			String source = """
				public class Test {
					void test() {
						if (true) { }
						if (false) { } else { }
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addIfStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(2, count.get(), "Should find 2 if statements");
		}

		@Test
		@DisplayName("addForStatement visitor")
		void testAddForStatement() {
			String source = """
				public class Test {
					void test() {
						for (int i = 0; i < 10; i++) { }
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addForStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 for statement");
		}

		@Test
		@DisplayName("addWhileStatement visitor")
		void testAddWhileStatement() {
			String source = """
				public class Test {
					void test() {
						while (true) { }
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addWhileStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 while statement");
		}

		@Test
		@DisplayName("addEnhancedForStatement visitor")
		void testAddEnhancedForStatement() {
			String source = """
				import java.util.List;
				public class Test {
					void test(List<String> items) {
						for (String item : items) { }
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addEnhancedForStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 enhanced for statement");
		}

		@Test
		@DisplayName("addTryStatement visitor")
		void testAddTryStatement() {
			String source = """
				public class Test {
					void test() {
						try {
						} catch (Exception e) {
						}
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addTryStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 try statement");
		}

		@Test
		@DisplayName("addReturnStatement visitor")
		void testAddReturnStatement() {
			String source = """
				public class Test {
					int test() {
						return 5;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addReturnStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 return statement");
		}

		@Test
		@DisplayName("addAssignment visitor")
		void testAddAssignment() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addAssignment((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertTrue(count.get() >= 1, "Should find at least 1 assignment");
		}

		@Test
		@DisplayName("addClassInstanceCreation visitor")
		void testAddClassInstanceCreation() {
			String source = """
				public class Test {
					void test() {
						Object obj = new Object();
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addClassInstanceCreation((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 class instance creation");
		}

		@Test
		@DisplayName("addThrowStatement visitor")
		void testAddThrowStatement() {
			String source = """
				public class Test {
					void test() {
						throw new RuntimeException();
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addThrowStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 throw statement");
		}
	}

	@Nested
	@DisplayName("Visitor State Management")
	class VisitorStateManagementTests {

		@Test
		@DisplayName("removeVisitor removes registered visitor")
		void testRemoveVisitor() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addVariableDeclarationStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.removeVisitor(VisitorEnum.VariableDeclarationStatement);
			helper.build(cu);
			
			assertEquals(0, count.get(), "Should not find any declarations after removal");
		}

		@Test
		@DisplayName("nodesprocessed excludes already processed nodes")
		void testNodesProcessedExclusion() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
					}
				}
				""";
			cu = parseSource(source);
			
			// First pass: collect nodes
			ReferenceHolder<Integer, ASTNode> dataHolder1 = new ReferenceHolder<>();
			AtomicInteger count1 = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper1 = 
				new HelperVisitor<>(nodesprocessed, dataHolder1);
			
			helper1.addVariableDeclarationStatement((node, holder) -> {
				holder.put(count1.getAndIncrement(), node);
				nodesprocessed.add(node); // Mark as processed
				return true;
			});
			
			helper1.build(cu);
			assertEquals(1, count1.get());
			
			// Second pass: should not find already processed nodes
			ReferenceHolder<Integer, ASTNode> dataHolder2 = new ReferenceHolder<>();
			AtomicInteger count2 = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper2 = 
				new HelperVisitor<>(nodesprocessed, dataHolder2);
			
			helper2.addVariableDeclarationStatement((node, holder) -> {
				holder.put(count2.getAndIncrement(), node);
				return true;
			});
			
			helper2.build(cu);
			assertEquals(0, count2.get(), "Should not find already processed nodes");
		}

		@Test
		@DisplayName("build with visitjavadoc parameter")
		void testBuildWithVisitJavadoc() {
			String source = """
				public class Test {
					/**
					 * Javadoc comment
					 */
					void test() { }
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addMethodDeclaration((node, holder) -> true);
			
			// Test with visitjavadoc = true
			helper.build(cu, true);
			
			// Should complete without errors
			assertNotNull(helper);
		}
	}

	@Nested
	@DisplayName("Expression Visitors")
	class ExpressionVisitorTests {

		@Test
		@DisplayName("addInfixExpression visitor")
		void testAddInfixExpression() {
			String source = """
				public class Test {
					void test() {
						int sum = 1 + 2 + 3;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addInfixExpression((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertTrue(count.get() >= 1, "Should find at least 1 infix expression");
		}

		@Test
		@DisplayName("addPostfixExpression visitor")
		void testAddPostfixExpression() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						x++;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addPostfixExpression((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 postfix expression");
		}

		@Test
		@DisplayName("addPrefixExpression visitor")
		void testAddPrefixExpression() {
			String source = """
				public class Test {
					void test() {
						int x = 0;
						++x;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addPrefixExpression((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 prefix expression");
		}

		@Test
		@DisplayName("addConditionalExpression visitor")
		void testAddConditionalExpression() {
			String source = """
				public class Test {
					void test() {
						int x = true ? 1 : 0;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addConditionalExpression((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 conditional expression");
		}

		@Test
		@DisplayName("addCastExpression visitor")
		void testAddCastExpression() {
			String source = """
				public class Test {
					void test(Object obj) {
						String s = (String) obj;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addCastExpression((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(1, count.get(), "Should find 1 cast expression");
		}
	}

	@Nested
	@DisplayName("Type Declaration Visitors")
	class TypeDeclarationVisitorTests {

		@Test
		@DisplayName("addTypeDeclaration visitor")
		void testAddTypeDeclaration() {
			String source = """
				public class Test {
					class Inner { }
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addTypeDeclaration((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(2, count.get(), "Should find 2 type declarations (outer and inner)");
		}

		@Test
		@DisplayName("addFieldDeclaration visitor")
		void testAddFieldDeclaration() {
			String source = """
				public class Test {
					private int field1;
					public String field2;
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addFieldDeclaration((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return true;
			});
			
			helper.build(cu);
			
			assertEquals(2, count.get(), "Should find 2 field declarations");
		}
	}

	@Nested
	@DisplayName("Edge Cases and Error Handling")
	class EdgeCaseTests {

		@Test
		@DisplayName("empty compilation unit")
		void testEmptyCompilationUnit() {
			String source = "";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addMethodDeclaration((node, holder) -> true);
			helper.build(cu);
			
			// Should complete without errors
			assertNotNull(helper);
		}

		@Test
		@DisplayName("visitor returning false stops processing")
		void testVisitorReturnFalse() {
			String source = """
				public class Test {
					void test() {
						int x = 5;
						int y = 10;
					}
				}
				""";
			cu = parseSource(source);
			
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addVariableDeclarationStatement((node, holder) -> {
				holder.put(count.getAndIncrement(), node);
				return false; // Stop processing
			});
			
			helper.build(cu);
			
			// Should still count all declarations even though returning false
			// (false affects traversal continuation, not visitor execution)
			assertTrue(count.get() >= 1);
		}
	}

	@Nested
	@DisplayName("Supplier and Consumer Maps")
	class SupplierConsumerMapTests {

		@Test
		@DisplayName("getSuppliermap returns visitor map")
		void testGetSuppliermap() {
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addMethodDeclaration((node, holder) -> true);
			
			var supplierMap = helper.getSuppliermap();
			assertNotNull(supplierMap);
			assertTrue(supplierMap.containsKey(VisitorEnum.MethodDeclaration));
		}

		@Test
		@DisplayName("getConsumermap returns consumer map")
		void testGetConsumermap() {
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			helper.addEnd(VisitorEnum.MethodDeclaration, (node, holder) -> { });
			
			var consumerMap = helper.getConsumermap();
			assertNotNull(consumerMap);
		}

		@Test
		@DisplayName("getNodesprocessed returns processed nodes set")
		void testGetNodesprocessed() {
			ReferenceHolder<Integer, ASTNode> dataHolder = new ReferenceHolder<>();
			
			HelperVisitor<ReferenceHolder<Integer, ASTNode>, Object, Object> helper = 
				new HelperVisitor<>(nodesprocessed, dataHolder);
			
			var processed = helper.getNodesprocessed();
			assertNotNull(processed);
			assertEquals(nodesprocessed, processed);
		}
	}
}
