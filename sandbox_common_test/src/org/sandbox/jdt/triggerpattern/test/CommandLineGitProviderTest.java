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
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.git.CommandLineGitProvider;

/**
 * Tests for {@link CommandLineGitProvider} parsing logic.
 *
 * <p>These tests validate the parsing of git output without requiring a real
 * Git repository. The actual git command execution is tested separately.</p>
 */
public class CommandLineGitProviderTest {

	private final CommandLineGitProvider provider = new CommandLineGitProvider();

	@Test
	public void testParseHistoryEmpty() {
		var commits = provider.parseHistory(""); //$NON-NLS-1$
		assertTrue(commits.isEmpty(), "Empty output should produce no commits"); //$NON-NLS-1$
	}

	@Test
	public void testParseHistoryNull() {
		var commits = provider.parseHistory(null);
		assertTrue(commits.isEmpty(), "Null output should produce no commits"); //$NON-NLS-1$
	}

	@Test
	public void testParseHistorySingleCommit() {
		// Simulated git log output with field/record separators
		String output = "abc123def456\u001fabc123\u001fFix encoding issues\u001fJohn Doe\u001f1700000000\u001e\n"  //$NON-NLS-1$
				+ " 3 files changed, 10 insertions(+), 5 deletions(-)"; //$NON-NLS-1$

		var commits = provider.parseHistory(output);

		assertEquals(1, commits.size(), "Should parse one commit"); //$NON-NLS-1$
		var commit = commits.get(0);
		assertEquals("abc123def456", commit.id()); //$NON-NLS-1$
		assertEquals("abc123", commit.shortId()); //$NON-NLS-1$
		assertEquals("Fix encoding issues", commit.message()); //$NON-NLS-1$
		assertEquals("John Doe", commit.author()); //$NON-NLS-1$
		assertNotNull(commit.timestamp());
	}

	@Test
	public void testParseHistoryMultipleCommits() {
		String output = "aaa\u001fa\u001fFirst\u001fAlice\u001f1700000000\u001e" //$NON-NLS-1$
				+ "bbb\u001fb\u001fSecond\u001fBob\u001f1700000100\u001e"; //$NON-NLS-1$

		var commits = provider.parseHistory(output);

		assertEquals(2, commits.size(), "Should parse two commits"); //$NON-NLS-1$
		assertEquals("First", commits.get(0).message()); //$NON-NLS-1$
		assertEquals("Second", commits.get(1).message()); //$NON-NLS-1$
	}

	@Test
	public void testParseHistoryMalformedRecord() {
		// Record with too few fields should be skipped
		String output = "abc\u001fshort\u001e"; //$NON-NLS-1$
		var commits = provider.parseHistory(output);
		assertTrue(commits.isEmpty(), "Malformed records should be skipped"); //$NON-NLS-1$
	}

	@Test
	public void testParseHunksEmpty() {
		List<DiffHunk> hunks = provider.parseHunks(""); //$NON-NLS-1$
		assertTrue(hunks.isEmpty(), "Empty diff output should produce no hunks"); //$NON-NLS-1$
	}

	@Test
	public void testParseHunksNull() {
		List<DiffHunk> hunks = provider.parseHunks(null);
		assertTrue(hunks.isEmpty(), "Null diff output should produce no hunks"); //$NON-NLS-1$
	}

	@Test
	public void testParseHunksSingleHunk() {
		String diffOutput = "diff --git a/Test.java b/Test.java\n" //$NON-NLS-1$
				+ "index abc..def 100644\n" //$NON-NLS-1$
				+ "--- a/Test.java\n" //$NON-NLS-1$
				+ "+++ b/Test.java\n" //$NON-NLS-1$
				+ "@@ -10,3 +10,3 @@\n" //$NON-NLS-1$
				+ " context line\n" //$NON-NLS-1$
				+ "-old line\n" //$NON-NLS-1$
				+ "+new line\n" //$NON-NLS-1$
				+ " another context"; //$NON-NLS-1$

		List<DiffHunk> hunks = provider.parseHunks(diffOutput);

		assertEquals(1, hunks.size(), "Should parse one hunk"); //$NON-NLS-1$
		DiffHunk hunk = hunks.get(0);
		assertEquals(10, hunk.beforeStartLine());
		assertEquals(3, hunk.beforeLineCount());
		assertEquals(10, hunk.afterStartLine());
		assertEquals(3, hunk.afterLineCount());
		assertTrue(hunk.beforeText().contains("old line"), //$NON-NLS-1$
				"Before text should contain removed line"); //$NON-NLS-1$
		assertTrue(hunk.afterText().contains("new line"), //$NON-NLS-1$
				"After text should contain added line"); //$NON-NLS-1$
	}

	@Test
	public void testParseHunksMultipleHunks() {
		String diffOutput = "@@ -5,2 +5,2 @@\n" //$NON-NLS-1$
				+ "-old1\n" //$NON-NLS-1$
				+ "+new1\n" //$NON-NLS-1$
				+ "@@ -20,3 +20,4 @@\n" //$NON-NLS-1$
				+ "-old2\n" //$NON-NLS-1$
				+ "+new2\n" //$NON-NLS-1$
				+ "+extra"; //$NON-NLS-1$

		List<DiffHunk> hunks = provider.parseHunks(diffOutput);

		assertEquals(2, hunks.size(), "Should parse two hunks"); //$NON-NLS-1$
		assertEquals(5, hunks.get(0).beforeStartLine());
		assertEquals(20, hunks.get(1).beforeStartLine());
	}

	@Test
	public void testParseHunkContextLinesIncluded() {
		String diffOutput = "@@ -1,5 +1,5 @@\n" //$NON-NLS-1$
				+ " context1\n" //$NON-NLS-1$
				+ " context2\n" //$NON-NLS-1$
				+ "-removed\n" //$NON-NLS-1$
				+ "+added\n" //$NON-NLS-1$
				+ " context3"; //$NON-NLS-1$

		List<DiffHunk> hunks = provider.parseHunks(diffOutput);

		assertFalse(hunks.isEmpty());
		DiffHunk hunk = hunks.get(0);
		assertTrue(hunk.beforeText().contains("context1"), //$NON-NLS-1$
				"Before text should include context lines"); //$NON-NLS-1$
		assertTrue(hunk.afterText().contains("context1"), //$NON-NLS-1$
				"After text should include context lines"); //$NON-NLS-1$
	}
}
