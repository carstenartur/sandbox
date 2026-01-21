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
package org.sandbox.jdt.internal.css.core;

import java.util.Collections;
import java.util.List;

/**
 * Result of CSS validation.
 */
public class CSSValidationResult {

	private final boolean valid;
	private final List<Issue> issues;

	public CSSValidationResult(boolean valid, List<Issue> issues) {
		this.valid = valid;
		this.issues = issues != null ? issues : Collections.emptyList();
	}

	public boolean isValid() {
		return valid;
	}

	public List<Issue> getIssues() {
		return Collections.unmodifiableList(issues);
	}

	/**
	 * A single validation issue.
	 */
	public static class Issue {
		public final int line;
		public final int column;
		public final String severity;
		public final String rule;
		public final String message;

		public Issue(int line, int column, String severity, String rule, String message) {
			this.line = line;
			this.column = column;
			this.severity = severity;
			this.rule = rule;
			this.message = message;
		}
	}
}
