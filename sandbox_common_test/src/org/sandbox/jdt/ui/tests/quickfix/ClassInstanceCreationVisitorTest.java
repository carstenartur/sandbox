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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;

/**
 * Tests for verifying that LambdaASTVisitor correctly finds ALL ClassInstanceCreation nodes.
 * 
 * <p>This test class addresses issues identified in PR #593 where the ClassInstanceCreation
 * visitor was only finding some instances instead of all instances in the code.</p>
 * 
 * <h2>Key Testing Areas</h2>
 * <ul>
 * <li>Multiple ClassInstanceCreation nodes in the same method</li>
 * <li>ClassInstanceCreation in nested blocks (if, while, for)</li>
 * <li>ClassInstanceCreation inside lambdas</li>
 * <li>ClassInstanceCreation in anonymous classes</li>
 * <li>Standalone ClassInstanceCreation without preceding method calls</li>
 * <li>Scope function parameter behavior</li>
 * <li>Chained visitor patterns similar to JFace plugin</li>
 * </ul>
 * 
 * @author Carsten Hammer
 */
public class ClassInstanceCreationVisitorTest {
	
	/**
	 * Helper method to create a CompilationUnit from source code.
	 * 
	 * @param code the Java source code
	 * @param name the unit name
	 * @return the parsed CompilationUnit
	 */
	private static CompilationUnit createUnit(String code, String name) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
		parser.setCompilerOptions(options);
		parser.setEnvironment(new String[]{}, new String[]{}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
	
	/**
	 * Test that the visitor finds all ClassInstanceCreation nodes in a simple method.
	 * This is the most basic test case - multiple instances in a linear flow.
	 */
	@Test
	public void testFindAllClassInstanceCreationInMethod() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					monitor.beginTask("Task", 100);
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
					MockProgressMonitor sub3 = new MockSubProgressMonitor(monitor, 20);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(3, found.size(), "Should find all 3 ClassInstanceCreation nodes");
	}
	
	/**
	 * Test that the visitor finds ClassInstanceCreation nodes with type filtering.
	 * This tests the typeof parameter functionality.
	 */
	@Test
	public void testFindClassInstanceCreationWithTypeFilter() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					monitor.beginTask("Task", 100);
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
					String s = new String("test");
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> foundFiltered = new ArrayList<>();
		List<ClassInstanceCreation> foundAll = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		
		// First find all without filter
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hvAll = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hvAll.addClassInstanceCreation((node, holder) -> {
			foundAll.add(node);
			return true;
		});
		hvAll.build(cu);
		
		// Then find only MockSubProgressMonitor instances
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hvFiltered = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hvFiltered.addClassInstanceCreation(MockSubProgressMonitor.class, (node, holder) -> {
			foundFiltered.add(node);
			return true;
		});
		hvFiltered.build(cu);
		
		assertEquals(3, foundAll.size(), "Should find 3 total ClassInstanceCreation nodes (2 MockSubProgressMonitor + 1 String)");
		assertEquals(2, foundFiltered.size(), "Should find only 2 MockSubProgressMonitor instances when filtered");
	}
	
	/**
	 * Test ClassInstanceCreation in nested blocks (if, while, for loops).
	 * This ensures the visitor properly traverses nested structures.
	 */
	@Test
	public void testFindClassInstanceCreationInNestedBlocks() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					monitor.beginTask("Task", 100);
					
					if (true) {
						MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 10);
					}
					
					while (false) {
						MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 20);
					}
					
					for (int i = 0; i < 5; i++) {
						MockProgressMonitor sub3 = new MockSubProgressMonitor(monitor, 30);
					}
					
					MockProgressMonitor sub4 = new MockSubProgressMonitor(monitor, 40);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(4, found.size(), "Should find all 4 ClassInstanceCreation nodes including those in nested blocks");
	}
	
	/**
	 * Test ClassInstanceCreation inside lambdas.
	 * This ensures the visitor traverses into lambda expressions.
	 */
	@Test
	public void testFindClassInstanceCreationInLambdas() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					Runnable r = () -> {
						MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					};
					
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(2, found.size(), "Should find all 2 ClassInstanceCreation nodes including one inside lambda");
	}
	
	/**
	 * Test ClassInstanceCreation in anonymous classes.
	 * This ensures the visitor traverses into anonymous class bodies.
	 */
	@Test
	public void testFindClassInstanceCreationInAnonymousClasses() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
						}
					};
					
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(3, found.size(), "Should find all 3 ClassInstanceCreation nodes (1 Runnable anonymous + 2 MockSubProgressMonitor)");
	}
	
	/**
	 * Test standalone ClassInstanceCreation without any preceding method calls.
	 * This tests the case where there's no beginTask() before SubProgressMonitor creation.
	 */
	@Test
	public void testFindStandaloneClassInstanceCreation() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					// No beginTask() call here
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
					MockProgressMonitor sub3 = new MockSubProgressMonitor(monitor, 20);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(3, found.size(), "Should find all 3 ClassInstanceCreation nodes even without beginTask");
	}
	
	/**
	 * Test the scope function parameter and its effect on visitor traversal.
	 * This is CRITICAL - it tests how the navigate/scope function limits the search space.
	 */
	@Test
	public void testScopeFunctionBehavior() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					monitor.beginTask("Task", 100);
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
					
					if (true) {
						MockProgressMonitor sub3 = new MockSubProgressMonitor(monitor, 20);
					}
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		// Test 1: Find all instances without scope limitation
		List<ClassInstanceCreation> foundAll = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hvAll = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hvAll.addClassInstanceCreation((node, holder) -> {
			foundAll.add(node);
			return true;
		});
		hvAll.build(cu);
		
		assertEquals(3, foundAll.size(), "Without scope function, should find all 3 ClassInstanceCreation nodes");
		
		// Test 2: Use ASTProcessor with scope function that navigates to Block
		// This mimics the JFace plugin pattern
		List<ClassInstanceCreation> foundWithScope = new ArrayList<>();
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		
		AstProcessorBuilder.with(dataholder, nodesprocessed)
			.processor()
			.callMethodInvocationVisitor(MockProgressMonitor.class, "beginTask", (node, holder) -> {
				holder.put("beginTask", node);
				return true;
			}, s -> ASTNodes.getTypedAncestor(s, Block.class))
			.callClassInstanceCreationVisitor((node, holder) -> {
				foundWithScope.add(node);
				return true;
			})
			.build(cu);
		
		// After navigating to Block containing beginTask, we should find ALL ClassInstanceCreation
		// nodes in that Block (including the one in the nested if block)
		assertTrue(foundWithScope.size() > 0, "With scope function, should find ClassInstanceCreation nodes in the Block scope");
		assertEquals(foundAll.size(), foundWithScope.size(), 
				"Scope function should find all nodes when the Block contains all instances");
	}
	
	/**
	 * Test the chained visitor pattern similar to JFace plugin.
	 * This reproduces the exact pattern used in JFacePlugin.java to find the issue.
	 */
	@Test
	public void testJFacePluginPattern() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					monitor.beginTask("Task", 100);
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
					MockProgressMonitor sub3 = new MockSubProgressMonitor(monitor, 20);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		// Track how many beginTask and ClassInstanceCreation nodes we find
		List<MethodInvocation> beginTaskNodes = new ArrayList<>();
		List<ClassInstanceCreation> cicNodes = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		
		AstProcessorBuilder.with(dataholder, nodesprocessed)
			.processor()
			.callMethodInvocationVisitor(MockProgressMonitor.class, "beginTask", (node, holder) -> {
				beginTaskNodes.add(node);
				holder.put("beginTask", node);
				return true;
			}, s -> ASTNodes.getTypedAncestor(s, Block.class))
			.callClassInstanceCreationVisitor(MockSubProgressMonitor.class, (node, holder) -> {
				cicNodes.add(node);
				return true;
			})
			.build(cu);
		
		assertEquals(1, beginTaskNodes.size(), "Should find 1 beginTask invocation");
		assertEquals(3, cicNodes.size(), "Should find all 3 MockSubProgressMonitor instances after beginTask");
	}
	
	/**
	 * Test with multiple blocks and verify scope behavior.
	 * This tests what happens when ClassInstanceCreation nodes are in different blocks.
	 */
	@Test
	public void testMultipleBlocksWithSeparateInstances() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void method1(MockProgressMonitor monitor) {
					monitor.beginTask("Task1", 50);
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
				}
				
				public void method2(MockProgressMonitor monitor) {
					monitor.beginTask("Task2", 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 50);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		// Find all without scope
		List<ClassInstanceCreation> foundAll = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		hv.addClassInstanceCreation((node, holder) -> {
			foundAll.add(node);
			return true;
		});
		hv.build(cu);
		
		assertEquals(2, foundAll.size(), "Should find both ClassInstanceCreation nodes in different methods");
	}
	
	/**
	 * Test edge case: deeply nested ClassInstanceCreation.
	 * This ensures the visitor traverses arbitrarily deep structures.
	 */
	@Test
	public void testDeeplyNestedClassInstanceCreation() {
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					if (true) {
						if (true) {
							while (false) {
								for (int i = 0; i < 1; i++) {
									MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
								}
							}
						}
					}
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 50);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> found = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		HelperVisitor<ReferenceHolder<String, Object>, String, Object> hv = 
				new HelperVisitor<>(nodesprocessed, new ReferenceHolder<>());
		
		hv.addClassInstanceCreation((node, holder) -> {
			found.add(node);
			return true;
		});
		
		hv.build(cu);
		
		assertEquals(2, found.size(), "Should find both ClassInstanceCreation nodes including deeply nested one");
	}
	
	/**
	 * Reproduces the AstProcessorBuilder chained visitor bug from PR #678.
	 * 
	 * <p>When using chained visitors (MethodInvocation → ClassInstanceCreation), 
	 * if the first visitor (beginTask) does NOT match anything, the second visitor 
	 * (ClassInstanceCreation) is never called because the chain progression happens 
	 * inside the first visitor's match callback in {@code ASTProcessor.process()}.</p>
	 * 
	 * <p>This means standalone ClassInstanceCreation nodes (without a preceding 
	 * beginTask) are never detected by the chained visitor.</p>
	 * 
	 * <p>The workaround used in JFacePlugin.java is to use a direct ASTVisitor for 
	 * the second pass instead of relying on chained visitors.</p>
	 * 
	 * @see <a href="https://github.com/carstenartur/sandbox/pull/678">PR #678</a>
	 */
	@Disabled("AstProcessorBuilder bug: chained ClassInstanceCreation visitor not called when first MethodInvocation visitor has no match (PR #678)")
	@Test
	public void testChainedVisitorWithoutFirstMatchBug() {
		// Code WITHOUT beginTask() - only standalone ClassInstanceCreation nodes
		String code = """
			package test;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockProgressMonitor;
			import org.sandbox.jdt.ui.tests.quickfix.mock.MockSubProgressMonitor;
			
			public class Test {
				public void doWork(MockProgressMonitor monitor) {
					// No beginTask() call here - standalone SubProgressMonitor usage
					MockProgressMonitor sub1 = new MockSubProgressMonitor(monitor, 50);
					MockProgressMonitor sub2 = new MockSubProgressMonitor(monitor, 30);
				}
			}
			""";
		
		CompilationUnit cu = createUnit(code, "Test");
		
		List<ClassInstanceCreation> cicNodes = new ArrayList<>();
		Set<ASTNode> nodesprocessed = null;
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		
		// Use the same chained pattern as JFacePlugin: MethodInvocation -> ClassInstanceCreation
		// Since there's no beginTask(), the first visitor will have ZERO matches.
		// BUG: The second visitor should still be able to find ClassInstanceCreation nodes,
		// but due to the chaining architecture, it is never called.
		AstProcessorBuilder.with(dataholder, nodesprocessed)
			.processor()
			.callMethodInvocationVisitor(MockProgressMonitor.class, "beginTask", (node, holder) -> {
				// This callback is never triggered (no beginTask in the code)
				return true;
			}, s -> ASTNodes.getTypedAncestor(s, Block.class))
			.callClassInstanceCreationVisitor(MockSubProgressMonitor.class, (node, holder) -> {
				cicNodes.add(node);
				return true;
			})
			.build(cu);
		
		// This assertion FAILS because the chained ClassInstanceCreation visitor
		// is never called when the MethodInvocation visitor has no matches.
		// The workaround is to use a separate ASTVisitor for standalone detection.
		assertEquals(2, cicNodes.size(), 
			"Chained ClassInstanceCreation visitor should find nodes even when " +
			"the first MethodInvocation visitor has no matches");
	}
}
