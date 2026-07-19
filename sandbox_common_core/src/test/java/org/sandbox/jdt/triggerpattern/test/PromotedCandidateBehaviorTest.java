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
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.HintFileStore;

import com.google.gson.Gson;

/**
 * Executes permanent, binding-aware behavior fixtures generated for approved
 * mining candidates and verifies the bidirectional link between active bundled
 * rules and their promotion fixtures.
 */
class PromotedCandidateBehaviorTest extends StrictHintRuleTestSupport {

	private static final String FIXTURE_PREFIX =
			"org/sandbox/jdt/triggerpattern/promoted/"; //$NON-NLS-1$
	private static final String HINT_PREFIX =
			"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$
	private static final Pattern PROMOTED_CANDIDATE_MARKER = Pattern.compile(
			"(?m)^// Promoted mining candidate: ([0-9a-f]{64})\\s*$"); //$NON-NLS-1$
	private static final Set<String> ACTIVE_BUNDLED_HINTS =
			Set.of(HintFileStore.getBundledLibraryNames());
	private static final Gson GSON = new Gson();

	@TestFactory
	Stream<DynamicTest> promotedCandidatesRemainVerified() throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		return loadFixtureNames(classLoader).stream().map(name -> DynamicTest.dynamicTest(name,
				() -> verifyFixture(classLoader, name)));
	}

	@Test
	void everyPromotedRuleInAnActiveBundleHasAnIndexedFixture() throws Exception {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		Set<String> fixtureNames = Set.copyOf(loadFixtureNames(classLoader));

		for (String activeHint : ACTIVE_BUNDLED_HINTS) {
			String hintSource = readUtf8Resource(classLoader, HINT_PREFIX + activeHint);
			Matcher matcher = PROMOTED_CANDIDATE_MARKER.matcher(hintSource);
			while (matcher.find()) {
				String fixtureName = matcher.group(1) + ".json"; //$NON-NLS-1$
				assertTrue(fixtureNames.contains(fixtureName),
						"Active promoted rule has no indexed behavior fixture: " //$NON-NLS-1$
								+ activeHint + " -> " + fixtureName); //$NON-NLS-1$
			}
		}
	}

	private void verifyFixture(ClassLoader classLoader, String fixtureName) throws Exception {
		PromotionFixture fixture = loadFixture(classLoader, fixtureName);
		assertTrue(ACTIVE_BUNDLED_HINTS.contains(fixture.targetHintFile()),
				"Promoted fixture targets a disabled maintenance-only hint library: " //$NON-NLS-1$
						+ fixture.targetHintFile());

		Function<String, GuardFunction> previousResolver = GuardFunctionResolverHolder.getResolver();
		registerBuiltInGuards();
		try {
			HintFile proposedRule = parseHint(fixture.dslRule());
			assertFullReplacement(proposedRule, fixture.beforeExample(), fixture.afterExample(),
					fixture.sourceVersion());
			assertNoMatch(proposedRule, fixture.negativeExample(), fixture.sourceVersion());

			String bundledRule = readUtf8Resource(classLoader,
					HINT_PREFIX + fixture.targetHintFile());
			assertTrue(bundledRule.contains(fixture.dslRule().strip()),
					"Promoted DSL is missing from bundled hint file " + fixture.targetHintFile()); //$NON-NLS-1$
			assertTrue(bundledRule.contains("// Promoted mining candidate: " + fixture.candidateId()), //$NON-NLS-1$
					"Bundled hint file is missing candidate provenance marker for " //$NON-NLS-1$
							+ fixture.candidateId());
		} finally {
			GuardFunctionResolverHolder.setResolver(previousResolver);
		}
	}

	private List<String> loadFixtureNames(ClassLoader classLoader) throws Exception {
		var indexStream = classLoader.getResourceAsStream(FIXTURE_PREFIX + "index.txt"); //$NON-NLS-1$
		if (indexStream == null) {
			return List.of();
		}
		try (var reader = new BufferedReader(
				new InputStreamReader(indexStream, StandardCharsets.UTF_8))) {
			return reader.lines()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#")) //$NON-NLS-1$
					.toList();
		}
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
