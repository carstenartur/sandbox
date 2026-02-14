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
 * Result of a structural comparison between two AST nodes.
 *
 * @param structurallyCompatible true if the before and after trees share the same
 *                               basic structure (same root node type, similar children),
 *                               differing only at leaf level
 * @param alignments             the list of per-node alignments discovered during the diff
 * @since 1.2.6
 */
public record AstDiff(boolean structurallyCompatible, List<NodeAlignment> alignments) {
}
