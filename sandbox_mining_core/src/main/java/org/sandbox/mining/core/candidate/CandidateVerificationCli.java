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
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Post-processes staged candidate JSON after discovery.
 *
 * <p>Usage:</p>
 * <pre>
 * java -cp sandbox-mining-core.jar \
 *   org.sandbox.mining.core.candidate.CandidateVerificationCli \
 *   --candidate-dir mining-candidates \
 *   --report-dir docs/mining-report
 * </pre>
 */
public final class CandidateVerificationCli {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private CandidateVerificationCli() {
	}

	public static void main(String[] args) {
		try {
			int exitCode = run(args);
			if (exitCode != 0) {
				System.exit(exitCode);
			}
		} catch (Exception e) {
			System.err.println("Candidate verification failed: " + e.getMessage()); //$NON-NLS-1$
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	static int run(String[] args) throws IOException {
		Path candidateDir = Path.of("mining-candidates"); //$NON-NLS-1$
		Path reportDir = Path.of("docs/mining-report"); //$NON-NLS-1$
		String defaultSourceVersion = "21"; //$NON-NLS-1$

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--candidate-dir": //$NON-NLS-1$
				candidateDir = Path.of(requireValue(args, ++i, "--candidate-dir")); //$NON-NLS-1$
				break;
			case "--report-dir": //$NON-NLS-1$
				reportDir = Path.of(requireValue(args, ++i, "--report-dir")); //$NON-NLS-1$
				break;
			case "--source-version": //$NON-NLS-1$
				defaultSourceVersion = requireValue(args, ++i, "--source-version"); //$NON-NLS-1$
				break;
			default:
				throw new IllegalArgumentException("Unknown option: " + args[i]); //$NON-NLS-1$
			}
		}

		CandidateStore store = new CandidateStore(candidateDir);
		List<MiningCandidate> candidates = new ArrayList<>(store.loadAll());
		candidates.sort(Comparator.comparing(MiningCandidate::getCandidateId));
		CandidateVerifier verifier = new CandidateVerifier();

		int verified = 0;
		int failed = 0;
		for (MiningCandidate candidate : candidates) {
			if (!candidate.hasDeclaredSourceVersion()) {
				candidate.setSourceVersion(defaultSourceVersion);
			}
			if (isTerminal(candidate.getStatus())
					|| candidate.getStatus() == CandidateStatus.READY_FOR_REVIEW) {
				store.save(candidate);
				continue;
			}

			CandidateVerification verification = verifier.verify(candidate);
			candidate.setVerification(verification);
			if (verification.successful()) {
				advanceToReadyForReview(candidate);
				verified++;
				System.out.println("Verified candidate " + candidate.getCandidateId()); //$NON-NLS-1$
			} else {
				failed++;
				System.out.println("Candidate " + candidate.getCandidateId() + " failed at " //$NON-NLS-1$ //$NON-NLS-2$
						+ verification.stage() + ": " + verification.message()); //$NON-NLS-1$
			}
			store.save(candidate);
		}

		List<MiningCandidate> updated = store.loadAll().stream()
				.sorted(Comparator.comparing(MiningCandidate::getCandidateId))
				.toList();
		writeReport(updated, candidateDir, reportDir);
		long ready = updated.stream()
				.filter(candidate -> candidate.getStatus() == CandidateStatus.READY_FOR_REVIEW)
				.count();
		System.out.println("Candidate verification summary: " + verified + " verified, " //$NON-NLS-1$ //$NON-NLS-2$
				+ failed + " failed, " + ready + " ready for review"); //$NON-NLS-1$ //$NON-NLS-2$
		return 0;
	}

	private static void advanceToReadyForReview(MiningCandidate candidate) {
		if (candidate.getStatus() == CandidateStatus.DISCOVERED) {
			candidate.transitionTo(CandidateStatus.DSL_VALID,
					"CandidateVerifier", "DSL parser and validator passed"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (candidate.getStatus() == CandidateStatus.DSL_VALID) {
			candidate.transitionTo(CandidateStatus.BEHAVIOR_VALID,
					"CandidateVerifier", "Positive and negative behavior examples passed"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (candidate.getStatus() == CandidateStatus.BEHAVIOR_VALID) {
			candidate.transitionTo(CandidateStatus.READY_FOR_REVIEW,
					"CandidateVerificationCli", "All deterministic candidate gates passed"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static boolean isTerminal(CandidateStatus status) {
		return status == CandidateStatus.PROMOTED
				|| status == CandidateStatus.REJECTED
				|| status == CandidateStatus.SUPERSEDED;
	}

	private static void writeReport(List<MiningCandidate> candidates, Path candidateDir,
			Path reportDir) throws IOException {
		Files.createDirectories(reportDir);
		Files.writeString(reportDir.resolve("candidates.json"), //$NON-NLS-1$
				GSON.toJson(candidates) + System.lineSeparator(), StandardCharsets.UTF_8);

		Path reportCandidates = reportDir.resolve("candidates"); //$NON-NLS-1$
		Files.createDirectories(reportCandidates);
		for (MiningCandidate candidate : candidates) {
			Path source = candidateDir.resolve(candidate.toFileName());
			if (Files.isRegularFile(source)) {
				Files.copy(source, reportCandidates.resolve(candidate.toFileName()),
						StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.writeString(reportDir.resolve("candidates.html"), buildHtml(candidates), //$NON-NLS-1$
				StandardCharsets.UTF_8);
	}

	private static String buildHtml(List<MiningCandidate> candidates) {
		StringBuilder html = new StringBuilder();
		html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">") //$NON-NLS-1$
				.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">") //$NON-NLS-1$
				.append("<title>Mining candidates</title>") //$NON-NLS-1$
				.append("<style>body{font-family:system-ui,sans-serif;max-width:1200px;margin:2rem auto;padding:0 1rem}") //$NON-NLS-1$
				.append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:.55rem;text-align:left;vertical-align:top}") //$NON-NLS-1$
				.append("th{background:#f4f4f4}code{overflow-wrap:anywhere}.ok{font-weight:600}.fail{font-weight:600}</style></head><body>") //$NON-NLS-1$
				.append("<h1>Staged mining candidates</h1>") //$NON-NLS-1$
				.append("<p>Only candidates marked <strong>READY_FOR_REVIEW</strong> passed deterministic DSL and behavior verification.</p>") //$NON-NLS-1$
				.append("<table><thead><tr><th>Status</th><th>Candidate</th><th>Proposal</th><th>Source</th><th>Verification</th></tr></thead><tbody>"); //$NON-NLS-1$
		for (MiningCandidate candidate : candidates) {
			CandidateVerification verification = candidate.getVerification();
			html.append("<tr><td>").append(escape(candidate.getStatus().name())).append("</td><td><a href=\"candidates/") //$NON-NLS-1$ //$NON-NLS-2$
					.append(escape(candidate.toFileName())).append("\"><code>") //$NON-NLS-1$
					.append(escape(candidate.getCandidateId())).append("</code></a><br>revision ") //$NON-NLS-1$
					.append(candidate.getRevision()).append("</td><td>") //$NON-NLS-1$
					.append(escape(candidate.getSummary())).append("<br><code>") //$NON-NLS-1$
					.append(escape(candidate.getTargetHintFile())).append("</code></td><td><a href=\"") //$NON-NLS-1$
					.append(escape(commitUrl(candidate))).append("\"><code>") //$NON-NLS-1$
					.append(escape(shortCommit(candidate.getSourceCommit()))).append("</code></a></td><td>"); //$NON-NLS-1$
			if (verification == null) {
				html.append("not run"); //$NON-NLS-1$
			} else {
				html.append("<span class=\"") //$NON-NLS-1$
						.append(verification.successful() ? "ok" : "fail") //$NON-NLS-1$ //$NON-NLS-2$
						.append("\">").append(escape(verification.stage().name())).append("</span><br>") //$NON-NLS-1$ //$NON-NLS-2$
						.append(escape(verification.message()));
			}
			html.append("</td></tr>"); //$NON-NLS-1$
		}
		return html.append("</tbody></table></body></html>\n").toString(); //$NON-NLS-1$
	}

	private static String commitUrl(MiningCandidate candidate) {
		String repo = candidate.getSourceRepo();
		if (repo == null) {
			return "#"; //$NON-NLS-1$
		}
		if (repo.endsWith(".git")) { //$NON-NLS-1$
			repo = repo.substring(0, repo.length() - 4);
		}
		return repo + "/commit/" + candidate.getSourceCommit(); //$NON-NLS-1$
	}

	private static String shortCommit(String commit) {
		return commit == null ? "unknown" : commit.substring(0, Math.min(12, commit.length())); //$NON-NLS-1$
	}

	private static String escape(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		return value.replace("&", "&amp;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace(">", "&gt;") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("\"", "&quot;"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String requireValue(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException(option + " requires a value"); //$NON-NLS-1$
		}
		return args[index];
	}
}
