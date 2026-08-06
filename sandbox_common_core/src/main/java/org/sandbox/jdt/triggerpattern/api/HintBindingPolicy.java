/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code <!binding-policy: optional|required>} safety contract from
 * an ordinary hint program without otherwise interpreting the DSL.
 *
 * <p>{@link #OPTIONAL} preserves the historical behaviour of ordinary hints:
 * a binding-aware guard may degrade when semantic bindings are unavailable.
 * {@link #REQUIRED} is the fail-closed mode for ordinary migration programs
 * whose correctness depends on overload, owner, type or hierarchy information.
 * Plan-aware programs do not need this second declaration because
 * {@code <!requires-plan: ...>} already implies required semantic bindings.</p>
 *
 * <p>Duplicate identical declarations are tolerated. Blank, malformed or
 * conflicting declarations are rejected. Commented declarations are ignored.</p>
 *
 * @since 1.3.3
 */
public enum HintBindingPolicy {
	OPTIONAL,
	REQUIRED;

	private static final String PREFIX= "<!binding-policy"; //$NON-NLS-1$
	private static final Pattern DIRECTIVE= Pattern.compile(
			"^\\s*<!binding-policy\\s*:\\s*([^>\\r\\n]*)>\\s*$"); //$NON-NLS-1$

	/**
	 * Reads an explicitly declared binding policy.
	 *
	 * @param content complete hint program text
	 * @return the declared policy, or empty when the program uses the compatibility default
	 * @throws IllegalArgumentException for blank, malformed, unknown or conflicting declarations
	 */
	public static Optional<HintBindingPolicy> fromContent(String content) {
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}
		HintBindingPolicy policy= null;
		boolean inBlockComment= false;
		for (String line : content.split("\\R", -1)) { //$NON-NLS-1$
			VisibleLine visibleLine= visibleLine(line, inBlockComment);
			inBlockComment= visibleLine.inBlockComment();
			String visible= visibleLine.text().trim();
			if (!visible.startsWith(PREFIX)) {
				continue;
			}
			Matcher matcher= DIRECTIVE.matcher(visible);
			if (!matcher.matches()) {
				throw new IllegalArgumentException("Malformed binding-policy declaration: " + visible); //$NON-NLS-1$
			}
			String candidateText= matcher.group(1).trim();
			if (candidateText.isEmpty()) {
				throw new IllegalArgumentException("binding-policy must be optional or required"); //$NON-NLS-1$
			}
			HintBindingPolicy candidate;
			try {
				candidate= HintBindingPolicy.valueOf(candidateText.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Unknown binding-policy " + candidateText + "; expected optional or required", e); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (policy != null && policy != candidate) {
				throw new IllegalArgumentException(
						"Conflicting binding-policy declarations: " + policy.name().toLowerCase(java.util.Locale.ROOT) //$NON-NLS-1$
								+ " and " + candidate.name().toLowerCase(java.util.Locale.ROOT)); //$NON-NLS-1$
			}
			policy= candidate;
		}
		return Optional.ofNullable(policy);
	}

	private static VisibleLine visibleLine(String line, boolean initiallyInBlockComment) {
		StringBuilder visible= new StringBuilder();
		boolean inBlockComment= initiallyInBlockComment;
		for (int index= 0; index < line.length();) {
			if (inBlockComment) {
				int end= line.indexOf("*/", index); //$NON-NLS-1$
				if (end < 0) {
					return new VisibleLine(visible.toString(), true);
				}
				inBlockComment= false;
				index= end + 2;
				continue;
			}
			if (line.startsWith("//", index)) { //$NON-NLS-1$
				break;
			}
			if (line.startsWith("/*", index)) { //$NON-NLS-1$
				inBlockComment= true;
				index+= 2;
				continue;
			}
			visible.append(line.charAt(index++));
		}
		return new VisibleLine(visible.toString(), inBlockComment);
	}

	private record VisibleLine(String text, boolean inBlockComment) {
	}
}
