/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import org.sandbox.jdt.triggerpattern.api.HintPredicateDefinition;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintLanguageVocabulary;
import org.sandbox.jdt.triggerpattern.internal.HintPredicatePreprocessor;

/** Content assist for directives, guards, predicates, actions and explicit kinds. */
public class SandboxHintContentAssistProcessor implements IContentAssistProcessor {

	/** Pure proposal description used by UI code and PDE unit tests. */
	static record CompletionEntry(String name, String replacement,
			int cursorPosition, String description) {
	}

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document= viewer.getDocument();
		String prefix= extractPrefix(document, offset);
		int replacementOffset= offset - prefix.length();
		String source= document.get();
		boolean predicateGuardContext= isPredicateGuardContext(source, replacementOffset);
		boolean directiveContext= !predicateGuardContext && isDirectiveContext(source, replacementOffset);
		boolean patternKindContext= !directiveContext && !predicateGuardContext
				&& isPatternKindContext(source, replacementOffset);
		boolean actionContext= !directiveContext && !predicateGuardContext && !patternKindContext
				&& isStructuredActionContext(source, replacementOffset);
		if (!directiveContext && !predicateGuardContext && !patternKindContext && !actionContext
				&& !isGuardContext(source, replacementOffset)) {
			return new ICompletionProposal[0];
		}

		String lowerPrefix= prefix.toLowerCase(Locale.ROOT);
		List<ICompletionProposal> proposals= new ArrayList<>();
		for (CompletionEntry entry : completionEntries(source, directiveContext, actionContext,
				patternKindContext)) {
			if (!entry.name().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
				continue;
			}
			proposals.add(new CompletionProposal(entry.replacement(), replacementOffset,
					prefix.length(), entry.cursorPosition(), null, entry.name(), null,
					entry.description()));
		}
		return proposals.toArray(ICompletionProposal[]::new);
	}

	static List<CompletionEntry> completionEntries(String source, boolean directiveContext) {
		return completionEntries(source, directiveContext, false, false);
	}

	static List<CompletionEntry> completionEntries(String source, boolean directiveContext,
			boolean actionContext) {
		return completionEntries(source, directiveContext, actionContext, false);
	}

	static List<CompletionEntry> completionEntries(String source, boolean directiveContext,
			boolean actionContext, boolean patternKindContext) {
		if (directiveContext) {
			return HintLanguageVocabulary.directives().stream().map(directive -> {
				String syntax= directive.syntax();
				String replacement= syntax.startsWith("<!") && syntax.endsWith(">") //$NON-NLS-1$ //$NON-NLS-2$
						? syntax.substring(2, syntax.length() - 1) : syntax;
				return new CompletionEntry(directive.name(), replacement, replacement.length(),
						directive.description() + " — " + directive.syntax()); //$NON-NLS-1$
			}).toList();
		}
		if (patternKindContext) {
			return java.util.Arrays.stream(PatternKind.values())
					.map(kind -> new CompletionEntry(kind.name(), kind.name(), kind.name().length(),
							"Explicit source pattern kind " + kind.name())) //$NON-NLS-1$
					.toList();
		}
		if (actionContext) {
			return HintLanguageVocabulary.actions().stream().map(action -> {
				String replacement= action.replacement();
				int cursor= replacement.indexOf('=');
				cursor= cursor < 0 ? replacement.length() : cursor + 1;
				return new CompletionEntry(action.name(), replacement, cursor,
						action.description() + " — " + action.syntax()); //$NON-NLS-1$
			}).toList();
		}

		Map<String, CompletionEntry> entries= new LinkedHashMap<>();
		for (String name : new TreeSet<>(GuardRegistry.getInstance().getRegisteredNames())) {
			entries.put(name, new CompletionEntry(name, name, name.length(),
					HintLanguageVocabulary.guardDescription(name)));
		}
		for (HintPredicateDefinition predicate : HintPredicatePreprocessor.discover(source)) {
			String description= "Local predicate " + predicate.signature() //$NON-NLS-1$
					+ " declared on line " + predicate.lineNumber(); //$NON-NLS-1$
			entries.put(predicate.name(), new CompletionEntry(predicate.name(), predicate.name(),
					predicate.name().length(), description));
		}
		return List.copyOf(entries.values());
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { ':', '<', '!' };
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

	private static String extractPrefix(IDocument document, int offset) {
		try {
			int start= offset;
			while (start > 0) {
				char character= document.getChar(start - 1);
				if (!Character.isJavaIdentifierPart(character)) {
					break;
				}
				start--;
			}
			return document.get(start, offset - start);
		} catch (BadLocationException exception) {
			return ""; //$NON-NLS-1$
		}
	}

	static boolean isDirectiveContext(String source, int offset) {
		int safeOffset= Math.max(0, Math.min(offset, source.length()));
		int opening= source.lastIndexOf("<!", safeOffset); //$NON-NLS-1$
		int closing= source.lastIndexOf('>', safeOffset);
		return opening >= 0 && opening > closing;
	}

	static boolean isPredicateGuardContext(String source, int offset) {
		int safeOffset= Math.max(0, Math.min(offset, source.length()));
		int opening= source.lastIndexOf("<!", safeOffset); //$NON-NLS-1$
		int closing= source.lastIndexOf('>', safeOffset);
		if (opening < 0 || opening <= closing) {
			return false;
		}
		int nameStart= opening + 2;
		while (nameStart < safeOffset && Character.isWhitespace(source.charAt(nameStart))) {
			nameStart++;
		}
		if (!source.startsWith("predicate", nameStart)) { //$NON-NLS-1$
			return false;
		}
		int parametersEnd= source.indexOf(')', nameStart + "predicate".length()); //$NON-NLS-1$
		if (parametersEnd < 0 || parametersEnd >= safeOffset) {
			return false;
		}
		int colon= source.indexOf(':', parametersEnd + 1);
		return colon >= 0 && colon < safeOffset;
	}

	static boolean isPatternKindContext(String source, int offset) {
		int safeOffset= Math.max(0, Math.min(offset, source.length()));
		int lineStart= source.lastIndexOf('\n', Math.max(0, safeOffset - 1)) + 1;
		String prefix= source.substring(lineStart, safeOffset).stripLeading();
		if (!prefix.startsWith("@kind:")) { //$NON-NLS-1$
			return false;
		}
		String valuePrefix= prefix.substring(6).trim();
		return valuePrefix.isEmpty() || valuePrefix.chars()
				.allMatch(character -> Character.isJavaIdentifierPart(character));
	}

	static boolean isStructuredActionContext(String source, int offset) {
		int safeOffset= Math.max(0, Math.min(offset, source.length()));
		int actionStart= source.lastIndexOf("=>!", safeOffset); //$NON-NLS-1$
		int terminator= source.lastIndexOf(";;", safeOffset); //$NON-NLS-1$
		if (actionStart < 0 || actionStart < terminator) {
			return false;
		}
		int guard= source.lastIndexOf("::", safeOffset); //$NON-NLS-1$
		if (guard > actionStart) {
			return false;
		}
		int sequenceStart= Math.max(actionStart + 3, source.lastIndexOf(';', safeOffset) + 1);
		String candidate= source.substring(sequenceStart, safeOffset).trim();
		return candidate.isEmpty() || candidate.chars().allMatch(Character::isJavaIdentifierPart);
	}

	private static boolean isGuardContext(String source, int offset) {
		int safeOffset= Math.max(0, Math.min(offset, source.length()));
		int guard= source.lastIndexOf("::", safeOffset); //$NON-NLS-1$
		int rewrite= source.lastIndexOf("=>", safeOffset); //$NON-NLS-1$
		int terminator= source.lastIndexOf(";;", safeOffset); //$NON-NLS-1$
		return guard >= 0 && guard > Math.max(rewrite, terminator);
	}
}
