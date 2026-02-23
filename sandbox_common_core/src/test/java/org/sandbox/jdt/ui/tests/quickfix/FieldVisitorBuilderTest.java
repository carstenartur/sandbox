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

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.compiler.IProblem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Unit tests for {@link org.sandbox.jdt.internal.common.FieldVisitorBuilder}.
 *
 * <p>These tests verify the fluent builder API for finding field declarations
 * with specific annotations and types. They run without Tycho/Eclipse runtime
 * using standalone ASTParser, which means bindings may not fully resolve.
 * This is intentional — it tests the fallback matching logic that uses
 * source-level type names when bindings are unavailable.</p>
 *
 * <h2>Key Scenarios Tested:</h2>
 * <ul>
 * <li>Finding @Deprecated fields by annotation + type (with resolved bindings)</li>
 * <li>Finding @Rule-like fields where JUnit is NOT on the classpath (null bindings)</li>
 * <li>Verifying that field visitors don't interfere with annotation visitors</li>
 * <li>Verifying that excluding(nodesprocessed) works for field visitors</li>
 * <li>Verifying that annotation nodesprocessed does NOT block field visitors</li>
 * </ul>
 *
 * @since 1.0
 */
@DisplayName("FieldVisitorBuilder Tests")
public class FieldVisitorBuilderTest {

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setEnvironment(new String[] {}, new String[] {}, null, true);
		parser.setBindingsRecovery(true);
		parser.setResolveBindings(true);
		parser.setUnitName("Test.java"); //$NON-NLS-1$
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Tests for basic field finding with annotation + type matching.
	 * Uses @Deprecated annotation which is always resolvable.
	 */
	@Nested
	@DisplayName("Basic Field Matching Tests")
	class BasicFieldMatchingTests {

		@Test
		@DisplayName("Find @Deprecated field of type String")
		void testFindDeprecatedStringField() {
			String source = """
					public class TestClass {
						@Deprecated
						public String oldField = "old";
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
				.ofType("java.lang.String") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(1, found.size(), "Should find one @Deprecated String field"); //$NON-NLS-1$
			assertTrue(found.get(0) instanceof FieldDeclaration);
		}

		@Test
		@DisplayName("Do NOT find field when annotation doesn't match")
		void testNoMatchWhenAnnotationDiffers() {
			String source = """
					public class TestClass {
						@SuppressWarnings("unused")
						public String field = "test";
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
				.ofType("java.lang.String") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(0, found.size(), "Should NOT find field when annotation doesn't match"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Do NOT find field when type doesn't match")
		void testNoMatchWhenTypeDiffers() {
			String source = """
					public class TestClass {
						@Deprecated
						public int count = 0;
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
				.ofType("java.lang.String") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(0, found.size(), "Should NOT find field when type doesn't match"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Find multiple fields with matching annotation and type")
		void testFindMultipleMatchingFields() {
			String source = """
					public class TestClass {
						@Deprecated
						public String field1 = "one";
						@Deprecated
						public String field2 = "two";
						public String field3 = "three";
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
				.ofType("java.lang.String") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(2, found.size(), "Should find two @Deprecated String fields"); //$NON-NLS-1$
		}
	}

	/**
	 * Tests for unresolved binding fallback matching.
	 * Simulates the scenario where JUnit classes are NOT on the classpath
	 * (as happens in standalone ASTParser without JUnit jars).
	 * The LambdaASTVisitor should fall back to source-level type name matching.
	 */
	@Nested
	@DisplayName("Unresolved Binding Fallback Tests (JUnit-like @Rule fields)")
	class UnresolvedBindingFallbackTests {

		@Test
		@DisplayName("Find @Rule ErrorCollector field via source-level name fallback")
		void testFindRuleErrorCollectorFieldFallback() {
			// Simulates the ErrorCollectorBasic test case from MigrationRulesToExtensionsTest.
			// Since JUnit is NOT on the classpath, bindings for @Rule and ErrorCollector
			// will be null/recovered. The LambdaASTVisitor must fall back to
			// matching by source type name.
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;
					import static org.hamcrest.CoreMatchers.equalTo;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();

						public void testMultipleErrors() {
							collector.checkThat("value1", equalTo("expected1"));
						}
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ErrorCollector") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(1, found.size(),
					"Should find @Rule ErrorCollector field via source name fallback " //$NON-NLS-1$
					+ "(bindings are null since JUnit is not on classpath)"); //$NON-NLS-1$
			assertTrue(found.get(0) instanceof FieldDeclaration);
		}

		@Test
		@DisplayName("Find @Rule TemporaryFolder field via source-level name fallback")
		void testFindRuleTemporaryFolderFieldFallback() {
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.TemporaryFolder;

					public class MyTest {
						@Rule
						public TemporaryFolder folder = new TemporaryFolder();
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.TemporaryFolder") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(1, found.size(),
					"Should find @Rule TemporaryFolder field via source name fallback"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Do NOT find @Rule ErrorCollector when searching for ExternalResource")
		void testExternalResourceDoesNotMatchErrorCollector() {
			// This tests that ExternalResource pattern does NOT falsely match ErrorCollector.
			// ErrorCollector does NOT extend ExternalResource.
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ExternalResource") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(0, found.size(),
					"ExternalResource type should NOT match ErrorCollector field"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Do NOT find @Rule Timeout when searching for ErrorCollector")
		void testTimeoutDoesNotMatchErrorCollector() {
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.Timeout;

					public class MyTest {
						@Rule
						public Timeout timeout = new Timeout(1000);
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			List<ASTNode> found = new ArrayList<>();

			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ErrorCollector") //$NON-NLS-1$
				.in(cu)
				.excluding(new HashSet<>())
				.processEach((field, holder) -> {
					found.add(field);
					return true;
				});

			assertEquals(0, found.size(),
					"ErrorCollector type should NOT match Timeout field"); //$NON-NLS-1$
		}
	}

	/**
	 * Tests for nodesprocessed interaction between annotation visitors and field visitors.
	 * This is the critical test — verifying that DSL-based annotation plugins
	 * (e.g., TestJUnitPlugin matching @Test) do NOT interfere with
	 * non-DSL field plugins (e.g., RuleErrorCollectorJUnitPlugin matching @Rule fields).
	 */
	@Nested
	@DisplayName("Annotation/Field nodesprocessed Interaction Tests")
	class NodesProcessedInteractionTests {

		@Test
		@DisplayName("Annotation visitor nodesprocessed does NOT block field visitor")
		void testAnnotationNodesprocessedDoesNotBlockFieldVisitor() {
			// Simulates: TestJUnitPlugin finds @Test annotation and adds it to nodesprocessed.
			// Then RuleErrorCollectorJUnitPlugin runs forField() with the same nodesprocessed set.
			// The @Test annotation node in nodesprocessed must NOT prevent finding the @Rule field.
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();

						@Deprecated
						public void testMethod() {}
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			Set<ASTNode> nodesprocessed = new HashSet<>();

			// Step 1: Simulate annotation visitor finding @Deprecated (like TestJUnitPlugin finds @Test)
			List<ASTNode> annotationNodes = new ArrayList<>();
			HelperVisitorFactory.forAnnotation("java.lang.Deprecated") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((node, holder) -> {
					annotationNodes.add(node);
					nodesprocessed.add(node); // Simulates TriggerPatternCleanupPlugin.find() adding to nodesprocessed
					return true;
				});
			assertEquals(1, annotationNodes.size(), "Should find @Deprecated annotation"); //$NON-NLS-1$
			assertEquals(1, nodesprocessed.size(), "nodesprocessed should contain the annotation"); //$NON-NLS-1$

			// Step 2: Field visitor should still find the @Rule ErrorCollector field
			// even though nodesprocessed contains the @Deprecated annotation node
			List<ASTNode> fieldNodes = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ErrorCollector") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					fieldNodes.add(field);
					return true;
				});

			assertEquals(1, fieldNodes.size(),
					"Field visitor MUST still find @Rule ErrorCollector field " //$NON-NLS-1$
					+ "even when nodesprocessed contains an annotation node"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Field visitor for ExternalResource does NOT block ErrorCollector field visitor")
		void testExternalResourceVisitorDoesNotBlockErrorCollector() {
			// Simulates: RuleExternalResourceJUnitPlugin runs first (for ExternalResource type),
			// then RuleErrorCollectorJUnitPlugin runs (for ErrorCollector type).
			// The ExternalResource visitor should NOT match the ErrorCollector field.
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			Set<ASTNode> nodesprocessed = new HashSet<>();

			// Step 1: ExternalResource visitor runs — should NOT match ErrorCollector
			List<ASTNode> externalResourceFields = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ExternalResource") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					externalResourceFields.add(field);
					nodesprocessed.add(field); // Would be added if it matched
					return true;
				});
			assertEquals(0, externalResourceFields.size(),
					"ExternalResource visitor should NOT match ErrorCollector field"); //$NON-NLS-1$
			assertEquals(0, nodesprocessed.size(),
					"nodesprocessed should be empty — nothing matched"); //$NON-NLS-1$

			// Step 2: ErrorCollector visitor should find the field
			List<ASTNode> errorCollectorFields = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ErrorCollector") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					errorCollectorFields.add(field);
					return true;
				});
			assertEquals(1, errorCollectorFields.size(),
					"ErrorCollector visitor MUST find the @Rule ErrorCollector field"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Full pipeline simulation: @Test migration then @Rule ErrorCollector migration")
		void testFullPipelineSimulation() {
			// Full simulation of the failing test scenario:
			// 1. TestJUnitPlugin (DSL-based) finds @Test annotation
			// 2. RuleExternalResourceJUnitPlugin finds @Rule ExternalResource (should not match)
			// 3. RuleTimeoutJUnitPlugin finds @Rule Timeout (should not match)
			// 4. RuleErrorCollectorJUnitPlugin finds @Rule ErrorCollector (SHOULD match)
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.Test;
					import org.junit.rules.ErrorCollector;
					import static org.hamcrest.CoreMatchers.equalTo;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();

						@Test
						public void testMultipleErrors() {
							collector.checkThat("value1", equalTo("expected1"));
							collector.checkThat("value2", equalTo("expected2"));
						}
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			Set<ASTNode> nodesprocessed = new HashSet<>();

			// Step 1: Annotation visitor for @Test (simulates TestJUnitPlugin)
			// Note: In standalone mode, @Test annotation binding may not resolve since
			// org.junit.Test is not on classpath. The AnnotationVisitorBuilder uses
			// callMarkerAnnotationVisitor which checks the annotation FQN.
			// With unresolved bindings, the simple name "Test" matches the simple part
			// of "org.junit.Test", so it should still be found.

			// Step 2: Field visitor for ExternalResource (simulates RuleExternalResourceJUnitPlugin)
			List<ASTNode> externalResourceFields = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ExternalResource") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					externalResourceFields.add(field);
					nodesprocessed.add(field);
					return true;
				});
			assertEquals(0, externalResourceFields.size(),
					"ExternalResource should NOT match ErrorCollector"); //$NON-NLS-1$

			// Step 3: Field visitor for Timeout (simulates RuleTimeoutJUnitPlugin)
			List<ASTNode> timeoutFields = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.Timeout") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					timeoutFields.add(field);
					nodesprocessed.add(field);
					return true;
				});
			assertEquals(0, timeoutFields.size(),
					"Timeout should NOT match ErrorCollector"); //$NON-NLS-1$

			// Step 4: Field visitor for ErrorCollector (simulates RuleErrorCollectorJUnitPlugin)
			// THIS IS THE CRITICAL ASSERTION — this is what fails in CI
			List<ASTNode> errorCollectorFields = new ArrayList<>();
			HelperVisitorFactory.forField()
				.withAnnotation("org.junit.Rule") //$NON-NLS-1$
				.ofType("org.junit.rules.ErrorCollector") //$NON-NLS-1$
				.in(cu)
				.excluding(nodesprocessed)
				.processEach((field, holder) -> {
					errorCollectorFields.add(field);
					return true;
				});

			assertEquals(1, errorCollectorFields.size(),
					"ErrorCollector visitor MUST find the @Rule ErrorCollector field. " //$NON-NLS-1$
					+ "nodesprocessed=" + nodesprocessed.size() //$NON-NLS-1$
					+ " (should be 0 since no previous plugin matched this field)"); //$NON-NLS-1$
		}
	}

	/**
	 * Tests for FieldVisitorBuilder excluding() behavior.
	 */
	@Nested
	@DisplayName("FieldVisitorBuilder Excluding Tests")
	class FieldExcludingTests {

		@Test
		@DisplayName("Validation: forField without withAnnotation and ofType should throw")
		void testValidationRequiresBothAnnotationAndType() {
			CompilationUnit cu = parseSource("public class TestClass {}"); //$NON-NLS-1$

			assertThrows(IllegalStateException.class, () -> {
				HelperVisitorFactory.forField()
					.in(cu)
					.excluding(new HashSet<>())
					.processEach((field, holder) -> true);
			}, "Should throw when withAnnotation and ofType not configured"); //$NON-NLS-1$
		}

		@Test
		@DisplayName("Validation: forField with only annotation should throw")
		void testValidationRequiresType() {
			CompilationUnit cu = parseSource("public class TestClass {}"); //$NON-NLS-1$

			assertThrows(IllegalStateException.class, () -> {
				HelperVisitorFactory.forField()
					.withAnnotation("java.lang.Deprecated") //$NON-NLS-1$
					.in(cu)
					.excluding(new HashSet<>())
					.processEach((field, holder) -> true);
			}, "Should throw when ofType not configured"); //$NON-NLS-1$
		}
	}

	/**
	 * Diagnostic tests that verify AST binding state for unresolved types.
	 * These tests document the expected behavior of the standalone ASTParser
	 * when types like JUnit's @Rule and ErrorCollector are not on the classpath.
	 */
	@Nested
	@DisplayName("AST Binding Diagnostics")
	class ASTBindingDiagnosticTests {

		@Test
		@DisplayName("Verify compiler problems exist for unresolved JUnit types")
		void testCompilerProblemsForUnresolvedTypes() {
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			IProblem[] problems = cu.getProblems();

			// We expect compiler problems because JUnit is not on the classpath
			assertTrue(problems.length > 0,
					"Should have compiler problems when JUnit is not on classpath"); //$NON-NLS-1$

			// Log problems for diagnostic purposes
			StringBuilder sb = new StringBuilder("Compiler problems:\n"); //$NON-NLS-1$
			for (IProblem p : problems) {
				sb.append(String.format("  [%s] line %d: %s%n", //$NON-NLS-1$
						p.isError() ? "ERROR" : "WARN", //$NON-NLS-1$ //$NON-NLS-2$
						p.getSourceLineNumber(),
						p.getMessage()));
			}
			System.out.println(sb);
		}

		@Test
		@DisplayName("Verify annotation binding state for @Rule (unresolved)")
		void testAnnotationBindingStateForUnresolvedRule() {
			String source = """
					package test;
					import org.junit.Rule;
					import org.junit.rules.ErrorCollector;

					public class MyTest {
						@Rule
						public ErrorCollector collector = new ErrorCollector();
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);

			// Walk the AST to find the @Rule annotation
			cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public boolean visit(FieldDeclaration node) {
					for (Object modifier : node.modifiers()) {
						if (modifier instanceof Annotation annotation) {
							ITypeBinding binding = annotation.resolveTypeBinding();
							String sourceName = annotation.getTypeName().getFullyQualifiedName();
							System.out.println("Annotation: " + sourceName); //$NON-NLS-1$
							System.out.println("  binding null? " + (binding == null)); //$NON-NLS-1$
							if (binding != null) {
								System.out.println("  isRecovered: " + binding.isRecovered()); //$NON-NLS-1$
								System.out.println("  qualifiedName: '" + binding.getQualifiedName() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
							}
						}
					}
					// Check field type binding
					ITypeBinding typeBinding = node.getType().resolveBinding();
					System.out.println("Field type source: " + node.getType()); //$NON-NLS-1$
					System.out.println("  typeBinding null? " + (typeBinding == null)); //$NON-NLS-1$
					if (typeBinding != null) {
						System.out.println("  isRecovered: " + typeBinding.isRecovered()); //$NON-NLS-1$
						System.out.println("  qualifiedName: '" + typeBinding.getQualifiedName() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
					}

					// Check fragment binding
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
					if (fragment.resolveBinding() != null) {
						ITypeBinding fragType = fragment.resolveBinding().getType();
						System.out.println("Fragment type null? " + (fragType == null)); //$NON-NLS-1$
						if (fragType != null) {
							System.out.println("  isRecovered: " + fragType.isRecovered()); //$NON-NLS-1$
							System.out.println("  qualifiedName: '" + fragType.getQualifiedName() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
						}
					} else {
						System.out.println("Fragment binding: null"); //$NON-NLS-1$
					}
					return true;
				}
			});
		}

		@Test
		@DisplayName("Verify annotation binding state for @Deprecated (resolved)")
		void testAnnotationBindingStateForResolvedDeprecated() {
			String source = """
					public class Test {
						@Deprecated
						public String field;
					}
					"""; //$NON-NLS-1$

			CompilationUnit cu = parseSource(source);
			IProblem[] problems = cu.getProblems();

			// @Deprecated and String should resolve without problems
			long errorCount = 0;
			for (IProblem p : problems) {
				if (p.isError()) {
					errorCount++;
				}
			}
			assertEquals(0, errorCount,
					"Should have no compiler errors when using only JDK types"); //$NON-NLS-1$

			// Walk the AST to verify binding state
			cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
				@Override
				public boolean visit(FieldDeclaration node) {
					for (Object modifier : node.modifiers()) {
						if (modifier instanceof Annotation annotation) {
							ITypeBinding binding = annotation.resolveTypeBinding();
							assertNotNull(binding,
									"@Deprecated binding should resolve"); //$NON-NLS-1$
							assertFalse(binding.isRecovered(),
									"@Deprecated binding should NOT be recovered"); //$NON-NLS-1$
							assertEquals("java.lang.Deprecated", binding.getQualifiedName()); //$NON-NLS-1$
						}
					}
					ITypeBinding typeBinding = node.getType().resolveBinding();
					assertNotNull(typeBinding,
							"String type binding should resolve"); //$NON-NLS-1$
					assertFalse(typeBinding.isRecovered(),
							"String type binding should NOT be recovered"); //$NON-NLS-1$
					return true;
				}
			});
		}
	}
}
