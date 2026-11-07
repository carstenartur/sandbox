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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/** Safety checks for optional bundled rules with observable semantic differences. */
class BundledLibraryRewriteSafetyTest extends HintRuleTestSupport {

	@BeforeEach
	void setUp() {
		registerBuiltInGuards();
	}

	@Test
	void immutableCollectionReplacementsNeedUseSiteAnalysis() throws Exception {
		HintFile hints= loadBundledHint("collections-immutable.sandbox-hint"); //$NON-NLS-1$
		assertTrue(hints.getRules().stream().allMatch(TransformationRule::isHintOnly));
		assertDiagnosticOnly(hints, "class Test { Object f() { return java.util.Collections.singletonList(null); } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { boolean f() { return java.util.Collections.singletonList(\"x\").contains(null); } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { boolean f() { return java.util.Collections.emptyList().contains(null); } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { Object f() { return java.util.Collections.singletonMap(null, \"v\"); } }"); //$NON-NLS-1$
	}

	@Test
	void emptyCollectionFactoriesHaveDifferentNullQueries() throws Exception {
		assertFalse(Collections.emptyList().contains(null));
		assertThrows(NullPointerException.class, () -> assertFalse(List.of().contains(null)));
		assertNull(Collections.emptyMap().get(null));
		assertThrows(NullPointerException.class, () -> assertNull(Map.of().get(null)));
		HintFile hints= loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { boolean f() { return java.util.Collections.emptyList().contains(null); } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { Object f() { return java.util.Collections.emptyMap().get(null); } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { boolean f() { return java.util.Collections.emptySet().contains(null); } }"); //$NON-NLS-1$
	}

	@Test
	void stringBufferLocalsCanEscapeAndTheirTypesMustStillCompile() throws Exception {
		HintFile hints= loadBundledHint("stringbuffer-to-stringbuilder.sandbox-hint"); //$NON-NLS-1$
		assertTrue(hints.getRules().stream().allMatch(TransformationRule::isHintOnly));
		assertDiagnosticOnly(hints, "class Test { Object f() { StringBuffer b = new StringBuffer(); return b; } }"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, "class Test { Object f() { var b = new StringBuffer(20); return b; } }"); //$NON-NLS-1$
	}

	@Test
	void fileToUrlMigrationRequiresReview() throws Exception {
		HintFile hints= loadBundledHint("deprecations.sandbox-hint"); //$NON-NLS-1$
		assertDiagnosticOnly(hints, """
				import java.io.File;
				import java.net.MalformedURLException;
				class Test {
				    Object legacy(File file) throws MalformedURLException { return file.toURL(); }
				    Object current(File file) throws MalformedURLException { return file.toURI().toURL(); }
				}
				"""); //$NON-NLS-1$
		assertDiagnosticOnly(hints, """
				import java.io.File;
				import java.net.MalformedURLException;
				class Test {
				    Object legacy() throws MalformedURLException { return new File("a #b").toURL(); }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void fileToUrlHintIgnoresOtherAndUnresolvedMethods() throws Exception {
		HintFile hints= loadBundledHint("deprecations.sandbox-hint"); //$NON-NLS-1$
		assertNoMatch(hints, """
				class Test {
				    static class File {
				        @Deprecated Object toURL() { return null; }
				    }
				    static class CustomFile extends java.io.File {
				        CustomFile() { super("."); }
				        @Override @Deprecated public java.net.URL toURL() { return null; }
				    }
				    Object custom(File file) { return file.toURL(); }
				    Object overridden(CustomFile file) { return file.toURL(); }
				    Object unresolved(MissingFile file) { return file.toURL(); }
				}
				"""); //$NON-NLS-1$
	}

	private void assertDiagnosticOnly(HintFile hints, String source) {
		var results= process(hints, source);
		assertEquals(1, results.size(), "Expected one review hint: " + source); //$NON-NLS-1$
		assertTrue(results.stream().noneMatch(TransformationResult::hasRewrite), source);
	}
}
