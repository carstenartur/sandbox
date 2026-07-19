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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests minimal promotion change generation. */
class CandidatePromotionCliTest {

	private static final String HINT_DIRECTORY =
			"sandbox_common_core/src/main/resources/org/sandbox/jdt/triggerpattern/internal"; //$NON-NLS-1$
	private static final String FIXTURE_DIRECTORY =
			"sandbox_common_core/src/test/resources/org/sandbox/jdt/triggerpattern/promoted"; //$NON-NLS-1$

	@TempDir
	Path tempDir;

	@Test
	void writesRuleFixtureAndIndexForApprovedCandidate() throws IOException {
		Path hintFile = createTargetHint();
		Path candidateFile = saveApprovedCandidate(candidate());

		CandidatePromotionCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--repo-root", tempDir.toString(), //$NON-NLS-1$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		});

		String hint = Files.readString(hintFile, StandardCharsets.UTF_8);
		assertTrue(hint.contains("Promoted mining candidate")); //$NON-NLS-1$
		assertTrue(hint.contains("$x + 0\n=> $x\n;;")); //$NON-NLS-1$
		Path fixtureDirectory = tempDir.resolve(FIXTURE_DIRECTORY);
		assertTrue(Files.isRegularFile(fixtureDirectory.resolve(
				candidate().getCandidateId() + ".json"))); //$NON-NLS-1$
		assertTrue(Files.readString(fixtureDirectory.resolve("index.txt"), StandardCharsets.UTF_8) //$NON-NLS-1$
				.contains(candidate().getCandidateId()));
	}

	@Test
	void refusesToPromoteUnapprovedCandidate() throws IOException {
		createTargetHint();
		MiningCandidate candidate = candidate();
		candidate.setVerification(new CandidateVerifier().verify(candidate));
		CandidateStore store = new CandidateStore(tempDir.resolve("candidates")); //$NON-NLS-1$
		store.save(candidate);
		Path candidateFile = store.getStoreDir().resolve(candidate.toFileName());

		assertThrows(IllegalStateException.class, () -> CandidatePromotionCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--repo-root", tempDir.toString(), //$NON-NLS-1$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		}));
	}

	@Test
	void refusesPathTraversalInTargetHintFile() throws IOException {
		createTargetHint();
		MiningCandidate candidate = candidate();
		candidate.setTargetHintFile("../outside.sandbox-hint"); //$NON-NLS-1$
		Path candidateFile = saveApprovedCandidate(candidate);

		assertThrows(IllegalArgumentException.class, () -> CandidatePromotionCli.run(new String[] {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--repo-root", tempDir.toString(), //$NON-NLS-1$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		}));
	}

	@Test
	void refusesPromotionIntoDisabledMaintenanceLibrary() throws IOException {
		createHint("jdt-api-modernization.sandbox-hint", "jdt-api-modernization"); //$NON-NLS-1$ //$NON-NLS-2$
		MiningCandidate candidate = candidate();
		candidate.setTargetHintFile("jdt-api-modernization.sandbox-hint"); //$NON-NLS-1$
		Path candidateFile = saveApprovedCandidate(candidate);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> CandidatePromotionCli.run(new String[] {
						"--candidate", candidateFile.toString(), //$NON-NLS-1$
						"--repo-root", tempDir.toString(), //$NON-NLS-1$
						"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
				}));
		assertTrue(exception.getMessage().contains("active cleanup/quick-assist bundle")); //$NON-NLS-1$
	}

	@Test
	void refusesDuplicateRule() throws IOException {
		createTargetHint();
		Path candidateFile = saveApprovedCandidate(candidate());
		String[] args = {
				"--candidate", candidateFile.toString(), //$NON-NLS-1$
				"--repo-root", tempDir.toString(), //$NON-NLS-1$
				"--actor", "reviewer" //$NON-NLS-1$ //$NON-NLS-2$
		};
		CandidatePromotionCli.run(args);

		assertThrows(IllegalStateException.class, () -> CandidatePromotionCli.run(args));
	}

	private Path createTargetHint() throws IOException {
		return createHint("performance.sandbox-hint", "performance"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Path createHint(String fileName, String id) throws IOException {
		Path hintDirectory = tempDir.resolve(HINT_DIRECTORY);
		Files.createDirectories(hintDirectory);
		Path hintFile = hintDirectory.resolve(fileName);
		Files.writeString(hintFile, "<!id: " + id + ">\n", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		return hintFile;
	}

	private Path saveApprovedCandidate(MiningCandidate candidate) throws IOException {
		CandidateVerification verification = new CandidateVerifier().verify(candidate);
		assertTrue(verification.successful(), verification.message());
		candidate.setVerification(verification);
		candidate.transitionTo(CandidateStatus.DSL_VALID, "validator", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.BEHAVIOR_VALID, "verifier", "valid"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.READY_FOR_REVIEW, "pipeline", "ready"); //$NON-NLS-1$ //$NON-NLS-2$
		candidate.transitionTo(CandidateStatus.APPROVED, "reviewer", "approved"); //$NON-NLS-1$ //$NON-NLS-2$
		CandidateStore store = new CandidateStore(tempDir.resolve("candidates")); //$NON-NLS-1$
		store.save(candidate);
		return store.getStoreDir().resolve(candidate.toFileName());
	}

	private static MiningCandidate candidate() {
		return new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				"abc123", //$NON-NLS-1$
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-07-19T00:00:00Z"); //$NON-NLS-1$
	}
}
