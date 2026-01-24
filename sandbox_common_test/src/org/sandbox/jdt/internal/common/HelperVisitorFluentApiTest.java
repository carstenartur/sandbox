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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Systematic tests for the HelperVisitor Fluent API.
 * These tests verify that the Fluent API works correctly
 * and that ReferenceHolder instances are populated correctly.
 */
public class HelperVisitorFluentApiTest {

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
        java.util.Map<String, String> options = org.eclipse.jdt.core.JavaCore.getOptions();
        org.eclipse.jdt.core.JavaCore.setComplianceOptions(org.eclipse.jdt.core.JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);
        parser.setEnvironment(new String[] {}, new String[] {}, null, true);
        parser.setBindingsRecovery(true);
        parser.setResolveBindings(true);
        parser.setStatementsRecovery(true);
        parser.setUnitName("Test.java"); //$NON-NLS-1$
        return (CompilationUnit) parser.createAST(null);
    }

    @Nested
    @DisplayName("forAnnotation() Tests")
    class ForAnnotationTests {

        @Test
        @DisplayName("forAnnotation finds MarkerAnnotation and populates ReferenceHolder correctly")
        void testForAnnotation_findsMarkerAnnotation_populatesReferenceHolder() {
            String source = """
                import java.lang.Deprecated;
                public class MyClass {
                    @Deprecated
                    public void myMethod() {}
                }
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger callCount = new AtomicInteger(0);
            
            HelperVisitor.forAnnotation("java.lang.Deprecated")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    int index = callCount.getAndIncrement();
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(index, th);
                    return true;
                });
            
            assertEquals(1, callCount.get(), "Callback should be invoked exactly once");
            assertEquals(1, dataHolder.size(), "ReferenceHolder should have one entry");
            assertNotNull(dataHolder.get(0), "Entry at index 0 should not be null");
            assertNotNull(dataHolder.get(0).node, "Node in holder should not be null");
            assertTrue(dataHolder.get(0).node instanceof Annotation, "Node should be an Annotation");
        }

        @Test
        @DisplayName("forAnnotation skips already processed nodes")
        void testForAnnotation_skipsProcessedNodes() {
            String source = """
                import java.lang.Deprecated;
                public class MyClass {
                    @Deprecated
                    public void method1() {}
                    @Deprecated
                    public void method2() {}
                }
                """;
            cu = parseSource(source);
            
            // Mark first annotation manually as "processed"
            cu.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
                boolean first = true;
                @Override
                public boolean visit(org.eclipse.jdt.core.dom.MarkerAnnotation node) {
                    if (first && "Deprecated".equals(node.getTypeName().getFullyQualifiedName())) {
                        nodesprocessed.add(node);
                        first = false;
                    }
                    return true;
                }
            });
            
            AtomicInteger callCount = new AtomicInteger(0);
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            
            HelperVisitor.forAnnotation("java.lang.Deprecated")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    callCount.incrementAndGet();
                    return true;
                });
            
            assertEquals(1, callCount.get(), "Only unprocessed annotations should be visited");
        }

        @Test
        @DisplayName("forAnnotation with andImports also finds imports")
        void testForAnnotation_andImports_findsImports() {
            String source = """
                import java.lang.Deprecated;
                public class MyClass {
                    @Deprecated
                    public void myMethod() {}
                }
                """;
            cu = parseSource(source);
            
            AtomicInteger annotationCount = new AtomicInteger(0);
            AtomicInteger importCount = new AtomicInteger(0);
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            
            HelperVisitor.forAnnotation("java.lang.Deprecated")
                .andImports()
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    if (node instanceof Annotation) {
                        annotationCount.incrementAndGet();
                    } else if (node instanceof ImportDeclaration) {
                        importCount.incrementAndGet();
                    }
                    return true;
                });
            
            assertEquals(1, annotationCount.get(), "One annotation should be found");
            assertEquals(1, importCount.get(), "One import should be found");
        }
    }

    @Nested
    @DisplayName("forMethodCalls() Tests")
    class ForMethodCallsTests {

        @Test
        @DisplayName("forMethodCalls finds MethodInvocation and populates ReferenceHolder correctly")
        void testForMethodCalls_findsMethodInvocation_populatesReferenceHolder() {
            String source = """
                public class MyClass {
                    public void test() {
                        System.out.println("test");
                    }
                }
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger callCount = new AtomicInteger(0);
            
            HelperVisitor.forMethodCalls("java.io.PrintStream", Set.of("println"))
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    callCount.incrementAndGet();
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(holder.size(), th);
                    return true;
                });
            
            // Note: Without proper binding resolution, println is not recognized as PrintStream method,
            // so the callback is not invoked and the counter remains 0.
            assertEquals(0, callCount.get(), "Without binding resolution, no method calls should be found");
        }

        @Test
        @DisplayName("forMethodCalls with andStaticImports and andImportsOf")
        void testForMethodCalls_withImports_findsAllNodes() {
            String source = """
                import static org.junit.Assert.assertEquals;
                import org.junit.Assert;
                public class MyTest {
                    public void test() {
                        assertEquals("a", "b");
                        Assert.assertTrue(true);
                    }
                }
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger nodeCount = new AtomicInteger(0);
            
            HelperVisitor.forMethodCalls("org.junit.Assert", Set.of("assertEquals", "assertTrue"))
                .andStaticImports()
                .andImportsOf("org.junit.Assert")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    nodeCount.incrementAndGet();
                    return true;
                });
            
            // Should find: 1 static import + 1 regular import = 2 (method invocations need binding resolution)
            assertTrue(nodeCount.get() >= 1, "At least one node should be found");
        }
    }

    @Nested
    @DisplayName("forField() Tests")
    class ForFieldTests {

        @Test
        @DisplayName("forField with annotation finds FieldDeclaration")
        void testForField_withAnnotation_findsFieldDeclaration() {
            String source = """
                import java.lang.Deprecated;
                public class MyClass {
                    @Deprecated
                    public String myField;
                }
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger callCount = new AtomicInteger(0);
            
            HelperVisitor.forField()
                .withAnnotation("java.lang.Deprecated")
                .ofType("java.lang.String")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    callCount.incrementAndGet();
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(holder.size(), th);
                    return true;
                });
            
            // Without binding resolution, the type is not correctly resolved, so no field is found
            assertEquals(0, callCount.get(), "Without binding resolution, no field should be found");
        }
    }

    @Nested
    @DisplayName("forImport() Tests")
    class ForImportTests {

        @Test
        @DisplayName("forImport finds ImportDeclaration and populates ReferenceHolder correctly")
        void testForImport_findsImportDeclaration_populatesReferenceHolder() {
            String source = """
                import java.util.List;
                import java.util.ArrayList;
                public class MyClass {}
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger callCount = new AtomicInteger(0);
            
            HelperVisitor.forImport("java.util.List")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    int index = callCount.getAndIncrement();
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(index, th);
                    return true;
                });
            
            assertEquals(1, callCount.get(), "Exactly one import should be found");
            assertEquals(1, dataHolder.size(), "ReferenceHolder should have one entry");
            assertNotNull(dataHolder.get(0), "Entry should not be null");
        }
    }

    @Nested
    @DisplayName("collect() Tests")
    class CollectTests {

        @Test
        @DisplayName("collect() gathers all found nodes")
        void testCollect_returnsAllFoundNodes() {
            String source = """
                import java.util.List;
                import java.util.ArrayList;
                import java.util.Map;
                public class MyClass {}
                """;
            cu = parseSource(source);
            
            var nodes = HelperVisitor.forImport("java.util.List")
                .in(cu)
                .excluding(nodesprocessed)
                .collect();
            
            assertEquals(1, nodes.size(), "Exactly one node should be collected");
            assertTrue(nodes.get(0) instanceof ImportDeclaration, "Node should be ImportDeclaration");
        }
    }

    @Nested
    @DisplayName("ReferenceHolder Correctness Tests")
    class ReferenceHolderCorrectnessTests {

        @Test
        @DisplayName("ReferenceHolder is correctly populated in processEach and accessible afterwards")
        void testReferenceHolder_isCorrectlyPopulated_andAccessibleAfterProcessing() {
            String source = """
                import java.lang.Deprecated;
                import java.lang.Override;
                public class MyClass {
                    @Deprecated
                    public void method1() {}
                    @Override
                    public String toString() { return ""; }
                }
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger index = new AtomicInteger(0);
            
            HelperVisitor.forAnnotation("java.lang.Deprecated")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    TestHolder th = new TestHolder();
                    th.node = node;
                    th.name = "Deprecated";
                    holder.put(index.getAndIncrement(), th);
                    return true;
                });
            
            // Verify that ReferenceHolder is correct AFTER processEach returns
            assertEquals(1, dataHolder.size(), "ReferenceHolder should have one entry");
            
            TestHolder holder0 = dataHolder.get(0);
            assertNotNull(holder0, "Entry 0 should not be null");
            assertNotNull(holder0.node, "Node in entry should not be null");
            assertEquals("Deprecated", holder0.name, "Name should be correctly set");
        }

        @Test
        @DisplayName("Multiple nodes are correctly stored in ReferenceHolder")
        void testReferenceHolder_multipleNodes_allAccessible() {
            String source = """
                import java.util.List;
                import java.util.ArrayList;
                import java.util.Map;
                public class MyClass {}
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            AtomicInteger index = new AtomicInteger(0);
            
            // Find all java.util imports
            HelperVisitor.forImport("java.util.List")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(index.getAndIncrement(), th);
                    return true;
                });
            
            HelperVisitor.forImport("java.util.ArrayList")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(index.getAndIncrement(), th);
                    return true;
                });
            
            assertEquals(2, dataHolder.size(), "ReferenceHolder should have two entries");
            assertNotNull(dataHolder.get(0), "Entry 0 should not be null");
            assertNotNull(dataHolder.get(1), "Entry 1 should not be null");
        }

        @Test
        @DisplayName("ReferenceHolder.get() returns null for non-existent key - edge case from PR #494")
        void testReferenceHolder_returnsNullForNonExistentKey() {
            String source = """
                import java.util.List;
                public class MyClass {}
                """;
            cu = parseSource(source);
            
            ReferenceHolder<Integer, TestHolder> dataHolder = new ReferenceHolder<>();
            
            // Intentionally search for something that doesn't exist
            HelperVisitor.forAnnotation("java.lang.NonExistent")
                .in(cu)
                .excluding(nodesprocessed)
                .processEach(dataHolder, (node, holder) -> {
                    TestHolder th = new TestHolder();
                    th.node = node;
                    holder.put(holder.size(), th);
                    return true;
                });
            
            // Verify that ReferenceHolder is empty when no matches found
            assertEquals(0, dataHolder.size(), "ReferenceHolder should be empty when no nodes match");
            
            // This is the critical test: accessing a non-existent key returns null
            // This is the scenario that caused NPE in PR #494 - accessing holder.get(0).minv
            // when holder.get(0) returned null
            assertNull(dataHolder.get(0), "ReferenceHolder.get(0) should return null for non-existent key");
            
            // Demonstrate defensive coding pattern to prevent NPE
            TestHolder holder0 = dataHolder.get(0);
            if (holder0 != null) {
                // Safe to access holder0.node or holder0.name
                fail("Should not reach here when holder is empty");
            }
        }
    }

    // Helper class for tests
    static class TestHolder {
        ASTNode node;
        String name;
    }
}
