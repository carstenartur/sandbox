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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
		Path target = storeDir.resolve(candidate.toFileName());
		Files.createDirectories(storeDir);
		if (candidate.getSchemaVersion() < 2) {
			candidate.setSchemaVersion(2);
		}
		candidate.setRevision(candidate.getRevision());

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

		Path tmpFile = Files.createTempFile(storeDir, "candidate-", ".tmp"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			Files.writeString(tmpFile, GSON.toJson(candidate) + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
			try {
				Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmpFile, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmpFile);
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
		if (!MiningCandidate.isValidCandidateId(candidateId)) {
			return Optional.empty();
		}
		return load(storeDir.resolve(candidateId + MiningCandidate.FILE_SUFFIX))
				.filter(candidate -> candidateId.equals(candidate.getCandidateId()));
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
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			return Optional.empty();
		}
		try {
			return Optional.of(readCandidate(path));
		} catch (Exception e) {
			System.err.println("Warning: could not load candidate " + path + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return Optional.empty();
		}
	}

	/** Reads a staged record, including legacy schema defaults and the v2 filename binding. */
	static MiningCandidate readCandidate(Path path) throws IOException {
		Path fileName = path.getFileName();
		if (fileName == null) {
			throw new IllegalArgumentException("Candidate path must name a file"); //$NON-NLS-1$
		}
		try (var reader = new InputStreamReader(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS),
				StandardCharsets.UTF_8.newDecoder())) {
			var json = JsonParser.parseReader(reader);
			if (!json.isJsonObject()) {
				throw new IllegalArgumentException("Candidate file must contain a JSON object"); //$NON-NLS-1$
			}
			JsonObject object = json.getAsJsonObject();
			MiningCandidate candidate = GSON.fromJson(object, MiningCandidate.class);
			if (!object.has("schemaVersion")) { //$NON-NLS-1$
				candidate.setSchemaVersion(1);
			}
			if (!object.has("revision")) { //$NON-NLS-1$
				candidate.setRevision(1);
			}
			if (!object.has("sourceVersion")) { //$NON-NLS-1$
				candidate.setSourceVersion(null);
			}
			String canonicalName = candidate.toFileName();
			String name = fileName.toString();
			// Schema v1 had no persisted ID and used a different content hash for its filenames.
			var storedId = object.get("candidateId"); //$NON-NLS-1$
			if ((candidate.getSchemaVersion() >= 2 || storedId != null && !storedId.isJsonNull())
					&& name.endsWith(MiningCandidate.FILE_SUFFIX) && !canonicalName.equals(name)) {
				throw new IllegalArgumentException("Stored candidate ID does not match its filename"); //$NON-NLS-1$
			}
			return candidate;
		}
	}

	private static boolean isCandidateFile(Path path) {
		Path fileName = path.getFileName();
		return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
				&& fileName != null
				&& fileName.toString().endsWith(MiningCandidate.FILE_SUFFIX);
	}
}
