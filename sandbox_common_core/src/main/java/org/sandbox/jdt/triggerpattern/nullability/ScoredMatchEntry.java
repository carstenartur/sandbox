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
package org.sandbox.jdt.triggerpattern.nullability;

/**
 * A mining match entry enriched with a nullability score.
 *
 * @param repository the repository name
 * @param rule the rule description
 * @param file the source file path
 * @param line the line number (1-based)
 * @param matchedCode the matched source code text
 * @param suggestedReplacement the suggested replacement (may be {@code null})
 * @param score the computed match score
 * @since 1.2.6
 */
public record ScoredMatchEntry(
		String repository,
		String rule,
		String file,
		int line,
		String matchedCode,
		String suggestedReplacement,
		MatchScore score) {
}
