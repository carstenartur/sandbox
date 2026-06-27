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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A mined cleanup candidate in the staged DSL mining pipeline.
 *
 * <p>Candidates are discovered by the LLM, validated, tested, and eventually
 * promoted into productive bundled {@code .sandbox-hint} files. They are
 * stored in the {@code mining-candidates/} directory as JSON files and
 * progress through the {@link CandidateStatus} lifecycle.</p>
 *
 * <p>Example JSON representation:</p>
 * <pre>
 * {
 *   "dslRule": "...",
 *   "beforeExample": "...",
 *   "afterExample": "...",
 *   "negativeExample": "...",
 *   "targetHintFile": "performance.sandbox-hint",
 *   "sourceCommit": "abc1234",
 *   "sourceRepo": "https://github.com/eclipse-jdt/eclipse.jdt.ui",
 *   "category": "string-modernization",
 *   "summary": "Replace StringBuffer with StringBuilder",
 *   "status": "DISCOVERED",
 *   "discoveredAt": "2026-01-01T00:00:00Z",
 *   "rejectionReason": null
 * }
 * </pre>
 */
public class MiningCandidate {

	/** The DSL rule text (raw {@code .sandbox-hint} content). */
	private String dslRule;

	/** Java code example that should be matched by the rule (before transformation). */
	private String beforeExample;

	/** Expected Java code after the rule is applied. */
	private String afterExample;

	/** Java code example that should NOT match the rule. */
	private String negativeExample;

	/** Target {@code .sandbox-hint} filename (e.g. {@code performance.sandbox-hint}). */
	private String targetHintFile;

	/** The Git commit hash that this candidate was discovered from. */
	private String sourceCommit;

	/** The repository URL this commit belongs to. */
	private String sourceRepo;

	/** The category of the transformation (e.g. {@code string-modernization}). */
	private String category;

	/** Human-readable summary of the candidate. */
	private String summary;

	/** Current lifecycle status of this candidate. */
	private CandidateStatus status;

	/** ISO-8601 timestamp when the candidate was first discovered. */
	private String discoveredAt;

	/** Reason this candidate was rejected (only set when status is {@link CandidateStatus#REJECTED}). */
	private String rejectionReason;

	/** No-arg constructor for Gson. */
	public MiningCandidate() {
		this.status = CandidateStatus.DISCOVERED;
	}

	/**
	 * Creates a new mining candidate with all required fields.
	 *
	 * @param dslRule         the DSL rule text
	 * @param beforeExample   the before-transformation Java code example
	 * @param afterExample    the after-transformation Java code example
	 * @param negativeExample the Java code that should NOT match
	 * @param targetHintFile  the target hint file name
	 * @param sourceCommit    the source commit hash
	 * @param sourceRepo      the source repository URL
	 * @param category        the transformation category
	 * @param summary         a human-readable summary
	 * @param discoveredAt    ISO-8601 discovery timestamp
	 */
	public MiningCandidate(String dslRule, String beforeExample, String afterExample,
			String negativeExample, String targetHintFile, String sourceCommit,
			String sourceRepo, String category, String summary, String discoveredAt) {
		this.dslRule = dslRule;
		this.beforeExample = beforeExample;
		this.afterExample = afterExample;
		this.negativeExample = negativeExample;
		this.targetHintFile = targetHintFile;
		this.sourceCommit = sourceCommit;
		this.sourceRepo = sourceRepo;
		this.category = category;
		this.summary = summary;
		this.discoveredAt = discoveredAt;
		this.status = CandidateStatus.DISCOVERED;
	}

	public String getDslRule() { return dslRule; }
	public void setDslRule(String dslRule) { this.dslRule = dslRule; }

	public String getBeforeExample() { return beforeExample; }
	public void setBeforeExample(String beforeExample) { this.beforeExample = beforeExample; }

	public String getAfterExample() { return afterExample; }
	public void setAfterExample(String afterExample) { this.afterExample = afterExample; }

	public String getNegativeExample() { return negativeExample; }
	public void setNegativeExample(String negativeExample) { this.negativeExample = negativeExample; }

	public String getTargetHintFile() { return targetHintFile; }
	public void setTargetHintFile(String targetHintFile) { this.targetHintFile = targetHintFile; }

	public String getSourceCommit() { return sourceCommit; }
	public void setSourceCommit(String sourceCommit) { this.sourceCommit = sourceCommit; }

	public String getSourceRepo() { return sourceRepo; }
	public void setSourceRepo(String sourceRepo) { this.sourceRepo = sourceRepo; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getSummary() { return summary; }
	public void setSummary(String summary) { this.summary = summary; }

	public CandidateStatus getStatus() { return status; }
	public void setStatus(CandidateStatus status) { this.status = status; }

	public String getDiscoveredAt() { return discoveredAt; }
	public void setDiscoveredAt(String discoveredAt) { this.discoveredAt = discoveredAt; }

	public String getRejectionReason() { return rejectionReason; }
	public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

	/**
	 * Returns a deterministic candidate ID based on candidate-defining content.
	 *
	 * @return a stable SHA-256 based candidate ID
	 */
	public String getCandidateId() {
		StringBuilder seed = new StringBuilder();
		seed.append(nullToEmpty(sourceRepo)).append('\n')
				.append(nullToEmpty(sourceCommit)).append('\n')
				.append(nullToEmpty(category)).append('\n')
				.append(nullToEmpty(targetHintFile)).append('\n')
				.append(nullToEmpty(dslRule));
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			byte[] hash = digest.digest(seed.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", Byte.valueOf(b))); //$NON-NLS-1$
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e); //$NON-NLS-1$
		}
	}

	/**
	 * Returns a stable filename for this candidate based on {@link #getCandidateId()}.
	 *
	 * @return a filename like {@code <candidateId>-candidate.json}
	 */
	public String toFileName() {
		return getCandidateId() + "-candidate.json"; //$NON-NLS-1$
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "MiningCandidate{" //$NON-NLS-1$
				+ "sourceCommit='" + sourceCommit + '\'' //$NON-NLS-1$
				+ ", status=" + status //$NON-NLS-1$
				+ ", category='" + category + '\'' //$NON-NLS-1$
				+ ", summary='" + summary + '\'' //$NON-NLS-1$
				+ '}';
	}
}
