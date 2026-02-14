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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
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
		assertTrue(hintFile.getRules().size() >= 5,
				"encoding library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
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
		assertEquals("warning", hintFile.getSeverity()); //$NON-NLS-1$
		assertTrue(hintFile.getMinJavaVersion() > 0,
				"encoding library should have a minimum Java version"); //$NON-NLS-1$
	}

	/**
	 * Loads the encoding hint file from the plugin's classpath.
	 */
	private HintFile loadHintFile() throws Exception {
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
			return parser.parse(sb.toString());
		}
	}
}
