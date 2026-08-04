/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Entry point for AST processing whose execution model is explicit at the call site.
 *
 * <p>{@link #scoped(ReferenceHolder)} creates a semantic pipeline in which every
 * stage searches only the scope selected by the preceding stage. In contrast,
 * {@link #independent(ReferenceHolder)} registers visitors that each inspect the
 * complete root passed to {@code build(...)}.</p>
 */
public final class AstProcessing {

	private AstProcessing() {
		// Static factory.
	}

	/** Creates an ordered, scoped semantic processing pipeline. */
	public static <V, T> ScopedAstProcessorBuilder<V, T> scoped(ReferenceHolder<V, T> holder) {
		return new ScopedAstProcessorBuilder<>(Objects.requireNonNull(holder, "holder")); //$NON-NLS-1$
	}

	/** Creates a group of visitors that each inspect the complete configured root. */
	public static <V, T> IndependentAstProcessorBuilder<V, T> independent(ReferenceHolder<V, T> holder) {
		return new IndependentAstProcessorBuilder<>(Objects.requireNonNull(holder, "holder")); //$NON-NLS-1$
	}

	static VisitorEnum visitorType(Class<? extends ASTNode> nodeType) {
		Objects.requireNonNull(nodeType, "nodeType"); //$NON-NLS-1$
		try {
			return VisitorEnum.valueOf(nodeType.getSimpleName());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(
					"No HelperVisitor dispatch is available for " + nodeType.getName(), exception); //$NON-NLS-1$
		}
	}
}
