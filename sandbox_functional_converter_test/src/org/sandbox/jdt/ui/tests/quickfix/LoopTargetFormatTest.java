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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for loop target format selection.
 * 
 * <p>Validates that the cleanup framework correctly reads and respects the
 * target format preference (STREAM, FOR_LOOP, WHILE_LOOP) from the UI.</p>
 */
@DisplayName("Loop Target Format Selection Tests")
public class LoopTargetFormatTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Tests that STREAM format (default) converts enhanced for-loops to streams.
	 */
	@Test
	@DisplayName("Target format STREAM: for → stream (default behavior)")
	public void testStreamFormat_convertsToStream() throws CoreException {
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
		
		// Enable cleanup with STREAM format (default radio button selected)
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_STREAM);
		
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that FOR_LOOP format does NOT convert (not yet implemented).
	 * 
	 * <p>When FOR_LOOP format is selected, the cleanup should skip transformation
	 * until the feature is implemented.</p>
	 */
	@Test
	@DisplayName("Target format FOR_LOOP: no conversion yet (not implemented)")
	public void testForLoopFormat_noConversion() throws CoreException {
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

		// Expected: no change (FOR_LOOP format not yet implemented)
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		
		// Enable cleanup with FOR_LOOP format (radio button selected)
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_FOR);
		
		// Should not transform (returns null from createFix)
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that WHILE_LOOP format does NOT convert (not yet implemented).
	 * 
	 * <p>When WHILE_LOOP format is selected, the cleanup should skip transformation
	 * until the feature is implemented.</p>
	 */
	@Test
	@DisplayName("Target format WHILE_LOOP: no conversion yet (not implemented)")
	public void testWhileLoopFormat_noConversion() throws CoreException {
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

		// Expected: no change (WHILE_LOOP format not yet implemented)
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		
		// Enable cleanup with WHILE_LOOP format (radio button selected)
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_FORMAT_WHILE);
		
		// Should not transform (returns null from createFix)
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests LoopTargetFormat enum parsing from string IDs.
	 */
	@Test
	@DisplayName("LoopTargetFormat.fromId() parses correctly")
	public void testLoopTargetFormat_parsing() {
		assertEquals(LoopTargetFormat.STREAM, LoopTargetFormat.fromId("stream"));
		assertEquals(LoopTargetFormat.FOR_LOOP, LoopTargetFormat.fromId("for"));
		assertEquals(LoopTargetFormat.WHILE_LOOP, LoopTargetFormat.fromId("while"));
		assertEquals(LoopTargetFormat.STREAM, LoopTargetFormat.fromId(null)); // default
		assertEquals(LoopTargetFormat.STREAM, LoopTargetFormat.fromId("invalid")); // default fallback
	}

	/**
	 * Tests LoopTargetFormat enum ID retrieval.
	 */
	@Test
	@DisplayName("LoopTargetFormat.getId() returns correct IDs")
	public void testLoopTargetFormat_ids() {
		assertEquals("stream", LoopTargetFormat.STREAM.getId());
		assertEquals("for", LoopTargetFormat.FOR_LOOP.getId());
		assertEquals("while", LoopTargetFormat.WHILE_LOOP.getId());
	}
}
