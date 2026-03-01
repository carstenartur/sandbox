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
import java.util.regex.Pattern;

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
	private final List<Pattern> patterns;

	/**
	 * Creates a filter with default refactoring keywords.
	 */
	public CommitKeywordFilter() {
		this.keywords = new ArrayList<>(DEFAULT_KEYWORDS);
		this.patterns = compilePatterns(this.keywords);
	}

	/**
	 * Creates a filter with keywords loaded from a file.
	 * Each line is one keyword (blank lines and # comments are ignored).
	 * Keywords containing regex metacharacters (e.g. {@code .*}) are treated
	 * as regex patterns; plain keywords use substring matching.
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
		this.patterns = compilePatterns(this.keywords);
	}

	/**
	 * Tests whether a commit message matches any keyword.
	 * Keywords containing regex metacharacters (e.g. {@code .*}) are matched
	 * as regex patterns; plain keywords use substring matching.
	 *
	 * @param commitMessage the commit message
	 * @return true if at least one keyword matches
	 */
	public boolean matches(String commitMessage) {
		if (commitMessage == null || commitMessage.isBlank()) {
			return false;
		}
		String lower = commitMessage.toLowerCase(Locale.ROOT);
		return patterns.stream().anyMatch(p -> p.matcher(lower).find());
	}

	private static boolean isRegex(String keyword) {
		return keyword.contains(".*") || keyword.contains(".+") //$NON-NLS-1$ //$NON-NLS-2$
				|| keyword.contains("[") || keyword.contains("("); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static List<Pattern> compilePatterns(List<String> keywords) {
		List<Pattern> result = new ArrayList<>(keywords.size());
		for (String kw : keywords) {
			if (isRegex(kw)) {
				result.add(Pattern.compile(kw, Pattern.CASE_INSENSITIVE));
			} else {
				result.add(Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE));
			}
		}
		return result;
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
