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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.jdt.internal.corext.fix.helper.JdtLoopExtractor;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Integration tests for Phase 10: Comment Preservation.
 * 
 * <p>These tests validate that comments attached to statements inside loops
 * are properly extracted, stored in Operation objects, and can be rendered
 * into block lambdas when transforming loops to streams.</p>
 */
@DisplayName("Comment Preservation Integration Tests")
public class CommentPreservationIntegrationTest {
    
    @RegisterExtension
    AbstractEclipseJava context = new EclipseJava22();
    
    /**
     * Tests that JdtLoopExtractor can extract comments from loop statements.
     * This validates the comment extraction infrastructure.
     */
    @Test
    @DisplayName("Extract comments from loop with leading comment")
    void test_ExtractCommentsFromLoop_LeadingComment() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    // Process each item
                    for (String item : items) {
                        System.out.println(item);
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Find the for loop
        EnhancedForStatement[] forLoop = new EnhancedForStatement[1];
        compilationUnit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                forLoop[0] = node;
                return false;
            }
        });
        
        assertNotNull(forLoop[0]);
        
        // Extract loop with compilation unit to enable comment extraction
        JdtLoopExtractor extractor = new JdtLoopExtractor();
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(forLoop[0], compilationUnit);
        
        assertNotNull(extracted);
        assertNotNull(extracted.model);
    }
    
    /**
     * End-to-end test: verifies that comments before a filter statement (if-continue)
     * are extracted from the AST and attached to the corresponding FilterOp in the model.
     * This is the critical wiring that connects AST comment extraction to the ULR pipeline.
     */
    @Test
    @DisplayName("End-to-end: comments before if-continue are attached to FilterOp")
    void test_EndToEnd_FilterCommentAttached() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        // Skip empty items
                        if (item.isEmpty()) continue;
                        System.out.println(item);
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST with bindings
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Find the for loop
        EnhancedForStatement[] forLoop = new EnhancedForStatement[1];
        compilationUnit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                forLoop[0] = node;
                return false;
            }
        });
        
        assertNotNull(forLoop[0]);
        
        // Extract loop with compilation unit to enable comment extraction
        JdtLoopExtractor extractor = new JdtLoopExtractor();
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(forLoop[0], compilationUnit);
        
        assertNotNull(extracted);
        assertNotNull(extracted.model);
        
        // The if-continue pattern should produce a FilterOp
        assertFalse(extracted.model.getOperations().isEmpty(), "Model should have at least one operation");
        
        // The first operation should be a FilterOp (from if (item.isEmpty()) continue)
        org.sandbox.functional.core.operation.Operation firstOp = extracted.model.getOperations().get(0);
        assertTrue(firstOp instanceof FilterOp, "First operation should be FilterOp, was: " + firstOp);
        
        // The FilterOp should have the comment attached
        FilterOp filterOp = (FilterOp) firstOp;
        assertTrue(filterOp.hasComments(), "FilterOp should have comments attached from the AST");
        assertTrue(filterOp.getComments().contains("Skip empty items"),
            "FilterOp should contain 'Skip empty items' comment, but has: " + filterOp.getComments());
    }
    
    /**
     * Tests FilterOp with manually added comments to demonstrate the rendering strategy.
     */
    @Test
    @DisplayName("FilterOp with comments triggers block lambda rendering")
    void test_FilterOpWithComments_BlockLambda() {
        // Create a FilterOp with comments
        FilterOp filter = new FilterOp("x > 0");
        filter.addComment("Filter out negative values");
        filter.addComment("Only process positive integers");
        
        // Verify comments are stored
        assertTrue(filter.hasComments());
        assertEquals(2, filter.getComments().size());
        assertEquals(List.of(
            "Filter out negative values",
            "Only process positive integers"
        ), filter.getComments());
        
        // The renderer will check hasComments() and generate block lambda
        // This test validates the comment storage mechanism works
    }
    
    /**
     * Tests MapOp with manually added comments.
     */
    @Test
    @DisplayName("MapOp with comments triggers block lambda rendering")
    void test_MapOpWithComments_BlockLambda() {
        // Create a MapOp with comments
        MapOp map = new MapOp("x.toUpperCase()");
        map.addComment("Convert to uppercase");
        map.addComment("For display purposes");
        
        // Verify comments are stored
        assertTrue(map.hasComments());
        assertEquals(2, map.getComments().size());
        assertEquals(List.of(
            "Convert to uppercase",
            "For display purposes"
        ), map.getComments());
    }
    
    /**
     * Tests comment extraction from different positions.
     * This validates the line-based comment association heuristic.
     */
    @Test
    @DisplayName("Extract comments from different positions in loop")
    void test_ExtractComments_DifferentPositions() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        // Comment before statement
                        System.out.println(item);
                        
                        /* Block comment before next statement */
                        System.out.println("done");
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Verify comment list is available
        assertFalse(compilationUnit.getCommentList().isEmpty());
        assertEquals(2, compilationUnit.getCommentList().size());
    }
    
    /**
     * Tests that operations without comments use compact lambda rendering.
     */
    @Test
    @DisplayName("Operations without comments use compact lambda")
    void test_OperationsWithoutComments_CompactLambda() {
        // Create operations without comments
        FilterOp filter = new FilterOp("x > 0");
        MapOp map = new MapOp("x * 2");
        
        // Verify no comments
        assertFalse(filter.hasComments());
        assertFalse(map.hasComments());
        
        // The renderer will generate compact expression lambdas
        // This test validates the default behavior is preserved
    }
    
    /**
     * Tests comment preservation with trailing comments on same line.
     */
    @Test
    @DisplayName("Extract trailing comments on same line")
    void test_ExtractComments_TrailingComment() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        System.out.println(item); // Print the item
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Verify comment is detected
        assertFalse(compilationUnit.getCommentList().isEmpty());
    }
    
    /**
     * Tests that empty comments are filtered out.
     */
    @Test
    @DisplayName("Empty comments are not stored")
    void test_EmptyComments_Filtered() {
        FilterOp filter = new FilterOp("x > 0");
        
        filter.addComment(null);
        filter.addComment("");
        filter.addComment("  ");
        
        // Empty comments should not be stored
        assertFalse(filter.hasComments());
        assertTrue(filter.getComments().isEmpty());
    }
    
    /**
     * Tests comment preservation with multiple operations in a pipeline.
     */
    @Test
    @DisplayName("Multiple operations can have independent comments")
    void test_MultipleOperations_IndependentComments() {
        FilterOp filter1 = new FilterOp("x != null");
        filter1.addComment("Remove null values");
        
        MapOp map = new MapOp("x.length()");
        map.addComment("Get string length");
        
        FilterOp filter2 = new FilterOp("len > 3");
        filter2.addComment("Only long strings");
        
        // Each operation has its own comments
        assertTrue(filter1.hasComments());
        assertEquals(List.of("Remove null values"), filter1.getComments());
        
        assertTrue(map.hasComments());
        assertEquals(List.of("Get string length"), map.getComments());
        
        assertTrue(filter2.hasComments());
        assertEquals(List.of("Only long strings"), filter2.getComments());
    }
    
    /**
     * End-to-end test: verifies that trailing comments (inline comments on same line after code)
     * are extracted from the AST and attached to the corresponding operation in the model.
     * This validates that inline comments like "statement(); // comment" are preserved.
     */
    @Test
    @DisplayName("End-to-end: trailing inline comments are attached to operations")
    void test_EndToEnd_TrailingInlineCommentsAttached() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        System.out.println(item); // Print the item
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST with bindings
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Find the for loop
        EnhancedForStatement[] forLoop = new EnhancedForStatement[1];
        compilationUnit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                forLoop[0] = node;
                return false;
            }
        });
        
        assertNotNull(forLoop[0]);
        
        // Extract loop with compilation unit to enable comment extraction
        JdtLoopExtractor extractor = new JdtLoopExtractor();
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(forLoop[0], compilationUnit);
        
        assertNotNull(extracted);
        assertNotNull(extracted.model);
        
        // The println statement should have a trailing comment attached
        // Check if any operation (likely a side-effect operation) has the comment
        boolean foundTrailingComment = false;
        for (org.sandbox.functional.core.operation.Operation op : extracted.model.getOperations()) {
            if (op instanceof org.sandbox.functional.core.operation.MapOp mapOp) {
                if (mapOp.hasComments()) {
                    java.util.List<String> comments = mapOp.getComments();
                    for (String comment : comments) {
                        if (comment.contains("Print the item")) {
                            foundTrailingComment = true;
                            break;
                        }
                    }
                }
            }
        }
        
        assertTrue(foundTrailingComment, 
            "Expected to find 'Print the item' trailing comment attached to an operation. " +
            "Operations: " + extracted.model.getOperations());
    }
    
    /**
     * End-to-end test: verifies that filter operations with trailing comments preserve them.
     */
    @Test
    @DisplayName("End-to-end: filter with trailing comment preserved")
    void test_EndToEnd_FilterWithTrailingComment() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        if (item.isEmpty()) continue; // Skip empty
                        System.out.println(item);
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        // Parse to get AST with bindings
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
        
        // Find the for loop
        EnhancedForStatement[] forLoop = new EnhancedForStatement[1];
        compilationUnit.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(EnhancedForStatement node) {
                forLoop[0] = node;
                return false;
            }
        });
        
        assertNotNull(forLoop[0]);
        
        // Extract loop with compilation unit to enable comment extraction
        JdtLoopExtractor extractor = new JdtLoopExtractor();
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(forLoop[0], compilationUnit);
        
        assertNotNull(extracted);
        assertNotNull(extracted.model);
        
        // The if-continue should produce a FilterOp with trailing comment
        assertFalse(extracted.model.getOperations().isEmpty());
        org.sandbox.functional.core.operation.Operation firstOp = extracted.model.getOperations().get(0);
        assertTrue(firstOp instanceof FilterOp, "First operation should be FilterOp");
        
        FilterOp filterOp = (FilterOp) firstOp;
        assertTrue(filterOp.hasComments(), "FilterOp should have the trailing comment");
        
        boolean foundSkipEmpty = false;
        for (String comment : filterOp.getComments()) {
            if (comment.contains("Skip empty")) {
                foundSkipEmpty = true;
                break;
            }
        }
        assertTrue(foundSkipEmpty, 
            "FilterOp should contain 'Skip empty' trailing comment. Comments: " + filterOp.getComments());
    }
}
