/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryImportTest {

	@TempDir Path directory;

	@Test void importPublishesCommittedBranchesAndTags() throws Exception {
		try (Git source= Git.init().setDirectory(directory.resolve("source").toFile()).setInitialBranch("main").call();
				Git target= Git.init().setDirectory(directory.resolve("target.git").toFile()).setBare(true).call()) {
			ObjectId head= commit(source, "Initial Java source");
			source.tag().setName("v1").setAnnotated(false).call();
			new IncrementalIndexer(target.getRepository().getDirectory().toURI().toString())
					.indexNewCommits(source.getRepository(), new NullProgressMonitor());
			assertEquals(head, target.getRepository().resolve("refs/heads/main"));
			assertEquals(head, target.getRepository().resolve("refs/tags/v1"));
		}
	}

	@Test void cancelledImportCannotReportSuccess() throws Exception {
		try (Git source= Git.init().setDirectory(directory.resolve("source").toFile()).call()) {
			commit(source, "Initial");
			var monitor= new NullProgressMonitor();
			monitor.setCanceled(true);
			assertThrows(OperationCanceledException.class,
					() -> new IncrementalIndexer(directory.resolve("target.git").toUri().toString())
					.indexNewCommits(source.getRepository(), monitor));
		}
	}

	@Test void unrelatedRemoteHistoryIsNotOverwritten() throws Exception {
		try (Git source= Git.init().setDirectory(directory.resolve("source").toFile()).setInitialBranch("main").call();
				Git target= Git.init().setDirectory(directory.resolve("target").toFile()).setInitialBranch("main").call()) {
			commit(source, "Local history");
			ObjectId remote= commit(target, "Independent server history");
			assertThrows(Exception.class, () -> new IncrementalIndexer(target.getRepository().getDirectory().toURI().toString())
					.indexNewCommits(source.getRepository(), new NullProgressMonitor()));
			assertEquals(remote, target.getRepository().resolve("refs/heads/main"));
		}
	}

	@Test void emptyRepositoryFailsAndServerOnlyRefsSurviveRepeatedImports() throws Exception {
		try (Git source= Git.init().setDirectory(directory.resolve("source").toFile()).setInitialBranch("main").call();
				Git target= Git.init().setDirectory(directory.resolve("target.git").toFile()).setBare(true).call()) {
			var importer= new IncrementalIndexer(target.getRepository().getDirectory().toURI().toString());
			assertThrows(java.io.IOException.class, () -> importer.indexNewCommits(source.getRepository(), new NullProgressMonitor()));
			ObjectId head= commit(source, "Initial");
			importer.indexNewCommits(source.getRepository(), new NullProgressMonitor());
			var serverOnly= target.getRepository().updateRef("refs/heads/server-only");
			serverOnly.setNewObjectId(head);
			serverOnly.update();
			importer.indexNewCommits(source.getRepository(), new NullProgressMonitor());
			assertEquals(head, target.getRepository().resolve("refs/heads/server-only"));
			assertEquals(head, target.getRepository().resolve("refs/heads/main"));
		}
	}

	private ObjectId commit(Git git, String message) throws Exception {
		Files.writeString(git.getRepository().getWorkTree().toPath().resolve("Clock.java"), "class Clock {}\n");
		git.add().addFilepattern(".").call();
		return git.commit().setMessage(message).setAuthor("Alice", "alice@example.test")
				.setCommitter("Alice", "alice@example.test").call().getId();
	}
}
