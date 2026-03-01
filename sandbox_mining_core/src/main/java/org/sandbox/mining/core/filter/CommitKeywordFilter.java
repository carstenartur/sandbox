/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Filters commit messages by refactoring-related keywords.
 *
 * <p>Keywords can be loaded from a file (one per line) or
 * provided programmatically. Matching is case-insensitive.</p>
 */
public class CommitKeywordFilter {

	private static final List<String> DEFAULT_KEYWORDS = List.of(
			"refactor", //$NON-NLS-1$
			"cleanup", //$NON-NLS-1$
			"clean up", //$NON-NLS-1$
			"modernize", //$NON-NLS-1$
			"deprecat", //$NON-NLS-1$
			"migrate", //$NON-NLS-1$
			"replace", //$NON-NLS-1$
			"simplif", //$NON-NLS-1$
			"convert", //$NON-NLS-1$
			"use.*instead", //$NON-NLS-1$
			"switch.*to", //$NON-NLS-1$
			"update.*api", //$NON-NLS-1$
			"remove.*deprecated", //$NON-NLS-1$
			"lambda", //$NON-NLS-1$
			"stream", //$NON-NLS-1$
			"foreach", //$NON-NLS-1$
			"encoding", //$NON-NLS-1$
			"charset", //$NON-NLS-1$
			"nio", //$NON-NLS-1$
			"try-with-resource", //$NON-NLS-1$
			"diamond", //$NON-NLS-1$
			"var ", //$NON-NLS-1$
			"pattern matching", //$NON-NLS-1$
			"instanceof", //$NON-NLS-1$
			"text block", //$NON-NLS-1$
			"record" //$NON-NLS-1$
	);

	private final List<String> keywords;

	/**
	 * Creates a filter with default refactoring keywords.
	 */
	public CommitKeywordFilter() {
		this.keywords = new ArrayList<>(DEFAULT_KEYWORDS);
	}

	/**
	 * Creates a filter with keywords loaded from a file.
	 * Each line is one keyword (blank lines and # comments are ignored).
	 *
	 * @param keywordFile path to the keyword file
	 * @throws IOException if the file cannot be read
	 */
	public CommitKeywordFilter(Path keywordFile) throws IOException {
		this.keywords = new ArrayList<>();
		for (String line : Files.readAllLines(keywordFile, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty() && !trimmed.startsWith("#")) { //$NON-NLS-1$
				keywords.add(trimmed.toLowerCase(Locale.ROOT));
			}
		}
	}

	/**
	 * Tests whether a commit message matches any keyword.
	 *
	 * @param commitMessage the commit message
	 * @return true if at least one keyword matches
	 */
	public boolean matches(String commitMessage) {
		if (commitMessage == null || commitMessage.isBlank()) {
			return false;
		}
		String lower = commitMessage.toLowerCase(Locale.ROOT);
		return keywords.stream().anyMatch(lower::contains);
	}

	/**
	 * Returns the list of active keywords.
	 *
	 * @return unmodifiable list of keywords
	 */
	public List<String> getKeywords() {
		return Collections.unmodifiableList(keywords);
	}
}
