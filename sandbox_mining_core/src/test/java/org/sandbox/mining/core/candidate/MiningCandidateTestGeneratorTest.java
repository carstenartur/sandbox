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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link MiningCandidateTestGenerator}.
 */
class MiningCandidateTestGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void testBuildClassName() {
		MiningCandidate candidate = new MiningCandidate();
		candidate.setSourceCommit("abc1234567890"); //$NON-NLS-1$
		candidate.setCategory("string-modernization"); //$NON-NLS-1$

		String className = MiningCandidateTestGenerator.buildClassName(candidate);
		// Category first char is uppercased to form a valid Java identifier prefix
		assertEquals("Generated_abc1234_String_modernization_CandidateTest", className); //$NON-NLS-1$
	}

	@Test
	void testBuildClassNameNullCommit() {
		MiningCandidate candidate = new MiningCandidate();
		candidate.setSourceCommit(null);
		candidate.setCategory("test-category"); //$NON-NLS-1$

		String className = MiningCandidateTestGenerator.buildClassName(candidate);
		assertTrue(className.startsWith("Generated_unknown_"), "Should use 'unknown' for null commit"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void testBuildClassNameNullCategory() {
		MiningCandidate candidate = new MiningCandidate();
		candidate.setSourceCommit("abc1234567890"); //$NON-NLS-1$
		candidate.setCategory(null);

		String className = MiningCandidateTestGenerator.buildClassName(candidate);
		// 'unknown' is also uppercased to 'Unknown'
		assertTrue(className.endsWith("_Unknown_CandidateTest"), "Should use 'Unknown' for null category"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void testGenerateTestCreatesFile() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);

		assertNotNull(generated);
		assertTrue(Files.exists(generated), "Generated file should exist"); //$NON-NLS-1$
		assertTrue(generated.getFileName().toString().endsWith(".java"), "Should be a .java file"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void testGeneratedTestContainsPackageDeclaration() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertTrue(content.contains("package org.sandbox.jdt.triggerpattern.generated;"), //$NON-NLS-1$
				"Should contain package declaration"); //$NON-NLS-1$
	}

	@Test
	void testGeneratedTestContainsDslParsesTest() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertTrue(content.contains("testDslParsesSuccessfully"), //$NON-NLS-1$
				"Should contain DSL parse test"); //$NON-NLS-1$
		assertTrue(content.contains("HintFileParser"), "Should use HintFileParser"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(content.contains("BatchTransformationProcessor"), "Should use BatchTransformationProcessor"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void testGeneratedTestContainsBeforeExampleTest() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertTrue(content.contains("testBeforeExampleMatches"), //$NON-NLS-1$
				"Should contain before example test"); //$NON-NLS-1$
	}

	@Test
	void testGeneratedTestContainsNegativeExampleTest() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertTrue(content.contains("testNegativeExampleDoesNotMatch"), //$NON-NLS-1$
				"Should contain negative example test"); //$NON-NLS-1$
	}

	@Test
	void testGeneratedTestSkipsBeforeExampleWhenBlank() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = new MiningCandidate();
		candidate.setSourceCommit("abc1234567890"); //$NON-NLS-1$
		candidate.setCategory("test"); //$NON-NLS-1$
		candidate.setDslRule("$x + 0\n=> $x\n;;"); //$NON-NLS-1$
		// No beforeExample/afterExample set

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertFalse(content.contains("testBeforeExampleMatches"), //$NON-NLS-1$
				"Should not include before example test when blank"); //$NON-NLS-1$
		// Should still have the parse test
		assertTrue(content.contains("testDslParsesSuccessfully")); //$NON-NLS-1$
	}

	@Test
	void testGeneratedTestContainsSourceCommitComment() throws IOException {
		MiningCandidateTestGenerator generator = new MiningCandidateTestGenerator();
		MiningCandidate candidate = createCandidate("abc1234567890"); //$NON-NLS-1$

		Path generated = generator.generateTest(candidate, tempDir);
		String content = Files.readString(generated, StandardCharsets.UTF_8);

		assertTrue(content.contains("abc1234567890"), //$NON-NLS-1$
				"Should contain source commit in comment"); //$NON-NLS-1$
	}

	@Test
	void testEscapeForTextBlock() {
		assertEquals("hello", MiningCandidateTestGenerator.escapeForTextBlock("hello")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("", MiningCandidateTestGenerator.escapeForTextBlock(null)); //$NON-NLS-1$
		assertEquals("", MiningCandidateTestGenerator.escapeForTextBlock("")); //$NON-NLS-1$ //$NON-NLS-2$
		// Triple quotes should be escaped
		String input = "foo \"\"\" bar"; //$NON-NLS-1$
		String escaped = MiningCandidateTestGenerator.escapeForTextBlock(input);
		assertFalse(escaped.contains("\"\"\""), "Triple quotes should be escaped"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- helpers ---

	private MiningCandidate createCandidate(String commitHash) {
		return new MiningCandidate(
				"$x + 0\n=> $x\n;;", //$NON-NLS-1$
				"class T { int m() { return 1 + 0; } }", //$NON-NLS-1$
				"class T { int m() { return 1; } }", //$NON-NLS-1$
				"class T { int m() { return 1 + 2; } }", //$NON-NLS-1$
				"performance.sandbox-hint", //$NON-NLS-1$
				commitHash,
				"https://github.com/example/repo", //$NON-NLS-1$
				"arithmetic", //$NON-NLS-1$
				"Remove addition of zero", //$NON-NLS-1$
				"2026-01-01T00:00:00Z"); //$NON-NLS-1$
	}
}
