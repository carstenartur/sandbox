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

import java.time.LocalDateTime;

/**
 * Information about a single Git commit.
 *
 * @param id               the full commit hash
 * @param shortId          the abbreviated commit hash
 * @param message          the commit message
 * @param author           the author name
 * @param timestamp        the commit timestamp
 * @param changedFileCount the number of files changed in this commit
 * @since 1.2.6
 */
public record CommitInfo(
		String id,
		String shortId,
		String message,
		String author,
		LocalDateTime timestamp,
		int changedFileCount) {
}
