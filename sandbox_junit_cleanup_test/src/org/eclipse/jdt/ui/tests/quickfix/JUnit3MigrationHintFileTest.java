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
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Tests for the JUnit 3 migration {@code .sandbox-hint} file bundled with the
 * JUnit cleanup plugin.
 *
 * <p>Validates that the hint file can be parsed and contains valid
 * JUnit 3→5 migration rules for conventionally-named methods.</p>
 *
 * <p>This hint file is intended to eventually replace the Java-based
 * JUnit 3→5 migration implementation with a declarative DSL approach.</p>
 *
 * @since 1.3.3
 */
public class JUnit3MigrationHintFileTest {

	private final HintFileParser parser = new HintFileParser();

	@Test
	public void testLoadJUnit3MigrationLibrary() throws Exception {
		HintFile hintFile = loadHintFile();
		assertNotNull(hintFile, "junit3-migration library should be loadable"); //$NON-NLS-1$
		assertEquals("junit3-migration", hintFile.getId()); //$NON-NLS-1$
	}

	@Test
	public void testJUnit3MigrationRuleCount() throws Exception {
		HintFile hintFile = loadHintFile();
		assertEquals(5, hintFile.getRules().size(),
				"junit3-migration library should have 5 rules (test*, setUp, tearDown, setUpBeforeClass, tearDownAfterClass)"); //$NON-NLS-1$
	}

	@Test
	public void testAllRulesAreMethodDeclarations() throws Exception {
		HintFile hintFile = loadHintFile();
		for (TransformationRule rule : hintFile.getRules()) {
			assertEquals(PatternKind.METHOD_DECLARATION,
					rule.sourcePattern().getKind(),
					"All junit3-migration rules should be METHOD_DECLARATION kind"); //$NON-NLS-1$
		}
	}

	@Test
	public void testAllRulesHaveGuards() throws Exception {
		HintFile hintFile = loadHintFile();
		for (TransformationRule rule : hintFile.getRules()) {
			assertNotNull(rule.sourceGuard(),
					"All junit3-migration rules should have guards (methodNameMatches + static check + enclosingClassExtends)"); //$NON-NLS-1$
		}
	}

	@Test
	public void testJUnit3MigrationMetadata() throws Exception {
		HintFile hintFile = loadHintFile();
		assertEquals(Severity.WARNING, hintFile.getSeverity());
		assertTrue(hintFile.getMinJavaVersion() > 0,
				"junit3-migration library should have a minimum Java version"); //$NON-NLS-1$
		assertTrue(hintFile.getTags().contains("junit3"), //$NON-NLS-1$
				"junit3-migration library should have the junit3 tag"); //$NON-NLS-1$
	}

	/**
	 * Loads the JUnit 3 migration hint file from the plugin's classpath.
	 */
	private HintFile loadHintFile() throws Exception {
		String resourcePath = "org/sandbox/jdt/internal/corext/fix/hints/junit3-migration.sandbox-hint"; //$NON-NLS-1$
		InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
		assertNotNull(is, "junit3-migration.sandbox-hint resource should be found"); //$NON-NLS-1$

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
