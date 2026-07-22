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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
	 * Saves a candidate atomically. Corrected semantic content for the same stable
	 * candidate ID starts a new revision at {@link CandidateStatus#DISCOVERED},
	 * clears stale verification/rejection state, and therefore requires all gates
	 * and human review to run again. Status-only updates do not increment the
	 * revision. Promoted provenance is immutable. Legacy filenames for the same
	 * proposal are removed after a successful write.
	 */
	public void save(MiningCandidate candidate) throws IOException {
		if (candidate == null) {
			throw new IllegalArgumentException("candidate must not be null"); //$NON-NLS-1$
		}
		Files.createDirectories(storeDir);
		if (candidate.getSchemaVersion() < 2) {
			candidate.setSchemaVersion(2);
		}
		candidate.setRevision(candidate.getRevision());

		Path target = storeDir.resolve(candidate.toFileName());
		Optional<MiningCandidate> existing = load(target);
		if (existing.isPresent()) {
			MiningCandidate previous = existing.get();
			boolean contentChanged = semanticContentChanged(previous, candidate);
			if (contentChanged) {
				startNewRevision(previous, candidate);
			} else if (candidate.getRevision() < previous.getRevision()) {
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
		deleteLegacyAliases(candidate, target);
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

	/** Returns whether the same candidate revision content is already stored. */
	public boolean containsCandidate(MiningCandidate candidate) {
		if (candidate == null) {
			return false;
		}
		Optional<MiningCandidate> stored = findById(candidate.getCandidateId());
		return stored.filter(existing -> !semanticContentChanged(existing, candidate)).isPresent();
	}

	public Path getStoreDir() {
		return storeDir;
	}

	private static boolean semanticContentChanged(MiningCandidate previous,
			MiningCandidate candidate) {
		return !previous.getRuleFingerprint().equals(candidate.getRuleFingerprint())
				|| !previous.getBehaviorFingerprint().equals(candidate.getBehaviorFingerprint())
				|| !Objects.equals(previous.getSourceVersion(), candidate.getSourceVersion());
	}

	private static void startNewRevision(MiningCandidate previous,
			MiningCandidate candidate) {
		if (previous.getStatus() == CandidateStatus.PROMOTED) {
			throw new IllegalStateException(
					"Promoted candidate content is immutable; create a new candidate origin"); //$NON-NLS-1$
		}

		int newRevision = Math.max(previous.getRevision() + 1, candidate.getRevision());
		List<CandidateTransition> history = new ArrayList<>(previous.getTransitions());
		history.add(new CandidateTransition(
				previous.getStatus(), CandidateStatus.DISCOVERED,
				"CandidateStore", //$NON-NLS-1$
				"Semantic content changed; revision " + newRevision //$NON-NLS-1$
						+ " requires deterministic verification and review", //$NON-NLS-1$
				Instant.now().toString()));

		candidate.setCandidateId(previous.getCandidateId());
		candidate.setRevision(newRevision);
		candidate.setStatus(CandidateStatus.DISCOVERED);
		candidate.setVerification(null);
		candidate.setRejectionReason(null);
		candidate.setTransitions(history);
	}

	private void deleteLegacyAliases(MiningCandidate candidate, Path canonicalPath)
			throws IOException {
		try (Stream<Path> paths = Files.list(storeDir)) {
			List<Path> aliases = paths.filter(CandidateStore::isCandidateFile)
					.filter(path -> !path.equals(canonicalPath))
					.filter(path -> load(path)
							.filter(existing -> existing.getSchemaVersion() < 2)
							.filter(existing -> sameProposal(existing, candidate))
							.isPresent())
					.toList();
			for (Path alias : aliases) {
				Files.deleteIfExists(alias);
			}
		}
	}

	private static boolean sameProposal(MiningCandidate first, MiningCandidate second) {
		return Objects.equals(first.getSourceRepo(), second.getSourceRepo())
				&& Objects.equals(first.getSourceCommit(), second.getSourceCommit())
				&& Objects.equals(first.getCategory(), second.getCategory())
				&& Objects.equals(first.getTargetHintFile(), second.getTargetHintFile())
				&& first.getRuleFingerprint().equals(second.getRuleFingerprint())
				&& first.getBehaviorFingerprint().equals(second.getBehaviorFingerprint());
	}

	private Optional<MiningCandidate> load(Path path) {
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject object = JsonParser.parseString(json).getAsJsonObject();
			MiningCandidate candidate = GSON.fromJson(object, MiningCandidate.class);
			if (candidate == null) {
				return Optional.empty();
			}
			if (!object.has("schemaVersion")) { //$NON-NLS-1$
				candidate.setSchemaVersion(1);
			}
			if (!object.has("revision")) { //$NON-NLS-1$
				candidate.setRevision(1);
			}
			if (!object.has("candidateId")) { //$NON-NLS-1$
				candidate.setCandidateId(null);
			}
			if (!object.has("sourceVersion")) { //$NON-NLS-1$
				candidate.setSourceVersion(null);
			}
			return Optional.of(candidate);
		} catch (Exception e) {
			System.err.println("Warning: could not load candidate " + path + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return Optional.empty();
		}
	}

	private static boolean isCandidateFile(Path path) {
		Path fileName = path.getFileName();
		return Files.isRegularFile(path)
				&& fileName != null
				&& fileName.toString().endsWith("-candidate.json"); //$NON-NLS-1$
	}
}
