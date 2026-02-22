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
package org.sandbox.mining.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/**
 * Manages persistent state for the mining process.
 *
 * <p>Tracks per-repository progress (last processed commit, date, count)
 * and global statistics. State is persisted as JSON.</p>
 */
public class MiningState {

	private Map<String, RepoState> repositories = new LinkedHashMap<>();
	private int globalTotalProcessed;

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
					new JsonPrimitive(src.toString()))
			.create();

	/**
	 * Per-repository state tracking.
	 */
	public static class RepoState {
		private String lastProcessedCommit;
		private String lastProcessedDate;
		private int totalProcessed;
		private String status = "CATCHING_UP";
		private List<DeferredCommit> deferredCommits = new ArrayList<>();
		private List<String> permanentlySkipped = new ArrayList<>();
		private int learnedMaxDiffLines = -1;
		private String lastModelUsed;

		public String getLastProcessedCommit() {
			return lastProcessedCommit;
		}

		public void setLastProcessedCommit(String lastProcessedCommit) {
			this.lastProcessedCommit = lastProcessedCommit;
		}

		public String getLastProcessedDate() {
			return lastProcessedDate;
		}

		public void setLastProcessedDate(String lastProcessedDate) {
			this.lastProcessedDate = lastProcessedDate;
		}

		public int getTotalProcessed() {
			return totalProcessed;
		}

		public void setTotalProcessed(int totalProcessed) {
			this.totalProcessed = totalProcessed;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public List<DeferredCommit> getDeferredCommits() {
			return deferredCommits;
		}

		public void setDeferredCommits(List<DeferredCommit> deferredCommits) {
			this.deferredCommits = deferredCommits != null ? deferredCommits : new ArrayList<>();
		}

		public List<String> getPermanentlySkipped() {
			return permanentlySkipped;
		}

		public void setPermanentlySkipped(List<String> permanentlySkipped) {
			this.permanentlySkipped = permanentlySkipped != null ? permanentlySkipped : new ArrayList<>();
		}

		public int getLearnedMaxDiffLines() {
			return learnedMaxDiffLines;
		}

		public void setLearnedMaxDiffLines(int learnedMaxDiffLines) {
			this.learnedMaxDiffLines = learnedMaxDiffLines;
		}

		public String getLastModelUsed() {
			return lastModelUsed;
		}

		public void setLastModelUsed(String lastModelUsed) {
			this.lastModelUsed = lastModelUsed;
		}

		/**
		 * Adds a deferred commit to this repository state.
		 *
		 * @param deferred the deferred commit to add
		 */
		public void addDeferredCommit(DeferredCommit deferred) {
			boolean alreadyDeferred = deferredCommits.stream()
					.anyMatch(d -> d.getHash().equals(deferred.getHash()));
			if (!alreadyDeferred && !permanentlySkipped.contains(deferred.getHash())) {
				deferredCommits.add(deferred);
			}
		}

		/**
		 * Removes a deferred commit by hash (e.g. when it has been successfully evaluated).
		 *
		 * @param hash the commit hash to remove
		 */
		public void removeDeferredCommit(String hash) {
			deferredCommits.removeIf(d -> d.getHash().equals(hash));
		}

		/**
		 * Moves a deferred commit to the permanently skipped list.
		 *
		 * @param hash the commit hash to move
		 */
		public void moveToPermanentlySkipped(String hash) {
			deferredCommits.removeIf(d -> d.getHash().equals(hash));
			if (!permanentlySkipped.contains(hash)) {
				permanentlySkipped.add(hash);
			}
		}
	}

	/**
	 * Represents a commit that was deferred for later retry.
	 */
	public static class DeferredCommit {
		private String hash;
		private String message;
		private int diffLines;
		private String reason;
		private String deferredAt;
		private int retryCount;
		private int maxRetries = 3;

		public DeferredCommit() {
		}

		public DeferredCommit(String hash, String message, int diffLines,
				String reason, String deferredAt, int retryCount, int maxRetries) {
			this.hash = hash;
			this.message = message;
			this.diffLines = diffLines;
			this.reason = reason;
			this.deferredAt = deferredAt;
			this.retryCount = retryCount;
			this.maxRetries = maxRetries;
		}

		public String getHash() { return hash; }
		public void setHash(String hash) { this.hash = hash; }
		public String getMessage() { return message; }
		public void setMessage(String message) { this.message = message; }
		public int getDiffLines() { return diffLines; }
		public void setDiffLines(int diffLines) { this.diffLines = diffLines; }
		public String getReason() { return reason; }
		public void setReason(String reason) { this.reason = reason; }
		public String getDeferredAt() { return deferredAt; }
		public void setDeferredAt(String deferredAt) { this.deferredAt = deferredAt; }
		public int getRetryCount() { return retryCount; }
		public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
		public int getMaxRetries() { return maxRetries; }
		public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
	}

	/**
	 * Loads state from a JSON file, or creates a new empty state if the file
	 * does not exist.
	 *
	 * @param path path to the state JSON file
	 * @return the loaded or new state
	 * @throws IOException if the file cannot be read
	 */
	public static MiningState load(Path path) throws IOException {
		if (!Files.exists(path)) {
			return new MiningState();
		}
		String json = Files.readString(path, StandardCharsets.UTF_8);
		MiningState state = GSON.fromJson(json, MiningState.class);
		return state != null ? state : new MiningState();
	}

	/**
	 * Saves the current state to a JSON file atomically.
	 *
	 * <p>Writes to a temporary file first, then atomically renames to avoid
	 * corruption if the process is interrupted.</p>
	 *
	 * @param path path to the state JSON file
	 * @throws IOException if the file cannot be written
	 */
	public void save(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temp = path.resolveSibling(path.getFileName() + ".tmp"); //$NON-NLS-1$
		Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
		try {
			Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Creates a backup of the state file if it exists.
	 *
	 * @param path path to the state JSON file
	 * @throws IOException if the file cannot be copied
	 */
	public static void backup(Path path) throws IOException {
		if (Files.exists(path)) {
			Path backupPath = path.resolveSibling(path.getFileName() + ".bak"); //$NON-NLS-1$
			Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Gets the last processed commit hash for a repository.
	 *
	 * @param repoUrl the repository URL
	 * @return the commit hash, or null if not yet processed
	 */
	public String getLastProcessedCommit(String repoUrl) {
		RepoState repoState = repositories.get(repoUrl);
		return repoState != null ? repoState.getLastProcessedCommit() : null;
	}

	/**
	 * Updates the last processed commit for a repository.
	 *
	 * @param repoUrl    the repository URL
	 * @param commitHash the last processed commit hash
	 */
	public void updateLastProcessedCommit(String repoUrl, String commitHash) {
		RepoState repoState = repositories.computeIfAbsent(repoUrl, k -> new RepoState());
		repoState.setLastProcessedCommit(commitHash);
		repoState.setLastProcessedDate(Instant.now().toString());
		repoState.setTotalProcessed(repoState.getTotalProcessed() + 1);
		globalTotalProcessed++;
	}

	/**
	 * Gets or creates the RepoState for a repository URL.
	 *
	 * @param repoUrl the repository URL
	 * @return the repo state
	 */
	public RepoState getRepoState(String repoUrl) {
		return repositories.computeIfAbsent(repoUrl, k -> new RepoState());
	}

	public Map<String, RepoState> getRepositories() {
		return repositories;
	}

	public int getGlobalTotalProcessed() {
		return globalTotalProcessed;
	}
}
