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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Preset budget profiles for LLM-backed mining runs.
 * <p>
 * The values are deliberately conservative for free-tier operation. They are
 * intended as defaults; callers may still override the resulting request and
 * commit limits explicitly.
 * </p>
 */
public enum BudgetProfile {

	/**
	 * Cheapest profile: one commit per request and low daily defaults.
	 */
	FREE(1, 50, 100),

	/**
	 * Default profile: keeps the current batching behaviour but still offers
	 * explicit accounting hooks.
	 */
	BALANCED(4, 200, 800),

	/**
	 * Full context profile: unlimited by default unless explicit limits are set.
	 */
	THOROUGH(4, 0, 0);

	private final int recommendedCommitsPerRequest;

	private final int defaultMaxRequests;

	private final int defaultMaxCommits;

	BudgetProfile(int recommendedCommitsPerRequest, int defaultMaxRequests,
			int defaultMaxCommits) {
		this.recommendedCommitsPerRequest = recommendedCommitsPerRequest;
		this.defaultMaxRequests = defaultMaxRequests;
		this.defaultMaxCommits = defaultMaxCommits;
	}

	/**
	 * @return recommended commits per LLM request for this profile
	 */
	public int recommendedCommitsPerRequest() {
		return recommendedCommitsPerRequest;
	}

	/**
	 * @return default maximum LLM requests; {@code 0} means unlimited
	 */
	public int defaultMaxRequests() {
		return defaultMaxRequests;
	}

	/**
	 * @return default maximum commits sent to the LLM; {@code 0} means unlimited
	 */
	public int defaultMaxCommits() {
		return defaultMaxCommits;
	}

	/**
	 * Parse a profile name from CLI/configuration text.
	 * <p>
	 * Matching is case-insensitive; hyphens are treated as underscores so that
	 * {@code "free"} and {@code "FREE"} are both valid.
	 * </p>
	 *
	 * @param value the profile name
	 * @return the parsed profile
	 * @throws IllegalArgumentException if the profile is unknown, with a message
	 *                                  that lists the supported profile names
	 */
	public static BudgetProfile parse(String value) {
		if (value == null || value.isBlank()) {
			return BALANCED;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		try {
			return BudgetProfile.valueOf(normalized);
		} catch (IllegalArgumentException e) {
			String supported = Arrays.stream(BudgetProfile.values())
					.map(Enum::name)
					.collect(Collectors.joining(", ")); //$NON-NLS-1$
			throw new IllegalArgumentException(
					"Unknown budget profile: '" + value + "'. Supported profiles: " + supported); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Apply this profile's batching recommendation unless the user explicitly
	 * provided a different value.
	 *
	 * @param currentValue value currently configured
	 * @param explicitlyConfigured whether the user configured the value explicitly
	 * @return effective commits-per-request value
	 */
	public int effectiveCommitsPerRequest(int currentValue,
			boolean explicitlyConfigured) {
		if (explicitlyConfigured) {
			return currentValue;
		}
		return Math.max(1, recommendedCommitsPerRequest);
	}
}
