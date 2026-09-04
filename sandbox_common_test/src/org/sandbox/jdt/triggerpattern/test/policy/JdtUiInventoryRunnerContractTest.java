/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Protects the Maven/JUnit authority boundary of the real JDT UI runner. */
public class JdtUiInventoryRunnerContractTest {

	private static final String RUNNER = "qa/upstream-jdt/run-jdt-ui-before-after.sh"; //$NON-NLS-1$
	private static final String TEST_SELECTOR =
			"JUnitXmlInventoryComparatorTest#configuredUpstreamEvidenceIsComparedByMaven"; //$NON-NLS-1$

	@Test
	public void runnerDelegatesJUnitXmlInventoryComparisonToMavenJUnit() throws Exception {
		String runner = Files.readString(repositoryRoot().resolve(RUNNER), StandardCharsets.UTF_8);

		assertTrue(runner.contains("compare_test_inventory()")); //$NON-NLS-1$
		assertTrue(runner.contains(TEST_SELECTOR));
		for (String property : List.of(
				"sandbox.junit.inventory.baseline", //$NON-NLS-1$
				"sandbox.junit.inventory.migrated", //$NON-NLS-1$
				"sandbox.junit.inventory.mapping", //$NON-NLS-1$
				"sandbox.junit.inventory.output")) { //$NON-NLS-1$
			assertEquals(1, occurrences(runner, property), property);
		}
		assertTrue(runner.contains("-pl sandbox_common_test")); //$NON-NLS-1$
		assertTrue(runner.contains("test-inventory-comparison-command.txt")); //$NON-NLS-1$
		assertTrue(runner.contains("test-inventory-comparison-exit-code.txt")); //$NON-NLS-1$
		assertFalse(runner.contains("compare_test_inventory.py")); //$NON-NLS-1$
		assertFalse(runner.contains("python3 \"$COMPARATOR\"")); //$NON-NLS-1$
	}

	private static int occurrences(String content, String token) {
		int count = 0;
		int offset = 0;
		while ((offset = content.indexOf(token, offset)) >= 0) {
			count++;
			offset += token.length();
		}
		return count;
	}

	private static Path repositoryRoot() throws IOException {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); //$NON-NLS-1$
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isRegularFile(current.resolve(RUNNER))) {
				return current;
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root"); //$NON-NLS-1$
	}
}
