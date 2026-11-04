/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.mining.core.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.gson.Gson;

/** Replays exact candidate JSON from mining/candidates c34752745558b335813d6d161f3066fe2e5bf2c7. */
class RecordedCandidateVerificationTest {

	@ParameterizedTest
	@CsvSource({
		"19c23ad5cd8402f941e6d8a311b7c57f59e605b9b223c7600c0a958017a72e4b, DSL_PARSE",
		"266cfd8d6bcf951ed18e2f825523f37e4c37ea56e00236d56a4cf531ec1768b2, BEFORE_MATCH",
		"2e840dfde8742b8d667c3bcdd33fedd68efc7c1581bea69bb54002721fb686b4, DEPENDENCY_RESOLUTION",
		"6e5686737d852c4fe4c815308a9d55539950b2d9aafaa18cfbfb875dc51283ba, DEPENDENCY_RESOLUTION",
		"f6318480b2cfee42abeb5ac25349f7a531ba6d71123b5266039ccd5a29c45fc7, BEFORE_MATCH"
	})
	void recordedCandidatesRemainBlockedForAccurateReasons(String id, String expectedStage) throws Exception {
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(
				"/mining-20261104/" + id + "-candidate.json"), StandardCharsets.UTF_8)) {
			MiningCandidate candidate = new Gson().fromJson(reader, MiningCandidate.class);
			CandidateVerification result = new CandidateVerifier().verify(candidate);
			System.out.println(id + ": " + result.stage() + " — " + result.message());
			assertFalse(result.successful());
			assertEquals(expectedStage, result.stage().name(), result.message());
			assertNotNull(candidate.getSourceEvidence().diffHunk());
		}
	}

	@Test
	void publicTopLevelTypesUseTheirOwnFilenameInAllExamples() {
		MiningCandidate candidate = candidate();
		CandidateVerification result = new CandidateVerifier().verify(candidate);
		assertTrue(result.successful(), result.message());
	}

	@Test
	void filenameInferenceRetainsCompileAndNegativeGates() {
		MiningCandidate candidate = candidate();
		candidate.setBeforeExample(candidate.getBeforeExample() + " public class Other {}");
		assertEquals(CandidateVerification.Stage.BEFORE_PARSE, new CandidateVerifier().verify(candidate).stage());
		candidate = candidate();
		candidate.setNegativeExample("public class NearMiss { int m() { return 2 + 0; } }");
		assertEquals(CandidateVerification.Stage.NEGATIVE_MATCH, new CandidateVerifier().verify(candidate).stage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"wrong\"", "1 +" })
	void independentCompileErrorsTakePriorityOverMissingImports(String expression) {
		MiningCandidate candidate = candidate();
		candidate.setBeforeExample("import missing.Api; public class Positive { int m() { return " + expression + "; } }");
		CandidateVerification result = new CandidateVerifier().verify(candidate);
		assertFalse(result.successful());
		assertEquals(CandidateVerification.Stage.BEFORE_PARSE, result.stage(), result.message());
	}

	private static MiningCandidate candidate() {
		return new MiningCandidate("$x + 0\n=> $x\n;;",
				"/* public class Decoy {} */ public class Positive { int m() { return 1 + 0; } }",
				"public class Positive { int m() { return 1; } }",
				"public class NearMiss { int m() { return 1 + 2; } }",
				"test.sandbox-hint", "commit", "https://example.test/repo", "test", "test", "2026-11-04");
	}
}
