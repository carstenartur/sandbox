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
package org.sandbox.mining.core.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests canonical fingerprints for candidate and curated rules. */
class RuleFingerprintIndexTest {

	@TempDir
	Path tempDir;

	@Test
	void candidateFingerprintIgnoresFileMetadataAndComments() throws IOException {
		String candidateDsl = "$x + 0\n=> $x\n;;"; //$NON-NLS-1$
		String curatedDsl = """
				<!id: arithmetic>
				<!description: Curated arithmetic rules>
				<!severity: warning>

				// Existing reviewed rule
				$x + 0
				=> $x
				;;
				"""; //$NON-NLS-1$

		Path hint = tempDir.resolve("arithmetic.sandbox-hint"); //$NON-NLS-1$
		Files.writeString(hint, curatedDsl);
		Map<String, String> index = RuleFingerprintIndex.loadCurated(tempDir);

		String candidateFingerprint = RuleFingerprintIndex.fingerprintDsl(candidateDsl);
		assertEquals(1, index.size());
		assertTrue(index.containsKey(candidateFingerprint));
		assertEquals("arithmetic.sandbox-hint#rule 1", index.get(candidateFingerprint)); //$NON-NLS-1$
	}

	@Test
	void differentRulesHaveDifferentFingerprints() throws IOException {
		String addZero = RuleFingerprintIndex.fingerprintDsl("$x + 0\n=> $x\n;;"); //$NON-NLS-1$
		String multiplyOne = RuleFingerprintIndex.fingerprintDsl("$x * 1\n=> $x\n;;"); //$NON-NLS-1$

		assertTrue(!addZero.equals(multiplyOne));
	}
}
