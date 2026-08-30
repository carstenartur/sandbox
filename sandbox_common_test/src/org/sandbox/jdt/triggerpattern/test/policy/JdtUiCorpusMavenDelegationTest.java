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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Prevents the compatibility launchers from becoming a second assertion
 * framework beside Maven and JUnit.
 *
 * @since 1.3.5
 */
public class JdtUiCorpusMavenDelegationTest {

	@Test
	public void pythonLaunchersOnlyDelegateToFocusedJUnitTests() throws IOException {
		Path root = repositoryRoot();
		String corpusLauncher = Files.readString(
				root.resolve("qa/upstream-jdt/verify_jdt_ui_corpus.py"), StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(corpusLauncher.contains("JdtUiCorpusEvidenceTest")); //$NON-NLS-1$
		assertTrue(corpusLauncher.contains("sandbox_common_test")); //$NON-NLS-1$
		assertTrue(corpusLauncher.contains("sandbox.jdt.ui.corpus.")); //$NON-NLS-1$
		assertFalse(corpusLauncher.contains("def fail")); //$NON-NLS-1$
		assertFalse(corpusLauncher.contains("baselineMustContain")); //$NON-NLS-1$
		assertFalse(corpusLauncher.contains("migratedMustContain")); //$NON-NLS-1$

		String contractLauncher = Files.readString(
				root.resolve("qa/upstream-jdt/verify_jdt_ui_contract.py"), StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(contractLauncher.contains("JdtUiCorpusEvidenceVerifierTest")); //$NON-NLS-1$
		assertTrue(contractLauncher.contains("sandbox_common_test")); //$NON-NLS-1$
		assertTrue(contractLauncher.contains("bash")); //$NON-NLS-1$
		assertFalse(contractLauncher.contains("def fail")); //$NON-NLS-1$
		assertFalse(contractLauncher.contains("EXPECTED_FILES")); //$NON-NLS-1$
		assertFalse(contractLauncher.contains("validate_verifier")); //$NON-NLS-1$

		String runner = Files.readString(
				root.resolve("qa/upstream-jdt/run-jdt-ui-before-after.sh"), StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(runner.contains("verify_jdt_ui_corpus.py")); //$NON-NLS-1$
		String workflow = Files.readString(
				root.resolve(".github/workflows/jdt-ui-junit4-strict-qa.yml"), StandardCharsets.UTF_8); //$NON-NLS-1$
		assertTrue(workflow.contains("verify_jdt_ui_contract.py")); //$NON-NLS-1$
	}

	private static Path repositoryRoot() {
		Path candidate = Path.of("").toAbsolutePath().normalize(); //$NON-NLS-1$
		while (candidate != null) {
			if (Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
					&& Files.isDirectory(candidate.resolve("qa/upstream-jdt"))) { //$NON-NLS-1$
				return candidate;
			}
			candidate = candidate.getParent();
		}
		throw new IllegalStateException("Could not locate the Sandbox repository root"); //$NON-NLS-1$
	}
}
