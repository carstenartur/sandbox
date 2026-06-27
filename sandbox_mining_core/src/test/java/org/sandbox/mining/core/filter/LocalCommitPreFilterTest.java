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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.filter.LocalCommitPreFilter.FilterDecision;

/**
 * Tests for {@link LocalCommitPreFilter}.
 */
class LocalCommitPreFilterTest {

	@Test
	void testExtractChangedPathsFromUnifiedDiff() {
		String diff = "--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		List<String> paths = LocalCommitPreFilter.extractChangedPaths(diff);

		assertEquals(List.of("README.md"), paths); //$NON-NLS-1$
	}

	@Test
	void testKeepsJavaChanges() {
		String diff = "--- a/src/Foo.java\n+++ b/src/Foo.java\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		FilterDecision decision = new LocalCommitPreFilter().evaluate("Refactor Foo", diff); //$NON-NLS-1$

		assertTrue(decision.process());
		assertEquals("contains Java changes", decision.reason()); //$NON-NLS-1$
	}

	@Test
	void testSkipsDocumentationOnlyChanges() {
		String diff = "--- a/docs/mining.md\n+++ b/docs/mining.md\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		FilterDecision decision = new LocalCommitPreFilter().evaluate("Update docs", diff); //$NON-NLS-1$

		assertFalse(decision.process());
		assertEquals("documentation/metadata only", decision.reason()); //$NON-NLS-1$
	}

	@Test
	void testSkipsWorkflowOnlyChanges() {
		String diff = "--- a/.github/workflows/maven.yml\n+++ b/.github/workflows/maven.yml\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		FilterDecision decision = new LocalCommitPreFilter().evaluate("Update CI", diff); //$NON-NLS-1$

		assertFalse(decision.process());
	}

	@Test
	void testSkipsReleaseOnlyChangeWithoutJavaFiles() {
		String diff = "--- a/feature.xml\n+++ b/feature.xml\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		FilterDecision decision = new LocalCommitPreFilter().evaluate("Release version 1.3.1", diff); //$NON-NLS-1$

		assertFalse(decision.process());
		assertEquals("release/version-only change without Java files", decision.reason()); //$NON-NLS-1$
	}

	@Test
	void testKeepsUnknownNonJavaChangesConservatively() {
		String diff = "--- a/schema/rules.exsd\n+++ b/schema/rules.exsd\n@@ -1 +1 @@\n-a\n+b\n"; //$NON-NLS-1$

		FilterDecision decision = new LocalCommitPreFilter().evaluate("Improve rule schema", diff); //$NON-NLS-1$

		assertTrue(decision.process());
		assertEquals("non-Java change retained for review", decision.reason()); //$NON-NLS-1$
	}
}
