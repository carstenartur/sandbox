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
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Mode;
import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Request;
import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Result;

/**
 * Tests and property-driven retained-workspace entry point for the pinned JDT
 * UI corpus evidence verifier.
 *
 * @since 1.3.5
 */
public class JdtUiCorpusEvidenceVerifierTest {

	private static final String REPOSITORY_PROPERTY = "sandbox.junit.corpus.repository"; //$NON-NLS-1$
	private static final String BASELINE_PROPERTY = "sandbox.junit.corpus.baselineSources"; //$NON-NLS-1$
	private static final String CONTRACT_PROPERTY = "sandbox.junit.corpus.contract"; //$NON-NLS-1$
	private static final String MODE_PROPERTY = "sandbox.junit.corpus.mode"; //$NON-NLS-1$
	private static final String CHANGED_PROPERTY = "sandbox.junit.corpus.changedFiles"; //$NON-NLS-1$
	private static final String CHECK_PROPERTY = "sandbox.junit.corpus.checkReport"; //$NON-NLS-1$
	private static final String APPLY_PROPERTY = "sandbox.junit.corpus.applyReport"; //$NON-NLS-1$
	private static final String OUTPUT_PROPERTY = "sandbox.junit.corpus.output"; //$NON-NLS-1$

	private static final String PROJECT = "sample.project"; //$NON-NLS-1$
	private static final String CHANGED = PROJECT + "/src/Changed.java"; //$NON-NLS-1$
	private static final String QUARANTINED = PROJECT + "/src/Quarantined.java"; //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void strictEvidenceRequiresExactChangedSetAndByteExactQuarantine() throws Exception {
		Fixture fixture = fixture(Mode.STRICT, true, true);

		Result result = JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals("PASS", result.result()); //$NON-NLS-1$
		assertEquals(1, result.changedJavaFiles());
		assertEquals(java.util.List.of(CHANGED), result.verifiedChangedCorpusFiles());
		assertEquals(java.util.List.of(QUARANTINED), result.strictlyQuarantinedCorpusFiles());
	}

	@Test
	public void strictQuarantineMutationFailsClosed() throws Exception {
		Fixture fixture = fixture(Mode.STRICT, true, true);
		write(fixture.repository().resolve(QUARANTINED), "legacy-parameterized\nchanged\n"); //$NON-NLS-1$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));

		assertTrue(failure.getMessage().contains("byte-for-byte")); //$NON-NLS-1$
	}

	@Test
	public void checkAndApplyMustDescribeTheSameGitPatch() throws Exception {
		Fixture fixture = fixture(Mode.STRICT, true, true);
		write(fixture.applyReport(), report("apply", java.util.List.of("src/Other.java"), false, true)); //$NON-NLS-1$ //$NON-NLS-2$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));

		assertTrue(failure.getMessage().contains("different changed-file sets")); //$NON-NLS-1$
	}

	@Test
	public void bestEffortEvidenceRequiresReasonAndManualCompletionFlag() throws Exception {
		Fixture fixture = fixture(Mode.BEST_EFFORT, true, true);

		Result result = JdtUiCorpusEvidenceVerifier.verify(fixture.request());

		assertEquals(java.util.List.of("PARAMETERIZED_FIELD_INJECTION"), result.requiredReasonCodes()); //$NON-NLS-1$
		assertTrue(result.strictlyQuarantinedCorpusFiles().isEmpty());
	}

	@Test
	public void bestEffortWithoutManualCompletionEvidenceFailsClosed() throws Exception {
		Fixture fixture = fixture(Mode.BEST_EFFORT, true, false);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> JdtUiCorpusEvidenceVerifier.verify(fixture.request()));

		assertTrue(failure.getMessage().contains("manual completion")); //$NON-NLS-1$
	}

	@Test
	public void configuredRetainedWorkspaceEvidenceIsVerifiedByMaven() throws Exception {
		Map<String, String> configured = new LinkedHashMap<>();
		configured.put(REPOSITORY_PROPERTY, System.getProperty(REPOSITORY_PROPERTY, "")); //$NON-NLS-1$
		configured.put(BASELINE_PROPERTY, System.getProperty(BASELINE_PROPERTY, "")); //$NON-NLS-1$
		configured.put(CONTRACT_PROPERTY, System.getProperty(CONTRACT_PROPERTY, "")); //$NON-NLS-1$
		configured.put(MODE_PROPERTY, System.getProperty(MODE_PROPERTY, "")); //$NON-NLS-1$
		configured.put(CHANGED_PROPERTY, System.getProperty(CHANGED_PROPERTY, "")); //$NON-NLS-1$
		configured.put(CHECK_PROPERTY, System.getProperty(CHECK_PROPERTY, "")); //$NON-NLS-1$
		configured.put(APPLY_PROPERTY, System.getProperty(APPLY_PROPERTY, "")); //$NON-NLS-1$
		configured.put(OUTPUT_PROPERTY, System.getProperty(OUTPUT_PROPERTY, "")); //$NON-NLS-1$
		long supplied = configured.values().stream().filter(value -> !value.isBlank()).count();
		if (supplied == 0) {
			return;
		}
		assertEquals(configured.size(), supplied,
				"The retained-workspace invocation must provide every corpus evidence path and mode"); //$NON-NLS-1$

		Request request = new Request(
				Path.of(configured.get(REPOSITORY_PROPERTY)),
				Path.of(configured.get(BASELINE_PROPERTY)),
				Path.of(configured.get(CONTRACT_PROPERTY)),
				Mode.parse(configured.get(MODE_PROPERTY)),
				Path.of(configured.get(CHANGED_PROPERTY)),
				Path.of(configured.get(CHECK_PROPERTY)),
				Path.of(configured.get(APPLY_PROPERTY)));
		Result result = JdtUiCorpusEvidenceVerifier.verify(request);
		Path output = Path.of(configured.get(OUTPUT_PROPERTY));
		result.write(output);

		assertEquals("PASS", result.result(), () -> "JDT UI corpus evidence differs; see " //$NON-NLS-1$ //$NON-NLS-2$
				+ output.toAbsolutePath());
	}

	private Fixture fixture(Mode mode, boolean includeReason, boolean manualCompletion) throws IOException {
		Path repository = temporaryDirectory.resolve("repository-" + mode.value()); //$NON-NLS-1$
		Path baseline = temporaryDirectory.resolve("baseline-" + mode.value()); //$NON-NLS-1$
		Path contract = temporaryDirectory.resolve("contract-" + mode.value() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Path changed = temporaryDirectory.resolve("changed-" + mode.value() + ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
		Path check = temporaryDirectory.resolve("check-" + mode.value() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$
		Path apply = temporaryDirectory.resolve("apply-" + mode.value() + ".json"); //$NON-NLS-1$ //$NON-NLS-2$

		write(contract, contract());
		write(baseline.resolve(CHANGED), "legacy-api\n"); //$NON-NLS-1$
		write(baseline.resolve(QUARANTINED), "legacy-parameterized\n"); //$NON-NLS-1$
		write(repository.resolve(CHANGED), "jupiter-api\n"); //$NON-NLS-1$
		if (mode == Mode.STRICT) {
			write(repository.resolve(QUARANTINED), "legacy-parameterized\n"); //$NON-NLS-1$
			write(changed, CHANGED + System.lineSeparator());
			write(check, report("check", java.util.List.of("src/Changed.java"), false, true)); //$NON-NLS-1$ //$NON-NLS-2$
			write(apply, report("apply", java.util.List.of("src/Changed.java"), false, true)); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			write(repository.resolve(QUARANTINED), "legacy-parameterized\n" //$NON-NLS-1$
					+ "Sandbox JUnit migration gap parameterized:\n" //$NON-NLS-1$
					+ "PARAMETERIZED_FIELD_INJECTION\n" //$NON-NLS-1$
					+ "manual-remediation\n"); //$NON-NLS-1$
			write(changed, CHANGED + System.lineSeparator() + QUARANTINED + System.lineSeparator());
			java.util.List<String> reportFiles = java.util.List.of("src/Changed.java", "src/Quarantined.java"); //$NON-NLS-1$ //$NON-NLS-2$
			write(check, report("check", reportFiles, includeReason, manualCompletion)); //$NON-NLS-1$
			write(apply, report("apply", reportFiles, includeReason, manualCompletion)); //$NON-NLS-1$
		}
		return new Fixture(repository, baseline, contract, changed, check, apply,
				new Request(repository, baseline, contract, mode, changed, check, apply));
	}

	private static String contract() {
		return """
				{
				  "project": "sample.project",
				  "minimumChangedJavaFiles": 1,
				  "requiredFiles": {
				    "sample.project/src/Changed.java": {
				      "baselineMustContain": ["legacy-api"],
				      "migratedMustContain": ["jupiter-api"],
				      "migratedMustNotContain": ["legacy-api"]
				    },
				    "sample.project/src/Quarantined.java": {
				      "baselineMustContain": ["legacy-parameterized"],
				      "strictUnchanged": true,
				      "strictReasonCode": "PARAMETERIZED_FIELD_INJECTION",
				      "bestEffortMustContain": [
				        "Sandbox JUnit migration gap parameterized:",
				        "PARAMETERIZED_FIELD_INJECTION",
				        "manual-remediation"
				      ]
				    }
				  }
				}
				"""; //$NON-NLS-1$
	}

	private static String report(String mode, java.util.List<String> changedFiles,
			boolean includeReason, boolean manualCompletion) {
		String files = changedFiles.stream()
				.map(path -> "\"" + path + "\"") //$NON-NLS-1$ //$NON-NLS-2$
				.collect(java.util.stream.Collectors.joining(",")); //$NON-NLS-1$
		String reason = includeReason ? "\"reasonCode\":\"PARAMETERIZED_FIELD_INJECTION\"," : ""; //$NON-NLS-1$ //$NON-NLS-2$
		return "{" //$NON-NLS-1$
				+ "\"mode\":\"" + mode + "\"," //$NON-NLS-1$ //$NON-NLS-2$
				+ "\"changedFiles\":[" + files + "]," //$NON-NLS-1$ //$NON-NLS-2$
				+ "\"errorCount\":0,\"errors\":[]," //$NON-NLS-1$
				+ "\"planningDiagnostics\":{" + reason //$NON-NLS-1$
				+ "\"manualCompletionRequired\":" + manualCompletion + "}}"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void write(Path path, String content) throws IOException {
		Path parent = path.toAbsolutePath().normalize().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private record Fixture(Path repository, Path baseline, Path contract, Path changedFiles,
			Path checkReport, Path applyReport, Request request) {
		// Test fixture paths.
	}
}
