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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

/**
 * Represents an embedded Java code block ({@code <? ?>}) extracted from a
 * {@code .sandbox-hint} file.
 *
 * <p>Each block stores the raw Java source code, the start and end line numbers
 * in the hint file (for error mapping), and the character offsets for editor
 * integration.</p>
 *
 * <p>Embedded Java blocks can contain:</p>
 * <ul>
 *   <li>Custom guard functions (methods returning {@code boolean})</li>
 *   <li>Custom fix/rewrite functions</li>
 *   <li>Import declarations</li>
 *   <li>Helper methods</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class EmbeddedJavaBlock {

	private final String source;
	private final int startLine;
	private final int endLine;
	private final int startOffset;
	private final int endOffset;

	/**
	 * Creates a new embedded Java block.
	 *
	 * @param source      the raw Java source code (without {@code <?} and {@code ?>} delimiters)
	 * @param startLine   the 1-based start line in the hint file
	 * @param endLine     the 1-based end line in the hint file
	 * @param startOffset the 0-based character offset of the {@code <?} in the hint file
	 * @param endOffset   the 0-based character offset after the {@code ?>} in the hint file
	 */
	public EmbeddedJavaBlock(String source, int startLine, int endLine, int startOffset, int endOffset) {
		this.source = source != null ? source : ""; //$NON-NLS-1$
		this.startLine = startLine;
		this.endLine = endLine;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
	}

	/**
	 * Returns the raw Java source code.
	 *
	 * @return the Java source without delimiters
	 */
	public String getSource() {
		return source;
	}

	/**
	 * Returns the 1-based start line in the hint file.
	 *
	 * @return the start line number
	 */
	public int getStartLine() {
		return startLine;
	}

	/**
	 * Returns the 1-based end line in the hint file.
	 *
	 * @return the end line number
	 */
	public int getEndLine() {
		return endLine;
	}

	/**
	 * Returns the 0-based start character offset in the hint file.
	 *
	 * @return the start offset (position of {@code <?})
	 */
	public int getStartOffset() {
		return startOffset;
	}

	/**
	 * Returns the 0-based end character offset in the hint file.
	 *
	 * @return the end offset (position after {@code ?>})
	 */
	public int getEndOffset() {
		return endOffset;
	}

	/**
	 * Returns the number of lines spanned by this block.
	 *
	 * @return the line count (always &ge; 1)
	 */
	public int getLineCount() {
		return endLine - startLine + 1;
	}
}
