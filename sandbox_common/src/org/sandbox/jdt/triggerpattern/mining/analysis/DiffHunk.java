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
package org.sandbox.jdt.triggerpattern.mining.analysis;

/**
 * Represents a single hunk from a unified diff.
 *
 * @param beforeStartLine the starting line number in the before-file
 * @param beforeLineCount the number of lines in the hunk from the before-file
 * @param afterStartLine  the starting line number in the after-file
 * @param afterLineCount  the number of lines in the hunk from the after-file
 * @param beforeText      the text from the before-file
 * @param afterText       the text from the after-file
 * @since 1.2.6
 */
public record DiffHunk(
		int beforeStartLine,
		int beforeLineCount,
		int afterStartLine,
		int afterLineCount,
		String beforeText,
		String afterText) {
}
