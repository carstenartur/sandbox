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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

import org.sandbox.jdt.triggerpattern.editor.SandboxHintContentAssistProcessor.CompletionEntry;

class SandboxHintEditorLanguageTest {

	@Test
	void highlightsDeclaredPredicatesRegisteredGuardsAndLogicalOperators() {
		String source= """
				<!predicate exactTest($method): isPublic($method) && paramCount($method, 0)>
				foo($x) :: exactTest($x) && plannedRole($x, "TEST") || otherwise
				=> bar($x)
				;;
				""";
		SandboxHintCodeScanner scanner= new SandboxHintCodeScanner();
		Document document= new Document(source);
		scanner.setRange(document, 0, document.getLength());

		assertToken(scanner, source, "exactTest", scanner.functionToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "plannedRole", scanner.functionToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "otherwise", scanner.functionToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "&&", scanner.operatorToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "||", scanner.operatorToken(), SWT.BOLD); //$NON-NLS-1$
	}

	@Test
	void constructsScannersWithoutLoadingNativeSwtInStandaloneMaven() {
		assertDoesNotThrow(SandboxHintCodeScanner::new);
		assertDoesNotThrow(SandboxHintMetadataScanner::new);
		assertEquals(new RGB(0, 0, 192), SandboxHintCodeScanner.FUNCTION_RGB);
		assertEquals(new RGB(128, 0, 128), SandboxHintCodeScanner.OPERATOR_RGB);
		assertEquals(new RGB(128, 0, 0), SandboxHintCodeScanner.PLACEHOLDER_RGB);
	}

	@Test
	void completionCombinesRegistryLocalPredicatesAndDirectivesWithoutDuplicates() {
		String source= """
				<!predicate exactTest($method): isPublic($method) && paramCount($method, 0)>
				foo($method) :: ex
				=> bar($method)
				;;
				""";

		List<CompletionEntry> guards= SandboxHintContentAssistProcessor.completionEntries(source, false);
		assertEquals(guards.size(), guards.stream().map(CompletionEntry::name).distinct().count());
		CompletionEntry local= guards.stream().filter(entry -> "exactTest".equals(entry.name())) //$NON-NLS-1$
				.findFirst().orElseThrow();
		assertTrue(local.description().contains("exactTest($method)")); //$NON-NLS-1$
		assertTrue(guards.stream().anyMatch(entry -> "plannedRole".equals(entry.name()))); //$NON-NLS-1$

		List<CompletionEntry> directives=
				SandboxHintContentAssistProcessor.completionEntries("<!pre", true); //$NON-NLS-1$
		CompletionEntry predicate= directives.stream()
				.filter(entry -> "predicate".equals(entry.name())).findFirst().orElseThrow(); //$NON-NLS-1$
		assertTrue(predicate.replacement().startsWith("predicate ")); //$NON-NLS-1$
	}

	private static void assertToken(SandboxHintCodeScanner scanner, String source,
			String expectedText, IToken expectedToken, int style) {
		scanner.setRange(new Document(source), 0, source.length());
		while (true) {
			IToken token= scanner.nextToken();
			if (token == Token.EOF) {
				break;
			}
			int offset= scanner.getTokenOffset();
			int length= scanner.getTokenLength();
			if (!expectedText.equals(source.substring(offset, offset + length))) {
				continue;
			}
			assertSame(expectedToken, token);
			assertTrue(token.getData() instanceof TextAttribute);
			TextAttribute attribute= (TextAttribute) token.getData();
			assertTrue((attribute.getStyle() & style) != 0);
			return;
		}
		throw new AssertionError("No token for " + expectedText); //$NON-NLS-1$
	}
}
