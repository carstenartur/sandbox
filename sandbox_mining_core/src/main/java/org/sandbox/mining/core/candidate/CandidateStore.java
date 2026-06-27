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
package org.sandbox.mining.core.candidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Persistent store for {@link MiningCandidate} objects in the
 * {@code mining-candidates/} directory.
 *
 * <p>Each candidate is stored as an individual JSON file named after a stable
 * candidate ID (SHA-256 derived from repo + commit + category + hint target + DSL).
 * This allows
 * individual candidates to be reviewed, promoted, or rejected without
 * touching productive bundled hint files.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * CandidateStore store = new CandidateStore(Path.of("mining-candidates"));
 * store.save(candidate);
 * List&lt;MiningCandidate&gt; all = store.loadAll();
 * </pre>
 */
public class CandidateStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path storeDir;

	/**
	 * Creates a new candidate store for the given directory.
	 * The directory will be created if it does not exist.
	 *
	 * @param storeDir the directory to store candidate JSON files in
	 */
	public CandidateStore(Path storeDir) {
		this.storeDir = storeDir;
	}

	/**
	 * Saves a candidate to its JSON file. If a file for the same commit
	 * hash already exists, it is overwritten atomically.
	 *
	 * @param candidate the candidate to save
	 * @throws IOException if the file cannot be written
	 */
	public void save(MiningCandidate candidate) throws IOException {
		Files.createDirectories(storeDir);
		String fileName = candidate.toFileName();
		Path target = storeDir.resolve(fileName);
		Path tmpFile = storeDir.resolve(fileName + ".tmp"); //$NON-NLS-1$
		String json = GSON.toJson(candidate);
		Files.writeString(tmpFile, json, StandardCharsets.UTF_8);
		Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/**
	 * Loads all candidate JSON files from the store directory.
	 * Files that cannot be parsed are silently skipped with a warning.
	 *
	 * @return list of all loaded candidates
	 * @throws IOException if the directory cannot be listed
	 */
	public List<MiningCandidate> loadAll() throws IOException {
		if (!Files.exists(storeDir)) {
			return List.of();
		}
		List<MiningCandidate> candidates = new ArrayList<>();
		try (Stream<Path> paths = Files.list(storeDir)) {
			paths.filter(p -> p.getFileName().toString().endsWith("-candidate.json")) //$NON-NLS-1$
				.sorted()
				.forEach(p -> {
					try {
						String json = Files.readString(p, StandardCharsets.UTF_8);
						MiningCandidate candidate = GSON.fromJson(json, MiningCandidate.class);
						if (candidate != null) {
							candidates.add(candidate);
						}
					} catch (Exception e) {
						System.err.println("Warning: could not load candidate " + p + ": " + e.getMessage()); //$NON-NLS-1$
					}
				});
		}
		return candidates;
	}

	/**
	 * Loads candidates filtered by status.
	 *
	 * @param status the status to filter by
	 * @return list of candidates with the given status
	 * @throws IOException if the directory cannot be listed
	 */
	public List<MiningCandidate> loadByStatus(CandidateStatus status) throws IOException {
		List<MiningCandidate> all = loadAll();
		List<MiningCandidate> filtered = new ArrayList<>();
		for (MiningCandidate c : all) {
			if (status.equals(c.getStatus())) {
				filtered.add(c);
			}
		}
		return filtered;
	}

	/**
	 * Returns whether this exact candidate already exists in the store.
	 *
	 * @param candidate the candidate to check
	 * @return {@code true} if a candidate file exists for this candidate ID
	 */
	public boolean containsCandidate(MiningCandidate candidate) {
		if (candidate == null) {
			return false;
		}
		return Files.exists(storeDir.resolve(candidate.toFileName()));
	}

	/**
	 * Returns the store directory path.
	 *
	 * @return the store directory
	 */
	public Path getStoreDir() {
		return storeDir;
	}
}
