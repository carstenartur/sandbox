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

import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * A transformation rule inferred by comparing before/after code snippets.
 *
 * @param sourcePattern      the source-side pattern with {@code $placeholder} syntax
 * @param replacementPattern the replacement-side pattern with {@code $placeholder} syntax
 * @param kind               the {@link PatternKind} of the matched construct
 * @param confidence         confidence score between 0.0 and 1.0
 * @param placeholderNames   names of all placeholders used in the patterns
 * @param importChanges      import additions/removals derived from the diff (may be {@code null})
 * @since 1.2.6
 */
public record InferredRule(
		String sourcePattern,
		String replacementPattern,
		PatternKind kind,
		double confidence,
		List<String> placeholderNames,
		ImportDirective importChanges) {
}
