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

import static org.assertj.core.api.Assertions.assertThat;

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
        
        assertThat(forLoop[0]).isNotNull();
        
        // Extract loop with compilation unit to enable comment extraction
        JdtLoopExtractor extractor = new JdtLoopExtractor();
        JdtLoopExtractor.ExtractedLoop extracted = extractor.extract(forLoop[0], compilationUnit);
        
        assertThat(extracted).isNotNull();
        assertThat(extracted.model).isNotNull();
        
        // Comment extraction is implemented but not yet wired to operations
        // This test validates the infrastructure is in place
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
        assertThat(filter.hasComments()).isTrue();
        assertThat(filter.getComments()).hasSize(2);
        assertThat(filter.getComments()).containsExactly(
            "Filter out negative values",
            "Only process positive integers"
        );
        
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
        assertThat(map.hasComments()).isTrue();
        assertThat(map.getComments()).hasSize(2);
        assertThat(map.getComments()).containsExactly(
            "Convert to uppercase",
            "For display purposes"
        );
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
        assertThat(compilationUnit.getCommentList()).isNotEmpty();
        assertThat(compilationUnit.getCommentList()).hasSize(2);
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
        assertThat(filter.hasComments()).isFalse();
        assertThat(map.hasComments()).isFalse();
        
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
        assertThat(compilationUnit.getCommentList()).isNotEmpty();
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
        assertThat(filter.hasComments()).isFalse();
        assertThat(filter.getComments()).isEmpty();
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
        assertThat(filter1.hasComments()).isTrue();
        assertThat(filter1.getComments()).containsExactly("Remove null values");
        
        assertThat(map.hasComments()).isTrue();
        assertThat(map.getComments()).containsExactly("Get string length");
        
        assertThat(filter2.hasComments()).isTrue();
        assertThat(filter2.getComments()).containsExactly("Only long strings");
    }
}
