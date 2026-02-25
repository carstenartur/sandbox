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
package org.sandbox.mining.core.astdiff;

/**
 * Holds a before/after code pair extracted from a diff hunk.
 *
 * @param filePath    the file path where the change occurred
 * @param before      the code before the change
 * @param after       the code after the change
 * @param startLine   the starting line number in the original file
 * @param commitId    the commit identifier (may be null for test data)
 */
public record CodeChangePair(
		String filePath,
		String before,
		String after,
		int startLine,
		String commitId) {

	/**
	 * Creates a simple pair without file/commit metadata (for testing or
	 * direct {@code inferRule(before, after)} usage).
	 */
	public static CodeChangePair of(String before, String after) {
		return new CodeChangePair(null, before, after, 0, null);
	}
}
