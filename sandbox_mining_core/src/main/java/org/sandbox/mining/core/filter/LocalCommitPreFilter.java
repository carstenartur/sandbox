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
package org.sandbox.mining.core.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cheap local pre-filter for commits before sending a diff to an LLM.
 * <p>
 * The filter is intentionally conservative: it skips obvious documentation,
 * workflow, metadata, and release-only changes, but keeps Java changes because
 * those may contain reusable cleanup patterns.
 * </p>
 */
public class LocalCommitPreFilter {

	private static final Set<String> DOCUMENTATION_EXTENSIONS = Set.of(
			".md", ".adoc", ".txt", ".png", ".jpg", ".jpeg", ".gif", ".svg"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

	/**
	 * Local pre-filter decision.
	 *
	 * @param process whether the commit should still be sent to the LLM
	 * @param reason  human-readable reason for logging/reporting
	 */
	public record FilterDecision(boolean process, String reason) {
	}

	/**
	 * Evaluate a commit using only its message and unified diff.
	 *
	 * @param commitMessage commit message
	 * @param unifiedDiff   unified diff text
	 * @return filter decision
	 */
	public FilterDecision evaluate(String commitMessage, String unifiedDiff) {
		List<String> paths = extractChangedPaths(unifiedDiff);
		if (paths.isEmpty()) {
			return new FilterDecision(true, "no path information"); //$NON-NLS-1$
		}
		boolean hasJava = paths.stream().anyMatch(p -> p.endsWith(".java")); //$NON-NLS-1$
		if (hasJava) {
			return new FilterDecision(true, "contains Java changes"); //$NON-NLS-1$
		}
		if (paths.stream().allMatch(LocalCommitPreFilter::isDocumentationOrMetadataPath)) {
			return new FilterDecision(false, "documentation/metadata only"); //$NON-NLS-1$
		}
		if (looksLikeReleaseOnlyChange(commitMessage)) {
			return new FilterDecision(false, "release/version-only change without Java files"); //$NON-NLS-1$
		}
		return new FilterDecision(true, "non-Java change retained for review"); //$NON-NLS-1$
	}

	/**
	 * Extract changed paths from a unified diff.
	 *
	 * @param unifiedDiff unified diff text
	 * @return changed paths from {@code +++ b/...} headers
	 */
	public static List<String> extractChangedPaths(String unifiedDiff) {
		List<String> result = new ArrayList<>();
		if (unifiedDiff == null || unifiedDiff.isBlank()) {
			return result;
		}
		String[] lines = unifiedDiff.split("\\R"); //$NON-NLS-1$
		for (String line : lines) {
			if (line.startsWith("+++ b/")) { //$NON-NLS-1$
				String path = line.substring("+++ b/".length()); //$NON-NLS-1$
				if (!"/dev/null".equals(path)) { //$NON-NLS-1$
					result.add(path);
				}
			}
		}
		return result;
	}

	private static boolean isDocumentationOrMetadataPath(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.startsWith(".github/") || lower.startsWith("docs/")) { //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}
		if (lower.equals("pom.xml") || lower.endsWith("/pom.xml")) { //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}
		if (lower.equals("readme.md") || lower.equals("license") //$NON-NLS-1$ //$NON-NLS-2$
				|| lower.equals("notice") || lower.equals("citation.cff")) { //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}
		return DOCUMENTATION_EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	private static boolean looksLikeReleaseOnlyChange(String commitMessage) {
		if (commitMessage == null) {
			return false;
		}
		String lower = commitMessage.toLowerCase(Locale.ROOT);
		return lower.contains("release version") //$NON-NLS-1$
				|| lower.contains("prepare for next development iteration") //$NON-NLS-1$
				|| lower.contains("bump version") //$NON-NLS-1$
				|| lower.contains("update dependency"); //$NON-NLS-1$
	}
}
