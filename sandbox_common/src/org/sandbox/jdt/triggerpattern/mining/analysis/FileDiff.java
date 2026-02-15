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

import java.util.List;

/**
 * Represents the diff for a single file, including the full before/after
 * content and the individual hunks.
 *
 * @param filePath      the path of the changed file
 * @param contentBefore the full content of the file before the change
 * @param contentAfter  the full content of the file after the change
 * @param hunks         the list of diff hunks
 * @since 1.2.6
 */
public record FileDiff(
		String filePath,
		String contentBefore,
		String contentAfter,
		List<DiffHunk> hunks) {
}
