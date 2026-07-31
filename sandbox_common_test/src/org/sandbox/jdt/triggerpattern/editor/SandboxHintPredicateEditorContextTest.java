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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

import org.eclipse.swt.SWT;

class SandboxHintPredicateEditorContextTest {

	@Test
	void highlightsGuardLanguageInsidePredicateMetadata() {
		String source= "<!predicate exactTest($method): isPublic($method) && !isStatic($method)>"; //$NON-NLS-1$
		SandboxHintMetadataScanner scanner= new SandboxHintMetadataScanner();

		assertToken(scanner, source, "exactTest", scanner.functionToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "isPublic", scanner.functionToken(), SWT.BOLD); //$NON-NLS-1$
		assertToken(scanner, source, "$method", scanner.placeholderToken(), SWT.NORMAL); //$NON-NLS-1$
		assertToken(scanner, source, "&&", scanner.operatorToken(), SWT.BOLD); //$NON-NLS-1$
	}

	@Test
	void recognizesPredicateBodyAsGuardCompletionContext() {
		String source= "<!predicate exactTest($method): isP"; //$NON-NLS-1$

		assertTrue(SandboxHintContentAssistProcessor.isPredicateGuardContext(source, source.length()));
		assertTrue(SandboxHintContentAssistProcessor.isDirectiveContext(source, source.length()));
		assertFalse(SandboxHintContentAssistProcessor.isPredicateGuardContext("<!pre", 5)); //$NON-NLS-1$
	}

	@Test
	void registersContentAssistForMetadataPartitions() {
		ContentAssistant assistant= (ContentAssistant) new SandboxHintSourceViewerConfiguration()
				.getContentAssistant(null);

		assertNotNull(assistant.getContentAssistProcessor(SandboxHintPartitionScanner.METADATA));
	}

	private static void assertToken(SandboxHintMetadataScanner scanner, String source,
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
			if (style == SWT.NORMAL) {
				assertTrue(attribute.getStyle() == SWT.NORMAL);
			} else {
				assertTrue((attribute.getStyle() & style) != 0);
			}
			return;
		}
		throw new AssertionError("No token for " + expectedText); //$NON-NLS-1$
	}
}
