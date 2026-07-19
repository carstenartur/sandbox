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
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Generates the repository changes for a minimal candidate promotion pull
 * request. The command never merges or pushes anything itself.
 */
public final class CandidatePromotionCli {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String HINT_DIRECTORY =
			"sandbox_common_core/src/main/resources/org/sandbox/jdt/triggerpattern/internal"; //$NON-NLS-1$
	private static final String FIXTURE_DIRECTORY =
			"sandbox_common_core/src/test/resources/org/sandbox/jdt/triggerpattern/promoted"; //$NON-NLS-1$

	private CandidatePromotionCli() {
	}

	public static void main(String[] args) {
		try {
			int result = run(args);
			if (result != 0) {
				System.exit(result);
			}
		} catch (Exception e) {
			System.err.println("Candidate promotion generation failed: " + e.getMessage()); //$NON-NLS-1$
			System.exit(1);
		}
	}

	static int run(String[] args) throws IOException {
		Path candidateFile = null;
		Path repoRoot = Path.of("."); //$NON-NLS-1$
		String actor = null;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--candidate": //$NON-NLS-1$
				candidateFile = Path.of(requireValue(args, ++i, "--candidate")); //$NON-NLS-1$
				break;
			case "--repo-root": //$NON-NLS-1$
				repoRoot = Path.of(requireValue(args, ++i, "--repo-root")); //$NON-NLS-1$
				break;
			case "--actor": //$NON-NLS-1$
				actor = requireValue(args, ++i, "--actor"); //$NON-NLS-1$
				break;
			default:
				throw new IllegalArgumentException("Unknown option: " + args[i]); //$NON-NLS-1$
			}
		}

		if (candidateFile == null || !Files.isRegularFile(candidateFile)) {
			throw new IllegalArgumentException("--candidate must reference an existing file"); //$NON-NLS-1$
		}
		if (actor == null || actor.isBlank()) {
			throw new IllegalArgumentException("--actor is required"); //$NON-NLS-1$
		}

		MiningCandidate candidate = GSON.fromJson(
				Files.readString(candidateFile, StandardCharsets.UTF_8), MiningCandidate.class);
		validateApprovedCandidate(candidate);
		CandidateVerification verification = new CandidateVerifier().verify(candidate);
		if (!verification.successful()) {
			throw new IllegalStateException("Approved candidate no longer verifies: " //$NON-NLS-1$
					+ verification.stage() + ": " + verification.message()); //$NON-NLS-1$
		}

		String targetName = validateTargetFileName(candidate.getTargetHintFile());
		Path normalizedRoot = repoRoot.toAbsolutePath().normalize();
		Path curatedHintDirectory = normalizedRoot.resolve(HINT_DIRECTORY).normalize();
		Path hintFile = curatedHintDirectory.resolve(targetName).normalize();
		if (!hintFile.startsWith(curatedHintDirectory)) {
			throw new IllegalArgumentException("Target hint file escapes the curated hint directory"); //$NON-NLS-1$
		}
		if (!Files.isRegularFile(hintFile)) {
			throw new IllegalArgumentException("Target hint file does not exist: " + hintFile); //$NON-NLS-1$
		}

		String semanticFingerprint = RuleFingerprintIndex.fingerprintDsl(candidate.getDslRule());
		String existingRule = RuleFingerprintIndex.loadCurated(curatedHintDirectory)
				.get(semanticFingerprint);
		if (existingRule != null) {
			throw new IllegalStateException(
					"Candidate duplicates curated rule " + existingRule); //$NON-NLS-1$
		}

		appendRule(candidate, hintFile);
		Path fixtureDirectory = normalizedRoot.resolve(FIXTURE_DIRECTORY);
		Files.createDirectories(fixtureDirectory);
		String fixtureName = candidate.getCandidateId() + ".json"; //$NON-NLS-1$
		Path fixtureFile = fixtureDirectory.resolve(fixtureName);
		PromotionFixture fixture = PromotionFixture.from(candidate, verification, actor);
		Files.writeString(fixtureFile, GSON.toJson(fixture) + System.lineSeparator(),
				StandardCharsets.UTF_8);
		updateIndex(fixtureDirectory.resolve("index.txt"), fixtureName); //$NON-NLS-1$

		System.out.println("Promotion changes generated:"); //$NON-NLS-1$
		System.out.println("  " + normalizedRoot.relativize(hintFile)); //$NON-NLS-1$
		System.out.println("  " + normalizedRoot.relativize(fixtureFile)); //$NON-NLS-1$
		System.out.println("  " + normalizedRoot.relativize(fixtureDirectory.resolve("index.txt"))); //$NON-NLS-1$ //$NON-NLS-2$
		return 0;
	}

	private static void validateApprovedCandidate(MiningCandidate candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Candidate JSON is empty"); //$NON-NLS-1$
		}
		if (candidate.getStatus() != CandidateStatus.APPROVED) {
			throw new IllegalStateException("Only APPROVED candidates can generate promotion changes"); //$NON-NLS-1$
		}
		CandidateVerification previousVerification = candidate.getVerification();
		if (previousVerification == null || !previousVerification.successful()) {
			throw new IllegalStateException("Approved candidate has no successful persisted verification"); //$NON-NLS-1$
		}
	}

	private static String validateTargetFileName(String targetHintFile) {
		if (targetHintFile == null || targetHintFile.isBlank()) {
			throw new IllegalArgumentException("targetHintFile is required"); //$NON-NLS-1$
		}
		String target = targetHintFile.trim();
		if (!target.endsWith(".sandbox-hint") //$NON-NLS-1$
				|| target.indexOf('/') >= 0 || target.indexOf('\\') >= 0
				|| ".".equals(target) || "..".equals(target)) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException("targetHintFile must be a simple .sandbox-hint filename"); //$NON-NLS-1$
		}
		return target;
	}

	private static void appendRule(MiningCandidate candidate, Path hintFile) throws IOException {
		String existing = Files.readString(hintFile, StandardCharsets.UTF_8);
		String rule = candidate.getDslRule().strip();
		if (existing.contains(rule)) {
			throw new IllegalStateException("The exact DSL rule already exists in " + hintFile); //$NON-NLS-1$
		}
		StringBuilder addition = new StringBuilder();
		if (!existing.endsWith(System.lineSeparator())) {
			addition.append(System.lineSeparator());
		}
		addition.append(System.lineSeparator())
				.append("// Promoted mining candidate: ").append(candidate.getCandidateId()) //$NON-NLS-1$
				.append(System.lineSeparator())
				.append("// Source: ").append(commitUrl(candidate)) //$NON-NLS-1$
				.append(System.lineSeparator())
				.append(rule)
				.append(System.lineSeparator());
		Files.writeString(hintFile, existing + addition, StandardCharsets.UTF_8);
	}

	private static void updateIndex(Path indexFile, String fixtureName) throws IOException {
		List<String> entries = Files.isRegularFile(indexFile)
				? new ArrayList<>(Files.readAllLines(indexFile, StandardCharsets.UTF_8))
				: new ArrayList<>();
		entries.removeIf(String::isBlank);
		if (!entries.contains(fixtureName)) {
			entries.add(fixtureName);
		}
		entries.sort(String::compareTo);
		Files.write(indexFile, entries, StandardCharsets.UTF_8);
	}

	private static String commitUrl(MiningCandidate candidate) {
		String repo = candidate.getSourceRepo();
		if (repo != null && repo.endsWith(".git")) { //$NON-NLS-1$
			repo = repo.substring(0, repo.length() - 4);
		}
		return repo + "/commit/" + candidate.getSourceCommit(); //$NON-NLS-1$
	}

	private static String requireValue(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException(option + " requires a value"); //$NON-NLS-1$
		}
		return args[index];
	}

	/** Permanent data-driven behavior test fixture generated for promotion. */
	public record PromotionFixture(
			String candidateId,
			int revision,
			String sourceRepo,
			String sourceCommit,
			String targetHintFile,
			String sourceVersion,
			String dslRule,
			String beforeExample,
			String afterExample,
			String negativeExample,
			String ruleFingerprint,
			String behaviorFingerprint,
			String verifierVersion,
			String approvedBy) {

		static PromotionFixture from(MiningCandidate candidate,
				CandidateVerification verification, String actor) {
			return new PromotionFixture(
					candidate.getCandidateId(),
					candidate.getRevision(),
					candidate.getSourceRepo(),
					candidate.getSourceCommit(),
					candidate.getTargetHintFile(),
					candidate.getSourceVersion(),
					candidate.getDslRule(),
					candidate.getBeforeExample(),
					candidate.getAfterExample(),
					candidate.getNegativeExample(),
					candidate.getRuleFingerprint(),
					candidate.getBehaviorFingerprint(),
					verification.verifierVersion(),
					actor);
		}
	}
}
