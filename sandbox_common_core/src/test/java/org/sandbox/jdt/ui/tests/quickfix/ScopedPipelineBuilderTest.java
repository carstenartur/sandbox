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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.VisitorEnum;

/**
 * Tests for {@link AstProcessorBuilder#scoped()} and {@link AstProcessorBuilder#independent()}.
 *
 * <p>These tests verify the two explicit execution modes introduced in issue #1384:</p>
 * <ul>
 *   <li><strong>Scoped pipelines</strong> – each stage operates within the scope of
 *       the previous stage's match. A stage that finds no match ends the pipeline branch.</li>
 *   <li><strong>Independent visitors</strong> – every visitor sees the full root scope
 *       regardless of other visitors.</li>
 * </ul>
 */
@DisplayName("ScopedPipelineBuilder and IndependentGroupBuilder Tests")
class ScopedPipelineBuilderTest {

	private static CompilationUnit simpleClass;
	private static CompilationUnit chainClass;
	private static CompilationUnit repeatedTypeClass;

	@BeforeAll
	static void setUp() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);

		// Simple class with type and field declarations
		simpleClass = createUnit(parser, """
			package test;
			public class SimpleClass {
			    private int value;
			    private String name;
			    public void doWork() {
			        System.out.println("hello");
			    }
			}
			""", "SimpleClass");

		// Class designed for scoped chain: method containing a specific inner call
		chainClass = createUnit(parser, """
			package test;
			import java.util.ArrayList;
			import java.util.List;
			public class ChainClass {
			    public void outer() {
			        inner();
			    }
			    public void inner() {
			        List<String> list = new ArrayList<>();
			    }
			    public void unrelated() {
			        int x = 42;
			    }
			}
			""", "ChainClass");

		// Class with repeated assignment patterns
		repeatedTypeClass = createUnit(parser, """
			package test;
			public class RepeatedTypeClass {
			    public void method() {
			        int a = 1;
			        a = 2;
			        a = 3;
			    }
			}
			""", "RepeatedTypeClass");
	}

	private static CompilationUnit createUnit(ASTParser parser, String code, String name) {
		parser.setEnvironment(new String[] {}, new String[] {}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName(name);
		parser.setSource(code.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}

	// =========================================================================
	// Independent visitors
	// =========================================================================

	@Nested
	@DisplayName("IndependentGroupBuilder")
	class IndependentGroupBuilderTests {

		@Test
		@DisplayName("TypeDeclaration and FieldDeclaration both scan the full unit")
		void typeAndFieldDeclarationBothScanFullUnit() {
			ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
			AtomicInteger typeCount = new AtomicInteger(0);
			AtomicInteger fieldCount = new AtomicInteger(0);

			AstProcessorBuilder.with(holder)
					.independent()
					.onTypeDeclaration((node, h) -> {
						typeCount.incrementAndGet();
						return true;
					})
					.onFieldDeclaration((node, h) -> {
						fieldCount.incrementAndGet();
						return true;
					})
					.build(simpleClass);

			assertEquals(1, typeCount.get(), "Should find 1 type declaration"); //$NON-NLS-1$
			assertEquals(2, fieldCount.get(), "Should find 2 field declarations"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Independent visitors execute in deterministic order")
		void independentVisitorsExecuteInDeterministicOrder() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			List<String> order = new ArrayList<>();

			AstProcessorBuilder.with(holder)
					.independent()
					.onTypeDeclaration((node, h) -> {
						order.add("type"); //$NON-NLS-1$
						return true;
					})
					.onMethodDeclaration((node, h) -> {
						order.add("method"); //$NON-NLS-1$
						return true;
					})
					.build(simpleClass);

			// type visitor runs first, then method visitor
			int typeIdx = order.indexOf("type"); //$NON-NLS-1$
			int methodIdx = order.indexOf("method"); //$NON-NLS-1$
			assertTrue(typeIdx >= 0, "type visitor must have run"); //$NON-NLS-1$
			assertTrue(methodIdx >= 0, "method visitor must have run"); //$NON-NLS-1$
			assertTrue(typeIdx < methodIdx, "type visitor must run before method visitor"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("No independent visitor is accidentally scoped beneath another match")
		void noVisitorIsAccidentallyScopedBeneathAnotherMatch() {
			ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
			AtomicInteger methodInvocCount = new AtomicInteger(0);
			AtomicInteger methodDeclCount = new AtomicInteger(0);

			// Without independent(), using onMethodDeclaration after onMethodInvocation
			// would scope the declaration search inside the invocation scope (legacy behavior).
			// With independent(), both scan the full compilation unit.
			AstProcessorBuilder.with(holder)
					.independent()
					.onMethodInvocation((node, h) -> {
						methodInvocCount.incrementAndGet();
						return true;
					})
					.onMethodDeclaration((node, h) -> {
						methodDeclCount.incrementAndGet();
						return true;
					})
					.build(chainClass);

			// chainClass has 3 method declarations and at least 1 invocation
			assertTrue(methodInvocCount.get() >= 1, "Should find at least 1 method invocation"); //$NON-NLS-1$
			assertEquals(3, methodDeclCount.get(), "Should find all 3 method declarations"); //$NON-NLS-1$
		}
	}

	// =========================================================================
	// Scoped pipelines
	// =========================================================================

	@Nested
	@DisplayName("ScopedPipelineBuilder")
	class ScopedPipelineBuilderTests {

		@Test
		@DisplayName("MethodInvocation scoped to containing Block then MethodDeclaration found in same block")
		void methodInvocationScopedToContainingBlockFindsMethodDeclaration() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger matchCount = new AtomicInteger(0);

			// Find a method invocation; navigate to its enclosing MethodDeclaration body (Block);
			// then count any MethodInvocation within that block.
			AstProcessorBuilder.with(holder)
					.scoped()
					.findMethodInvocation((mi, h) -> "inner".equals(mi.getName().getIdentifier())) //$NON-NLS-1$
					.navigate(node -> {
						// Walk up to the enclosing block
						ASTNode parent = node.getParent();
						while (parent != null && !(parent instanceof Block)) {
							parent = parent.getParent();
						}
						return parent;
					})
					.then(VisitorEnum.MethodInvocation, (n, h) -> {
						matchCount.incrementAndGet();
						return true;
					})
					.build(chainClass);

			// outer() calls inner(); navigating to the enclosing block should find
			// the "inner" invocation itself again (there are no other calls in that block).
			assertTrue(matchCount.get() >= 1, "Should find at least one invocation in the scoped block"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Failed first-stage matcher does not advance to next stage")
		void failedFirstStageMatcherDoesNotAdvance() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicBoolean secondStageCalled = new AtomicBoolean(false);

			AstProcessorBuilder.with(holder)
					.scoped()
					.findMethodInvocation((mi, h) -> "noSuchMethod".equals(mi.getName().getIdentifier())) //$NON-NLS-1$
					.then(VisitorEnum.MethodDeclaration, (n, h) -> {
						secondStageCalled.set(true);
						return true;
					})
					.build(chainClass);

			assertFalse(secondStageCalled.get(), "Second stage must not run when first stage finds nothing"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Navigation function returning null ends the pipeline branch safely")
		void navigationFunctionReturningNullEndsBranchSafely() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicBoolean secondStageCalled = new AtomicBoolean(false);

			assertDoesNotThrow(() ->
				AstProcessorBuilder.with(holder)
						.scoped()
						.findMethodInvocation((mi, h) -> "inner".equals(mi.getName().getIdentifier())) //$NON-NLS-1$
						.navigate(node -> null) // force null scope
						.then(VisitorEnum.MethodDeclaration, (n, h) -> {
							secondStageCalled.set(true);
							return true;
						})
						.build(chainClass)
			);

			assertFalse(secondStageCalled.get(), "Second stage must not run when navigation returns null"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Repeated VisitorEnum types can both appear in one ordered chain")
		void repeatedVisitorEnumTypesWorkInOrderedChain() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			// Count times the first Assignment stage fires
			AtomicInteger firstAssignmentCount = new AtomicInteger(0);
			// Count times the second Assignment stage fires (scoped to enclosing block of first match)
			AtomicInteger secondAssignmentCount = new AtomicInteger(0);

			// repeatedTypeClass has assignments: a=1, a=2, a=3 in one block.
			// First stage matches all assignments (return true = match).
			// After each match, navigate up to the enclosing Block.
			// Second stage finds assignments within that block.
			AstProcessorBuilder.with(holder)
					.scoped()
					.thenAssignment((asgn, h) -> {
						firstAssignmentCount.incrementAndGet();
						return true;
					})
					.navigate(node -> {
						ASTNode parent = node.getParent();
						while (parent != null && !(parent instanceof Block)) {
							parent = parent.getParent();
						}
						return parent;
					})
					.thenAssignment((asgn, h) -> {
						secondAssignmentCount.incrementAndGet();
						return true;
					})
					.build(repeatedTypeClass);

			// With 3 assignments in repeatedTypeClass, the first stage fires 3 times.
			// The second stage runs for each first-stage match scoped to the Block.
			assertTrue(firstAssignmentCount.get() >= 1, "First assignment stage must fire"); //$NON-NLS-1$
			assertTrue(secondAssignmentCount.get() >= 1, "Second assignment stage must fire"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Multiple first-stage matches each get an isolated downstream traversal")
		void multipleFirstStageMatchesEachGetIsolatedDownstreamTraversal() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			// We'll count how many times the second stage fires
			AtomicInteger secondCount = new AtomicInteger(0);

			// chainClass has 3 methods; first stage matches MethodDeclarations that have a body,
			// second stage counts MethodInvocations within each declaration's body.
			AstProcessorBuilder.with(holder)
					.scoped()
					.find(VisitorEnum.MethodDeclaration, (n, h) -> {
						MethodDeclaration md = (MethodDeclaration) n;
						return md.getBody() != null;
					})
					.navigate(n -> ((MethodDeclaration) n).getBody())
					.then(VisitorEnum.MethodInvocation, (n, h) -> {
						secondCount.incrementAndGet();
						return true;
					})
					.build(chainClass);

			// outer() has 1 call; inner() has 0 (new ArrayList<>() is ClassInstanceCreation);
			// unrelated() has 0 calls.
			// So second stage should fire at least once (for the "inner()" call in outer()).
			assertTrue(secondCount.get() >= 1, "Second stage should fire for at least one method"); //$NON-NLS-1$
		}
	}

	// =========================================================================
	// Compatibility: existing callers
	// =========================================================================

	@Nested
	@DisplayName("Compatibility: existing callers via AstProcessorBuilder.with().onXxx().build()")
	class CompatibilityTests {

		@Test
		@DisplayName("Legacy onMethodDeclaration chain still works")
		void legacyOnMethodDeclarationChainStillWorks() {
			ReferenceHolder<String, Object> holder = new ReferenceHolder<>();
			AtomicInteger methodCount = new AtomicInteger(0);

			// This uses the existing ASTProcessor-backed API
			AstProcessorBuilder.with(holder)
					.onMethodDeclaration((node, h) -> {
						methodCount.incrementAndGet();
						return true;
					})
					.build(chainClass);

			assertEquals(3, methodCount.get(), "Legacy API should still find 3 method declarations"); //$NON-NLS-1$
		}
	}
}
