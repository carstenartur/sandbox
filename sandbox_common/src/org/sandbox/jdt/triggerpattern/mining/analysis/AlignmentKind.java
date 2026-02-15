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
 * Describes the kind of alignment between two AST nodes in a diff.
 *
 * @since 1.2.6
 */
public enum AlignmentKind {
	/** Both nodes are structurally identical. */
	IDENTICAL,
	/** Both nodes exist but differ in content. */
	MODIFIED,
	/** The node was inserted (only in after-tree). */
	INSERTED,
	/** The node was deleted (only in before-tree). */
	DELETED
}
