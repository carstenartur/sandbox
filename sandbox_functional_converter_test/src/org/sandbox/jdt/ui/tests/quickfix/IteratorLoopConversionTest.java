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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for iterator loop conversion to functional streams.
 * 
 * <p>These tests validate conversion of:</p>
 * <ul>
 *   <li>while-iterator pattern</li>
 *   <li>for-loop-iterator pattern</li>
 *   <li>Safety checks (iterator.remove(), multiple next() calls, etc.)</li>
 * </ul>
 * 
 * <p>Note: ITERATOR_LOOP has been activated in UseFunctionalCallFixCore (Phase 7).
 * Tests are enabled and validate iterator-to-stream conversions.</p>
 */
public class IteratorLoopConversionTest {
    
    @RegisterExtension
    AbstractEclipseJava context = new EclipseJava22();
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testSimpleWhileIterator_forEach() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    Iterator<String> it = items.iterator();
                    while (it.hasNext()) {
                        String item = it.next();
                        System.out.println(item);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    items.stream()
                        .forEach(item -> System.out.println(item));
                }
            }
            """;
        
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testForLoopIterator_forEach() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    for (Iterator<String> it = items.iterator(); it.hasNext(); ) {
                        String item = it.next();
                        System.out.println(item);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    items.stream()
                        .forEach(item -> System.out.println(item));
                }
            }
            """;
        
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testIteratorWithRemove_notConverted() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    Iterator<String> it = items.iterator();
                    while (it.hasNext()) {
                        String item = it.next();
                        if (item.isEmpty()) {
                            it.remove();
                        }
                    }
                }
            }
            """;
        
        // Should not be converted because it.remove() is not safe
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
    }
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testIteratorMultipleNext_notConverted() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    Iterator<String> it = items.iterator();
                    while (it.hasNext()) {
                        String item1 = it.next();
                        String item2 = it.next();
                        System.out.println(item1 + item2);
                    }
                }
            }
            """;
        
        // Should not be converted because multiple next() calls
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
    }
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testMultipleStatements_forEach() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    Iterator<String> it = items.iterator();
                    while (it.hasNext()) {
                        String item = it.next();
                        String upper = item.toUpperCase();
                        System.out.println(upper);
                    }
                }
            }
            """;
        
        String expected = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    items.stream()
                        .forEach(item -> {
                            String upper = item.toUpperCase();
                            System.out.println(upper);
                        });
                }
            }
            """;
        
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
    }
    
    // Enabled January 2026 - Phase 7: Iterator pattern support
    @Test
    public void testWithBreak_notYetSupported() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
        
        String given = """
            package test;
            import java.util.*;
            public class E {
                void foo(List<String> items) {
                    Iterator<String> it = items.iterator();
                    while (it.hasNext()) {
                        String item = it.next();
                        if (item.isEmpty()) {
                            break;
                        }
                        System.out.println(item);
                    }
                }
            }
            """;
        
        // Break statements are not yet supported - will not be converted until
        // the feature is enhanced to handle them (e.g., using filter or takeWhile)
        ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
        context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
        // enable(UseFunctionalCallFixCore.ITERATOR_LOOP); // Commented out until ITERATOR_LOOP is enabled
        context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
    }
}
