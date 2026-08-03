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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.Token;

import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;
import org.sandbox.jdt.triggerpattern.cleanup.actions.StructuredRewriteActionRegistry;

class SandboxHintStructuredActionEditorTest {

	@Test
	void recognizesOnlyActionNamePositionsAsCompletionContexts() {
		String start= "void $name()\n=>! addA"; //$NON-NLS-1$
		String afterSeparator= "void $name()\n=>! addAnnotation(target=$name, annotation=\"A\"); rem"; //$NON-NLS-1$
		String insideArguments= "void $name()\n=>! addAnnotation(tar"; //$NON-NLS-1$
		String guard= "void $name()\n=>! addAnnotation(target=$name, annotation=\"A\") :: planned"; //$NON-NLS-1$

		assertTrue(SandboxHintContentAssistProcessor.isStructuredActionContext(start, start.length()));
		assertTrue(SandboxHintContentAssistProcessor.isStructuredActionContext(
				afterSeparator, afterSeparator.length()));
		assertFalse(SandboxHintContentAssistProcessor.isStructuredActionContext(
				insideArguments, insideArguments.length()));
		assertFalse(SandboxHintContentAssistProcessor.isStructuredActionContext(guard, guard.length()));
	}

	@Test
	void actionProposalsComeFromTheCanonicalCatalogAndInsertValidTemplates() {
		Map<String, SandboxHintContentAssistProcessor.CompletionEntry> entries=
				SandboxHintContentAssistProcessor.completionEntries("=>! ", false, true).stream() //$NON-NLS-1$
						.collect(Collectors.toMap(
								SandboxHintContentAssistProcessor.CompletionEntry::name,
								entry -> entry));

		assertEquals(RewriteActionCatalog.standard().names(), entries.keySet());
		SandboxHintContentAssistProcessor.CompletionEntry addAnnotation= entries.get("addAnnotation"); //$NON-NLS-1$
		assertTrue(addAnnotation.replacement().startsWith("addAnnotation(")); //$NON-NLS-1$
		assertTrue(addAnnotation.replacement().contains("annotation=")); //$NON-NLS-1$
		assertFalse(addAnnotation.replacement().contains("target=")); //$NON-NLS-1$
		assertTrue(addAnnotation.description().contains("target=VALUE")); //$NON-NLS-1$
		assertFalse(addAnnotation.replacement().contains("?")); //$NON-NLS-1$
		assertTrue(addAnnotation.cursorPosition() > addAnnotation.replacement().indexOf('='));
	}

	@Test
	void patternKindCompletionIsRestrictedToKindMetadataValues() {
		String kindLine= "@kind: TYPE_D"; //$NON-NLS-1$
		String ordinaryLine= "class TYPE_D"; //$NON-NLS-1$
		assertTrue(SandboxHintContentAssistProcessor.isPatternKindContext(kindLine, kindLine.length()));
		assertFalse(SandboxHintContentAssistProcessor.isPatternKindContext(
				ordinaryLine, ordinaryLine.length()));

		Map<String, SandboxHintContentAssistProcessor.CompletionEntry> entries=
				SandboxHintContentAssistProcessor.completionEntries(kindLine, false, false, true).stream()
						.collect(Collectors.toMap(
								SandboxHintContentAssistProcessor.CompletionEntry::name,
								entry -> entry));
		assertEquals(Arrays.stream(PatternKind.values()).map(Enum::name).collect(Collectors.toSet()),
				entries.keySet());
		assertEquals("TYPE_DECLARATION", entries.get("TYPE_DECLARATION").replacement()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void runtimeHandlersAndParserSchemasCannotDrift() {
		assertEquals(RewriteActionCatalog.standard().names(),
				StructuredRewriteActionRegistry.getInstance().registeredNames());
	}

	@Test
	void structuredOperatorIsHighlightedAsOneToken() {
		String source= "void $name()\n=>! addAnnotation(target=$name, annotation=\"A\")\n;;"; //$NON-NLS-1$
		SandboxHintCodeScanner scanner= new SandboxHintCodeScanner();
		scanner.setRange(new Document(source), 0, source.length());
		while (true) {
			IToken token= scanner.nextToken();
			if (token == Token.EOF) {
				break;
			}
			if ("=>!".equals(source.substring(scanner.getTokenOffset(), //$NON-NLS-1$
					scanner.getTokenOffset() + scanner.getTokenLength()))) {
				assertEquals(scanner.operatorToken(), token);
				return;
			}
		}
		throw new AssertionError("No structured action operator token"); //$NON-NLS-1$
	}
}
