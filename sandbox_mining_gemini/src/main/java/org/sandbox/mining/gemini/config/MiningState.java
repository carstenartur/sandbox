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
package org.sandbox.mining.gemini.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Manages persistent state for the mining process.
 *
 * <p>Tracks per-repository progress (last processed commit, date, count)
 * and global statistics. State is persisted as JSON.</p>
 */
public class MiningState {

	private Map<String, RepoState> repositories = new LinkedHashMap<>();
	private int globalTotalProcessed;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Per-repository state tracking.
	 */
	public static class RepoState {
		private String lastProcessedCommit;
		private String lastProcessedDate;
		private int totalProcessed;
		private String status = "CATCHING_UP";

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
	 * Saves the current state to a JSON file.
	 *
	 * @param path path to the state JSON file
	 * @throws IOException if the file cannot be written
	 */
	public void save(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
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

	public Map<String, RepoState> getRepositories() {
		return repositories;
	}

	public int getGlobalTotalProcessed() {
		return globalTotalProcessed;
	}
}
