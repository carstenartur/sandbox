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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.sandbox.jdt.triggerpattern.api.HintFile;

import com.google.gson.Gson;

/**
 * Executes permanent, binding-aware behavior fixtures generated for approved
 * mining candidates. The suite is empty until the first candidate is promoted.
 */
class PromotedCandidateBehaviorTest extends StrictHintRuleTestSupport {

	private static final String FIXTURE_PREFIX =
			"org/sandbox/jdt/triggerpattern/promoted/"; //$NON-NLS-1$
	private static final String HINT_PREFIX =
			"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$
	private static final Gson GSON = new Gson();

	@TestFactory
	Stream<DynamicTest> promotedCandidatesRemainVerified() throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		var indexStream = classLoader.getResourceAsStream(FIXTURE_PREFIX + "index.txt"); //$NON-NLS-1$
		if (indexStream == null) {
			return Stream.empty();
		}
		List<String> fixtures;
		try (var reader = new BufferedReader(
				new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
			fixtures = reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")) //$NON-NLS-1$
					.toList();
		}
		return fixtures.stream().map(name -> DynamicTest.dynamicTest(name,
				() -> verifyFixture(classLoader, name)));
	}

	private void verifyFixture(ClassLoader classLoader, String fixtureName) throws Exception {
		PromotionFixture fixture = loadFixture(classLoader, fixtureName);
		registerBuiltInGuards();
		HintFile proposedRule = parseHint(fixture.dslRule());
		assertFullReplacement(proposedRule, fixture.beforeExample(), fixture.afterExample(),
				fixture.sourceVersion());
		assertNoMatch(proposedRule, fixture.negativeExample(), fixture.sourceVersion());

		String bundledRule = readUtf8Resource(classLoader,
				HINT_PREFIX + fixture.targetHintFile());
		assertTrue(bundledRule.contains(fixture.dslRule().strip()),
				"Promoted DSL is missing from bundled hint file " + fixture.targetHintFile()); //$NON-NLS-1$
	}

	private PromotionFixture loadFixture(ClassLoader classLoader, String fixtureName)
			throws Exception {
		String json = readUtf8Resource(classLoader, FIXTURE_PREFIX + fixtureName);
		PromotionFixture fixture = GSON.fromJson(json, PromotionFixture.class);
		assertNotNull(fixture, "Empty promotion fixture: " + fixtureName); //$NON-NLS-1$
		return fixture;
	}

	private record PromotionFixture(
			String candidateId,
			int revision,
			String sourceRepo,
			String sourceCommit,
			String targetHintFile,
			String sourceVersion,
			String dslRule,
			String beforeExample,
			String afterExample,
			String negativeExample,
			String ruleFingerprint,
			String behaviorFingerprint,
			String verifierVersion,
			String approvedBy) {
	}
}
