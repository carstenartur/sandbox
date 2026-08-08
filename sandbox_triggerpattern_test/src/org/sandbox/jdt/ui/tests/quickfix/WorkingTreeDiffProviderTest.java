/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.git.WorkingTreeDiffProvider;

/** Tests the source contract behind "Mine DSL Rules from Working Tree". */
public class WorkingTreeDiffProviderTest {

	@Test
	public void includesModifiedAddedAndDeletedJavaFiles(@TempDir Path repositoryPath) throws Exception {
		Path modified = repositoryPath.resolve("Modified.java"); //$NON-NLS-1$
		Path deleted = repositoryPath.resolve("Deleted.java"); //$NON-NLS-1$
		Path ignored = repositoryPath.resolve("notes.txt"); //$NON-NLS-1$

		try (Git git = Git.init().setDirectory(repositoryPath.toFile()).call()) {
			Files.writeString(modified, "class Modified { int value = 1; }\n", StandardCharsets.UTF_8); //$NON-NLS-1$
			Files.writeString(deleted, "class Deleted {}\n", StandardCharsets.UTF_8); //$NON-NLS-1$
			Files.writeString(ignored, "old\n", StandardCharsets.UTF_8); //$NON-NLS-1$
			git.add().addFilepattern(".").call(); //$NON-NLS-1$
			git.commit().setMessage("baseline").setAuthor("Sandbox", "sandbox@example.invalid") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.setCommitter("Sandbox", "sandbox@example.invalid").call(); //$NON-NLS-1$ //$NON-NLS-2$

			Files.writeString(modified, "class Modified { int value = 2; }\n", StandardCharsets.UTF_8); //$NON-NLS-1$
			Path added = repositoryPath.resolve("Added.java"); //$NON-NLS-1$
			Files.writeString(added, "class Added {}\n", StandardCharsets.UTF_8); //$NON-NLS-1$
			git.add().addFilepattern("Added.java").call(); //$NON-NLS-1$
			Files.delete(deleted);
			Files.writeString(ignored, "new\n", StandardCharsets.UTF_8); //$NON-NLS-1$

			Map<String, FileDiff> diffs = new WorkingTreeDiffProvider().getDiffs(repositoryPath).stream()
					.collect(Collectors.toMap(FileDiff::filePath, Function.identity()));

			assertEquals(3, diffs.size());
			assertTrue(diffs.containsKey("Modified.java")); //$NON-NLS-1$
			assertTrue(diffs.containsKey("Added.java")); //$NON-NLS-1$
			assertTrue(diffs.containsKey("Deleted.java")); //$NON-NLS-1$

			assertTrue(diffs.get("Modified.java").contentBefore().contains("value = 1")); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(diffs.get("Modified.java").contentAfter().contains("value = 2")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("", diffs.get("Added.java").contentBefore()); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(diffs.get("Added.java").contentAfter().contains("class Added")); //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(diffs.get("Deleted.java").contentBefore().contains("class Deleted")); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("", diffs.get("Deleted.java").contentAfter()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
