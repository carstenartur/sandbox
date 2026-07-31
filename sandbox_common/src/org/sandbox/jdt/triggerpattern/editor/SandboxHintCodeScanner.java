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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.internal.ui.JavaPlugin;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintLanguageVocabulary;
import org.sandbox.jdt.triggerpattern.internal.HintPredicatePreprocessor;

/** Scanner for code regions in {@code .sandbox-hint} files. */
public class SandboxHintCodeScanner extends RuleBasedScanner {

	static final RGB OPERATOR_RGB= new RGB(128, 0, 128);
	static final RGB PLACEHOLDER_RGB= new RGB(128, 0, 0);
	static final RGB FUNCTION_RGB= new RGB(0, 0, 192);
	static final RGB STRING_RGB= new RGB(42, 0, 255);
	static final RGB METADATA_RGB= new RGB(64, 128, 128);
	static final RGB DIRECTIVE_RGB= new RGB(0, 0, 128);

	private static final String FALLBACK_COLOR_PREFIX= "org.sandbox.jdt.triggerpattern.color."; //$NON-NLS-1$

	private final IToken operatorToken= token(OPERATOR_RGB, SWT.BOLD);
	private final IToken placeholderToken= token(PLACEHOLDER_RGB, SWT.NORMAL);
	private final IToken variadicToken= token(PLACEHOLDER_RGB, SWT.BOLD);
	private final IToken functionToken= token(FUNCTION_RGB, SWT.BOLD);
	private final IToken stringToken= token(STRING_RGB, SWT.NORMAL);
	private final IToken metadataToken= token(METADATA_RGB, SWT.ITALIC);
	private Set<String> localPredicateNames= Set.of();

	public SandboxHintCodeScanner() {
		configureRules();
	}

	@Override
	public void setRange(IDocument document, int offset, int length) {
		Set<String> discovered= HintPredicatePreprocessor.discover(document.get()).stream()
				.map(predicate -> predicate.name())
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		if (!discovered.equals(localPredicateNames)) {
			localPredicateNames= discovered;
			configureRules();
		}
		super.setRange(document, offset, length);
	}

	private void configureRules() {
		List<IRule> rules= new ArrayList<>();
		rules.add(new SingleLineRule("<!", ">", metadataToken)); //$NON-NLS-1$ //$NON-NLS-2$
		rules.add(new SingleLineRule("\"", "\"", stringToken, '\\')); //$NON-NLS-1$ //$NON-NLS-2$
		HintLanguageVocabulary.operators().stream()
				.sorted(Comparator.comparingInt(String::length).reversed())
				.map(operator -> new OperatorRule(operator, operatorToken))
				.forEach(rules::add);
		rules.add(new PlaceholderRule(placeholderToken, variadicToken));

		WordRule guards= new WordRule(new IWordDetector() {
			@Override
			public boolean isWordStart(char character) {
				return Character.isJavaIdentifierStart(character);
			}

			@Override
			public boolean isWordPart(char character) {
				return Character.isJavaIdentifierPart(character);
			}
		}, Token.UNDEFINED);
		Set<String> names= new LinkedHashSet<>(GuardRegistry.getInstance().getRegisteredNames());
		names.addAll(localPredicateNames);
		names.stream().sorted().forEach(name -> guards.addWord(name, functionToken));
		rules.add(guards);
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
		return new Token(new TextAttribute(managedColor(rgb), null, style));
	}

	/**
	 * Returns an editor-managed shared color. Plain Maven tests deliberately use
	 * a null foreground so lexical scanner tests never initialize platform-specific
	 * SWT native libraries.
	 */
	static Color managedColor(RGB rgb) {
		if (!Platform.isRunning()) {
			return null;
		}
		JavaPlugin plugin= JavaPlugin.getDefault();
		if (plugin != null) {
			return plugin.getJavaTextTools().getColorManager().getColor(rgb);
		}
		ColorRegistry registry= JFaceResources.getColorRegistry();
		String key= FALLBACK_COLOR_PREFIX + rgb.red + '.' + rgb.green + '.' + rgb.blue;
		synchronized (registry) {
			if (!registry.hasValueFor(key)) {
				registry.put(key, rgb);
			}
			return registry.get(key);
		}
	}
}
