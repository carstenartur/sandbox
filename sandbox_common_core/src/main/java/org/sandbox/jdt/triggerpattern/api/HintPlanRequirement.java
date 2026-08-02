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
 * Reads the fail-closed {@code <!requires-plan: ...>} contract from a hint
 * program without otherwise interpreting the DSL.
 *
 * <p>The directive deliberately remains separate from ordinary hint metadata:
 * callers that execute a trusted plan-aware program must opt into validating
 * it before handing the program to the normal hint backend. Duplicate identical
 * declarations are tolerated, while missing values, malformed declarations and
 * conflicting declarations are rejected. Commented declarations are ignored.</p>
 *
 * <p>Every plan-aware program must also declare
 * {@code <!binding-policy: required>}. A semantic plan cannot safely authorize
 * overload-, owner-, type- or hierarchy-dependent rewrites when the local AST
 * was parsed without the bindings required to re-identify its targets.</p>
 */
public final class HintPlanRequirement {

	private static final String PREFIX= "<!requires-plan"; //$NON-NLS-1$
	private static final Pattern DIRECTIVE= Pattern.compile(
			"^\\s*<!requires-plan\\s*:\\s*([^>\\r\\n]*)>\\s*$"); //$NON-NLS-1$

	private HintPlanRequirement() {
	}

	/**
	 * Returns the declared semantic-plan contract identifier.
	 *
	 * @param content complete hint program text
	 * @return the required plan identifier, or empty for an ordinary hint program
	 * @throws IllegalArgumentException for blank, malformed or conflicting declarations,
	 *         or when a plan-aware program does not require semantic bindings
	 */
	public static Optional<String> fromContent(String content) {
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}
		String requirement= null;
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
				throw new IllegalArgumentException("Malformed requires-plan declaration: " + visible); //$NON-NLS-1$
			}
			String candidate= matcher.group(1).trim();
			if (candidate.isEmpty()) {
				throw new IllegalArgumentException("requires-plan must name a semantic plan contract"); //$NON-NLS-1$
			}
			if (requirement != null && !requirement.equals(candidate)) {
				throw new IllegalArgumentException(
						"Conflicting requires-plan declarations: " + requirement + " and " + candidate); //$NON-NLS-1$ //$NON-NLS-2$
			}
			requirement= candidate;
		}
		if (requirement != null) {
			HintBindingPolicy policy= HintBindingPolicy.fromContent(content).orElseThrow(() ->
					new IllegalArgumentException(
							"Plan-aware hint programs must declare <!binding-policy: required>")); //$NON-NLS-1$
			if (policy != HintBindingPolicy.REQUIRED) {
				throw new IllegalArgumentException(
						"Plan-aware hint programs require binding-policy required, not " //$NON-NLS-1$
								+ policy.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
		return Optional.ofNullable(requirement);
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
