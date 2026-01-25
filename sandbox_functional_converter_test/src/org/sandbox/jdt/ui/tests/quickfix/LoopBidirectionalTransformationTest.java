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
 *   <li><b>while → for</b> - Classic while to enhanced for (where applicable)</li>
 * </ul>
 * 
 * <p><b>Note:</b> Some transformations (e.g., Stream → for, for → while) are
 * conceptually inverse operations that would require separate quickfix implementations.
 * These tests document the desired behavior for future enhancements.</p>
 * 
 * <p><b>Current Implementation Status:</b></p>
 * <ul>
 *   <li>✅ Enhanced for → Stream (via LOOP and LOOP_V2)</li>
 *   <li>✅ Iterator → Stream (via ITERATOR_LOOP)</li>
 *   <li>❌ Stream → for (not yet implemented)</li>
 *   <li>❌ for → while (not yet implemented)</li>
 *   <li>❌ while → for (not yet implemented)</li>
 * </ul>
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
						items.forEach(item -> System.out.println(item));
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
	 * <p><b>Implementation Note:</b> Would require a new cleanup/quickfix that
	 * detects simple forEach() calls and converts them back to enhanced for-loops.
	 * This is the inverse of the for → Stream transformation.</p>
	 */
	@Disabled("Stream → for transformation not yet implemented - future enhancement")
	@Test
	@DisplayName("Stream → for: forEach to enhanced for-loop (future)")
	public void testStreamToFor_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
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
		// Would need new constant: MYCleanUpConstants.STREAM_TO_LOOP_CLEANUP
		// context.enable(MYCleanUpConstants.STREAM_TO_LOOP_CLEANUP);
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
	 * <p><b>Implementation Note:</b> This is a niche use case. Most modern Java code
	 * prefers enhanced for-loops over manual iterator manipulation.</p>
	 */
	@Disabled("for → while transformation not yet implemented - niche use case")
	@Test
	@DisplayName("for → while: Enhanced for to iterator while-loop (future)")
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
		// Would need new constant: MYCleanUpConstants.FOR_TO_WHILE_CLEANUP
		// context.enable(MYCleanUpConstants.FOR_TO_WHILE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests classic while-iterator to enhanced for-loop transformation (NOT YET IMPLEMENTED).
	 * 
	 * <p><b>Direction:</b> while → for</p>
	 * <p><b>Rationale:</b> Simplify iterator-based loops to more idiomatic enhanced for-loops
	 * when the iterator is only used for simple sequential iteration.</p>
	 * <p><b>Pattern:</b> {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
	 * <p><b>Expected:</b> {@code for (T item : collection) { ... }}</p>
	 * 
	 * <p><b>Implementation Note:</b> This transformation already exists in a sense - 
	 * our ITERATOR_LOOP currently converts to streams, but an alternative quickfix could
	 * offer conversion to enhanced for-loops instead.</p>
	 */
	@Disabled("while → for transformation not yet implemented - alternative to Iterator → Stream")
	@Test
	@DisplayName("while → for: Iterator while-loop to enhanced for (future)")
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
		// Could be part of ITERATOR_LOOP but offering enhanced for instead of stream
		// context.enable(MYCleanUpConstants.WHILE_TO_FOR_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
