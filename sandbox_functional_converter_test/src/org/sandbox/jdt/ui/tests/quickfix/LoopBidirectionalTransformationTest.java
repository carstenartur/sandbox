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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for bidirectional loop transformations.
 * 
 * <p>This test class validates that the refactoring framework can offer
 * quickfixes for multiple transformation directions:</p>
 * <ul>
 *   <li><b>for → Stream</b> - Enhanced for-loop to functional stream</li>
 *   <li><b>Iterator → Stream</b> - Iterator-based loop to stream</li>
 *   <li><b>Stream → for</b> - Stream forEach to enhanced for-loop</li>
 *   <li><b>for → while</b> - Enhanced for to iterator while-loop</li>
 *   <li><b>while → for</b> - Iterator while-loop to enhanced for</li>
 * </ul>
 * 
 * <p><b>Current Implementation Status (Phase 9):</b></p>
 * <ul>
 *   <li>✅ Enhanced for → Stream (via LOOP and LOOP_V2)</li>
 *   <li>✅ Iterator → Stream (via ITERATOR_LOOP)</li>
 *   <li>❌ Stream → for (not yet implemented - requires CleanUpOptions string access)</li>
 *   <li>❌ for → while (not yet implemented - requires CleanUpOptions string access)</li>
 *   <li>❌ while → for (not yet implemented - requires CleanUpOptions string access)</li>
 * </ul>
 * 
 * <p><b>Implementation Note:</b></p>
 * <p>The bidirectional transformations require reading the LOOP_CONVERSION_TARGET_FORMAT
 * option as a string value from CleanUpOptions. The current Eclipse cleanup infrastructure
 * doesn't provide an easy way to access non-boolean option values from the cleanup profile
 * at runtime. This needs further investigation.</p>
 */
@DisplayName("Bidirectional Loop Transformation Tests")
public class LoopBidirectionalTransformationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// FORWARD TRANSFORMATIONS (Currently Supported)
	// ===========================================

	/**
	 * Tests enhanced for-loop to stream transformation (already supported).
	 * 
	 * <p><b>Direction:</b> for → Stream</p>
	 * <p><b>Pattern:</b> {@code for (T item : collection) { ... }}</p>
	 * <p><b>Expected:</b> {@code collection.forEach(item -> ...)}</p>
	 */
	@Test
	@DisplayName("for → Stream: Basic forEach transformation")
	public void testForToStream_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests iterator-based loop to stream transformation (already supported via ITERATOR_LOOP).
	 * 
	 * <p><b>Direction:</b> Iterator → Stream</p>
	 * <p><b>Pattern:</b> {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
	 * <p><b>Expected:</b> {@code collection.forEach(item -> ...)}</p>
	 */
	@Test
	@DisplayName("Iterator → Stream: while-iterator to forEach")
	public void testIteratorToStream_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						items.stream().forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// REVERSE TRANSFORMATIONS (Future Enhancements)
	// ===========================================

	/**
	 * Tests stream to enhanced for-loop transformation (NOT YET IMPLEMENTED).
	 * 
	 * <p><b>Direction:</b> Stream → for</p>
	 * <p><b>Rationale:</b> Sometimes imperative code is clearer than streams,
	 * especially for simple iterations or when debugging.</p>
	 * <p><b>Pattern:</b> {@code collection.forEach(item -> ...)}</p>
	 * <p><b>Expected:</b> {@code for (T item : collection) { ... }}</p>
	 * 
	 * <p><b>Implementation Note:</b> Uses StreamToEnhancedFor transformer activated via
	 * LOOP_CONVERSION_ENABLED + LOOP_CONVERSION_TARGET_FORMAT="enhanced_for" + LOOP_CONVERSION_FROM_STREAM.</p>
	 */
	@Disabled("Bidirectional transformations not yet implemented - CleanUpOptions string value access needed")
	@Test
	@DisplayName("Stream → for: forEach to enhanced for-loop")
	public void testStreamToFor_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						items.stream().forEach(item -> System.out.println(item));
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		// Enable bidirectional conversion with "enhanced_for" as target format
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		context.set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "enhanced_for");
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests enhanced for-loop to classic while-loop transformation (NOT YET IMPLEMENTED).
	 * 
	 * <p><b>Direction:</b> for → while</p>
	 * <p><b>Rationale:</b> Rarely needed, but useful when manual iteration control is required
	 * (e.g., when you need to call hasNext() conditionally or skip elements).</p>
	 * <p><b>Pattern:</b> {@code for (T item : collection) { ... }}</p>
	 * <p><b>Expected:</b> {@code Iterator<T> it = collection.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
	 * 
	 * <p><b>Implementation Note:</b> Uses EnhancedForToIteratorWhile transformer activated via
	 * LOOP_CONVERSION_ENABLED + LOOP_CONVERSION_TARGET_FORMAT="iterator_while" + LOOP_CONVERSION_FROM_ENHANCED_FOR.</p>
	 */
	@Disabled("Bidirectional transformations not yet implemented - CleanUpOptions string value access needed")
	@Test
	@DisplayName("for → while: Enhanced for to iterator while-loop")
	public void testForToWhile_iterator() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		// Enable bidirectional conversion with "iterator_while" as target format
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		context.set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "iterator_while");
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests classic while-iterator to enhanced for-loop transformation.
	 * 
	 * <p><b>Direction:</b> while → for</p>
	 * <p><b>Rationale:</b> Simplify iterator-based loops to more idiomatic enhanced for-loops
	 * when the iterator is only used for simple sequential iteration.</p>
	 * <p><b>Pattern:</b> {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
	 * <p><b>Expected:</b> {@code for (T item : collection) { ... }}</p>
	 * 
	 * <p><b>Implementation Note:</b> Uses IteratorWhileToEnhancedFor transformer activated via
	 * LOOP_CONVERSION_ENABLED + LOOP_CONVERSION_TARGET_FORMAT="enhanced_for" + LOOP_CONVERSION_FROM_ITERATOR_WHILE.</p>
	 */
	@Disabled("Bidirectional transformations not yet implemented - CleanUpOptions string value access needed")
	@Test
	@DisplayName("while → for: Iterator while-loop to enhanced for")
	public void testWhileToFor_iterator() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		// Enable bidirectional conversion with "enhanced_for" as target format
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		context.set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "enhanced_for");
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
