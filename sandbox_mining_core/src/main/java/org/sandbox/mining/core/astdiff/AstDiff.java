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

import java.util.List;

/**
 * Result of an AST-level diff between two code fragments.
 *
 * @param source  the original {@link CodeChangePair}
 * @param changes the list of individual AST node changes
 */
public record AstDiff(
		CodeChangePair source,
		List<AstNodeChange> changes) {

	/**
	 * Returns {@code true} if no meaningful changes were detected.
	 */
	public boolean isEmpty() {
		return changes == null || changes.isEmpty();
	}

	/**
	 * Returns the number of changes.
	 */
	public int changeCount() {
		return changes == null ? 0 : changes.size();
	}
}
