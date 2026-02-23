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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Tests for the encoding {@code .sandbox-hint} file bundled with the
 * encoding quickfix plugin.
 *
 * <p>Validates that the hint file can be parsed and contains valid
 * encoding transformation rules with proper import directives and guards.</p>
 *
 * @since 1.3.3
 */
public class EncodingHintFileTest {

	private final HintFileParser parser = new HintFileParser();

	@Test
	public void testLoadEncodingLibrary() throws Exception {
		HintFile hintFile = loadHintFile();
		assertNotNull(hintFile, "encoding library should be loadable"); //$NON-NLS-1$
		assertEquals("encoding", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 130,
				"encoding library should have at least 130 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testEncodingRulesHaveImportDirectives() throws Exception {
		HintFile hintFile = loadHintFile();

		long rulesWithImports = hintFile.getRules().stream()
				.filter(r -> r.hasImportDirective())
				.count();
		assertTrue(rulesWithImports > 0,
				"Encoding rules should have import directives"); //$NON-NLS-1$
	}

	@Test
	public void testEncodingRulesHaveGuards() throws Exception {
		HintFile hintFile = loadHintFile();

		long rulesWithGuards = hintFile.getRules().stream()
				.filter(r -> r.sourceGuard() != null)
				.count();
		assertTrue(rulesWithGuards > 0,
				"Encoding rules should have guards"); //$NON-NLS-1$
	}

	@Test
	public void testEncodingMetadata() throws Exception {
		HintFile hintFile = loadHintFile();
		assertEquals(Severity.WARNING, hintFile.getSeverity());
		assertTrue(hintFile.getMinJavaVersion() > 0,
				"encoding library should have a minimum Java version"); //$NON-NLS-1$
	}

	@Test
	public void testEncodingRulesCoverAllCharsets() throws Exception {
		String content = loadHintFileContent();

		// Verify all 6 standard charsets are covered
		String[] charsets = { "UTF-8", "ISO-8859-1", "US-ASCII", "UTF-16", "UTF-16BE", "UTF-16LE" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
		for (String charset : charsets) {
			assertTrue(content.contains("\"" + charset + "\""), //$NON-NLS-1$ //$NON-NLS-2$
					"encoding library should have rules for charset: " + charset); //$NON-NLS-1$
		}
	}

	@Test
	public void testTierClassification() {
		// Tier 1 (DSL-handled): simple argument replacement patterns
		assertTrue(UseExplicitEncodingFixCore.CHARSET.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.STRING_GETBYTES.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.STRING.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.INPUTSTREAMREADER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.OUTPUTSTREAMWRITER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.SCANNER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.FORMATTER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.PRINTSTREAM.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.URLDECODER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.URLENCODER.isDslHandled());

		// Tier 2 (DSL-handled): string charset replacement + zero-arg expansion
		assertTrue(UseExplicitEncodingFixCore.CHANNELSNEWREADER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.CHANNELSNEWWRITER.isDslHandled());
		assertTrue(UseExplicitEncodingFixCore.PROPERTIES_STORETOXML.isDslHandled());

		// Tier 2 (partial DSL + imperative fallback): DSL handles zero-arg expansion,
		// imperative handles 2-arg FQN shortening and zero-arg toString()
		assertFalse(UseExplicitEncodingFixCore.FILES_NEWBUFFEREDREADER.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.FILES_NEWBUFFEREDWRITER.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.FILES_READALLLINES.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.FILES_READSTRING.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.FILES_WRITESTRING.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.BYTEARRAYOUTPUTSTREAM.isDslHandled());

		// Tier 3 (imperative-only): complex structural rewrites
		assertFalse(UseExplicitEncodingFixCore.FILEREADER.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.FILEWRITER.isDslHandled());
		assertFalse(UseExplicitEncodingFixCore.PRINTWRITER.isDslHandled());
	}

	/**
	 * Loads the encoding hint file from the plugin's classpath.
	 */
	private HintFile loadHintFile() throws Exception {
		return parser.parse(loadHintFileContent());
	}

	/**
	 * Loads the raw content of the encoding hint file.
	 */
	private String loadHintFileContent() throws Exception {
		String resourcePath = "org/sandbox/jdt/internal/corext/fix/hints/encoding.sandbox-hint"; //$NON-NLS-1$
		InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
		assertNotNull(is, "encoding.sandbox-hint resource should be found"); //$NON-NLS-1$

		try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[1024];
			int n;
			while ((n = reader.read(buf)) > 0) {
				sb.append(buf, 0, n);
			}
			return sb.toString();
		}
	}
}
