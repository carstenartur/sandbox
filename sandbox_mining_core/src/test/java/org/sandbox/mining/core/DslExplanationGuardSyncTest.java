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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;

/**
 * Integration test that ensures the guard table in {@code dsl-explanation.md}
 * stays in sync with the actual guards registered in
 * {@link BuiltInGuards#registerAll(Map)}.
 *
 * <p>This test prevents documentation drift by failing the build if:</p>
 * <ul>
 *   <li>A guard exists in the code but is missing from the documentation</li>
 *   <li>A guard is documented but does not exist in the code</li>
 * </ul>
 *
 * @since 1.3.6
 */
class DslExplanationGuardSyncTest {

	private static Set<String> codeGuards;
	private static Set<String> docGuards;

	@BeforeAll
	static void setUp() throws IOException {
		codeGuards = extractGuardsFromCode();
		docGuards = extractGuardsFromDocumentation();
	}

	/**
	 * Verifies that every guard registered in {@link BuiltInGuards#registerAll(Map)}
	 * is documented in the guard table in {@code dsl-explanation.md}.
	 */
	@Test
	void everyCodeGuardIsDocumented() {
		List<String> missing = new ArrayList<>();
		for (String guard : codeGuards) {
			if (!docGuards.contains(guard)) {
				missing.add(guard);
			}
		}
		assertTrue(missing.isEmpty(),
				"Guards registered in BuiltInGuards but MISSING from dsl-explanation.md: " + missing);
	}

	/**
	 * Verifies that every guard documented in {@code dsl-explanation.md}
	 * actually exists in {@link BuiltInGuards#registerAll(Map)}.
	 */
	@Test
	void everyDocumentedGuardExistsInCode() {
		List<String> phantom = new ArrayList<>();
		for (String guard : docGuards) {
			if (!codeGuards.contains(guard)) {
				phantom.add(guard);
			}
		}
		assertTrue(phantom.isEmpty(),
				"Guards documented in dsl-explanation.md but NOT registered in BuiltInGuards: " + phantom);
	}

	/**
	 * Sanity check: we should find a reasonable number of guards in both sources.
	 */
	@Test
	void guardCountsAreSane() {
		assertFalse(codeGuards.isEmpty(), "No guards found in BuiltInGuards.registerAll()");
		assertFalse(docGuards.isEmpty(), "No guards found in dsl-explanation.md");
		assertTrue(codeGuards.size() >= 10,
				"Expected at least 10 guards in code, found: " + codeGuards.size());
		assertTrue(docGuards.size() >= 10,
				"Expected at least 10 guards in documentation, found: " + docGuards.size());
	}

	/**
	 * Extracts guard names from {@link BuiltInGuards#registerAll(Map)} by actually
	 * calling it and collecting the keys.
	 */
	private static Set<String> extractGuardsFromCode() {
		Map<String, GuardFunction> guards = new HashMap<>();
		BuiltInGuards.registerAll(guards);
		return new LinkedHashSet<>(guards.keySet());
	}

	/**
	 * Extracts guard names from the guard table in {@code dsl-explanation.md}.
	 *
	 * <p>Parses rows in the "Available Guards" table only (stops before the next
	 * section heading). Each row starts with {@code | `guardName(} and we extract
	 * the guard name from it.</p>
	 */
	private static Set<String> extractGuardsFromDocumentation() throws IOException {
		String content = loadDslExplanation();
		Set<String> guards = new LinkedHashSet<>();

		// Match table rows like: | `guardName(...)` | description |
		// or: | `guardName` | description | (for guards without parens like "otherwise")
		Pattern tableRow = Pattern.compile(
				"^\\|\\s*`(\\w+)(?:\\(|`)"); //$NON-NLS-1$

		boolean inGuardTable = false;
		for (String line : content.split("\n")) { //$NON-NLS-1$
			String trimmed = line.trim();

			// Start parsing when we find the "Available Guards" section
			if (trimmed.contains("### Available Guards")) { //$NON-NLS-1$
				inGuardTable = true;
				continue;
			}

			// Stop parsing at the next section heading
			if (inGuardTable && trimmed.startsWith("###")) { //$NON-NLS-1$
				break;
			}

			if (!inGuardTable) {
				continue;
			}

			Matcher m = tableRow.matcher(trimmed);
			if (m.find()) {
				String guardName = m.group(1);
				// Skip the table header row
				if (!"Guard".equals(guardName)) { //$NON-NLS-1$
					guards.add(guardName);
				}
			}
		}
		return guards;
	}

	private static String loadDslExplanation() throws IOException {
		try (InputStream is = DslExplanationGuardSyncTest.class
				.getResourceAsStream("/dsl-explanation.md")) { //$NON-NLS-1$
			if (is == null) {
				fail("dsl-explanation.md not found on classpath");
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
