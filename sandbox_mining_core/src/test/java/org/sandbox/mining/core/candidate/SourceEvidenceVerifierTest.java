/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.mining.core.candidate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.llm.SourceEvidence;

class SourceEvidenceVerifierTest {

	// Original changed expression from eclipse.platform.text commit 22d9da227602ed8cbbb6257193c1e5867fefa910.
	private static final String BEFORE = "new RuntimeException()";
	private static final String AFTER = "new RuntimeException(\"Expected Exception for junit test\")";
	private static final String DSL = "new java.lang.RuntimeException()\n=> "
			+ "new java.lang.RuntimeException(\"Expected Exception for junit test\")\n;;";
	private static final String DIFF = """
			diff --git a/Processor.java b/Processor.java
			--- a/Processor.java
			+++ b/Processor.java
			@@ -1,3 +1,3 @@
			 void run() {
			-    throw new RuntimeException();
			+    throw new RuntimeException("Expected Exception for junit test");
			 }
			""";

	@Test
	void recordsMatchingHunkWithoutClaimingSemanticSafety() {
		var result = SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), DSL, DIFF);
		assertNull(result.problem());
		assertTrue(result.evidence().diffHunk().contains("@@ -1,3 +1,3 @@"));
		assertNull(SourceEvidenceVerifier.verify(result.evidence(), DSL, result.evidence().diffHunk()).problem());
	}

	@Test
	void correctQuotedHunkDoesNotExcuseInventedReplacementMessage() {
		String invented = DSL.replace("Expected Exception for junit test", "Unnamed runtime exception");
		var result = SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), invented, DIFF);
		assertNotNull(result.problem());
		assertTrue(result.problem().contains("literal"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"new RuntimeException(\"Unnamed runtime exception\")",
			"new RuntimeException(\"Expected  Exception for junit test\")", "void run()", "", "new RuntimeException"})
	void rejectsInventedOrUnchangedAfterEvidence(String after) {
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, after), DSL, DIFF).problem());
	}

	@Test
	void rejectsMissingOversizedOrWrongFileEvidence() {
		assertNotNull(SourceEvidenceVerifier.verify(null, DSL, DIFF).problem());
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Other.java", BEFORE, AFTER), DSL, DIFF).problem());
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", "x".repeat(513), AFTER), DSL, DIFF).problem());
	}

	@Test
	void doesNotCombineDifferentHunksOrFiles() {
		String first = DIFF.replace("new RuntimeException(\"Expected Exception for junit test\")", "new RuntimeException(\"different\")");
		String second = "@@ -10,1 +10,1 @@\n-old();\n+throw " + AFTER + ";\n";
		var evidence = new SourceEvidence("Processor.java", BEFORE, AFTER);
		assertNotNull(SourceEvidenceVerifier.verify(evidence, DSL, first + second).problem());
		assertNotNull(SourceEvidenceVerifier.verify(evidence, DSL, first + DIFF.replace("Processor.java", "Other.java")).problem());
	}

	@Test
	void commentsCannotSupplySourceOrReplacementLiterals() {
		String commentDiff = DIFF.replace("throw new RuntimeException();", "// new RuntimeException()")
				.replace("throw " + AFTER + ";", "// " + AFTER);
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), DSL, commentDiff).problem());
		String hiddenLiteral = DSL.replace("Expected Exception for junit test", "fabricated")
				+ "\n// Expected Exception for junit test";
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), hiddenLiteral, DIFF).problem());
	}

	@Test
	void blockCommentContextCannotTurnChangedCommentTextIntoJavaEvidence() {
		String commented = DIFF.replace(" void run() {", " /*").replace(" }", " */");
		assertNotNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), DSL, commented).problem());
	}

	@Test
	void preservesLiteralContentsWhileAllowingFormattingAndCrLf() {
		var evidence = new SourceEvidence("Processor.java", "new RuntimeException ( )", AFTER);
		assertNull(SourceEvidenceVerifier.verify(evidence, DSL, DIFF.replace("\n", "\r\n")).problem());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void unchangedQuotesCannotBorrowACommentOrUnrelatedCodeChange(boolean unrelatedCodeChange) {
		String before = "boolean a = items.size() == 0; boolean b = items.isEmpty(); int n = 1; // old";
		String after = before.replace("// old", "// new");
		if (unrelatedCodeChange) {
			after = after.replace("n = 1", "n = 2");
		}
		assertNotNull(checkCollectionEdit(before, after).problem());
	}

	@Test
	void findsChangedOccurrencesAfterUnchangedOccurrencesOfBothQuotes() {
		String common = "boolean a = items.size() == 0; boolean b = items.isEmpty(); ";
		assertNull(checkCollectionEdit(common + "boolean c = items.size() == 0;",
				common + "boolean c = items.isEmpty();").problem());
	}

	@Test
	void partialCommentAfterTheEvidenceDoesNotDiscardARealEdit() {
		String partial = DIFF.replace("-1,3 +1,3", "-1,5 +1,5") + " /**\n  * continued outside the hunk\n";
		assertNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), DSL, partial).problem());
	}

	@Test
	void earlierCommentEditDoesNotHideLaterJavaEvidence() {
		String prefix = " /*\n-old documentation\n+new documentation\n */\n";
		String mixed = DIFF.replace("@@ -1,3 +1,3 @@\n", "@@ -1,6 +1,6 @@\n" + prefix);
		assertNull(SourceEvidenceVerifier.verify(new SourceEvidence("Processor.java", BEFORE, AFTER), DSL, mixed).problem());
	}

	private static SourceEvidenceVerifier.Result checkCollectionEdit(String before, String after) {
		String diff = "diff --git a/T.java b/T.java\n--- a/T.java\n+++ b/T.java\n@@ -1,1 +1,1 @@\n-"
				+ before + "\n+" + after + "\n";
		return SourceEvidenceVerifier.verify(new SourceEvidence("T.java", "items.size() == 0", "items.isEmpty()"),
				"$x.size() == 0\n=> $x.isEmpty()\n;;", diff);
	}
}
