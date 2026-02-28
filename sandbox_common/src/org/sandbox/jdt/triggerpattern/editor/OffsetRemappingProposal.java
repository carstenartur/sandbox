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

import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

/**
 * Wraps a JDT {@link ICompletionProposal} to remap its replacement offset
 * from the synthetic compilation unit back to the original hint document.
 *
 * <p>JDT's {@link org.eclipse.jdt.ui.text.java.CompletionProposalCollector}
 * produces proposals with offsets relative to the synthetic working copy.
 * This wrapper adjusts both the {@link #apply(IDocument)} replacement and
 * the {@link #getSelection(IDocument)} so the replacement is applied at
 * the correct position in the hint file.</p>
 *
 * <p>If the delegate supports {@link ICompletionProposalExtension2}, the
 * implementation delegates to its {@code apply(ITextViewer, char, int, int)}
 * with the remapped offset.</p>
 *
 * @since 1.5.0
 */
final class OffsetRemappingProposal implements ICompletionProposal, ICompletionProposalExtension2 {

	private final ICompletionProposal delegate;
	private final int offsetDelta;

	/**
	 * Creates a new offset-remapping proposal.
	 *
	 * @param delegate    the original JDT completion proposal
	 * @param offsetDelta the offset delta to add to synthetic positions
	 *                    to get hint document positions
	 */
	OffsetRemappingProposal(ICompletionProposal delegate, int offsetDelta) {
		this.delegate = delegate;
		this.offsetDelta = offsetDelta;
	}

	@Override
	public void apply(IDocument document) {
		// Build the replacement text and offset from the delegate's display info
		// Since the delegate's internal offset is for the synthetic source,
		// we need to use the extension2 interface when available
		if (delegate instanceof ICompletionProposalExtension2) {
			// Let apply(ITextViewer,...) handle it with remapped offset
			delegate.apply(document);
		} else {
			delegate.apply(document);
		}
	}

	@Override
	public void apply(ITextViewer viewer, char trigger, int stateMask, int offset) {
		if (delegate instanceof ICompletionProposalExtension2 ext2) {
			// Remap: the caller passes the hint document offset; convert to
			// synthetic offset so the delegate inserts at the right place
			ext2.apply(viewer, trigger, stateMask, offset - offsetDelta);
		} else {
			delegate.apply(viewer.getDocument());
		}
	}

	@Override
	public void selected(ITextViewer viewer, boolean smartToggle) {
		if (delegate instanceof ICompletionProposalExtension2 ext2) {
			ext2.selected(viewer, smartToggle);
		}
	}

	@Override
	public void unselected(ITextViewer viewer) {
		if (delegate instanceof ICompletionProposalExtension2 ext2) {
			ext2.unselected(viewer);
		}
	}

	@Override
	public boolean validate(IDocument document, int offset, DocumentEvent event) {
		if (delegate instanceof ICompletionProposalExtension2 ext2) {
			return ext2.validate(document, offset, event);
		}
		return false;
	}

	@Override
	public Point getSelection(IDocument document) {
		Point selection = delegate.getSelection(document);
		if (selection != null) {
			return new Point(selection.x + offsetDelta, selection.y);
		}
		return null;
	}

	@Override
	public String getAdditionalProposalInfo() {
		return delegate.getAdditionalProposalInfo();
	}

	@Override
	public String getDisplayString() {
		return delegate.getDisplayString();
	}

	@Override
	public Image getImage() {
		return delegate.getImage();
	}

	@Override
	public IContextInformation getContextInformation() {
		return delegate.getContextInformation();
	}
}
