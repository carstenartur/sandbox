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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;

/**
 * A JGit-backed test fixture that checks out one exact repository ref and commit.
 *
 * <p>The fixture deliberately does not invoke command-line Git or Python. It
 * resolves the advertised ref first and fetches only that ref. Closing the
 * fixture releases JGit resources and removes the checkout directory.</p>
 *
 * @since 1.3.4
 */
public final class PinnedGitRepository implements AutoCloseable {

	private static final String PINNED_REF = "refs/sandbox/pinned"; //$NON-NLS-1$

	private final Path directory;
	private final Git git;

	private PinnedGitRepository(Path directory, Git git) {
		this.directory = directory;
		this.git = git;
	}

	/**
	 * Fetches one repository ref, verifies that it resolves to
	 * {@code expectedCommit}, and checks out that commit detached.
	 *
	 * @param directory      target checkout directory
	 * @param remote         repository URI
	 * @param ref            branch, tag, or full ref to verify
	 * @param expectedCommit exact expected commit SHA
	 * @return the verified fixture
	 * @throws GitAPIException when ref discovery, fetch, or checkout fails
	 * @throws IOException     when identity verification or cleanup fails
	 */
	public static PinnedGitRepository cloneAt(Path directory, URI remote, String ref,
			String expectedCommit) throws GitAPIException, IOException {
		Objects.requireNonNull(directory, "directory"); //$NON-NLS-1$
		Objects.requireNonNull(remote, "remote"); //$NON-NLS-1$
		Objects.requireNonNull(ref, "ref"); //$NON-NLS-1$
		Objects.requireNonNull(expectedCommit, "expectedCommit"); //$NON-NLS-1$
		if (Files.exists(directory)) {
			throw new IllegalArgumentException("Checkout directory already exists: " + directory); //$NON-NLS-1$
		}

		Ref advertisedRef = resolveRemoteRef(remote, ref);
		if (advertisedRef == null) {
			throw new IllegalArgumentException("Pinned ref is not advertised by the remote: " + ref); //$NON-NLS-1$
		}

		Git git = Git.init().setDirectory(directory.toFile()).call();
		try {
			Repository repository = git.getRepository();
			StoredConfig config = repository.getConfig();
			config.setString("remote", Constants.DEFAULT_REMOTE_NAME, "url", remote.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			config.save();

			RefSpec pinnedRefSpec = new RefSpec()
					.setForceUpdate(true)
					.setSource(advertisedRef.getName())
					.setDestination(PINNED_REF);
			git.fetch()
					.setRemote(Constants.DEFAULT_REMOTE_NAME)
					.setRefSpecs(pinnedRefSpec)
					.setTagOpt(TagOpt.NO_TAGS)
					.call();

			ObjectId expected = repository.resolve(expectedCommit + "^{commit}"); //$NON-NLS-1$
			if (expected == null) {
				throw new IllegalArgumentException(
						"Expected commit is unavailable after fetching the pinned ref: " + expectedCommit); //$NON-NLS-1$
			}
			ObjectId actualRef = repository.resolve(PINNED_REF + "^{commit}"); //$NON-NLS-1$
			if (actualRef == null) {
				throw new IllegalStateException("Fetched pinned ref cannot be resolved: " + PINNED_REF); //$NON-NLS-1$
			}
			if (!expected.equals(actualRef)) {
				throw new IllegalArgumentException("Pinned ref " + advertisedRef.getName() + " resolves to " //$NON-NLS-1$ //$NON-NLS-2$
						+ actualRef.name() + ", expected " + expected.name()); //$NON-NLS-1$
			}

			git.checkout().setName(expected.name()).call();
			ObjectId head = repository.resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
			if (!expected.equals(head)) {
				throw new IllegalStateException("Detached checkout resolved to " //$NON-NLS-1$
						+ (head == null ? "<missing>" : head.name()) + ", expected " + expected.name()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return new PinnedGitRepository(directory, git);
		} catch (GitAPIException | IOException | RuntimeException failure) {
			git.close();
			try {
				deleteRecursively(directory);
			} catch (IOException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
	}

	/**
	 * Returns the checkout directory.
	 *
	 * @return checkout directory
	 */
	public Path directory() {
		return directory;
	}

	/**
	 * Returns the exact checked-out commit SHA.
	 *
	 * @return current HEAD commit
	 * @throws IOException when HEAD cannot be resolved
	 */
	public String headCommit() throws IOException {
		ObjectId head = git.getRepository().resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
		if (head == null) {
			throw new IOException("Pinned repository has no resolvable HEAD"); //$NON-NLS-1$
		}
		return head.name();
	}

	/**
	 * Reads a UTF-8 file from the checkout.
	 *
	 * @param relativePath repository-relative path
	 * @return file contents
	 * @throws IOException when the file cannot be read
	 */
	public String readString(String relativePath) throws IOException {
		return Files.readString(directory.resolve(relativePath), StandardCharsets.UTF_8);
	}

	boolean hasRef(String ref) throws IOException {
		return git.getRepository().exactRef(ref) != null;
	}

	@Override
	public void close() throws IOException {
		git.close();
		deleteRecursively(directory);
	}

	private static Ref resolveRemoteRef(URI remote, String ref) throws GitAPIException {
		Collection<Ref> advertisedRefs = Git.lsRemoteRepository()
				.setRemote(remote.toString())
				.setHeads(true)
				.setTags(true)
				.call();
		Set<String> candidates = new LinkedHashSet<>();
		candidates.add(ref);
		if (ref.startsWith(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/")) { //$NON-NLS-1$
			candidates.add(Constants.R_HEADS
					+ ref.substring((Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/").length())); //$NON-NLS-1$
		} else if (!ref.startsWith("refs/")) { //$NON-NLS-1$
			candidates.add(Constants.R_HEADS + ref);
			candidates.add(Constants.R_TAGS + ref);
		}
		for (String candidate : candidates) {
			for (Ref advertisedRef : advertisedRefs) {
				if (candidate.equals(advertisedRef.getName())) {
					return advertisedRef;
				}
			}
		}
		return null;
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
				if (failure != null) {
					throw failure;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
