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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for bundled pattern library files and HintFileRegistry loading.
 * 
 * <p>Validates that each bundled {@code .sandbox-hint} file can be parsed
 * successfully and contains valid transformation rules.</p>
 * 
 * @since 1.3.3
 */
public class BundledLibrariesTest {

	private final HintFileParser parser = new HintFileParser();

	// --- Bundled library loading tests ---

	@Test
	public void testLoadEncodingLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("encoding.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "encoding library should be loadable"); //$NON-NLS-1$
		assertEquals("encoding", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"encoding library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadCollectionsLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("collections.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "collections library should be loadable"); //$NON-NLS-1$
		assertEquals("collections", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"collections library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadModernizeLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("modernize-java11.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "modernize-java11 library should be loadable"); //$NON-NLS-1$
		assertEquals("modernize-java11", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"modernize-java11 library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadPerformanceLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("performance.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "performance library should be loadable"); //$NON-NLS-1$
		assertEquals("performance", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"performance library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadJUnit5Library() throws Exception {
		HintFile hintFile = loadBundledLibrary("junit5.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "junit5 library should be loadable"); //$NON-NLS-1$
		assertEquals("junit5", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"junit5 library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testAllBundledLibrariesHaveMetadata() throws Exception {
		for (String name : HintFileRegistry.getBundledLibraryNames()) {
			HintFile hintFile = loadBundledLibrary(name);
			assertNotNull(hintFile, "Library should load: " + name); //$NON-NLS-1$
			assertNotNull(hintFile.getId(),
					"Library should have an ID: " + name); //$NON-NLS-1$
			assertNotNull(hintFile.getDescription(),
					"Library should have a description: " + name); //$NON-NLS-1$
			assertFalse(hintFile.getRules().isEmpty(),
					"Library should have rules: " + name); //$NON-NLS-1$
		}
	}

	@Test
	public void testHintFileRegistryLoadBundled() throws Exception {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.clear();

		List<String> loaded = registry.loadBundledLibraries(
				getClass().getClassLoader());

		assertFalse(loaded.isEmpty(),
				"Should load at least one bundled library"); //$NON-NLS-1$

		// Verify each loaded library
		for (String id : loaded) {
			HintFile hintFile = registry.getHintFile(id);
			assertNotNull(hintFile, "Loaded library should be accessible: " + id); //$NON-NLS-1$
			assertFalse(hintFile.getRules().isEmpty(),
					"Loaded library should have rules: " + id); //$NON-NLS-1$
		}

		// Cleanup
		registry.clear();
	}

	@Test
	public void testEncodingRulesHaveImportDirectives() throws Exception {
		HintFile hintFile = loadBundledLibrary("encoding.sandbox-hint"); //$NON-NLS-1$

		// At least some encoding rules should have import directives for StandardCharsets
		long rulesWithImports = hintFile.getRules().stream()
				.filter(r -> r.hasImportDirective())
				.count();
		assertTrue(rulesWithImports > 0,
				"Encoding rules should have import directives"); //$NON-NLS-1$
	}

	@Test
	public void testJUnit5RulesHaveImportDirectives() throws Exception {
		HintFile hintFile = loadBundledLibrary("junit5.sandbox-hint"); //$NON-NLS-1$

		// JUnit 5 migration rules should have import directives
		long rulesWithImports = hintFile.getRules().stream()
				.filter(r -> r.hasImportDirective())
				.count();
		assertTrue(rulesWithImports > 0,
				"JUnit5 rules should have import directives"); //$NON-NLS-1$
	}

	@Test
	public void testEncodingRulesHaveGuards() throws Exception {
		HintFile hintFile = loadBundledLibrary("encoding.sandbox-hint"); //$NON-NLS-1$

		// Encoding rules should have sourceVersionGE guards
		long rulesWithGuards = hintFile.getRules().stream()
				.filter(r -> r.sourceGuard() != null)
				.count();
		assertTrue(rulesWithGuards > 0,
				"Encoding rules should have guards"); //$NON-NLS-1$
	}

	// --- Helper methods ---

	/**
	 * Loads a bundled library from the classpath (next to HintFileRegistry class).
	 */
	private HintFile loadBundledLibrary(String resourceName)
			throws HintParseException, java.io.IOException {
		// The .sandbox-hint files are in the same package as HintFileRegistry
		String resourcePath = "org/sandbox/jdt/triggerpattern/internal/" + resourceName; //$NON-NLS-1$
		java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
		if (is == null) {
			// Try loading with just the filename (same package)
			is = HintFileRegistry.class.getResourceAsStream(resourceName);
		}
		assertNotNull(is, "Resource should be found: " + resourceName); //$NON-NLS-1$

		try (java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)) {
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
