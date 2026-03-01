/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.filter.CommitKeywordFilter;

/**
 * Tests for {@link CommitKeywordFilter}.
 */
class CommitKeywordFilterTest {

	@TempDir
	Path tempDir;

	@Test
	void testDefaultFilterMatchesRefactor() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertTrue(filter.matches("Refactor dialog layout"));
	}

	@Test
	void testDefaultFilterMatchesCleanup() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertTrue(filter.matches("Code cleanup in editor"));
	}

	@Test
	void testDefaultFilterMatchesDeprecated() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertTrue(filter.matches("Remove deprecated methods"));
	}

	@Test
	void testDefaultFilterRejectsUnrelatedCommit() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertFalse(filter.matches("Update version to 4.28.0"));
	}

	@Test
	void testNullMessageReturnsFalse() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertFalse(filter.matches(null));
	}

	@Test
	void testBlankMessageReturnsFalse() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertFalse(filter.matches("   "));
	}

	@Test
	void testCustomKeywordsFromFile() throws IOException {
		Path keywordFile = tempDir.resolve("keywords.txt");
		Files.writeString(keywordFile, "# Comment\nmypattern\nanotherkey\n");
		CommitKeywordFilter filter = new CommitKeywordFilter(keywordFile);
		assertTrue(filter.matches("Apply mypattern to code"));
		assertFalse(filter.matches("Unrelated commit message"));
	}

	@Test
	void testGetKeywordsNotEmpty() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertFalse(filter.getKeywords().isEmpty());
	}

	@Test
	void testCaseInsensitiveMatching() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		assertTrue(filter.matches("REFACTOR the module"));
		assertTrue(filter.matches("Lambda conversion applied"));
	}

	@Test
	void testRegexPatternMatching() {
		CommitKeywordFilter filter = new CommitKeywordFilter();
		// "use.*instead" should match via regex, not substring
		assertTrue(filter.matches("Use SafeRunner.run instead of Platform.run"),
				"'use.*instead' regex should match commit with words between 'use' and 'instead'");
		assertTrue(filter.matches("remove deprecated API calls"),
				"'remove.*deprecated' regex should match commit with words between");
		assertTrue(filter.matches("Switch to new API"),
				"'switch.*to' regex should match");
	}
}
