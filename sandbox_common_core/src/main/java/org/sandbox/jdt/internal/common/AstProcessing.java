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

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Javadoc;

/**
 * Entry point for AST processing whose execution model is explicit at the call site.
 *
 * <p>{@link #scoped(ReferenceHolder)} creates a semantic pipeline in which every
 * stage searches only the scope selected by the preceding stage. In contrast,
 * {@link #independent(ReferenceHolder)} registers visitors that each inspect the
 * complete root passed to {@code build(...)}.</p>
 *
 * <p>Both classic and Java 23+ Markdown documentation comments are represented
 * by {@link Javadoc}. When using an {@link ASTParser} directly, enable
 * {@link JavaCore#COMPILER_DOC_COMMENT_SUPPORT} to parse the comment contents.
 * Use a Java 23 or later source and AST level to process Markdown documentation
 * and use {@link Javadoc#isMarkdown()} to distinguish the formats.</p>
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

	/** Creates independent visitors when callbacks need no shared reference holder. */
	public static IndependentAstProcessorBuilder<Void, Void> independent() {
		return independent(ReferenceHolder.create());
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
