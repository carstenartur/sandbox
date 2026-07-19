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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/** Pins one real JDT-derived candidate as an offline end-to-end fixture. */
class RealCandidateFixtureTest {

	private static final String RESOURCE =
			"org/sandbox/mining/core/candidate/real-jdt-jls15-candidate.json"; //$NON-NLS-1$

	@Test
	void verifiesPinnedRealJdtCandidateWithoutLlmCall() throws Exception {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try (var stream = loader.getResourceAsStream(RESOURCE)) {
			assertNotNull(stream, "Pinned candidate fixture is missing"); //$NON-NLS-1$
			MiningCandidate candidate = new Gson().fromJson(
					new InputStreamReader(stream, StandardCharsets.UTF_8),
					MiningCandidate.class);

			assertEquals("5a1165f14f197472183f53e939b8c77ffcd965aa", //$NON-NLS-1$
					candidate.getSourceCommit());
			CandidateVerification result = new CandidateVerifier().verify(candidate);
			assertTrue(result.successful(), result.message());
			assertEquals(CandidateVerification.Stage.SUCCESS, result.stage());
			assertEquals(1, result.replacements());
		}
	}
}
