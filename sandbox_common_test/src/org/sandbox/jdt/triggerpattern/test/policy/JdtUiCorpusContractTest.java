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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Mode;
import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Verification;

/** Repository contract and synthetic negative fixtures; not upstream execution evidence. */
public class JdtUiCorpusContractTest {

	private static final String PROJECT = "org.eclipse.jdt.ui.tests"; //$NON-NLS-1$
	private static final String PIN = "c922f757b27b7e2b6215db383cec5f8aafd13227"; //$NON-NLS-1$
	private static final String PREFIX = PROJECT + "/ui/org/eclipse/jdt/ui/tests/"; //$NON-NLS-1$
	private static final String QUARANTINED = PREFIX + "quickfix/ConvertLoopOperationTest.java"; //$NON-NLS-1$
	private static final String REASON = "PARAMETERIZED_FIELD_INJECTION"; //$NON-NLS-1$
	private static final Set<String> EXPECTED_FILES = Set.of(
			PREFIX + "core/rules/JUnitSourceSetup.java", //$NON-NLS-1$
			PREFIX + "core/rules/LeakTestSetup.java", //$NON-NLS-1$
			PREFIX + "search/FileAdapterTest.java", //$NON-NLS-1$
			PREFIX + "search/SearchLeakTestWrapper.java", QUARANTINED); //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void corpusIdentityAndDifficultCasesStayPinned() throws Exception {
		JsonObject contract = contract();
		Map<String, String> pins = properties(qa().resolve("pins.env")); //$NON-NLS-1$
		assertEquals("https://github.com/eclipse-jdt/eclipse.jdt.ui.git", contract.get("repository").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("R4_40", contract.get("ref").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(PIN, contract.get("commit").getAsString()); //$NON-NLS-1$
		assertEquals(PIN, pins.get("PIN_JDT_UI_COMMIT")); //$NON-NLS-1$
		assertEquals(contract.get("repository").getAsString(), pins.get("PIN_JDT_UI_REPOSITORY")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(contract.get("ref").getAsString(), pins.get("PIN_JDT_UI_REF")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(PROJECT, contract.get("project").getAsString()); //$NON-NLS-1$
		JsonObject required = contract.getAsJsonObject("requiredFiles"); //$NON-NLS-1$
		assertEquals(EXPECTED_FILES, required.keySet());
		assertTrue(contract.get("minimumChangedJavaFiles").getAsInt() >= 4); //$NON-NLS-1$
		JsonObject difficult = required.getAsJsonObject(QUARANTINED);
		assertEquals(new JsonPrimitive(true), difficult.get("strictUnchanged")); //$NON-NLS-1$
		assertEquals(REASON, difficult.get("strictReasonCode").getAsString()); //$NON-NLS-1$
		assertFalse(markers(difficult, "bestEffortMustContain").isEmpty()); //$NON-NLS-1$
	}

	@ParameterizedTest
	@EnumSource(Mode.class)
	public void profilesKeepJUnitFourAndTheChosenPolicyExplicit(Mode mode) throws Exception {
		String filename = mode == Mode.STRICT ? "junit4-to-jupiter.properties" //$NON-NLS-1$
				: "junit4-to-jupiter-best-effort.properties"; //$NON-NLS-1$
		Map<String, String> profile = properties(qa().resolve(filename));
		assertEquals("true", profile.get("cleanup.junitcleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("false", profile.get("cleanup.junit3cleanup")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(Boolean.toString(mode == Mode.BEST_EFFORT), profile.get("cleanup.junitcleanup_best_effort")); //$NON-NLS-1$
		assertEquals("true", profile.get("cleanup.junitcleanup_4_parameterized")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@EnumSource(Mode.class)
	public void actualContractIsExercisedByTheJavaVerifier(Mode mode) throws Exception {
		Fixture fixture = populate(mode);
		Verification result = verify(fixture, mode);
		assertEquals("PASS", result.result()); //$NON-NLS-1$
		assertEquals(mode.value(), result.mode());
		assertEquals(mode == Mode.STRICT ? 4 : 5, result.changedJavaFiles());
		assertEquals(mode == Mode.STRICT ? List.of(QUARANTINED) : List.of(),
				result.strictlyQuarantinedCorpusFiles());
		assertEquals(mode == Mode.BEST_EFFORT ? List.of(REASON) : List.of(), result.requiredReasonCodes());
	}

	@Test
	public void unreportedQuarantineModificationIsRejected() throws Exception {
		Fixture fixture = populate(Mode.STRICT);
		Path current = fixture.repository().resolve(QUARANTINED);
		Files.writeString(current, Files.readString(current, StandardCharsets.UTF_8)
				+ "\n// accidental strict partial migration\n", StandardCharsets.UTF_8); //$NON-NLS-1$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> verify(fixture, Mode.STRICT));
		assertTrue(failure.getMessage().contains("byte-for-byte")); //$NON-NLS-1$
	}

	@Test
	public void runnerRetainsThePinnedReactorAndInventoryBoundary() throws Exception {
		String runner = Files.readString(qa().resolve("run-jdt-ui-before-after.sh"), StandardCharsets.UTF_8); //$NON-NLS-1$
		for (String marker : List.of(PROJECT, "org.eclipse.jdt.bcoview", "REACTOR_PROJECTS", //$NON-NLS-1$ //$NON-NLS-2$
				"verify_reactor_bcoview_runtime", "jdt-ui-junit4-corpus.json", //$NON-NLS-1$ //$NON-NLS-2$
				"JUnitXmlInventoryComparatorTest#configuredUpstreamEvidenceIsComparedByMaven", //$NON-NLS-1$
				"sandbox.junit.inventory.baseline", "sandbox.junit.inventory.migrated", //$NON-NLS-1$ //$NON-NLS-2$
				"sandbox.junit.inventory.mapping", "sandbox.junit.inventory.output", "strict|best-effort")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(runner.contains(marker), marker);
		}
		assertFalse(runner.contains("compare_test_inventory.py")); //$NON-NLS-1$
	}

	private Fixture populate(Mode mode) throws Exception {
		Path repository = Files.createDirectories(temporaryDirectory.resolve("repository")); //$NON-NLS-1$
		Path baseline = Files.createDirectories(temporaryDirectory.resolve("baseline")); //$NON-NLS-1$
		JsonObject required = contract().getAsJsonObject("requiredFiles"); //$NON-NLS-1$
		List<String> changed = new ArrayList<>();
		for (String relative : required.keySet().stream().sorted().toList()) {
			JsonObject rules = required.getAsJsonObject(relative);
			String before = String.join("\n", markers(rules, "baselineMustContain")) + '\n'; //$NON-NLS-1$ //$NON-NLS-2$
			write(baseline.resolve(relative), before);
			if (mode == Mode.STRICT && new JsonPrimitive(true).equals(rules.get("strictUnchanged"))) { //$NON-NLS-1$
				write(repository.resolve(relative), before);
			} else {
				List<String> after = new ArrayList<>(markers(rules, "migratedMustContain")); //$NON-NLS-1$
				if (mode == Mode.BEST_EFFORT) {
					after.addAll(markers(rules, "bestEffortMustContain")); //$NON-NLS-1$
				}
				write(repository.resolve(relative), String.join("\n", after) + '\n'); //$NON-NLS-1$
				changed.add(relative);
			}
		}
		Path changedFiles = temporaryDirectory.resolve("changed-files.txt"); //$NON-NLS-1$
		write(changedFiles, String.join("\n", changed) + '\n'); //$NON-NLS-1$
		Path check = temporaryDirectory.resolve("check.json"); //$NON-NLS-1$
		Path apply = temporaryDirectory.resolve("apply.json"); //$NON-NLS-1$
		write(check, report("check", changed, mode).toString()); //$NON-NLS-1$
		write(apply, report("apply", changed, mode).toString()); //$NON-NLS-1$
		return new Fixture(repository, baseline, changedFiles, check, apply);
	}

	private static JsonObject report(String mode, List<String> changed, Mode policy) {
		JsonObject report = new JsonObject();
		report.addProperty("mode", mode); //$NON-NLS-1$
		report.addProperty("errorCount", 0); //$NON-NLS-1$
		report.add("errors", new JsonArray()); //$NON-NLS-1$
		JsonArray paths = new JsonArray();
		changed.forEach(path -> paths.add(path.substring(PROJECT.length() + 1)));
		report.add("changedFiles", paths); //$NON-NLS-1$
		JsonObject diagnostics = new JsonObject();
		if (policy == Mode.BEST_EFFORT) {
			diagnostics.addProperty("manualCompletionRequired", true); //$NON-NLS-1$
			diagnostics.addProperty("reasonCode", REASON); //$NON-NLS-1$
		}
		report.add("planningDiagnostics", diagnostics); //$NON-NLS-1$
		return report;
	}

	private static List<String> markers(JsonObject rules, String property) {
		List<String> result = new ArrayList<>();
		if (rules.has(property)) {
			for (JsonElement marker : rules.getAsJsonArray(property)) {
				assertTrue(marker.isJsonPrimitive() && marker.getAsJsonPrimitive().isString(), property);
				assertFalse(marker.getAsString().isEmpty(), property);
				result.add(marker.getAsString());
			}
		}
		return result;
	}

	private static Verification verify(Fixture fixture, Mode mode) throws Exception {
		return JdtUiCorpusEvidenceVerifier.verify(fixture.repository(), fixture.baseline(),
				qa().resolve("jdt-ui-junit4-corpus.json"), mode, fixture.changed(), fixture.check(), fixture.apply()); //$NON-NLS-1$
	}

	private static JsonObject contract() throws IOException {
		return JsonParser.parseString(Files.readString(qa().resolve("jdt-ui-junit4-corpus.json"), //$NON-NLS-1$
				StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static Map<String, String> properties(Path path) throws IOException {
		Map<String, String> result = new HashMap<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			int separator = line.indexOf('=');
			assertTrue(separator > 0 && separator < line.length() - 1, () -> "Invalid property: " + line); //$NON-NLS-1$
			String key = line.substring(0, separator);
			assertNull(result.put(key, line.substring(separator + 1)), () -> "Duplicate property: " + key); //$NON-NLS-1$
		}
		return result;
	}

	private static Path qa() throws IOException {
		return JdtUiCorpusRunnerContractTest.repositoryRoot().resolve("qa/upstream-jdt"); //$NON-NLS-1$
	}

	private static void write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private record Fixture(Path repository, Path baseline, Path changed, Path check, Path apply) {
	}
}
