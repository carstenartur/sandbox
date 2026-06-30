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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;

/**
 * Behavior tests for active bundled {@code .sandbox-hint} libraries.
 *
 * <p>When a mined candidate is promoted into a bundled hint file, a test like
 * these should be added at the same time. The test should contain at least one
 * positive before/after full-source example and one negative example for known
 * risky cases.</p>
 */
class BundledHintRuleBehaviorTest extends HintRuleTestSupport {

	@BeforeEach
	void setUp() {
		registerBuiltInGuards();
	}

	@Test
	void performanceKeepsSafeBooleanValueOfReplacement() throws Exception {
		HintFile hintFile = loadBundledHint("performance.sandbox-hint"); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.lang.Boolean.valueOf(true); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.lang.Boolean.TRUE; } }"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.lang.Boolean.valueOf(false); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.lang.Boolean.FALSE; } }"); //$NON-NLS-1$
	}

	@Test
	void performanceDoesNotRewriteNewStringConstructorBlindly() throws Exception {
		HintFile hintFile = loadBundledHint("performance.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { String m(char[] chars) { return new java.lang.String(chars); } }"); //$NON-NLS-1$
	}

	@Test
	void performanceDoesNotSuggestNonExistingIntegerZeroConstant() throws Exception {
		HintFile hintFile = loadBundledHint("performance.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m() { return java.lang.Integer.valueOf(0); } }"); //$NON-NLS-1$
	}

	@Test
	void collectionsKeepsSafeEmptyCollectionFactoryReplacements() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.util.Collections.emptyList(); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.util.List.of(); } }"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.util.Collections.emptyMap(); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.util.Map.of(); } }"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.util.Collections.emptySet(); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.util.Set.of(); } }"); //$NON-NLS-1$
	}

	@Test
	void collectionsUsesStableRuleIdsForEmptyCollectionFactories() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertEquals("collections.empty-list.to-list-of", //$NON-NLS-1$
				process(hintFile, "class Test { Object m() { return java.util.Collections.emptyList(); } }") //$NON-NLS-1$
						.get(0).rule().getRuleId());
		assertEquals("collections.empty-map.to-map-of", //$NON-NLS-1$
				process(hintFile, "class Test { Object m() { return java.util.Collections.emptyMap(); } }") //$NON-NLS-1$
						.get(0).rule().getRuleId());
		assertEquals("collections.empty-set.to-set-of", //$NON-NLS-1$
				process(hintFile, "class Test { Object m() { return java.util.Collections.emptySet(); } }") //$NON-NLS-1$
						.get(0).rule().getRuleId());
	}

	@Test
	void collectionsRespectsJava9SourceVersionGuard() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m() { return java.util.Collections.emptyList(); } }", //$NON-NLS-1$
				"1.8"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { Object m() { return java.util.Collections.emptyList(); } }", //$NON-NLS-1$
				"class Test { Object m() { return java.util.List.of(); } }", //$NON-NLS-1$
				"21"); //$NON-NLS-1$
	}

	@Test
	void collectionsDoesNotRewriteDiamondOnlyConstructorNoise() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m() { return new java.util.ArrayList<String>(); } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { Object m() { return new java.util.ArrayList<String>(4); } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { Object m() { return new java.util.HashSet<String>(); } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { Object m() { return new java.util.Hashtable<String, String>(); } }"); //$NON-NLS-1$
	}

	@Test
	void collectionsDoesNotRewriteSingletonCollectionsWithDifferentNullSemantics() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m() { return java.util.Collections.singletonList(null); } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { Object m() { return java.util.Collections.singletonMap(\"k\", null); } }"); //$NON-NLS-1$
	}

	@Test
	void collectionsDoesNotRewriteVectorMigrationWithoutPromotionTests() throws Exception {
		HintFile hintFile = loadBundledHint("collections.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m() { return new java.util.Vector(); } }"); //$NON-NLS-1$
	}

	@Test
	void modernizeJava9KeepsRiskyListFactoryMigrationHintOnly() throws Exception {
		HintFile hintFile = loadBundledHint("modernize-java9.sandbox-hint"); //$NON-NLS-1$

		assertHintOnlyMatch(hintFile,
				"class Test { Object m() { return java.util.Collections.unmodifiableList(java.util.Arrays.asList(\"a\")); } }"); //$NON-NLS-1$
	}

	@Test
	void modernizeJava9RewritesMapGetOrDefaultNullSafely() throws Exception {
		HintFile hintFile = loadBundledHint("modernize-java9.sandbox-hint"); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"class Test { Object m(java.util.Map<String, String> map, String key) { return map.getOrDefault(key, null); } }", //$NON-NLS-1$
				"class Test { Object m(java.util.Map<String, String> map, String key) { return map.get(key); } }"); //$NON-NLS-1$
	}

	@Test
	void modernizeJava9DoesNotRewriteStreamToListWhenMutabilityNeedsReview() throws Exception {
		HintFile hintFile = loadBundledHint("modernize-java9.sandbox-hint"); //$NON-NLS-1$

		assertHintOnlyMatch(hintFile,
				"class Test { Object m(java.util.List<String> items) { return items.stream().collect(java.util.stream.Collectors.toList()); } }"); //$NON-NLS-1$
	}

	@Test
	void deprecationsUseValueOfForWrapperConstructors() throws Exception {
		HintFile hintFile = loadBundledHint("deprecations.sandbox-hint"); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"class Test { Object m(String value) { return new java.lang.Boolean(value); } }", //$NON-NLS-1$
				"class Test { Object m(String value) { return java.lang.Boolean.valueOf(value); } }"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { Object m(String value) { return new java.lang.Integer(value); } }", //$NON-NLS-1$
				"class Test { Object m(String value) { return java.lang.Integer.valueOf(value); } }"); //$NON-NLS-1$
	}

	@Test
	void collectionPerformanceDoesNotRewriteKeySetRemoveBecauseReturnValueDiffers() throws Exception {
		HintFile hintFile = loadBundledHint("collection-performance.sandbox-hint"); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"class Test { Object m(java.util.Map<String, String> map, String key) { return map.keySet().remove(key); } }"); //$NON-NLS-1$
		assertNoMatch(hintFile,
				"class Test { void m(java.util.Map<String, String> map, String key) { map.keySet().remove(key); } }"); //$NON-NLS-1$
	}

	@Test
	void stringEqualsDoesNotDuplicateReflexiveEqualsProbableBugRule() throws Exception {
		HintFile stringEquals = loadBundledHint("string-equals.sandbox-hint"); //$NON-NLS-1$
		HintFile probableBugs = loadBundledHint("probable-bugs.sandbox-hint"); //$NON-NLS-1$
		String code = "class Test { boolean m(String value) { return value.equals(value); } }"; //$NON-NLS-1$

		assertNoMatch(stringEquals, code);
		assertHintOnlyMatch(probableBugs, code);
	}

	@Test
	void modernizeJava11KeepsOnlyNarrowLiteralRules() throws Exception {
		HintFile hintFile = loadBundledHint("modernize-java11.sandbox-hint"); //$NON-NLS-1$

		assertTrue(hintFile.getRules().size() >= 5,
				"Java 11 library should keep only narrow tested literal rules"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { boolean m() { return \"\".isBlank(); } }", //$NON-NLS-1$
				"class Test { boolean m() { return true; } }"); //$NON-NLS-1$
		assertFullReplacement(hintFile,
				"class Test { boolean m() { return \"abc\".isBlank(); } }", //$NON-NLS-1$
				"class Test { boolean m() { return false; } }"); //$NON-NLS-1$
	}
}
