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
package org.sandbox.jdt.internal.ui.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/**
 * Content assist processor for {@code .sandbox-hint} files that provides
 * completions for TriggerPattern DSL keywords.
 *
 * <p>Keyword categories:</p>
 * <ul>
 *   <li><b>Metadata directives</b>: {@code <!id:>}, {@code <!description:>}, etc.</li>
 *   <li><b>Guard functions</b>: {@code instanceof}, {@code matchesAny}, etc.</li>
 *   <li><b>Rule separators</b>: {@code =>}, {@code ;;}, {@code otherwise}</li>
 * </ul>
 *
 * @since 1.2.6
 */
public class DslContentAssistProcessor implements IContentAssistProcessor {

	private static final String[] METADATA_DIRECTIVES = {
			"<!id:>", //$NON-NLS-1$
			"<!description:>", //$NON-NLS-1$
			"<!severity:>", //$NON-NLS-1$
			"<!minJavaVersion:>", //$NON-NLS-1$
			"<!tags:>", //$NON-NLS-1$
			"<!include:>" //$NON-NLS-1$
	};

	private static final String[] GUARD_FUNCTIONS = {
			"instanceof", //$NON-NLS-1$
			"matchesAny", //$NON-NLS-1$
			"matchesNone", //$NON-NLS-1$
			"hasNoSideEffect", //$NON-NLS-1$
			"sourceVersionGE", //$NON-NLS-1$
			"sourceVersionLE", //$NON-NLS-1$
			"sourceVersionBetween", //$NON-NLS-1$
			"isStatic", //$NON-NLS-1$
			"isFinal", //$NON-NLS-1$
			"hasAnnotation", //$NON-NLS-1$
			"isDeprecated", //$NON-NLS-1$
			"referencedIn", //$NON-NLS-1$
			"elementKindMatches", //$NON-NLS-1$
			"contains", //$NON-NLS-1$
			"notContains", //$NON-NLS-1$
			"methodNameMatches" //$NON-NLS-1$
	};

	private static final String[] RULE_SEPARATORS = {
			"=>", //$NON-NLS-1$
			";;", //$NON-NLS-1$
			"otherwise" //$NON-NLS-1$
	};

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		String text = viewer.getDocument().get();
		String prefix = extractPrefix(text, offset);
		String prefixLower = prefix.toLowerCase();

		List<ICompletionProposal> proposals = new ArrayList<>();
		addMatchingProposals(METADATA_DIRECTIVES, "directive", prefix, prefixLower, offset, proposals); //$NON-NLS-1$
		addMatchingProposals(GUARD_FUNCTIONS, "guard", prefix, prefixLower, offset, proposals); //$NON-NLS-1$
		addMatchingProposals(RULE_SEPARATORS, "separator", prefix, prefixLower, offset, proposals); //$NON-NLS-1$
		return proposals.toArray(new ICompletionProposal[0]);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '<', '!' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	private static String extractPrefix(String text, int offset) {
		int start = offset;
		while (start > 0) {
			char ch = text.charAt(start - 1);
			if (Character.isWhitespace(ch)) {
				break;
			}
			start--;
		}
		return text.substring(start, offset);
	}

	private static void addMatchingProposals(String[] keywords, String category,
			String prefix, String prefixLower, int offset,
			List<ICompletionProposal> proposals) {
		for (String keyword : keywords) {
			if (prefix.isEmpty() || keyword.toLowerCase().startsWith(prefixLower)) {
				int replacementOffset = offset - prefix.length();
				proposals.add(new CompletionProposal(
						keyword,
						replacementOffset,
						prefix.length(),
						keyword.length(),
						null,
						keyword + " - " + category, //$NON-NLS-1$
						null,
						null));
			}
		}
	}
}
