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
package org.sandbox.mining.core.budget;

/**
 * Tracks request and commit budgets for LLM mining runs.
 * <p>
 * A value of {@code 0} for a maximum means unlimited. The class intentionally
 * does not depend on a concrete LLM client so it can be tested and reused by
 * the CLI, scheduled jobs, and dry-run tooling.
 * </p>
 */
public final class MiningBudget {

	private final int maxRequests;

	private final int maxCommits;

	private int requestsUsed;

	private int commitsSent;

	/**
	 * Create a new budget.
	 *
	 * @param maxRequests maximum number of LLM requests; {@code 0} means unlimited
	 * @param maxCommits maximum number of commits sent to the LLM; {@code 0} means unlimited
	 */
	public MiningBudget(int maxRequests, int maxCommits) {
		if (maxRequests < 0) {
			throw new IllegalArgumentException("maxRequests must be >= 0"); //$NON-NLS-1$
		}
		if (maxCommits < 0) {
			throw new IllegalArgumentException("maxCommits must be >= 0"); //$NON-NLS-1$
		}
		this.maxRequests = maxRequests;
		this.maxCommits = maxCommits;
	}

	/**
	 * Build a budget from a profile plus optional explicit overrides.
	 * <p>
	 * Pass {@code -1} for either override to use the profile default.
	 * Pass {@code 0} to explicitly request an unlimited budget for that dimension.
	 * Any value lower than {@code -1} is rejected as invalid.
	 * </p>
	 *
	 * @param profile the budget profile
	 * @param explicitMaxRequests explicit request limit, {@code 0} for unlimited, or {@code -1} for profile default
	 * @param explicitMaxCommits explicit commit limit, {@code 0} for unlimited, or {@code -1} for profile default
	 * @return budget instance
	 * @throws IllegalArgumentException if either explicit value is less than {@code -1}
	 */
	public static MiningBudget from(BudgetProfile profile, int explicitMaxRequests,
			int explicitMaxCommits) {
		if (explicitMaxRequests < -1) {
			throw new IllegalArgumentException("explicitMaxRequests must be >= -1"); //$NON-NLS-1$
		}
		if (explicitMaxCommits < -1) {
			throw new IllegalArgumentException("explicitMaxCommits must be >= -1"); //$NON-NLS-1$
		}
		BudgetProfile effectiveProfile = profile == null ? BudgetProfile.BALANCED : profile;
		int requestLimit = explicitMaxRequests >= 0
				? explicitMaxRequests
				: effectiveProfile.defaultMaxRequests();
		int commitLimit = explicitMaxCommits >= 0
				? explicitMaxCommits
				: effectiveProfile.defaultMaxCommits();
		return new MiningBudget(requestLimit, commitLimit);
	}

	/**
	 * @return whether another LLM request may be started
	 */
	public boolean hasRequestCapacity() {
		return maxRequests == 0 || requestsUsed < maxRequests;
	}

	/**
	 * Returns how many commits may be included in the next LLM request.
	 *
	 * @param proposedCommitCount requested number of commits
	 * @return allowed number of commits, possibly {@code 0}
	 */
	public int allowedCommitsForNextRequest(int proposedCommitCount) {
		if (proposedCommitCount <= 0 || !hasRequestCapacity()) {
			return 0;
		}
		if (maxCommits == 0) {
			return proposedCommitCount;
		}
		int remaining = maxCommits - commitsSent;
		return Math.max(0, Math.min(proposedCommitCount, remaining));
	}

	/**
	 * Records an LLM request after the caller has decided to send one.
	 *
	 * @param commitCount number of commits included in the request
	 */
	public void recordRequest(int commitCount) {
		if (commitCount < 0) {
			throw new IllegalArgumentException("commitCount must be >= 0"); //$NON-NLS-1$
		}
		requestsUsed++;
		commitsSent += commitCount;
	}

	/**
	 * @return whether either configured limit has been exhausted
	 */
	public boolean isExhausted() {
		boolean requestsExhausted = maxRequests > 0 && requestsUsed >= maxRequests;
		boolean commitsExhausted = maxCommits > 0 && commitsSent >= maxCommits;
		return requestsExhausted || commitsExhausted;
	}

	public int getMaxRequests() {
		return maxRequests;
	}

	public int getMaxCommits() {
		return maxCommits;
	}

	public int getRequestsUsed() {
		return requestsUsed;
	}

	public int getCommitsSent() {
		return commitsSent;
	}

	/**
	 * @return human-readable budget summary for logs and reports
	 */
	public String formatSummary() {
		return "LLM budget: requests " + requestsUsed + '/' //$NON-NLS-1$
				+ (maxRequests == 0 ? "unlimited" : Integer.toString(maxRequests)) //$NON-NLS-1$
				+ ", commits " + commitsSent + '/' //$NON-NLS-1$
				+ (maxCommits == 0 ? "unlimited" : Integer.toString(maxCommits)); //$NON-NLS-1$
	}
}
