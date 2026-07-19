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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative staged representation of one mined cleanup proposal.
 *
 * <p>The candidate ID identifies the discovery origin and stays stable while a
 * proposal is corrected. Mutable rule and example content has separate
 * fingerprints, and corrected content increments {@link #revision} when saved
 * by {@link CandidateStore}.</p>
 */
public class MiningCandidate {

	private int schemaVersion = 2;
	private String candidateId;
	private int revision = 1;
	private int candidateOrdinal;

	private String dslRule;
	private String beforeExample;
	private String afterExample;
	private String negativeExample;
	private String targetHintFile;
	private String sourceCommit;
	private String sourceRepo;
	private String category;
	private String summary;
	private String sourceVersion = "21"; //$NON-NLS-1$
	private CandidateStatus status;
	private String discoveredAt;
	private String rejectionReason;

	private String ruleFingerprint;
	private String behaviorFingerprint;
	private CandidateVerification verification;
	private List<CandidateTransition> transitions = new ArrayList<>();

	/** No-arg constructor for Gson. */
	public MiningCandidate() {
		this.status = CandidateStatus.DISCOVERED;
	}

	/**
	 * Creates a candidate with the fields currently supplied by commit discovery.
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

	public int getSchemaVersion() {
		return schemaVersion;
	}

	public void setSchemaVersion(int schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	/**
	 * Stable ID derived from repository, source commit, discovery category,
	 * target rule library, and candidate ordinal. Rule text and examples are
	 * deliberately excluded so corrections remain revisions of one candidate.
	 */
	public String getCandidateId() {
		if (candidateId == null || candidateId.isBlank()) {
			candidateId = sha256(nullToEmpty(sourceRepo) + '\n'
					+ nullToEmpty(sourceCommit) + '\n'
					+ nullToEmpty(category) + '\n'
					+ nullToEmpty(targetHintFile) + '\n'
					+ candidateOrdinal);
		}
		return candidateId;
	}

	public void setCandidateId(String candidateId) {
		this.candidateId = candidateId;
	}

	public int getRevision() {
		return revision;
	}

	public void setRevision(int revision) {
		this.revision = Math.max(1, revision);
	}

	public int getCandidateOrdinal() {
		return candidateOrdinal;
	}

	public void setCandidateOrdinal(int candidateOrdinal) {
		if (candidateOrdinal < 0) {
			throw new IllegalArgumentException("candidateOrdinal must be >= 0"); //$NON-NLS-1$
		}
		this.candidateOrdinal = candidateOrdinal;
		this.candidateId = null;
	}

	public String getDslRule() {
		return dslRule;
	}

	public void setDslRule(String dslRule) {
		this.dslRule = dslRule;
		this.ruleFingerprint = null;
	}

	public String getBeforeExample() {
		return beforeExample;
	}

	public void setBeforeExample(String beforeExample) {
		this.beforeExample = beforeExample;
		this.behaviorFingerprint = null;
	}

	public String getAfterExample() {
		return afterExample;
	}

	public void setAfterExample(String afterExample) {
		this.afterExample = afterExample;
		this.behaviorFingerprint = null;
	}

	public String getNegativeExample() {
		return negativeExample;
	}

	public void setNegativeExample(String negativeExample) {
		this.negativeExample = negativeExample;
		this.behaviorFingerprint = null;
	}

	public String getTargetHintFile() {
		return targetHintFile;
	}

	public void setTargetHintFile(String targetHintFile) {
		this.targetHintFile = targetHintFile;
		this.candidateId = null;
	}

	public String getSourceCommit() {
		return sourceCommit;
	}

	public void setSourceCommit(String sourceCommit) {
		this.sourceCommit = sourceCommit;
		this.candidateId = null;
	}

	public String getSourceRepo() {
		return sourceRepo;
	}

	public void setSourceRepo(String sourceRepo) {
		this.sourceRepo = sourceRepo;
		this.candidateId = null;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
		this.candidateId = null;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getSourceVersion() {
		return sourceVersion == null || sourceVersion.isBlank() ? "21" : sourceVersion; //$NON-NLS-1$
	}

	public void setSourceVersion(String sourceVersion) {
		this.sourceVersion = sourceVersion;
	}

	public CandidateStatus getStatus() {
		return status == null ? CandidateStatus.DISCOVERED : status;
	}

	/**
	 * Compatibility setter used by Gson and the existing discovery code.
	 * New operational code should use {@link #transitionTo(CandidateStatus, String, String)}.
	 */
	public void setStatus(CandidateStatus status) {
		this.status = status;
	}

	public String getDiscoveredAt() {
		return discoveredAt;
	}

	public void setDiscoveredAt(String discoveredAt) {
		this.discoveredAt = discoveredAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	public String getRuleFingerprint() {
		if (ruleFingerprint == null || ruleFingerprint.isBlank()) {
			ruleFingerprint = "sha256:" + sha256(normalizeDsl(dslRule)); //$NON-NLS-1$
		}
		return ruleFingerprint;
	}

	public void setRuleFingerprint(String ruleFingerprint) {
		this.ruleFingerprint = ruleFingerprint;
	}

	public String getBehaviorFingerprint() {
		if (behaviorFingerprint == null || behaviorFingerprint.isBlank()) {
			String behavior = normalizeSource(beforeExample) + '\n'
					+ normalizeSource(afterExample) + '\n'
					+ normalizeSource(negativeExample);
			behaviorFingerprint = "sha256:" + sha256(behavior); //$NON-NLS-1$
		}
		return behaviorFingerprint;
	}

	public void setBehaviorFingerprint(String behaviorFingerprint) {
		this.behaviorFingerprint = behaviorFingerprint;
	}

	public CandidateVerification getVerification() {
		return verification;
	}

	public void setVerification(CandidateVerification verification) {
		this.verification = verification;
	}

	public List<CandidateTransition> getTransitions() {
		if (transitions == null) {
			transitions = new ArrayList<>();
		}
		return List.copyOf(transitions);
	}

	public void setTransitions(List<CandidateTransition> transitions) {
		this.transitions = transitions == null ? new ArrayList<>() : new ArrayList<>(transitions);
	}

	/**
	 * Applies and records an allowed lifecycle transition.
	 */
	public void transitionTo(CandidateStatus target, String actor, String reason) {
		CandidateStatus current = getStatus();
		if (!current.canTransitionTo(target)) {
			throw new IllegalStateException("Invalid candidate transition: " + current + " -> " + target); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (transitions == null) {
			transitions = new ArrayList<>();
		}
		transitions.add(new CandidateTransition(current, target,
				nullToEmpty(actor), nullToEmpty(reason), Instant.now().toString()));
		status = target;
		if (target == CandidateStatus.REJECTED) {
			rejectionReason = reason;
		}
	}

	/** Returns the stable JSON file name for this candidate. */
	public String toFileName() {
		return getCandidateId() + "-candidate.json"; //$NON-NLS-1$
	}

	private static String normalizeDsl(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1); //$NON-NLS-1$ //$NON-NLS-2$
		StringBuilder normalized = new StringBuilder();
		for (String line : lines) {
			String trimmed = line.strip();
			if (!trimmed.isEmpty()) {
				if (!normalized.isEmpty()) {
					normalized.append('\n');
				}
				normalized.append(trimmed);
			}
		}
		return normalized.toString();
	}

	private static String normalizeSource(String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value; //$NON-NLS-1$
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(Character.forDigit((b >>> 4) & 0x0f, 16));
				hex.append(Character.forDigit(b & 0x0f, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e); //$NON-NLS-1$
		}
	}

	@Override
	public String toString() {
		return "MiningCandidate{" //$NON-NLS-1$
				+ "candidateId='" + getCandidateId() + '\'' //$NON-NLS-1$
				+ ", revision=" + revision //$NON-NLS-1$
				+ ", sourceCommit='" + sourceCommit + '\'' //$NON-NLS-1$
				+ ", status=" + getStatus() //$NON-NLS-1$
				+ ", summary='" + summary + '\'' //$NON-NLS-1$
				+ '}';
	}
}
