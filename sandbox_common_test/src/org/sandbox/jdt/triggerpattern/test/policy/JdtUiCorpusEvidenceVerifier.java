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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Verifies the checked-in and generated evidence for the pinned JDT UI JUnit 4
 * migration corpus.
 *
 * <p>This class is the Maven/JUnit authority for corpus identity, cleanup
 * profiles, check/apply agreement, named source shapes and strict quarantine.
 * The shell runner remains responsible only for provisioning and executing the
 * external tools that produce the evidence.</p>
 *
 * @since 1.3.4
 */
public final class JdtUiCorpusEvidenceVerifier {

	public static final String REPOSITORY_PROPERTY = "sandbox.jdtui.repository"; //$NON-NLS-1$
	public static final String BASELINE_SOURCES_PROPERTY = "sandbox.jdtui.baselineSources"; //$NON-NLS-1$
	public static final String CONTRACT_PROPERTY = "sandbox.jdtui.contract"; //$NON-NLS-1$
	public static final String MODE_PROPERTY = "sandbox.jdtui.mode"; //$NON-NLS-1$
	public static final String CHANGED_FILES_PROPERTY = "sandbox.jdtui.changedFiles"; //$NON-NLS-1$
	public static final String CHECK_REPORT_PROPERTY = "sandbox.jdtui.checkReport"; //$NON-NLS-1$
	public static final String APPLY_REPORT_PROPERTY = "sandbox.jdtui.applyReport"; //$NON-NLS-1$
	public static final String OUTPUT_PROPERTY = "sandbox.jdtui.output"; //$NON-NLS-1$

	private static final String EXPECTED_REPOSITORY =
			"https://github.com/eclipse-jdt/eclipse.jdt.ui.git"; //$NON-NLS-1$
	private static final String EXPECTED_REF = "R4_40"; //$NON-NLS-1$
	private static final String EXPECTED_COMMIT =
			"c922f757b27b7e2b6215db383cec5f8aafd13227"; //$NON-NLS-1$
	private static final String EXPECTED_PROJECT = "org.eclipse.jdt.ui.tests"; //$NON-NLS-1$
	private static final String PARAMETERIZED_SOURCE =
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/ConvertLoopOperationTest.java"; //$NON-NLS-1$
	private static final String PARAMETERIZED_REASON = "PARAMETERIZED_FIELD_INJECTION"; //$NON-NLS-1$
	private static final String BEST_EFFORT_MARKER = "Sandbox JUnit migration gap"; //$NON-NLS-1$
	private static final Set<String> EXPECTED_FILES = Set.of(
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/JUnitSourceSetup.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/core/rules/LeakTestSetup.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/FileAdapterTest.java", //$NON-NLS-1$
			"org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/search/SearchLeakTestWrapper.java", //$NON-NLS-1$
			PARAMETERIZED_SOURCE);
	private static final Gson PRETTY_JSON = new GsonBuilder().setPrettyPrinting().create();

	private JdtUiCorpusEvidenceVerifier() {
	}

	/**
	 * Supported execution modes of the real-corpus runner.
	 */
	public enum Mode {
		STRICT("strict"), //$NON-NLS-1$
		BEST_EFFORT("best-effort"); //$NON-NLS-1$

		private final String argument;

		Mode(String argument) {
			this.argument = argument;
		}

		/**
		 * Returns the command-line representation.
		 *
		 * @return runner argument
		 */
		public String argument() {
			return argument;
		}

		/**
		 * Parses one runner argument.
		 *
		 * @param value argument to parse
		 * @return matching mode
		 */
		public static Mode parse(String value) {
			for (Mode mode : values()) {
				if (mode.argument.equals(value)) {
					return mode;
				}
			}
			throw failure("Unsupported JDT UI corpus mode: " + value); //$NON-NLS-1$
		}
	}

	/**
	 * Complete generated-evidence input.
	 *
	 * @param repository      migrated pinned JDT UI checkout
	 * @param baselineSources byte-exact named baseline source snapshot
	 * @param contract        checked-in corpus contract
	 * @param mode            strict or best-effort mode
	 * @param changedFiles    Git changed-file inventory
	 * @param checkReport     project-wide check report
	 * @param applyReport     project-wide apply report
	 * @param output          summary output path
	 */
	public record Request(Path repository, Path baselineSources, Path contract, Mode mode,
			Path changedFiles, Path checkReport, Path applyReport, Path output) {

		public Request {
			repository = normalized(repository, "repository"); //$NON-NLS-1$
			baselineSources = normalized(baselineSources, "baselineSources"); //$NON-NLS-1$
			contract = normalized(contract, "contract"); //$NON-NLS-1$
			mode = Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
			changedFiles = normalized(changedFiles, "changedFiles"); //$NON-NLS-1$
			checkReport = normalized(checkReport, "checkReport"); //$NON-NLS-1$
			applyReport = normalized(applyReport, "applyReport"); //$NON-NLS-1$
			output = normalized(output, "output"); //$NON-NLS-1$
		}
	}

	/**
	 * Stable machine-readable result of one corpus verification.
	 *
	 * @param mode                             execution mode
	 * @param project                          verified Eclipse project
	 * @param changedFiles                     complete changed-file count
	 * @param changedJavaFiles                 changed Java source count
	 * @param changedNonJavaFiles              changed resource paths
	 * @param verifiedChangedCorpusFiles       required migrated source paths
	 * @param strictlyQuarantinedCorpusFiles   byte-exact strict quarantine
	 * @param requiredReasonCodes              best-effort remediation reasons
	 */
	public record Summary(Mode mode, String project, int changedFiles, int changedJavaFiles,
			List<String> changedNonJavaFiles, List<String> verifiedChangedCorpusFiles,
			List<String> strictlyQuarantinedCorpusFiles, List<String> requiredReasonCodes) {

		public Summary {
			mode = Objects.requireNonNull(mode, "mode"); //$NON-NLS-1$
			project = Objects.requireNonNull(project, "project"); //$NON-NLS-1$
			changedNonJavaFiles = List.copyOf(changedNonJavaFiles);
			verifiedChangedCorpusFiles = List.copyOf(verifiedChangedCorpusFiles);
			strictlyQuarantinedCorpusFiles = List.copyOf(strictlyQuarantinedCorpusFiles);
			requiredReasonCodes = List.copyOf(requiredReasonCodes);
		}

		JsonObject toJson() {
			JsonObject result = new JsonObject();
			result.addProperty("result", "PASS"); //$NON-NLS-1$ //$NON-NLS-2$
			result.addProperty("mode", mode.argument()); //$NON-NLS-1$
			result.addProperty("project", project); //$NON-NLS-1$
			result.addProperty("changedFiles", changedFiles); //$NON-NLS-1$
			result.addProperty("changedJavaFiles", changedJavaFiles); //$NON-NLS-1$
			result.add("changedNonJavaFiles", strings(changedNonJavaFiles)); //$NON-NLS-1$
			result.add("verifiedChangedCorpusFiles", strings(verifiedChangedCorpusFiles)); //$NON-NLS-1$
			result.add("strictlyQuarantinedCorpusFiles", strings(strictlyQuarantinedCorpusFiles)); //$NON-NLS-1$
			result.add("requiredReasonCodes", strings(requiredReasonCodes)); //$NON-NLS-1$
			return result;
		}
	}

	/**
	 * Finds the Sandbox repository root from the current working directory.
	 *
	 * @return repository root
	 */
	public static Path repositoryRoot() {
		Path candidate = Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
		while (candidate != null) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("qa/upstream-jdt")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("sandbox_common_test"))) { //$NON-NLS-1$
				return candidate;
			}
			candidate = candidate.getParent();
		}
		throw failure("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}

	/**
	 * Verifies the checked-in pin, corpus, profile, runner and workflow contract.
	 *
	 * @param repositoryRoot Sandbox repository root
	 * @throws IOException when a checked-in contract file cannot be read
	 */
	public static void verifyCheckedInContract(Path repositoryRoot) throws IOException {
		Path root = normalized(repositoryRoot, "repositoryRoot"); //$NON-NLS-1$
		Path qa = root.resolve("qa/upstream-jdt"); //$NON-NLS-1$
		JsonObject contract = object(qa.resolve("jdt-ui-junit4-corpus.json")); //$NON-NLS-1$

		require(EXPECTED_REPOSITORY.equals(string(contract, "repository", "corpus repository")), //$NON-NLS-1$ //$NON-NLS-2$
				"JDT UI corpus repository is not pinned to eclipse-jdt/eclipse.jdt.ui"); //$NON-NLS-1$
		require(EXPECTED_REF.equals(string(contract, "ref", "corpus ref")), //$NON-NLS-1$ //$NON-NLS-2$
				"JDT UI corpus ref differs from the pinned R4_40 source"); //$NON-NLS-1$
		require(EXPECTED_COMMIT.equals(string(contract, "commit", "corpus commit")), //$NON-NLS-1$ //$NON-NLS-2$
				"JDT UI corpus commit differs from the pinned R4_40 source"); //$NON-NLS-1$
		require(EXPECTED_PROJECT.equals(string(contract, "project", "corpus project")), //$NON-NLS-1$ //$NON-NLS-2$
				"JDT UI corpus must target org.eclipse.jdt.ui.tests"); //$NON-NLS-1$

		JsonObject requiredFiles = requiredFiles(contract);
		require(EXPECTED_FILES.equals(requiredFiles.keySet()),
				"JDT UI corpus must name exactly " + new TreeSet<>(EXPECTED_FILES)); //$NON-NLS-1$
		require(integer(contract, "minimumChangedJavaFiles", 0) >= 4, //$NON-NLS-1$
				"JDT UI corpus allows fewer than four supported source changes"); //$NON-NLS-1$

		JsonObject difficult = requiredFiles.getAsJsonObject(PARAMETERIZED_SOURCE);
		require(booleanValue(difficult, "strictUnchanged", false), //$NON-NLS-1$
				"ConvertLoopOperationTest is not protected by strict quarantine"); //$NON-NLS-1$
		require(PARAMETERIZED_REASON.equals(
				string(difficult, "strictReasonCode", "parameterized strict reason")), //$NON-NLS-1$ //$NON-NLS-2$
				"ConvertLoopOperationTest does not require the field-injection reason code"); //$NON-NLS-1$
		require(!stringList(difficult, "bestEffortMustContain", //$NON-NLS-1$
				PARAMETERIZED_SOURCE + ".bestEffortMustContain").isEmpty(), //$NON-NLS-1$
				"Best-effort corpus contains no required TODO scaffold evidence"); //$NON-NLS-1$

		Map<String, String> pins = properties(qa.resolve("pins.env")); //$NON-NLS-1$
		require(EXPECTED_REPOSITORY.equals(pins.get("PIN_JDT_UI_REPOSITORY")), //$NON-NLS-1$
				"pins.env contains a different JDT UI repository"); //$NON-NLS-1$
		require(EXPECTED_REF.equals(pins.get("PIN_JDT_UI_REF")), //$NON-NLS-1$
				"pins.env contains a different JDT UI ref"); //$NON-NLS-1$
		require(EXPECTED_COMMIT.equals(pins.get("PIN_JDT_UI_COMMIT")), //$NON-NLS-1$
				"pins.env contains a different JDT UI commit"); //$NON-NLS-1$

		verifyProfile(qa.resolve("junit4-to-jupiter.properties"), false, "strict"); //$NON-NLS-1$ //$NON-NLS-2$
		verifyProfile(qa.resolve("junit4-to-jupiter-best-effort.properties"), true, //$NON-NLS-1$
				"best-effort"); //$NON-NLS-1$

		String runner = read(qa.resolve("run-jdt-ui-before-after.sh")); //$NON-NLS-1$
		for (String marker : List.of(
				"org.eclipse.jdt.ui.tests", //$NON-NLS-1$
				"org.eclipse.jdt.bcoview", //$NON-NLS-1$
				"REACTOR_PROJECTS", //$NON-NLS-1$
				"verify_reactor_bcoview_runtime", //$NON-NLS-1$
				"jdt-ui-junit4-corpus.json", //$NON-NLS-1$
				"JdtUiCorpusEvidenceVerifierTest#retainedWorkspaceEvidenceMatchesContract", //$NON-NLS-1$
				REPOSITORY_PROPERTY,
				"compare_test_inventory.py", //$NON-NLS-1$
				"strict|best-effort")) { //$NON-NLS-1$
			require(runner.contains(marker), "JDT UI runner is missing contract marker " + marker); //$NON-NLS-1$
		}
		require(!runner.contains("verify_jdt_ui_corpus.py"), //$NON-NLS-1$
				"JDT UI runner still delegates corpus assertions to Python"); //$NON-NLS-1$

		Path workflowPath = root.resolve(".github/workflows/jdt-ui-junit4-strict-qa.yml"); //$NON-NLS-1$
		String workflow = read(workflowPath);
		require(workflow.contains("'sandbox_common_test/**'"), //$NON-NLS-1$
				"JDT UI workflow does not react to Maven/JUnit authority changes"); //$NON-NLS-1$
		require(workflow.contains("JdtUiCorpusContractTest,JdtUiCorpusEvidenceVerifierTest"), //$NON-NLS-1$
				"JDT UI workflow does not execute the Java/JUnit contract authority"); //$NON-NLS-1$
		require(!workflow.contains("verify_jdt_ui_contract.py"), //$NON-NLS-1$
				"JDT UI workflow still delegates contract assertions to Python"); //$NON-NLS-1$

		require(!Files.exists(qa.resolve("verify_jdt_ui_contract.py")), //$NON-NLS-1$
				"Duplicate Python JDT UI contract authority still exists"); //$NON-NLS-1$
		require(!Files.exists(qa.resolve("verify_jdt_ui_corpus.py")), //$NON-NLS-1$
				"Duplicate Python JDT UI corpus authority still exists"); //$NON-NLS-1$
	}

	/**
	 * Verifies the generated real-corpus evidence described by {@code request}.
	 *
	 * @param request evidence input
	 * @return stable result summary
	 * @throws IOException when evidence cannot be read or written
	 */
	public static Summary verify(Request request) throws IOException {
		Objects.requireNonNull(request, "request"); //$NON-NLS-1$
		JsonObject contract = object(request.contract());
		String project = string(contract, "project", "corpus project"); //$NON-NLS-1$ //$NON-NLS-2$
		JsonObject required = requiredFiles(contract);

		Set<String> actualChanged = changedFiles(request.changedFiles());
		Set<String> actualJava = new TreeSet<>();
		Set<String> actualNonJava = new TreeSet<>();
		for (String path : actualChanged) {
			(path.endsWith(".java") ? actualJava : actualNonJava).add(path); //$NON-NLS-1$
		}
		int minimum = integer(contract, "minimumChangedJavaFiles", 0); //$NON-NLS-1$
		require(actualJava.size() >= minimum, "Only " + actualJava.size() //$NON-NLS-1$
				+ " Java files changed; contract requires at least " + minimum); //$NON-NLS-1$

		JsonObject checkReport = object(request.checkReport());
		JsonObject applyReport = object(request.applyReport());
		requireReport(checkReport, "check", "check"); //$NON-NLS-1$ //$NON-NLS-2$
		requireReport(applyReport, "apply", "apply"); //$NON-NLS-1$ //$NON-NLS-2$
		Set<String> checkChanged = reportChangedFiles(checkReport, project, "check"); //$NON-NLS-1$
		Set<String> applyChanged = reportChangedFiles(applyReport, project, "apply"); //$NON-NLS-1$
		require(checkChanged.equals(applyChanged),
				"Cleanup check and apply report different changed-file sets"); //$NON-NLS-1$
		require(checkChanged.equals(actualChanged),
				"Cleanup reports and Git migration patch differ: reportOnly=" //$NON-NLS-1$
						+ difference(checkChanged, actualChanged) + ", gitOnly=" //$NON-NLS-1$
						+ difference(actualChanged, checkChanged));

		List<String> strictUnchanged = new ArrayList<>();
		List<String> verifiedChanged = new ArrayList<>();
		Set<String> requiredReasonCodes = new TreeSet<>();
		for (String relative : new TreeSet<>(required.keySet())) {
			JsonElement rawRules = required.get(relative);
			require(rawRules != null && rawRules.isJsonObject(),
					"Invalid requiredFiles entry: " + relative); //$NON-NLS-1$
			JsonObject rules = rawRules.getAsJsonObject();
			Path current = request.repository().resolve(relative);
			Path baseline = request.baselineSources().resolve(relative);
			require(Files.isRegularFile(current) && Files.isRegularFile(baseline),
					"Required corpus source is missing: " + relative); //$NON-NLS-1$
			String baselineText = read(baseline);
			String currentText = read(current);
			requireMarkers(baselineText,
					stringList(rules, "baselineMustContain", relative + ".baselineMustContain"), //$NON-NLS-1$ //$NON-NLS-2$
					"baseline " + relative); //$NON-NLS-1$

			boolean unchangedInStrict = booleanValue(rules, "strictUnchanged", false); //$NON-NLS-1$
			if (request.mode() == Mode.STRICT && unchangedInStrict) {
				require(!actualChanged.contains(relative),
						"Strict mode changed quarantined corpus file: " + relative); //$NON-NLS-1$
				require(Arrays.equals(Files.readAllBytes(baseline), Files.readAllBytes(current)),
						"Strict mode did not preserve quarantined file byte-for-byte: " + relative); //$NON-NLS-1$
				require(!currentText.contains(BEST_EFFORT_MARKER),
						"Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
				strictUnchanged.add(relative);
				continue;
			}

			require(actualChanged.contains(relative),
					"Expected migrated corpus file is absent from the patch: " + relative); //$NON-NLS-1$
			requireMarkers(currentText,
					stringList(rules, "migratedMustContain", relative + ".migratedMustContain"), //$NON-NLS-1$ //$NON-NLS-2$
					"migrated " + relative); //$NON-NLS-1$
			requireAbsent(currentText,
					stringList(rules, "migratedMustNotContain", relative + ".migratedMustNotContain"), //$NON-NLS-1$ //$NON-NLS-2$
					"migrated " + relative); //$NON-NLS-1$

			if (request.mode() == Mode.BEST_EFFORT) {
				requireMarkers(currentText,
						stringList(rules, "bestEffortMustContain", //$NON-NLS-1$
								relative + ".bestEffortMustContain"), //$NON-NLS-1$
						"best-effort " + relative); //$NON-NLS-1$
				JsonElement reason = rules.get("strictReasonCode"); //$NON-NLS-1$
				if (reason != null && !reason.isJsonNull()) {
					require(reason.isJsonPrimitive() && reason.getAsJsonPrimitive().isString()
							&& !reason.getAsString().isBlank(),
							relative + ".strictReasonCode must be a non-empty string"); //$NON-NLS-1$
					requiredReasonCodes.add(reason.getAsString());
				}
			} else {
				require(!currentText.contains(BEST_EFFORT_MARKER),
						"Strict mode inserted a best-effort marker into " + relative); //$NON-NLS-1$
			}
			verifiedChanged.add(relative);
		}

		if (request.mode() == Mode.BEST_EFFORT) {
			JsonObject combined = new JsonObject();
			combined.add("check", checkReport); //$NON-NLS-1$
			combined.add("apply", applyReport); //$NON-NLS-1$
			List<String> missingReasons = requiredReasonCodes.stream()
					.filter(reason -> !containsString(combined, reason))
					.toList();
			require(missingReasons.isEmpty(),
					"Best-effort reports omit required reason codes: " + missingReasons); //$NON-NLS-1$
			require(containsTrueProperty(combined, "manualCompletionRequired"), //$NON-NLS-1$
					"Best-effort reports do not state that manual completion is required"); //$NON-NLS-1$
		}

		Summary summary = new Summary(request.mode(), project, actualChanged.size(),
				actualJava.size(), List.copyOf(actualNonJava), verifiedChanged, strictUnchanged,
				List.copyOf(requiredReasonCodes));
		writeSummary(request.output(), summary);
		return summary;
	}

	/**
	 * Creates a request from the system properties used by the retained-workspace
	 * runner and verifies it.
	 *
	 * @return verification summary
	 * @throws IOException when evidence cannot be read or written
	 */
	public static Summary verifyConfiguredEvidence() throws IOException {
		return verify(new Request(
				Path.of(requiredProperty(REPOSITORY_PROPERTY)),
				Path.of(requiredProperty(BASELINE_SOURCES_PROPERTY)),
				Path.of(requiredProperty(CONTRACT_PROPERTY)),
				Mode.parse(requiredProperty(MODE_PROPERTY)),
				Path.of(requiredProperty(CHANGED_FILES_PROPERTY)),
				Path.of(requiredProperty(CHECK_REPORT_PROPERTY)),
				Path.of(requiredProperty(APPLY_REPORT_PROPERTY)),
				Path.of(requiredProperty(OUTPUT_PROPERTY))));
	}

	private static void verifyProfile(Path profilePath, boolean bestEffort, String label)
			throws IOException {
		Map<String, String> profile = properties(profilePath);
		require("true".equals(profile.get("cleanup.junitcleanup")), //$NON-NLS-1$ //$NON-NLS-2$
				label + " profile does not enable JUnit 4 migration"); //$NON-NLS-1$
		require("false".equals(profile.get("cleanup.junit3cleanup")), //$NON-NLS-1$ //$NON-NLS-2$
				label + " profile mixes the JDT Core/JUnit 3 migration track"); //$NON-NLS-1$
		require(Boolean.toString(bestEffort).equals(
				profile.get("cleanup.junitcleanup_best_effort")), //$NON-NLS-1$
				label + " profile has the wrong best-effort policy"); //$NON-NLS-1$
		require("true".equals(profile.get("cleanup.junitcleanup_4_parameterized")), //$NON-NLS-1$ //$NON-NLS-2$
				label + " profile does not exercise real JDT UI parameterization"); //$NON-NLS-1$
	}

	private static void requireReport(JsonObject report, String mode, String label) {
		require(mode.equals(optionalString(report, "mode")), //$NON-NLS-1$
				label + " report mode is " + optionalString(report, "mode") //$NON-NLS-1$ //$NON-NLS-2$
						+ ", expected " + mode); //$NON-NLS-1$
		require(integer(report, "errorCount", 0) == 0, //$NON-NLS-1$
				label + " report contains cleanup errors"); //$NON-NLS-1$
		JsonElement errors = report.get("errors"); //$NON-NLS-1$
		require(errors == null || errors.isJsonArray(),
				label + " report contains a non-array errors value"); //$NON-NLS-1$
		require(errors == null || errors.getAsJsonArray().size() == 0,
				label + " report contains error entries"); //$NON-NLS-1$
		require(report.has("planningDiagnostics"), //$NON-NLS-1$
				label + " report contains no planningDiagnostics"); //$NON-NLS-1$
	}

	private static Set<String> reportChangedFiles(JsonObject report, String project, String label) {
		JsonElement values = report.get("changedFiles"); //$NON-NLS-1$
		require(values != null && values.isJsonArray(),
				label + " report has no valid changedFiles list"); //$NON-NLS-1$
		Set<String> result = new TreeSet<>();
		for (JsonElement item : values.getAsJsonArray()) {
			require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
					&& !item.getAsString().isBlank(),
					label + " report has no valid changedFiles list"); //$NON-NLS-1$
			result.add(normalizeReportFile(project, item.getAsString()));
		}
		return result;
	}

	private static String normalizeReportFile(String project, String value) {
		String normalized = value.replace('\\', '/');
		int start = 0;
		while (start < normalized.length()
				&& (normalized.charAt(start) == '.' || normalized.charAt(start) == '/')) {
			start++;
		}
		normalized = normalized.substring(start);
		return normalized.startsWith(project + "/") //$NON-NLS-1$
				? normalized
				: project + "/" + normalized; //$NON-NLS-1$
	}

	private static Set<String> changedFiles(Path path) throws IOException {
		Set<String> result = new TreeSet<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String normalized = raw.strip().replace('\\', '/');
			if (!normalized.isEmpty()) {
				result.add(normalized);
			}
		}
		return result;
	}

	private static JsonObject requiredFiles(JsonObject contract) {
		JsonElement required = contract.get("requiredFiles"); //$NON-NLS-1$
		require(required != null && required.isJsonObject()
				&& required.getAsJsonObject().size() > 0,
				"Corpus contract has no requiredFiles object"); //$NON-NLS-1$
		return required.getAsJsonObject();
	}

	private static JsonObject object(Path path) throws IOException {
		JsonElement value;
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			value = JsonParser.parseReader(reader);
		}
		require(value.isJsonObject(), path + " does not contain a JSON object"); //$NON-NLS-1$
		return value.getAsJsonObject();
	}

	private static Map<String, String> properties(Path path) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		int number = 0;
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			number++;
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			int separator = line.indexOf('=');
			require(separator > 0 && separator < line.length() - 1,
					path + ":" + number + ": expected key=value"); //$NON-NLS-1$ //$NON-NLS-2$
			String key = line.substring(0, separator);
			String value = line.substring(separator + 1);
			require(!values.containsKey(key),
					path + ":" + number + ": invalid or duplicate property"); //$NON-NLS-1$ //$NON-NLS-2$
			values.put(key, value);
		}
		return Map.copyOf(values);
	}

	private static List<String> stringList(JsonObject object, String name, String label) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return List.of();
		}
		require(value.isJsonArray(), label + " must be a list of non-empty strings"); //$NON-NLS-1$
		List<String> result = new ArrayList<>();
		for (JsonElement item : value.getAsJsonArray()) {
			require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
					&& !item.getAsString().isEmpty(),
					label + " must be a list of non-empty strings"); //$NON-NLS-1$
			result.add(item.getAsString());
		}
		return List.copyOf(result);
	}

	private static String string(JsonObject object, String name, String label) {
		JsonElement value = object.get(name);
		require(value != null && value.isJsonPrimitive()
				&& value.getAsJsonPrimitive().isString() && !value.getAsString().isEmpty(),
				label + " must be a non-empty string"); //$NON-NLS-1$
		return value.getAsString();
	}

	private static String optionalString(JsonObject object, String name) {
		JsonElement value = object.get(name);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static int integer(JsonObject object, String name, int defaultValue) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return defaultValue;
		}
		try {
			return value.getAsInt();
		} catch (RuntimeException exception) {
			throw failure(name + " must be an integer"); //$NON-NLS-1$
		}
	}

	private static boolean booleanValue(JsonObject object, String name, boolean defaultValue) {
		JsonElement value = object.get(name);
		if (value == null || value.isJsonNull()) {
			return defaultValue;
		}
		require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(),
				name + " must be a boolean"); //$NON-NLS-1$
		return value.getAsBoolean();
	}

	private static void requireMarkers(String text, List<String> markers, String label) {
		List<String> missing = markers.stream().filter(marker -> !text.contains(marker)).toList();
		require(missing.isEmpty(), label + " is missing markers: " + missing); //$NON-NLS-1$
	}

	private static void requireAbsent(String text, List<String> markers, String label) {
		List<String> present = markers.stream().filter(text::contains).toList();
		require(present.isEmpty(), label + " still contains forbidden markers: " + present); //$NON-NLS-1$
	}

	private static boolean containsString(JsonElement element, String expected) {
		if (element == null || element.isJsonNull()) {
			return false;
		}
		if (element.isJsonPrimitive()) {
			return element.getAsJsonPrimitive().isString()
					&& expected.equals(element.getAsString());
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsString(child, expected)) {
					return true;
				}
			}
			return false;
		}
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (containsString(entry.getValue(), expected)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsTrueProperty(JsonElement element, String name) {
		if (element == null || element.isJsonNull() || element.isJsonPrimitive()) {
			return false;
		}
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) {
				if (containsTrueProperty(child, name)) {
					return true;
				}
			}
			return false;
		}
		JsonObject object = element.getAsJsonObject();
		JsonElement direct = object.get(name);
		if (direct != null && direct.isJsonPrimitive()
				&& direct.getAsJsonPrimitive().isBoolean() && direct.getAsBoolean()) {
			return true;
		}
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			if (containsTrueProperty(entry.getValue(), name)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> difference(Set<String> left, Set<String> right) {
		Set<String> result = new TreeSet<>(left);
		result.removeAll(right);
		return List.copyOf(result);
	}

	private static void writeSummary(Path output, Summary summary) throws IOException {
		Path parent = output.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, PRETTY_JSON.toJson(summary.toJson()) + "\n", //$NON-NLS-1$
				StandardCharsets.UTF_8);
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		require(value != null && !value.isBlank(), "Missing system property: " + name); //$NON-NLS-1$
		return value;
	}

	private static Path normalized(Path path, String label) {
		return Objects.requireNonNull(path, label).toAbsolutePath().normalize();
	}

	private static JsonArray strings(List<String> values) {
		JsonArray result = new JsonArray();
		values.forEach(result::add);
		return result;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw failure(message);
		}
	}

	private static IllegalArgumentException failure(String message) {
		return new IllegalArgumentException(message);
	}
}
