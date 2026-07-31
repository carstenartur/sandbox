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
package org.sandbox.jdt.triggerpattern.help;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.HintLanguageVocabulary;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Verifies the Eclipse help table of contents and its language reference. */
public class SandboxHelpContentTest {

	@Test
	public void allTocAndContextTargetsArePackagedResources() throws Exception {
		List<String> targets= new ArrayList<>();
		collectTargets(parseXml("toc.xml"), "topic", targets); //$NON-NLS-1$ //$NON-NLS-2$
		collectTargets(parseXml("contexts.xml"), "topic", targets); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(targets.isEmpty(), "The Sandbox Eclipse help must expose topic resources"); //$NON-NLS-1$
		for (String target : targets) {
			try (InputStream resource= resource(target)) {
				assertNotNull(resource, () -> "Missing Eclipse help resource " + target); //$NON-NLS-1$
				assertTrue(resource.readAllBytes().length > 0,
						() -> "Empty Eclipse help resource " + target); //$NON-NLS-1$
			}
		}
	}

	@Test
	public void languageReferenceCoversCanonicalDirectivesAndActions() throws IOException {
		String reference;
		try (InputStream resource= resource("help/language-reference.html")) { //$NON-NLS-1$
			assertNotNull(resource, "Missing Hint DSL language reference"); //$NON-NLS-1$
			reference= new String(resource.readAllBytes(), StandardCharsets.UTF_8);
		}
		for (String directive : HintLanguageVocabulary.directiveNames()) {
			assertTrue(reference.contains("<code>" + directive + "</code>"), //$NON-NLS-1$ //$NON-NLS-2$
					() -> "Language reference does not document directive " + directive); //$NON-NLS-1$
		}
		for (String action : HintLanguageVocabulary.actionNames()) {
			assertTrue(reference.contains("<code>" + action + "("), //$NON-NLS-1$ //$NON-NLS-2$
					() -> "Language reference does not document structured action " + action); //$NON-NLS-1$
		}
	}

	private static Document parseXml(String path)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory= DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.setExpandEntityReferences(false);
		try (InputStream resource= resource(path)) {
			assertNotNull(resource, () -> "Missing Eclipse help descriptor " + path); //$NON-NLS-1$
			return factory.newDocumentBuilder().parse(resource);
		}
	}

	private static void collectTargets(Document document, String elementName, List<String> targets) {
		NodeList topics= document.getElementsByTagName(elementName);
		for (int index= 0; index < topics.getLength(); index++) {
			Element topic= (Element) topics.item(index);
			String href= topic.getAttribute("href"); //$NON-NLS-1$
			if (!href.isBlank() && !targets.contains(href)) {
				targets.add(href);
			}
		}
	}

	private static InputStream resource(String path) {
		return SandboxHelpContentTest.class.getClassLoader().getResourceAsStream(path);
	}
}
