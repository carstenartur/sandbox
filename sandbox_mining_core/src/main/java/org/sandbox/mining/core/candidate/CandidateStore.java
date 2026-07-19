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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Persistent candidate store. Candidate JSON is the authoritative record for an
 * unreviewed proposal; reports and workflow issues are derived views.
 */
public class CandidateStore {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path storeDir;

	public CandidateStore(Path storeDir) {
		this.storeDir = storeDir;
	}

	/**
	 * Saves a candidate atomically. Corrected content for the same stable
	 * candidate ID increments the revision; status-only updates do not.
	 */
	public void save(MiningCandidate candidate) throws IOException {
		if (candidate == null) {
			throw new IllegalArgumentException("candidate must not be null"); //$NON-NLS-1$
		}
		Files.createDirectories(storeDir);
		Path target = storeDir.resolve(candidate.toFileName());
		Optional<MiningCandidate> existing = load(target);
		if (existing.isPresent()) {
			MiningCandidate previous = existing.get();
			boolean contentChanged = !previous.getRuleFingerprint().equals(candidate.getRuleFingerprint())
					|| !previous.getBehaviorFingerprint().equals(candidate.getBehaviorFingerprint());
			if (contentChanged && candidate.getRevision() <= previous.getRevision()) {
				candidate.setRevision(previous.getRevision() + 1);
			} else if (!contentChanged && candidate.getRevision() < previous.getRevision()) {
				candidate.setRevision(previous.getRevision());
			}
		}

		Path tmpFile = storeDir.resolve(candidate.toFileName() + ".tmp"); //$NON-NLS-1$
		Files.writeString(tmpFile, GSON.toJson(candidate) + System.lineSeparator(),
				StandardCharsets.UTF_8);
		try {
			Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Loads all candidate files in deterministic filename order. */
	public List<MiningCandidate> loadAll() throws IOException {
		if (!Files.exists(storeDir)) {
			return List.of();
		}
		List<MiningCandidate> candidates = new ArrayList<>();
		try (Stream<Path> paths = Files.list(storeDir)) {
			paths.filter(CandidateStore::isCandidateFile)
					.sorted()
					.forEach(path -> load(path).ifPresent(candidates::add));
		}
		return candidates;
	}

	/** Loads candidates filtered by lifecycle status. */
	public List<MiningCandidate> loadByStatus(CandidateStatus status) throws IOException {
		if (status == null) {
			return List.of();
		}
		return loadAll().stream().filter(candidate -> status == candidate.getStatus()).toList();
	}

	/** Loads a candidate by stable candidate ID. */
	public Optional<MiningCandidate> findById(String candidateId) {
		if (candidateId == null || candidateId.isBlank()) {
			return Optional.empty();
		}
		return load(storeDir.resolve(candidateId + "-candidate.json")); //$NON-NLS-1$
	}

	/**
	 * Returns whether the same candidate revision content is already stored.
	 * A corrected rule or example set for the same origin is not considered an
	 * exact duplicate and will be saved as a new revision.
	 */
	public boolean containsCandidate(MiningCandidate candidate) {
		if (candidate == null) {
			return false;
		}
		Optional<MiningCandidate> stored = findById(candidate.getCandidateId());
		return stored.filter(existing -> existing.getRuleFingerprint().equals(candidate.getRuleFingerprint())
				&& existing.getBehaviorFingerprint().equals(candidate.getBehaviorFingerprint())).isPresent();
	}

	public Path getStoreDir() {
		return storeDir;
	}

	private Optional<MiningCandidate> load(Path path) {
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			MiningCandidate candidate = GSON.fromJson(json, MiningCandidate.class);
			return Optional.ofNullable(candidate);
		} catch (Exception e) {
			System.err.println("Warning: could not load candidate " + path + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return Optional.empty();
		}
	}

	private static boolean isCandidateFile(Path path) {
		return Files.isRegularFile(path)
				&& path.getFileName().toString().endsWith("-candidate.json"); //$NON-NLS-1$
	}
}
