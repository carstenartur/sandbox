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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;

/**
 * Content assist processor for {@code .sandbox-hint} files.
 *
 * <p>Provides completion proposals for guard function names after the
 * {@code ::} guard separator. Proposals are sourced from the
 * {@link GuardRegistry}.</p>
 *
 * @since 1.3.6
 */
public class SandboxHintContentAssistProcessor implements IContentAssistProcessor {

	/**
	 * Guard function entries with name and description.
	 */
	private static final String[][] GUARD_PROPOSALS = {
		{ "instanceof", "$x instanceof Type – type check" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "matchesAny", "matchesAny($x, \"lit1\", \"lit2\") – value is one of the given literals" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "matchesNone", "matchesNone($x, \"lit1\", ...) – value is none of the given literals" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "referencedIn", "referencedIn($x, $y) – variable $x is referenced in expression $y" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "hasNoSideEffect", "hasNoSideEffect($x) – expression has no side effects" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "sourceVersionGE", "sourceVersionGE(n) – source version >= n" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "sourceVersionLE", "sourceVersionLE(n) – source version <= n" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "sourceVersionBetween", "sourceVersionBetween(min, max) – source version in range" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "elementKindMatches", "elementKindMatches($x, KIND) – element is of a specific kind" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "isStatic", "isStatic($x) – element has static modifier" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "isFinal", "isFinal($x) – element has final modifier" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "hasAnnotation", "hasAnnotation($x, \"fully.qualified.Annotation\")" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "isDeprecated", "isDeprecated($x) – element is @Deprecated" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "otherwise", "otherwise – catch-all guard (always true)" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "parent", "parent($x, Type) – parent node is of the given type" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "contains", "contains($x, pattern) – expression contains the pattern" }, //$NON-NLS-1$ //$NON-NLS-2$
	};

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();
		String prefix = extractPrefix(document, offset);

		// Check if we are after a :: guard separator
		boolean afterGuard = isAfterGuardSeparator(document, offset - prefix.length());

		if (!afterGuard && prefix.isEmpty()) {
			return new ICompletionProposal[0];
		}

		List<ICompletionProposal> proposals = new ArrayList<>();
		String lowerPrefix = prefix.toLowerCase();

		for (String[] entry : GUARD_PROPOSALS) {
			String name = entry[0];
			String description = entry[1];
			if (name.toLowerCase().startsWith(lowerPrefix)) {
				String replacement = name;
				int replacementOffset = offset - prefix.length();
				int replacementLength = prefix.length();
				int cursorPosition = replacement.length();
				proposals.add(new CompletionProposal(
						replacement, replacementOffset, replacementLength,
						cursorPosition, null, name + " – " + description, //$NON-NLS-1$
						null, description));
			}
		}

		// Also propose guard functions from the registry
		GuardRegistry registry = GuardRegistry.getInstance();
		for (String guardName : registry.getRegisteredNames()) {
			if (guardName.toLowerCase().startsWith(lowerPrefix)) {
				boolean alreadyProposed = false;
				for (String[] entry : GUARD_PROPOSALS) {
					if (entry[0].equals(guardName)) {
						alreadyProposed = true;
						break;
					}
				}
				if (!alreadyProposed) {
					String replacement = guardName;
					int replacementOffset = offset - prefix.length();
					int replacementLength = prefix.length();
					int cursorPosition = replacement.length();
					proposals.add(new CompletionProposal(
							replacement, replacementOffset, replacementLength,
							cursorPosition, null, guardName,
							null, "Custom guard function")); //$NON-NLS-1$
				}
			}
		}

		return proposals.toArray(new ICompletionProposal[0]);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { ':' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	/**
	 * Extracts the word prefix before the cursor position.
	 */
	private String extractPrefix(IDocument document, int offset) {
		try {
			int start = offset;
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (!Character.isLetterOrDigit(c) && c != '_') {
					break;
				}
				start--;
			}
			return document.get(start, offset - start);
		} catch (BadLocationException e) {
			return ""; //$NON-NLS-1$
		}
	}

	/**
	 * Checks if the position is after a {@code ::} guard separator
	 * (possibly with whitespace in between).
	 */
	private boolean isAfterGuardSeparator(IDocument document, int offset) {
		try {
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
		} catch (BadLocationException e) {
			// ignore
		}
		return false;
	}
}
