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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.CodeChangePair;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunkRefiner;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;

/**
 * Tests for {@link DiffHunkRefiner}.
 */
public class DiffHunkRefinerTest {

	private final DiffHunkRefiner refiner = new DiffHunkRefiner();

	@Test
	public void testRefineSimpleStatementChange() {
		String before = """
				class Test {
				    void method() {
				        String s = new String(bytes, "UTF-8");
				    }
				}
				"""; //$NON-NLS-1$
		String after = """
				class Test {
				    void method() {
				        String s = new String(bytes, StandardCharsets.UTF_8);
				    }
				}
				"""; //$NON-NLS-1$

		DiffHunk hunk = new DiffHunk(3, 1, 3, 1,
				"        String s = new String(bytes, \"UTF-8\");", //$NON-NLS-1$
				"        String s = new String(bytes, StandardCharsets.UTF_8);"); //$NON-NLS-1$

		FileDiff diff = new FileDiff("Test.java", before, after, List.of(hunk)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertNotNull(pairs);
		assertFalse(pairs.isEmpty(), "Should produce at least one code change pair"); //$NON-NLS-1$

		CodeChangePair pair = pairs.get(0);
		assertNotNull(pair.beforeSnippet());
		assertNotNull(pair.afterSnippet());
		assertFalse(pair.beforeSnippet().equals(pair.afterSnippet()),
				"Before and after snippets should differ"); //$NON-NLS-1$
	}

	@Test
	public void testRefineMethodCallChange() {
		String before = """
				class Test {
				    void method() {
				        Collections.emptyList();
				    }
				}
				"""; //$NON-NLS-1$
		String after = """
				class Test {
				    void method() {
				        List.of();
				    }
				}
				"""; //$NON-NLS-1$

		DiffHunk hunk = new DiffHunk(3, 1, 3, 1,
				"        Collections.emptyList();", //$NON-NLS-1$
				"        List.of();"); //$NON-NLS-1$

		FileDiff diff = new FileDiff("Test.java", before, after, List.of(hunk)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertNotNull(pairs);
		assertFalse(pairs.isEmpty(), "Should produce code change pairs for method call change"); //$NON-NLS-1$

		CodeChangePair pair = pairs.get(0);
		assertEquals(PatternKind.METHOD_CALL, pair.inferredKind(),
				"Should infer METHOD_CALL kind for method invocation change"); //$NON-NLS-1$
	}

	@Test
	public void testNonJavaFileSkipped() {
		String content = "Hello World\n"; //$NON-NLS-1$
		DiffHunk hunk = new DiffHunk(1, 1, 1, 1, "Hello", "Goodbye"); //$NON-NLS-1$ //$NON-NLS-2$
		FileDiff diff = new FileDiff("README.md", content, content, List.of(hunk)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertTrue(pairs.isEmpty(), "Non-Java files should produce no code change pairs"); //$NON-NLS-1$
	}

	@Test
	public void testNullContentProducesEmptyResult() {
		DiffHunk hunk = new DiffHunk(1, 1, 1, 1, "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
		FileDiff diff = new FileDiff("Test.java", null, null, List.of(hunk)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertTrue(pairs.isEmpty(), "Null content should produce empty result"); //$NON-NLS-1$
	}

	@Test
	public void testMultipleHunksProcessed() {
		String before = """
				class Test {
				    void method1() {
				        String s = new String(bytes, "UTF-8");
				    }
				    void method2() {
				        String t = new String(data, "UTF-8");
				    }
				}
				"""; //$NON-NLS-1$
		String after = """
				class Test {
				    void method1() {
				        String s = new String(bytes, StandardCharsets.UTF_8);
				    }
				    void method2() {
				        String t = new String(data, StandardCharsets.UTF_8);
				    }
				}
				"""; //$NON-NLS-1$

		DiffHunk hunk1 = new DiffHunk(3, 1, 3, 1,
				"        String s = new String(bytes, \"UTF-8\");", //$NON-NLS-1$
				"        String s = new String(bytes, StandardCharsets.UTF_8);"); //$NON-NLS-1$
		DiffHunk hunk2 = new DiffHunk(6, 1, 6, 1,
				"        String t = new String(data, \"UTF-8\");", //$NON-NLS-1$
				"        String t = new String(data, StandardCharsets.UTF_8);"); //$NON-NLS-1$

		FileDiff diff = new FileDiff("Test.java", before, after, List.of(hunk1, hunk2)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertNotNull(pairs);
		assertEquals(2, pairs.size(), "Should produce a pair for each hunk"); //$NON-NLS-1$
	}

	@Test
	public void testEmptyHunksProduceEmptyResult() {
		String code = """
				class Test {
				    void method() {
				        int x = 1;
				    }
				}
				"""; //$NON-NLS-1$

		FileDiff diff = new FileDiff("Test.java", code, code, List.of()); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertTrue(pairs.isEmpty(), "Empty hunks should produce empty result"); //$NON-NLS-1$
	}

	@Test
	public void testCodeChangePairHasLineNumber() {
		String before = """
				class Test {
				    void method() {
				        String s = new String(bytes, "UTF-8");
				    }
				}
				"""; //$NON-NLS-1$
		String after = """
				class Test {
				    void method() {
				        String s = new String(bytes, StandardCharsets.UTF_8);
				    }
				}
				"""; //$NON-NLS-1$

		DiffHunk hunk = new DiffHunk(3, 1, 3, 1,
				"        String s = new String(bytes, \"UTF-8\");", //$NON-NLS-1$
				"        String s = new String(bytes, StandardCharsets.UTF_8);"); //$NON-NLS-1$

		FileDiff diff = new FileDiff("Test.java", before, after, List.of(hunk)); //$NON-NLS-1$

		List<CodeChangePair> pairs = refiner.refineToStatements(diff);

		assertFalse(pairs.isEmpty());
		assertTrue(pairs.get(0).lineNumber() > 0,
				"Code change pair should have a positive line number"); //$NON-NLS-1$
		assertEquals("Test.java", pairs.get(0).filePath()); //$NON-NLS-1$
	}
}
