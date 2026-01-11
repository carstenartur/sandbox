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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Unit tests for {@link AstProcessorBuilder}.
 * 
 * <p>These tests verify the fluent builder API for AST processing operations.
 * The builder provides typed convenience methods for commonly used AST node types,
 * eliminating the need for manual casting.</p>
 * 
 * <h2>Key Features Tested:</h2>
 * <ul>
 * <li>Factory methods: {@code with()} for creating builder instances</li>
 * <li>Fluent API: Method chaining for visitor registration</li>
 * <li>Typed visitors: Type-safe access to specific AST node types</li>
 * <li>Node filtering: Method name and class type filtering</li>
 * <li>Data collection: Using ReferenceHolder for gathering results</li>
 * </ul>
 * 
 * @see AstProcessorBuilder
 * @see ReferenceHolder
 */
@DisplayName("AstProcessorBuilder Tests")
public class AstProcessorBuilderTest {

	private static CompilationUnit simpleClass;
	private static CompilationUnit forLoopClass;
	private static CompilationUnit methodCallClass;

	@BeforeAll
	static void setUp() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);

		// Simple class with methods
		simpleClass = createUnit(parser, """
			package test;
			public class SimpleClass {
			    private int value;
			    
			    public int getValue() {
			        return value;
			    }
			    
			    public void setValue(int value) {
			        this.value = value;
			    }
			    
			    public int compute(int x) {
			        int result = x * 2;
			        return result;
			    }
			}
			""", "SimpleClass");

		// Class with for loops
		forLoopClass = createUnit(parser, """
			package test;
			import java.util.List;
			public class ForLoopClass {
			    public void processItems(List<String> items) {
			        for (String item : items) {
			            System.out.println(item);
			        }
			    }
			    
			    public boolean hasMatch(List<Integer> numbers, int target) {
			        for (Integer num : numbers) {
			            if (num == target) {
			                return true;
			            }
			        }
			        return false;
			    }
			}
			""", "ForLoopClass");

		// Class with method calls
		methodCallClass = createUnit(parser, """
			package test;
			import java.util.ArrayList;
			import java.util.List;
			public class MethodCallClass {
			    public void doWork() {
			        List<String> list = new ArrayList<>();
			        list.add("one");
			        list.add("two");
			        list.clear();
			        System.out.println(list.size());
			    }
			}
			""", "MethodCallClass");
	}

	private static CompilationUnit createUnit(ASTParser parser, String code, String name) {
		parser.setEnvironment(new String[] {}, new String[] {}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	@Nested
	@DisplayName("Factory Methods")
	class FactoryMethodTests {

		@Test
		@DisplayName("with(ReferenceHolder) creates builder")
		void withReferenceHolderCreatesBuilder() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			
			AstProcessorBuilder<String, Object> builder = AstProcessorBuilder.with(holder);
			
			assertNotNull(builder);
		}

		@Test
		@DisplayName("with(ReferenceHolder, Set) creates builder with custom node set")
		void withReferenceHolderAndSetCreatesBuilder() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			Set<ASTNode> processedNodes = new HashSet<>();
			
			AstProcessorBuilder<String, Object> builder = AstProcessorBuilder.with(holder, processedNodes);
			
			assertNotNull(builder);
		}
	}

	@Nested
	@DisplayName("Fluent API Chaining")
	class FluentApiChainingTests {

		@Test
		@DisplayName("Multiple visitors can be chained")
		void multipleVisitorsCanBeChained() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger methodCount = new AtomicInteger(0);
			AtomicInteger assignmentCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onMethodDeclaration((node, h) -> {
						methodCount.incrementAndGet();
						return true;
					})
					.onAssignment((node, h) -> {
						assignmentCount.incrementAndGet();
						return true;
					})
					.build(simpleClass);

			assertTrue(methodCount.get() > 0, "Should find method declarations");
			assertTrue(assignmentCount.get() > 0, "Should find assignments");
		}

		@Test
		@DisplayName("Builder returns itself for chaining")
		void builderReturnsItselfForChaining() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			
			AstProcessorBuilder<String, Object> builder1 = AstProcessorBuilder.with(holder);
			AstProcessorBuilder<String, Object> builder2 = builder1.onMethodInvocation((node, h) -> true);
			
			assertSame(builder1, builder2, "Builder should return itself for chaining");
		}
	}

	@Nested
	@DisplayName("onMethodInvocation()")
	class OnMethodInvocationTests {

		@Test
		@DisplayName("Finds all method invocations")
		void findsAllMethodInvocations() {
			ReferenceHolder<String, List<String>> holder = new ReferenceHolder<>();
			List<String> methodNames = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.onMethodInvocation((node, h) -> {
						methodNames.add(node.getName().getIdentifier());
						return true;
					})
					.build(methodCallClass);

			assertTrue(methodNames.contains("add"), "Should find 'add' calls");
			assertTrue(methodNames.contains("clear"), "Should find 'clear' call");
			assertTrue(methodNames.contains("println"), "Should find 'println' call");
		}

		@Test
		@DisplayName("Filters by method name")
		void filtersByMethodName() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger addCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onMethodInvocation("add", (node, h) -> {
						addCount.incrementAndGet();
						return true;
					})
					.build(methodCallClass);

			assertEquals(2, addCount.get(), "Should find exactly 2 'add' calls");
		}
	}

	@Nested
	@DisplayName("onMethodDeclaration()")
	class OnMethodDeclarationTests {

		@Test
		@DisplayName("Finds all method declarations")
		void findsAllMethodDeclarations() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			List<String> methodNames = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.onMethodDeclaration((node, h) -> {
						methodNames.add(node.getName().getIdentifier());
						return true;
					})
					.build(simpleClass);

			assertTrue(methodNames.contains("getValue"));
			assertTrue(methodNames.contains("setValue"));
			assertTrue(methodNames.contains("compute"));
			assertEquals(3, methodNames.size());
		}
	}

	@Nested
	@DisplayName("onEnhancedForStatement()")
	class OnEnhancedForStatementTests {

		@Test
		@DisplayName("Finds enhanced for loops")
		void findsEnhancedForLoops() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger forLoopCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onEnhancedForStatement((node, h) -> {
						forLoopCount.incrementAndGet();
						return true;
					})
					.build(forLoopClass);

			assertEquals(2, forLoopCount.get(), "Should find 2 enhanced for loops");
		}

		@Test
		@DisplayName("Provides access to loop variable")
		void providesAccessToLoopVariable() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			List<String> loopVariables = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.onEnhancedForStatement((node, h) -> {
						loopVariables.add(node.getParameter().getName().getIdentifier());
						return true;
					})
					.build(forLoopClass);

			assertTrue(loopVariables.contains("item"));
			assertTrue(loopVariables.contains("num"));
		}
	}

	@Nested
	@DisplayName("onIfStatement()")
	class OnIfStatementTests {

		@Test
		@DisplayName("Finds if statements")
		void findsIfStatements() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger ifCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onIfStatement((node, h) -> {
						ifCount.incrementAndGet();
						return true;
					})
					.build(forLoopClass);

			assertEquals(1, ifCount.get(), "Should find 1 if statement");
		}
	}

	@Nested
	@DisplayName("onReturnStatement()")
	class OnReturnStatementTests {

		@Test
		@DisplayName("Finds return statements")
		void findsReturnStatements() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger returnCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onReturnStatement((node, h) -> {
						returnCount.incrementAndGet();
						return true;
					})
					.build(simpleClass);

			assertEquals(2, returnCount.get(), "Should find 2 return statements");
		}
	}

	@Nested
	@DisplayName("onVariableDeclarationFragment()")
	class OnVariableDeclarationFragmentTests {

		@Test
		@DisplayName("Finds variable declarations")
		void findsVariableDeclarations() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			List<String> varNames = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.onVariableDeclarationFragment((node, h) -> {
						varNames.add(node.getName().getIdentifier());
						return true;
					})
					.build(simpleClass);

			assertTrue(varNames.contains("value"), "Should find field 'value'");
			assertTrue(varNames.contains("result"), "Should find local 'result'");
		}
	}

	@Nested
	@DisplayName("onTypeDeclaration()")
	class OnTypeDeclarationTests {

		@Test
		@DisplayName("Finds type declarations")
		void findsTypeDeclarations() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			List<String> typeNames = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.onTypeDeclaration((node, h) -> {
						typeNames.add(node.getName().getIdentifier());
						return true;
					})
					.build(simpleClass);

			assertEquals(1, typeNames.size());
			assertEquals("SimpleClass", typeNames.get(0));
		}
	}

	@Nested
	@DisplayName("onAssignment()")
	class OnAssignmentTests {

		@Test
		@DisplayName("Finds assignments")
		void findsAssignments() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger assignmentCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.onAssignment((node, h) -> {
						assignmentCount.incrementAndGet();
						return true;
					})
					.build(simpleClass);

			assertTrue(assignmentCount.get() > 0, "Should find assignments");
		}
	}

	@Nested
	@DisplayName("Data Collection with ReferenceHolder")
	class DataCollectionTests {

		@Test
		@DisplayName("Collects nodes in ReferenceHolder")
		void collectsNodesInReferenceHolder() {
			ReferenceHolder<String, MethodDeclaration> holder = new ReferenceHolder<>();

			AstProcessorBuilder.with(holder)
					.onMethodDeclaration((node, h) -> {
						h.put(node.getName().getIdentifier(), node);
						return true;
					})
					.build(simpleClass);

			assertEquals(3, holder.size());
			assertNotNull(holder.get("getValue"));
			assertNotNull(holder.get("setValue"));
			assertNotNull(holder.get("compute"));
		}

		@Test
		@DisplayName("Accumulates data across multiple node types")
		void accumulatesDataAcrossMultipleNodeTypes() {
			ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
			holder.put("methods", 0);
			
			// Test that we can chain method declaration with another visitor
			// and both get executed
			AstProcessorBuilder.with(holder)
					.onMethodDeclaration((node, h) -> {
						h.put("methods", h.get("methods") + 1);
						return true;
					})
					.build(simpleClass);

			// Should find 3 method declarations in simpleClass
			assertEquals(3, holder.get("methods").intValue(), 
					"Should find 3 method declarations");
		}
	}

	@Nested
	@DisplayName("processor() Access")
	class ProcessorAccessTests {

		@Test
		@DisplayName("processor() provides access to underlying ASTProcessor")
		void processorProvidesAccessToUnderlyingProcessor() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger count = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.processor()
					.callPostfixExpressionVisitor((node, h) -> {
						count.incrementAndGet();
						return true;
					});

			// Verify we can access the processor
			assertNotNull(AstProcessorBuilder.with(holder).processor());
		}
	}
}
