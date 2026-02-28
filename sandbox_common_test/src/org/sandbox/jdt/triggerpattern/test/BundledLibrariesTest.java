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
 * <p>Note: Domain-specific libraries ({@code encoding.sandbox-hint} and
 * {@code junit5.sandbox-hint}) have been moved to their dedicated plugins
 * ({@code sandbox_encoding_quickfix} and {@code sandbox_junit_cleanup})
 * and are tested in their respective test modules.</p>
 * 
 * @since 1.3.3
 */
public class BundledLibrariesTest {

	private final HintFileParser parser = new HintFileParser();

	// --- Bundled library loading tests ---

	@Test
	public void testLoadCollectionsLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("collections.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "collections library should be loadable"); //$NON-NLS-1$
		assertEquals("collections", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"collections library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadModernizeJava9Library() throws Exception {
		HintFile hintFile = loadBundledLibrary("modernize-java9.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "modernize-java9 library should be loadable"); //$NON-NLS-1$
		assertEquals("modernize-java9", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"modernize-java9 library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadModernizeJava11Library() throws Exception {
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
	public void testLoadStreamPerformanceLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("stream-performance.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "stream-performance library should be loadable"); //$NON-NLS-1$
		assertEquals("stream-performance", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 4,
				"stream-performance library should have at least 4 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadIoPerformanceLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("io-performance.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "io-performance library should be loadable"); //$NON-NLS-1$
		assertEquals("io-performance", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 4,
				"io-performance library should have at least 4 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadCollectionPerformanceLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("collection-performance.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "collection-performance library should be loadable"); //$NON-NLS-1$
		assertEquals("collection-performance", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"collection-performance library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadNumberCompareLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("number-compare.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "number-compare library should be loadable"); //$NON-NLS-1$
		assertEquals("number-compare", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 10,
				"number-compare library should have at least 10 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadStringEqualsLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("string-equals.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "string-equals library should be loadable"); //$NON-NLS-1$
		assertEquals("string-equals", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 2,
				"string-equals library should have at least 2 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadStringIsblankLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("string-isblank.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "string-isblank library should be loadable"); //$NON-NLS-1$
		assertEquals("string-isblank", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 4,
				"string-isblank library should have at least 4 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadArraysLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("arrays.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "arrays library should be loadable"); //$NON-NLS-1$
		assertEquals("arrays", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 1,
				"arrays library should have at least 1 rule, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadCollectionToarrayLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("collection-toarray.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "collection-toarray library should be loadable"); //$NON-NLS-1$
		assertEquals("collection-toarray", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 2,
				"collection-toarray library should have at least 2 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadProbableBugsLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("probable-bugs.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "probable-bugs library should be loadable"); //$NON-NLS-1$
		assertEquals("probable-bugs", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 4,
				"probable-bugs library should have at least 4 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadMiscLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("misc.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "misc library should be loadable"); //$NON-NLS-1$
		assertEquals("misc", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 2,
				"misc library should have at least 2 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadDeprecationsLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("deprecations.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "deprecations library should be loadable"); //$NON-NLS-1$
		assertEquals("deprecations", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 5,
				"deprecations library should have at least 5 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadClassfileApiLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("classfile-api.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "classfile-api library should be loadable"); //$NON-NLS-1$
		assertEquals("classfile-api", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 1,
				"classfile-api library should have at least 1 rule, found: " + hintFile.getRules().size()); //$NON-NLS-1$
	}

	@Test
	public void testLoadSerializationLibrary() throws Exception {
		HintFile hintFile = loadBundledLibrary("serialization.sandbox-hint"); //$NON-NLS-1$
		assertNotNull(hintFile, "serialization library should be loadable"); //$NON-NLS-1$
		assertEquals("serialization", hintFile.getId()); //$NON-NLS-1$
		assertTrue(hintFile.getRules().size() >= 2,
				"serialization library should have at least 2 rules, found: " + hintFile.getRules().size()); //$NON-NLS-1$
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
		assertEquals(17, loaded.size(),
				"Should load exactly 17 bundled libraries"); //$NON-NLS-1$

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
	public void testBundledLibraryCount() {
		String[] names = HintFileRegistry.getBundledLibraryNames();
		assertEquals(17, names.length,
				"Should have exactly 17 bundled libraries"); //$NON-NLS-1$
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
