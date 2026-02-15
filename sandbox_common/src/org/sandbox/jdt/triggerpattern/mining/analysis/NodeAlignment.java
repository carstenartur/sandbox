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

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Represents the alignment of a single pair of AST nodes in a structural diff.
 *
 * @param beforeNode the node from the before-tree (may be null for INSERTED)
 * @param afterNode  the node from the after-tree (may be null for DELETED)
 * @param kind       the kind of alignment
 * @since 1.2.6
 */
public record NodeAlignment(ASTNode beforeNode, ASTNode afterNode, AlignmentKind kind) {
}
