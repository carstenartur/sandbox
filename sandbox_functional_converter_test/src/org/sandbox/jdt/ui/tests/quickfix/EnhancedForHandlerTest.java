/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for the unified EnhancedForHandler ULR Integration.
 * 
 * <p>
 * This test class validates the Unified Loop Representation (ULR) based
 * implementation of loop-to-functional transformations. The implementation
 * uses ULR for simple forEach patterns and falls back to the Refactorer
 * for complex patterns (filter, map, collect, reduce).
 * </p>
 * 
 * @see org.sandbox.jdt.internal.corext.fix.helper.EnhancedForHandler
 * @see org.sandbox.jdt.internal.corext.fix.helper.JdtLoopExtractor
 * @see org.sandbox.jdt.internal.corext.fix.helper.ASTStreamRenderer
 */
@DisplayName("EnhancedForHandler ULR Integration Tests")
public class EnhancedForHandlerTest {
    
    @RegisterExtension
    AbstractEclipseJava context = new EclipseJava22();
    
    /**
     * Tests simple forEach conversion with V2 implementation.
     * 
     * <p>This validates that the ULR-based V2 implementation can convert
     * a basic enhanced for-loop over a List into a forEach() call.</p>
     */
    @Test
    @DisplayName("Simple forEach conversion with V2")
    void test_SimpleForEach_V2() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        System.out.println(item);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    items.forEach(item -> System.out.println(item));
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    /**
     * Tests array iteration conversion with V2 implementation.
     * 
     * <p>This validates that the V2 implementation correctly identifies arrays
     * and generates Arrays.stream() calls.</p>
     */
    @Test
    @DisplayName("Array iteration with V2 uses Arrays.stream()")
    void test_ArrayIteration_V2() throws CoreException {
        String input = """
            package test;
            public class Test {
                public void method(String[] items) {
                    for (String item : items) {
                        System.out.println(item);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            
            import java.util.Arrays;
            
            public class Test {
                public void method(String[] items) {
                    Arrays.stream(items).forEach(item -> System.out.println(item));
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    /**
     * Tests that loops with break statements are NOT converted.
     * 
     * <p>This validates that the LoopMetadata from JdtLoopExtractor correctly
     * identifies break statements and prevents conversion.</p>
     */
    @Test
    @DisplayName("Loop with break should NOT be converted by V2")
    void test_LoopWithBreak_NotConverted_V2() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        if (item == null) break;
                        System.out.println(item);
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        
        // Should remain unchanged
        context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
    }
    
    /**
     * Tests that loops with continue statements are NOT converted.
     * 
     * <p>This validates that the LoopMetadata correctly identifies continue
     * statements and converts them to filter().</p>
     */
    @Test
    @DisplayName("Loop with continue should be converted to filter by V2")
    void test_LoopWithContinue_NotConverted_V2() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        if (item == null) continue;
                        System.out.println(item);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    items.stream().filter(item -> !(item == null)).forEachOrdered(item -> System.out.println(item));
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        
        // Continue is converted to filter()
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    /**
     * Tests that loops with return statements are NOT converted.
     * 
     * <p>This validates that the LoopMetadata correctly identifies return
     * statements and prevents conversion.</p>
     */
    @Test
    @DisplayName("Loop with return should NOT be converted by V2")
    void test_LoopWithReturn_NotConverted_V2() throws CoreException {
        String input = """
            package test;
            import java.util.List;
            public class Test {
                public void method(List<String> items) {
                    for (String item : items) {
                        if (item == null) return;
                        System.out.println(item);
                    }
                }
            }
            """;
        
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, false, null);
        
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        
        // Should remain unchanged
        context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
    }
}
