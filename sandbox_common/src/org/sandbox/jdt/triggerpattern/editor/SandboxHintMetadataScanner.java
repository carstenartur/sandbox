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
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

import org.sandbox.jdt.triggerpattern.internal.HintLanguageVocabulary;

/** Highlights directive names and embedded guard expressions in metadata blocks. */
final class SandboxHintMetadataScanner extends RuleBasedScanner {

	private final IToken directiveToken= token(SandboxHintCodeScanner.DIRECTIVE_RGB, SWT.BOLD);
	private final IToken operatorToken= token(SandboxHintCodeScanner.OPERATOR_RGB, SWT.BOLD);
	private final IToken placeholderToken= token(SandboxHintCodeScanner.PLACEHOLDER_RGB, SWT.NORMAL);
	private final IToken variadicToken= token(SandboxHintCodeScanner.PLACEHOLDER_RGB, SWT.BOLD);
	private final IToken functionToken= token(SandboxHintCodeScanner.FUNCTION_RGB, SWT.BOLD);
	private final IToken stringToken= token(SandboxHintCodeScanner.STRING_RGB, SWT.NORMAL);

	SandboxHintMetadataScanner() {
		setDefaultReturnToken(directiveToken);

		List<IRule> rules= new ArrayList<>();
		rules.add(new SingleLineRule("\"", "\"", stringToken, '\\')); //$NON-NLS-1$ //$NON-NLS-2$
		HintLanguageVocabulary.operators().stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.map(operator -> new OperatorRule(operator, operatorToken))
				.forEach(rules::add);
		rules.add(new PlaceholderRule(placeholderToken, variadicToken));
		rules.add(new SandboxHintFunctionCallRule(functionToken));

		WordRule directives= new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char character) {
				return Character.isJavaIdentifierStart(character);
			}

			@Override
			public boolean isWordPart(char character) {
				return Character.isJavaIdentifierPart(character) || character == '-';
			}
		}, Token.UNDEFINED);
		HintLanguageVocabulary.directiveNames().stream().sorted()
				.forEach(name -> directives.addWord(name, directiveToken));
		rules.add(directives);
		setRules(rules.toArray(IRule[]::new));
	}

	IToken operatorToken() {
		return operatorToken;
	}

	IToken placeholderToken() {
		return placeholderToken;
	}

	IToken functionToken() {
		return functionToken;
	}

	private static IToken token(RGB rgb, int style) {
		return new Token(new TextAttribute(SandboxHintCodeScanner.managedColor(rgb), null, style));
	}
}
