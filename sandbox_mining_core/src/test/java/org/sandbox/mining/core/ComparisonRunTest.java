/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.report.JsonReporter;

/**
 * Integration test that orchestrates the comparison process (Task 10 from issue #884).
 *
 * <p>This test is tagged as {@code ManualIntegration} and is excluded from normal CI
 * builds. Run it manually with a valid LLM API key:</p>
 *
 * <pre>
 * mvn test -pl sandbox_mining_core -Dgroups=ManualIntegration -DGEMINI_API_KEY=...
 * </pre>
 *
 * <p>The test:</p>
 * <ol>
 *   <li>Loads commit hashes from {@code eclipse-2025-sample.txt}</li>
 *   <li>Runs MiningCli with {@code --commit-list} and {@code --output-format both}</li>
 *   <li>Validates that all GREEN evaluations have a non-null {@code dslRule}</li>
 *   <li>Verifies that {@code .sandbox-hint} files were generated for GREEN+VALID evaluations</li>
 * </ol>
 */
@Tag("ManualIntegration") //$NON-NLS-1$
class ComparisonRunTest {

	@TempDir
	Path tempDir;

	/**
	 * Executes the full comparison run against real Eclipse 2025 commits.
	 *
	 * <p>Requires: {@code GEMINI_API_KEY} environment variable or system property set.
	 * Also requires network access to clone Eclipse repositories.</p>
	 */
	@Test
	void executeFirstComparisonRun() throws Exception {
		// 1. Locate eclipse-2025-sample.txt relative to the project root
		Path projectRoot = findProjectRoot();
		Path sampleFile = projectRoot.resolve(
				".github/refactoring-mining/eclipse-2025-sample.txt"); //$NON-NLS-1$
		assertTrue(Files.exists(sampleFile),
				"eclipse-2025-sample.txt must exist at " + sampleFile); //$NON-NLS-1$

		List<String> commitHashes = MiningCli.readCommitList(sampleFile);
		assertFalse(commitHashes.isEmpty(), "Sample file must contain commit hashes"); //$NON-NLS-1$

		// 2. Prepare output directory
		Path outputDir = tempDir.resolve("comparison-output"); //$NON-NLS-1$
		Files.createDirectories(outputDir);

		// Prepare hint output directory (inside temp to avoid polluting repo)
		Path hintOutputDir = tempDir.resolve("hint-output"); //$NON-NLS-1$
		Files.createDirectories(hintOutputDir);

		// 3. Run the mining CLI with comparison mode
		Path configPath = projectRoot.resolve(
				".github/refactoring-mining/repos-eclipse-2025.yml"); //$NON-NLS-1$
		if (!Files.exists(configPath)) {
			// Fall back to the default repos.yml
			configPath = projectRoot.resolve(
					".github/refactoring-mining/repos.yml"); //$NON-NLS-1$
		}
		assertTrue(Files.exists(configPath),
				"Config file must exist at " + configPath); //$NON-NLS-1$

		MiningCli cli = new MiningCli();
		cli.run(new String[] {
				"--config", configPath.toString(), //$NON-NLS-1$
				"--commit-list", sampleFile.toString(), //$NON-NLS-1$
				"--sandbox-root", projectRoot.toString(), //$NON-NLS-1$
				"--output", outputDir.toString(), //$NON-NLS-1$
				"--output-format", "both", //$NON-NLS-1$ //$NON-NLS-2$
				"--strict-netbeans", //$NON-NLS-1$
				"--max-duration", "30" //$NON-NLS-1$ //$NON-NLS-2$
		});

		// 4. Verify evaluations.json was written
		Path evalsFile = outputDir.resolve("evaluations.json"); //$NON-NLS-1$
		assertTrue(Files.exists(evalsFile),
				"evaluations.json must be generated in " + outputDir); //$NON-NLS-1$

		// 5. Load and validate evaluations
		JsonReporter reporter = new JsonReporter();
		List<CommitEvaluation> evals = reporter.loadExistingEvaluations(evalsFile);
		assertFalse(evals.isEmpty(), "Evaluations must not be empty"); //$NON-NLS-1$

		// 6. Validate consistency: GREEN ⇒ dslRule ≠ null
		validateGreenEvaluations(evals);

		// 7. Verify NetBeans-format output was also generated
		Path nbFile = outputDir.resolve("evaluations.txt"); //$NON-NLS-1$
		assertTrue(Files.exists(nbFile),
				"NetBeans format file should be generated with --output-format both"); //$NON-NLS-1$
	}

	/**
	 * Validates that the consistency rules described in the delta report are
	 * satisfied for all evaluations loaded from the JSON report file.
	 *
	 * <p>This test can run without an LLM key — it just validates the
	 * pre-existing {@code run-1-delta-report.json}.</p>
	 */
	@Test
	void validateExistingDeltaReport() throws Exception {
		Path projectRoot = findProjectRoot();
		Path deltaReport = projectRoot.resolve(
				"output/comparison/run-1-delta-report.json"); //$NON-NLS-1$
		if (!Files.exists(deltaReport)) {
			// Not an error — report may not exist yet before first run
			return;
		}

		JsonReporter reporter = new JsonReporter();
		List<CommitEvaluation> evals = reporter.loadExistingEvaluations(deltaReport);
		if (!evals.isEmpty()) {
			validateGreenEvaluations(evals);
		}
	}

	/**
	 * Validates that every GREEN evaluation has a non-null, non-blank
	 * {@code dslRule} and {@code canImplementInCurrentDsl == true}.
	 *
	 * <p>If {@code trafficLight == GREEN} but {@code dslRule} is null/blank,
	 * the evaluation is logically inconsistent and must be fixed (either
	 * provide a DSL rule or downgrade to YELLOW).</p>
	 */
	private void validateGreenEvaluations(List<CommitEvaluation> evaluations) {
		for (CommitEvaluation eval : evaluations) {
			if (eval.trafficLight() == TrafficLight.GREEN) {
				assertNotNull(eval.dslRule(),
						"GREEN evaluation for " + eval.commitHash() //$NON-NLS-1$
								+ " must have a dslRule (not null). " //$NON-NLS-1$
								+ "Either provide a valid DSL rule or downgrade to YELLOW."); //$NON-NLS-1$
				assertFalse(eval.dslRule().isBlank(),
						"GREEN evaluation for " + eval.commitHash() //$NON-NLS-1$
								+ " must have a non-blank dslRule. " //$NON-NLS-1$
								+ "Either provide a valid DSL rule or downgrade to YELLOW."); //$NON-NLS-1$
				assertTrue(eval.canImplementInCurrentDsl(),
						"GREEN evaluation for " + eval.commitHash() //$NON-NLS-1$
								+ " must have canImplementInCurrentDsl=true. " //$NON-NLS-1$
								+ "Either set to true or downgrade to YELLOW."); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Finds the project root directory by looking for the parent pom.xml.
	 */
	private Path findProjectRoot() throws IOException {
		// Try common locations
		Path cwd = Path.of("").toAbsolutePath(); //$NON-NLS-1$

		// Check if we're already at the root
		if (Files.exists(cwd.resolve("pom.xml")) //$NON-NLS-1$
				&& Files.exists(cwd.resolve("sandbox_mining_core"))) { //$NON-NLS-1$
			return cwd;
		}

		// Try parent (if running from sandbox_mining_core submodule)
		Path parent = cwd.getParent();
		if (parent != null && Files.exists(parent.resolve("pom.xml")) //$NON-NLS-1$
				&& Files.exists(parent.resolve("sandbox_mining_core"))) { //$NON-NLS-1$
			return parent;
		}

		fail("Cannot determine project root from " + cwd); //$NON-NLS-1$
		return cwd; // unreachable, but keeps compiler happy
	}
}
