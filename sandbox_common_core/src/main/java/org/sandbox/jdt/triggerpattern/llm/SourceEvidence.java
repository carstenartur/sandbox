/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

/** Original changed spans; only the local Git checker supplies {@code diffHunk}. */
public record SourceEvidence(String path, String before, String after, String diffHunk) {

	/** Creates an unverified model claim without a locally attested hunk. */
	public SourceEvidence(String path, String before, String after) {
		this(path, before, after, null);
	}
}
