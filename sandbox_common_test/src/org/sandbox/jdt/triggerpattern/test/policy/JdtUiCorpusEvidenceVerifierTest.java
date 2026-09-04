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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Mode;
import org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifier.Verification;

/** Tests and Maven entry point for the pinned JDT UI corpus verifier. */
public class JdtUiCorpusEvidenceVerifierTest {

	private static final String REPOSITORY_PROPERTY = "sandbox.junit.corpus.repository"; //$NON-NLS-1$
	private static final String BASELINE_PROPERTY = "sandbox.junit.corpus.baseline"; //$NON-NLS-1$
	private static final String CONTRACT_PROPERTY = "sandbox.junit.corpus.contract"; //$NON-NLS-1$
	private static final String MODE_PROPERTY = "sandbox.junit.corpus.mode"; //$NON-NLS-1$
	private static final String CHANGED_FILES_PROPERTY = "sandbox.junit.corpus.changedFiles"; //$NON-NLS-1$
	private static final String CHECK_REPORT_PROPERTY = "sandbox.junit.corpus.checkReport"; //$NON-NLS-1$
	private static final String APPLY_REPORT_PROPERTY = "sandbox.junit.corpus.applyReport"; //$NON-NLS-1$
	private static final String OUTPUT_PROPERTY = "sandbox.junit.corpus.output"; //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void strictEvidenceRequiresPatchAgreementAndPreservesQuarantine() throws Exception {
		Fixture fixture = fixture();
		String changed = fixture.project() + "/src/Changed.java"; //$NON-NLS-1$
		String quarantined = fixture.project() + "/src/Quarantined.java"; //$NON-NLS-1$
		writeSource(fixture.baseline(), changed, "legacy call\n"); //$NON-NLS-1$
		writeSource(fixture.repository(), changed, "modern call\n"); //$NON-NLS-1$
		writeSource(fixture.baseline(), quarantined, "@RunWith legacy\n"); //$NON-NLS-1$
		writeSource(fixture.repository(), quarantined, "@RunWith legacy\n"); //$NON-NLS-1$
		writeUtf8(fixture.contract(), strictContract(fixture.project(), changed, quarantined));
		writeUtf8(fixture.changedFiles(), changed + System.lineSeparator());
		writeReport(fixture.checkReport(), "check", "src/Changed.java", "[]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		writeReport(fixture.applyReport(), "apply", changed, "[]"); //$NON-NLS-1$ //$NON-NLS-2$

		Verification result = verify(fixture, Mode.STRICT);

		assertEquals("PASS", result.result()); //$NON-NLS-1$
		assertEquals(1, result.changedFiles());
		assertEquals(1, result.changedJavaFiles());
		assertEquals(java.util.List.of(changed), result.verifiedChangedCorpusFiles());
		assertEquals(java.util.List.of(quarantined), result.strictlyQuarantinedCorpusFiles());
		assertTrue(result.requiredReasonCodes().isEmpty());
	}

	@Test
	public void bestEffortEvidenceRequiresMarkersReasonAndManualCompletion() throws Exception {
		Fixture fixture = fixture();
		String relative = fixture.project() + "/src/Parameterized.java"; //$NON-NLS-1$
		writeSource(fixture.baseline(), relative, "@RunWith(Parameterized.class)\n"); //$NON-NLS-1$
		writeSource(fixture.repository(), relative, """
				Sandbox JUnit migration gap parameterized:
				PARAMETERIZED_FIELD_INJECTION
				sandboxJUnitMigrationTodoParameterizedFieldInjection
				Manual JUnit migration required: PARAMETERIZED_FIELD_INJECTION
				"""); //$NON-NLS-1$
		writeUtf8(fixture.contract(), bestEffortContract(fixture.project(), relative));
		writeUtf8(fixture.changedFiles(), relative + System.lineSeparator());
		String diagnostics = """
				[{"reasonCode":"PARAMETERIZED_FIELD_INJECTION","manualCompletionRequired":true}]
				"""; //$NON-NLS-1$
		writeReport(fixture.checkReport(), "check", "src/Parameterized.java", diagnostics); //$NON-NLS-1$ //$NON-NLS-2$
		writeReport(fixture.applyReport(), "apply", relative, diagnostics); //$NON-NLS-1$

		Verification result = verify(fixture, Mode.BEST_EFFORT);

		assertEquals(java.util.List.of(relative), result.verifiedChangedCorpusFiles());
		assertEquals(java.util.List.of("PARAMETERIZED_FIELD_INJECTION"), result.requiredReasonCodes()); //$NON-NLS-1$
		assertTrue(result.strictlyQuarantinedCorpusFiles().isEmpty());
	}

	@Test
	public void strictModeRejectsAChangedQuarantinedFile() throws Exception {
		Fixture fixture = fixture();
		String relative = fixture.project() + "/src/Quarantined.java"; //$NON-NLS-1$
		writeSource(fixture.baseline(), relative, "@RunWith legacy\n"); //$NON-NLS-1$
		writeSource(fixture.repository(), relative, "@RunWith changed\n"); //$NON-NLS-1$
		writeUtf8(fixture.contract(), quarantineOnlyContract(fixture.project(), relative));
		writeUtf8(fixture.changedFiles(), relative + System.lineSeparator());
		writeReport(fixture.checkReport(), "check", relative, "[]"); //$NON-NLS-1$ //$NON-NLS-2$
		writeReport(fixture.applyReport(), "apply", relative, "[]"); //$NON-NLS-1$ //$NON-NLS-2$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> verify(fixture, Mode.STRICT));

		assertTrue(failure.getMessage().contains("Strict mode changed quarantined corpus file")); //$NON-NLS-1$
	}

	@Test
	public void checkAndApplyReportsMustDescribeTheSamePatch() throws Exception {
		Fixture fixture = fixture();
		String relative = fixture.project() + "/src/Changed.java"; //$NON-NLS-1$
		writeSource(fixture.baseline(), relative, "legacy call\n"); //$NON-NLS-1$
		writeSource(fixture.repository(), relative, "modern call\n"); //$NON-NLS-1$
		writeUtf8(fixture.contract(), changedOnlyContract(fixture.project(), relative));
		writeUtf8(fixture.changedFiles(), relative + System.lineSeparator());
		writeReport(fixture.checkReport(), "check", relative, "[]"); //$NON-NLS-1$ //$NON-NLS-2$
		writeReport(fixture.applyReport(), "apply", "src/Other.java", "[]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> verify(fixture, Mode.STRICT));

		assertTrue(failure.getMessage().contains("check and apply report different changed-file sets")); //$NON-NLS-1$
	}

	@Test
	public void malformedContractsFailBeforeSourceVerification() throws Exception {
		Fixture fixture = fixture();
		writeUtf8(fixture.contract(), "{\"project\":\"example\",\"requiredFiles\":[]}"); //$NON-NLS-1$
		writeUtf8(fixture.changedFiles(), ""); //$NON-NLS-1$
		writeReport(fixture.checkReport(), "check", "src/Changed.java", "[]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		writeReport(fixture.applyReport(), "apply", "src/Changed.java", "[]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> verify(fixture, Mode.STRICT));

		assertTrue(failure.getMessage().contains("requiredFiles object")); //$NON-NLS-1$
	}

	@Test
	public void configuredUpstreamEvidenceIsVerifiedByMaven() throws Exception {
		Map<String, String> configured = Map.of(
				REPOSITORY_PROPERTY, System.getProperty(REPOSITORY_PROPERTY, ""), //$NON-NLS-1$
				BASELINE_PROPERTY, System.getProperty(BASELINE_PROPERTY, ""), //$NON-NLS-1$
				CONTRACT_PROPERTY, System.getProperty(CONTRACT_PROPERTY, ""), //$NON-NLS-1$
				MODE_PROPERTY, System.getProperty(MODE_PROPERTY, ""), //$NON-NLS-1$
				CHANGED_FILES_PROPERTY, System.getProperty(CHANGED_FILES_PROPERTY, ""), //$NON-NLS-1$
				CHECK_REPORT_PROPERTY, System.getProperty(CHECK_REPORT_PROPERTY, ""), //$NON-NLS-1$
				APPLY_REPORT_PROPERTY, System.getProperty(APPLY_REPORT_PROPERTY, ""), //$NON-NLS-1$
				OUTPUT_PROPERTY, System.getProperty(OUTPUT_PROPERTY, "")); //$NON-NLS-1$
		long supplied = configured.values().stream().filter(value -> !value.isBlank()).count();
		if (supplied == 0) {
			return;
		}
		assertEquals(configured.size(), supplied,
				"The retained-workspace invocation must provide every JDT UI corpus evidence path"); //$NON-NLS-1$

		Verification result = JdtUiCorpusEvidenceVerifier.verify(
				Path.of(configured.get(REPOSITORY_PROPERTY)),
				Path.of(configured.get(BASELINE_PROPERTY)),
				Path.of(configured.get(CONTRACT_PROPERTY)),
				Mode.parse(configured.get(MODE_PROPERTY)),
				Path.of(configured.get(CHANGED_FILES_PROPERTY)),
				Path.of(configured.get(CHECK_REPORT_PROPERTY)),
				Path.of(configured.get(APPLY_REPORT_PROPERTY)));
		Path output = Path.of(configured.get(OUTPUT_PROPERTY));
		result.write(output);

		assertEquals("PASS", result.result(), //$NON-NLS-1$
				() -> "JDT UI corpus evidence failed; see " + output.toAbsolutePath()); //$NON-NLS-1$
	}

	private Fixture fixture() throws IOException {
		Path repository = temporaryDirectory.resolve("repository"); //$NON-NLS-1$
		Path baseline = temporaryDirectory.resolve("baseline"); //$NON-NLS-1$
		Files.createDirectories(repository);
		Files.createDirectories(baseline);
		return new Fixture(repository, baseline, temporaryDirectory.resolve("contract.json"), //$NON-NLS-1$
				temporaryDirectory.resolve("changed-files.txt"), //$NON-NLS-1$
				temporaryDirectory.resolve("check-report.json"), //$NON-NLS-1$
				temporaryDirectory.resolve("apply-report.json"), //$NON-NLS-1$
				"org.eclipse.jdt.ui.tests"); //$NON-NLS-1$
	}

	private static Verification verify(Fixture fixture, Mode mode) throws IOException {
		return JdtUiCorpusEvidenceVerifier.verify(fixture.repository(), fixture.baseline(),
				fixture.contract(), mode, fixture.changedFiles(), fixture.checkReport(), fixture.applyReport());
	}

	private static void writeSource(Path root, String relative, String content) throws IOException {
		writeUtf8(root.resolve(relative), content);
	}

	private static void writeUtf8(Path path, String content) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	private static void writeReport(Path path, String mode, String changedFile, String diagnostics)
			throws IOException {
		writeUtf8(path, """
				{
				  "mode": "%s",
				  "errorCount": 0,
				  "errors": [],
				  "planningDiagnostics": %s,
				  "changedFiles": ["%s"]
				}
				""".formatted(mode, diagnostics.strip(), changedFile)); //$NON-NLS-1$
	}

	private static String strictContract(String project, String changed, String quarantined) {
		return """
				{
				  "project": "%s",
				  "minimumChangedJavaFiles": 1,
				  "requiredFiles": {
				    "%s": {
				      "baselineMustContain": ["legacy"],
				      "migratedMustContain": ["modern"],
				      "migratedMustNotContain": ["legacy"]
				    },
				    "%s": {
				      "baselineMustContain": ["@RunWith"],
				      "strictUnchanged": true,
				      "strictReasonCode": "PARAMETERIZED_FIELD_INJECTION"
				    }
				  }
				}
				""".formatted(project, changed, quarantined); //$NON-NLS-1$
	}

	private static String changedOnlyContract(String project, String relative) {
		return """
				{
				  "project": "%s",
				  "minimumChangedJavaFiles": 1,
				  "requiredFiles": {
				    "%s": {
				      "baselineMustContain": ["legacy"],
				      "migratedMustContain": ["modern"],
				      "migratedMustNotContain": ["legacy"]
				    }
				  }
				}
				""".formatted(project, relative); //$NON-NLS-1$
	}

	private static String quarantineOnlyContract(String project, String relative) {
		return """
				{
				  "project": "%s",
				  "requiredFiles": {
				    "%s": {
				      "baselineMustContain": ["@RunWith"],
				      "strictUnchanged": true
				    }
				  }
				}
				""".formatted(project, relative); //$NON-NLS-1$
	}

	private static String bestEffortContract(String project, String relative) {
		return """
				{
				  "project": "%s",
				  "minimumChangedJavaFiles": 1,
				  "requiredFiles": {
				    "%s": {
				      "baselineMustContain": ["@RunWith(Parameterized.class)"],
				      "strictUnchanged": true,
				      "strictReasonCode": "PARAMETERIZED_FIELD_INJECTION",
				      "bestEffortMustContain": [
				        "Sandbox JUnit migration gap parameterized:",
				        "PARAMETERIZED_FIELD_INJECTION",
				        "sandboxJUnitMigrationTodoParameterizedFieldInjection",
				        "Manual JUnit migration required: PARAMETERIZED_FIELD_INJECTION"
				      ]
				    }
				  }
				}
				""".formatted(project, relative); //$NON-NLS-1$
	}

	private record Fixture(Path repository, Path baseline, Path contract, Path changedFiles,
			Path checkReport, Path applyReport, String project) {
		// Test paths.
	}
}
