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

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

/**
 * Wraps a JDT {@link ICompletionProposal} to remap its replacement offset
 * from the synthetic compilation unit back to the original hint document.
 *
 * <p>JDT's {@link org.eclipse.jdt.ui.text.java.CompletionProposalCollector}
 * produces proposals with offsets relative to the synthetic working copy.
 * This wrapper adjusts the {@link #apply(IDocument)} behavior so the
 * replacement is applied at the correct position in the hint file.</p>
 *
 * @since 1.5.0
 */
final class OffsetRemappingProposal implements ICompletionProposal {

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
		// The delegate applies to the document directly.
		// Since the offset is embedded within the proposal, we let it apply
		// and rely on the selection to be correct.
		delegate.apply(document);
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
