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
package org.sandbox.jdt.triggerpattern.editor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

/**
 * Hyperlink detector for navigating from guard function references in
 * DSL code to their definitions in embedded Java ({@code <? ?>}) blocks.
 *
 * <p>When the user Ctrl+Clicks (or presses F3) on a guard function name
 * after a {@code ::} separator, this detector searches for a matching
 * method in the document's {@code <? ?>} blocks and navigates to it.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintHyperlinkDetector extends AbstractHyperlinkDetector {

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
			boolean canShowMultipleHyperlinks) {
		IDocument document = textViewer.getDocument();
		if (document == null) {
			return null;
		}

		int offset = region.getOffset();

		try {
			// Check that we're in the default content type (DSL code)
			ITypedRegion partition = document.getPartition(offset);
			if (!IDocument.DEFAULT_CONTENT_TYPE.equals(partition.getType())) {
				return null;
			}

			// Extract the word at the cursor
			String word = extractWord(document, offset);
			if (word == null || word.isEmpty()) {
				return null;
			}

			// Check if the word is preceded by :: (guard reference)
			int wordStart = findWordStart(document, offset);
			if (!isAfterGuardSeparator(document, wordStart)) {
				return null;
			}

			// Search for a method with this name in <? ?> blocks
			IRegion target = findMethodInJavaBlocks(document, word);
			if (target == null) {
				return null;
			}

			IRegion linkRegion = new Region(wordStart, word.length());
			return new IHyperlink[] {
				new GuardFunctionHyperlink(linkRegion, target, word, textViewer)
			};

		} catch (BadLocationException e) {
			return null;
		}
	}

	private String extractWord(IDocument document, int offset) throws BadLocationException {
		int start = findWordStart(document, offset);
		int end = offset;
		while (end < document.getLength()) {
			char c = document.getChar(end);
			if (!Character.isLetterOrDigit(c) && c != '_') {
				break;
			}
			end++;
		}
		if (end > start) {
			return document.get(start, end - start);
		}
		return null;
	}

	private int findWordStart(IDocument document, int offset) throws BadLocationException {
		int start = offset;
		while (start > 0) {
			char c = document.getChar(start - 1);
			if (!Character.isLetterOrDigit(c) && c != '_') {
				break;
			}
			start--;
		}
		return start;
	}

	private boolean isAfterGuardSeparator(IDocument document, int offset) throws BadLocationException {
		int pos = offset - 1;
		// Skip whitespace
		while (pos >= 0 && Character.isWhitespace(document.getChar(pos))) {
			pos--;
		}
		// Check for '::'
		if (pos >= 1
				&& document.getChar(pos) == ':'
				&& document.getChar(pos - 1) == ':') {
			return true;
		}
		return false;
	}

	/**
	 * Searches for a method definition in embedded Java blocks.
	 */
	private IRegion findMethodInJavaBlocks(IDocument document, String methodName)
			throws BadLocationException {
		ITypedRegion[] partitions = document.computePartitioning(0, document.getLength());
		for (ITypedRegion partition : partitions) {
			if (SandboxHintPartitionScanner.JAVA_CODE.equals(partition.getType())) {
				String text = document.get(partition.getOffset(), partition.getLength());
				// Look for the method name followed by (
				int idx = text.indexOf(methodName + "("); //$NON-NLS-1$
				if (idx >= 0) {
					// Verify it's a method declaration (preceded by a type name)
					int checkPos = idx - 1;
					while (checkPos >= 0 && Character.isWhitespace(text.charAt(checkPos))) {
						checkPos--;
					}
					if (checkPos >= 0 && (Character.isLetterOrDigit(text.charAt(checkPos))
							|| text.charAt(checkPos) == ']')) {
						return new Region(partition.getOffset() + idx, methodName.length());
					}
				}
			}
		}
		return null;
	}

	/**
	 * A hyperlink that navigates to a guard function definition.
	 */
	private static class GuardFunctionHyperlink implements IHyperlink {

		private final IRegion linkRegion;
		private final IRegion targetRegion;
		private final String functionName;
		private final ITextViewer viewer;

		GuardFunctionHyperlink(IRegion linkRegion, IRegion targetRegion,
				String functionName, ITextViewer viewer) {
			this.linkRegion = linkRegion;
			this.targetRegion = targetRegion;
			this.functionName = functionName;
			this.viewer = viewer;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return linkRegion;
		}

		@Override
		public String getTypeLabel() {
			return "Guard Function"; //$NON-NLS-1$
		}

		@Override
		public String getHyperlinkText() {
			return "Open guard function '" + functionName + "'"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public void open() {
			viewer.setSelectedRange(targetRegion.getOffset(), targetRegion.getLength());
			viewer.revealRange(targetRegion.getOffset(), targetRegion.getLength());
		}
	}
}
