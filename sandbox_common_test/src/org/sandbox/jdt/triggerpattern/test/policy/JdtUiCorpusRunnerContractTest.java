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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Tests process transport, not a substitute implementation of the corpus verdict. */
public class JdtUiCorpusRunnerContractTest {

	private static final String RUNNER = "qa/upstream-jdt/run-jdt-ui-before-after.sh"; //$NON-NLS-1$
	private static final String ADAPTER = "qa/upstream-jdt/run-jdt-ui-corpus-verifier.sh"; //$NON-NLS-1$
	private static final String SELECTOR =
			"org.sandbox.jdt.triggerpattern.test.policy.JdtUiCorpusEvidenceVerifierTest#configuredUpstreamEvidenceIsVerifiedByMaven"; //$NON-NLS-1$

	@TempDir
	Path temporaryDirectory;

	@Test
	public void runnerAndWorkflowsUseTheSameMavenJUnitAuthority() throws Exception {
		Path root = repositoryRoot();
		String runner = Files.readString(root.resolve(RUNNER), StandardCharsets.UTF_8);
		String adapter = Files.readString(root.resolve(ADAPTER), StandardCharsets.UTF_8);
		assertTrue(runner.contains("CORPUS_VERIFIER=\"$SCRIPT_DIR/run-jdt-ui-corpus-verifier.sh\"")); //$NON-NLS-1$
		assertTrue(runner.contains("MAVEN_BIN=\"$MAVEN_BIN\" bash \"$CORPUS_VERIFIER\"")); //$NON-NLS-1$
		assertTrue(adapter.contains(SELECTOR));
		assertTrue(adapter.contains("-pl sandbox_target,sandbox_common_test\n  -am\n  package\n)")); //$NON-NLS-1$
		for (String workflow : List.of("jdt-ui-junit4-strict-qa.yml", "upstream-jdt-migration-qa.yml")) { //$NON-NLS-1$ //$NON-NLS-2$
			String source = Files.readString(root.resolve(".github/workflows").resolve(workflow), //$NON-NLS-1$
					StandardCharsets.UTF_8);
			assertTrue(source.contains("-Dtest='JdtUiCorpus*Test'"), workflow); //$NON-NLS-1$
			assertFalse(source.contains("verify_jdt_ui_contract.py"), workflow); //$NON-NLS-1$
		}
		String allowlist = Files.readString(root.resolve(".github/repository-policy/python-files.allowlist"), //$NON-NLS-1$
				StandardCharsets.UTF_8);
		for (String legacy : List.of("verify_jdt_ui_contract.py", "verify_jdt_ui_corpus.py")) { //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(Files.exists(root.resolve("qa/upstream-jdt").resolve(legacy)), legacy); //$NON-NLS-1$
			assertFalse(runner.contains(legacy), legacy);
			assertFalse(adapter.contains(legacy), legacy);
			assertFalse(allowlist.contains(legacy), legacy);
		}
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	public void adapterPassesAllEvidencePathsWithoutSplittingSpaces() throws Exception {
		Path evidence = Files.createDirectories(temporaryDirectory.resolve("evidence with spaces")); //$NON-NLS-1$
		Path output = evidence.resolve("corpus-result.json"); //$NON-NLS-1$
		Path arguments = temporaryDirectory.resolve("arguments.bin"); //$NON-NLS-1$
		Path maven = executable("""
				printf '%s\\0' "$@" > "$RECORDED_ARGUMENTS"
				for argument in "$@"; do
				  case "$argument" in
				    -Dsandbox.junit.corpus.output=*)
				      printf '{"result":"PASS"}\\n' > "${argument#*=}" ;;
				  esac
				done
				"""); //$NON-NLS-1$

		assertEquals(0, invoke(maven, output, Map.of("RECORDED_ARGUMENTS", arguments.toString()))); //$NON-NLS-1$

		List<String> actual = Arrays.asList(Files.readString(arguments, StandardCharsets.UTF_8)
				.split(String.valueOf('\0')));
		for (String property : List.of("repository", "baseline", "contract", "changedFiles", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"checkReport", "applyReport")) { //$NON-NLS-1$ //$NON-NLS-2$
			assertTrue(actual.contains("-Dsandbox.junit.corpus." + property + '=' + input(property)), property); //$NON-NLS-1$
		}
		assertTrue(actual.contains("-Dsandbox.junit.corpus.output=" + output)); //$NON-NLS-1$
		assertTrue(actual.contains("-Dsandbox.junit.corpus.mode=strict")); //$NON-NLS-1$
		assertTrue(actual.contains("-Dtest=" + SELECTOR)); //$NON-NLS-1$
		assertTrue(actual.contains("-DskipTests=false")); //$NON-NLS-1$
		assertTrue(actual.contains("-Dmaven.test.skip=false")); //$NON-NLS-1$
		assertTrue(actual.contains(repositoryRoot().resolve("pom.xml").toString())); //$NON-NLS-1$
		assertTrue(actual.contains("sandbox_target,sandbox_common_test")); //$NON-NLS-1$
		assertEquals("package", actual.getLast()); //$NON-NLS-1$
		assertEquals("0", read(evidence.resolve("corpus-verification-maven-exit-code.txt")).strip()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(read(evidence.resolve("corpus-verification-command.txt")).contains(SELECTOR)); //$NON-NLS-1$
		assertTrue(Files.isRegularFile(evidence.resolve("logs/corpus-verification-maven.log"))); //$NON-NLS-1$
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	public void failedMavenVerdictIsNotHiddenByAnOldPass() throws Exception {
		Path output = temporaryDirectory.resolve("corpus-result.json"); //$NON-NLS-1$
		Files.writeString(output, "{\"result\":\"PASS\"}", StandardCharsets.UTF_8); //$NON-NLS-1$

		assertEquals(7, invoke(executable("echo deliberate-maven-failure >&2\nexit 7\n"), output, Map.of())); //$NON-NLS-1$
		assertFalse(Files.exists(output));
		assertEquals("7", read(temporaryDirectory.resolve("corpus-verification-maven-exit-code.txt")).strip()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(read(temporaryDirectory.resolve("process.log")).contains("deliberate-maven-failure")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	public void successfulMavenWithoutExecutingTheVerifierCannotReuseAnOldPass() throws Exception {
		Path output = temporaryDirectory.resolve("corpus-result.json"); //$NON-NLS-1$
		Files.writeString(output, "{\"result\":\"PASS\"}", StandardCharsets.UTF_8); //$NON-NLS-1$

		assertEquals(3, invoke(executable("exit 0\n"), output, Map.of())); //$NON-NLS-1$
		assertFalse(Files.exists(output));
		assertTrue(read(temporaryDirectory.resolve("process.log")).contains("produced no fresh evidence")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	public void successfulMavenWithAnEmptyResultIsRejected() throws Exception {
		Path output = temporaryDirectory.resolve("corpus-result.json"); //$NON-NLS-1$
		Path maven = executable("""
				for argument in "$@"; do
				  case "$argument" in
				    -Dsandbox.junit.corpus.output=*) : > "${argument#*=}" ;;
				  esac
				done
				"""); //$NON-NLS-1$
		assertEquals(3, invoke(maven, output, Map.of()));
		assertEquals(0, Files.size(output));
	}

	@Test
	@EnabledOnOs({ OS.LINUX, OS.MAC })
	public void runnersHaveValidBashSyntax() throws Exception {
		for (String script : List.of(RUNNER, ADAPTER)) {
			Path log = temporaryDirectory.resolve("syntax.log"); //$NON-NLS-1$
			Process process = new ProcessBuilder("bash", "-n", repositoryRoot().resolve(script).toString()) //$NON-NLS-1$ //$NON-NLS-2$
					.redirectErrorStream(true).redirectOutput(log.toFile()).start();
			try {
				assertTrue(process.waitFor(30, TimeUnit.SECONDS), script);
				assertEquals(0, process.exitValue(), () -> "Invalid Bash syntax in " + script); //$NON-NLS-1$
			} finally {
				process.destroyForcibly();
			}
		}
	}

	private Path executable(String body) throws IOException {
		Path path = temporaryDirectory.resolve("mock maven"); //$NON-NLS-1$
		Files.writeString(path, "#!/usr/bin/env bash\nset -euo pipefail\n" + body, StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(path.toFile().setExecutable(true), "The process fixture must be executable"); //$NON-NLS-1$
		return path;
	}

	private Path input(String property) {
		return temporaryDirectory.resolve("input with spaces").resolve(property); //$NON-NLS-1$
	}

	private int invoke(Path maven, Path output, Map<String, String> environment) throws Exception {
		List<String> command = new ArrayList<>(List.of("bash", repositoryRoot().resolve(ADAPTER).toString())); //$NON-NLS-1$
		command.addAll(List.of("--repository", input("repository").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--baseline-sources", input("baseline").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--contract", input("contract").toString(), "--mode", "strict", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"--changed-files", input("changedFiles").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--check-report", input("checkReport").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--apply-report", input("applyReport").toString(), //$NON-NLS-1$ //$NON-NLS-2$
				"--output", output.toString())); //$NON-NLS-1$
		ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectOutput(temporaryDirectory.resolve("process.log").toFile()); //$NON-NLS-1$
		builder.environment().put("MAVEN_BIN", maven.toString()); //$NON-NLS-1$
		builder.environment().putAll(environment);
		Process process = builder.start();
		try {
			assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Corpus process adapter did not finish"); //$NON-NLS-1$
			return process.exitValue();
		} finally {
			process.destroyForcibly();
		}
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	static Path repositoryRoot() throws IOException {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); //$NON-NLS-1$
		while (current != null) {
			if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isRegularFile(current.resolve(RUNNER))) { //$NON-NLS-1$
				return current;
			}
			current = current.getParent();
		}
		throw new IOException("Cannot locate the Sandbox repository root"); //$NON-NLS-1$
	}
}
