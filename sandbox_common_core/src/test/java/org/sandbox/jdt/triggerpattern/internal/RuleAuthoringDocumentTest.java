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
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintBindingPolicy;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

class RuleAuthoringDocumentTest {

	@Test
	void importedDocumentRetainsMetadataCommentsGuardsAndEveryAlternativeVerbatim() {
		String dsl = """
				<!id: imported>
				<!binding-policy: required>
				<!include: shared.sandbox-hint>
				// Keep the condition and both alternatives.
				@id: encoding
				$x.getBytes("UTF-8") :: sourceVersionGE(7)
				=> $x.getBytes(java.nio.charset.StandardCharsets.UTF_8) :: sourceVersionGE(11)
				=> $x.getBytes(java.nio.charset.Charset.forName("UTF-8")) :: otherwise
				;;
				""".replace("\n", "\r\n");

		assertEquals(dsl, new RuleAuthoringDocument(dsl).content());
		assertEquals(dsl, new RuleAuthoringDocument(dsl).withMetadata(Map.of()).content());
	}

	@Test
	void javaChangeKeepsEveryLineAndLiteralAndRequiresBindings() throws Exception {
		String before = "byte[] bytes = text.getBytes(\"UTF-8\");\nreturn bytes;";
		String after = "byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);\nreturn bytes;";
		RuleAuthoringDocument document = RuleAuthoringDocument.fromChange(before, after, "sourceVersionGE(7)");

		assertTrue(document.content().contains(before + "\n:: sourceVersionGE(7)"));
		assertTrue(document.content().contains("=> " + after));
		assertFalse(document.content().contains("$text"));
		var parsed = new HintFileParser().parse(document.content());
		assertEquals(HintBindingPolicy.REQUIRED, parsed.getBindingPolicy());
		assertEquals(1, parsed.getRules().size());
		assertEquals(PatternKind.STATEMENT_SEQUENCE, parsed.getRules().getFirst().sourcePattern().getKind());
		assertTrue(parsed.getRules().getFirst().sourcePattern().getValue().contains("return bytes;"));
		assertTrue(parsed.getRules().getFirst().alternatives().getFirst().replacementPattern().contains("return bytes;"));
	}

	@Test
	void metadataEditDoesNotRewriteTheRuleOrLoseUneditedDirectives() {
		String body = "// comment\r\n$x.old()\r\n=> $x.modern() :: sourceVersionGE(11)\r\n=> $x.old() :: otherwise\r\n;;\r\n";
		var document = new RuleAuthoringDocument("<!id: previous>\r\n<!tags: team, migration>\r\n" + body);

		assertEquals("<!id: modern>\r\n<!tags: team, migration>\r\n" + body,
				document.withMetadata(Map.of("id", "modern")).content());
	}

	@Test
	void equalsSyntaxMetadataIsReplacedRatherThanShadowingTheExplicitEdit() throws Exception {
		String body = "$x.old()\n=> $x.modern()\n;;\n";
		String changed = new RuleAuthoringDocument("<!id=\"old\">\n" + body)
				.withMetadata(Map.of("id", "modern")).content();
		assertEquals("<!id: modern>\n" + body, changed);
		assertEquals("modern", new HintFileParser().parse(changed).getId());
	}

	@Test
	void clearingMetadataRemovesEveryActiveEqualsOrColonDeclarationButKeepsExamples() throws Exception {
		String examples = "/*\n<!id=\"example\">\n*/\n";
		String body = "$x.old()\n=> $x.modern()\n;;\n";
		String changed = new RuleAuthoringDocument(examples + "<!id=\"old\">\n<!id: old>\n" + body)
				.withMetadata(Map.of("id", "")).content();
		assertEquals(examples + body, changed);
		assertEquals(null, new HintFileParser().parse(changed).getId());
	}

	@Test
	void metadataCannotInjectRules() {
		assertThrows(IllegalArgumentException.class,
				() -> new RuleAuthoringDocument("$x\n;;\n").withMetadata(Map.of("id", "new>\n$x")));
	}

	@Test
	void metadataInsideCommentsDoesNotHideTheActiveDirective() {
		String commented = "/*\n<!id: example-only>\n*/\n";
		String rule = "$x.old()\n=> $x.modern()\n;;\n";
		assertEquals(commented + "<!id: modern>\n" + rule,
				new RuleAuthoringDocument(commented + "<!id: actual>\n" + rule)
						.withMetadata(Map.of("id", "modern")).content());
	}

	@Test
	void repeatedActiveBindingPoliciesStayConsistentAfterAnExplicitEdit() throws Exception {
		String dsl = "<!binding-policy: optional>\n<!binding-policy: optional>\n$x.old()\n=> $x.modern()\n;;\n";
		String edited = new RuleAuthoringDocument(dsl).withMetadata(Map.of("binding-policy", "required")).content();
		assertEquals(HintBindingPolicy.REQUIRED, new HintProgramParser().parseHintFile(edited).getBindingPolicy());
		assertFalse(edited.contains("optional"));
	}

	@Test
	void editingMultilineMetadataReplacesTheActiveDirective() throws Exception {
		String dsl = "<!description: First line\nsecond line>\n$x.old()\n=> $x.modern()\n;;\n";
		String edited = new RuleAuthoringDocument(dsl).withMetadata(Map.of("description", "Modern API")).content();
		assertEquals("Modern API", new HintFileParser().parse(edited).getDescription());
		assertFalse(edited.contains("second line"));
	}

	@Test
	void sourceLineCommentDoesNotConsumeTheGeneratedGuard() throws Exception {
		var draft = RuleAuthoringDocument.fromChange("old(); // reason", "modern();", "sourceVersionGE(11)");
		var rule = new HintFileParser().parse(draft.content()).getRules().getFirst();
		assertTrue(rule.sourceGuard() != null);
		assertTrue(draft.content().contains("// reason\n:: sourceVersionGE(11)"));
	}

	@Test
	void savedChangeIncludesWholeChangedLinesWithCrLfAndNoUnrelatedFileContext() {
		String before = "class Example {\r\n  void run() {\r\n    oldApi();\r\n    keep();\r\n  }\r\n}\r\n";
		String after = "class Example {\r\n  void run() {\r\n    modernApi();\r\n    another();\r\n    keep();\r\n  }\r\n}\r\n";
		var example = RuleAuthoringDocument.changedLines(before, after).orElseThrow();

		assertEquals("    oldApi();\r\n", example.before());
		assertEquals("    modernApi();\r\n    another();\r\n", example.after());
	}

	@Test
	void insertionAtTheBeginningDoesNotConsumeTheFollowingUnchangedLine() {
		var change = RuleAuthoringDocument.changedLines("keep();\n", "added();\nkeep();\n").orElseThrow();
		assertEquals("", change.before());
		assertEquals("added();\n", change.after());
	}

	@Test
	void deletionAtTheBeginningDoesNotConsumeTheFollowingUnchangedLine() {
		var change = RuleAuthoringDocument.changedLines("removed();\nkeep();\n", "keep();\n").orElseThrow();
		assertEquals("removed();\n", change.before());
		assertEquals("", change.after());
	}

	@Test
	void insertionAndDeletionAtTheBeginningKeepCrLfUnchangedContextOutsideTheDraft() {
		var inserted = RuleAuthoringDocument.changedLines("keep();\r\n", "added();\r\nkeep();\r\n").orElseThrow();
		var removed = RuleAuthoringDocument.changedLines("removed();\r\nkeep();\r\n", "keep();\r\n").orElseThrow();
		assertEquals(new RuleAuthoringDocument.ChangeExample("", "added();\r\n"), inserted);
		assertEquals(new RuleAuthoringDocument.ChangeExample("removed();\r\n", ""), removed);
	}

	@Test
	void unchangedDocumentHasNoChangeToGeneralize() {
		assertTrue(RuleAuthoringDocument.changedLines("same();\n", "same();\n").isEmpty());
	}

	@Test
	void textBlocksCannotBecomeAnAutomaticRuleUntilTheirWhitespaceIsPreserved() {
		String textBlock = "return \"\"\"\n  exact whitespace\n  \"\"\";";
		var exception = assertThrows(IllegalArgumentException.class,
				() -> RuleAuthoringDocument.fromChange(textBlock, "return modern();", ""));
		assertTrue(exception.getMessage().contains("text blocks"));
		assertThrows(IllegalArgumentException.class,
				() -> RuleAuthoringDocument.fromChange("return previous();", textBlock, ""));
	}

	@Test
	void tripleQuotesInCommentsAreNotMistakenForJavaTextBlocks() {
		var document = RuleAuthoringDocument.fromChange("old(); // \"\"\" is a comment", "modern();", "");
		assertTrue(document.content().contains("// \"\"\" is a comment"));
	}

	@Test
	void wildcardTypesDoNotHideUnsupportedJavaTextBlocks() {
		String java = "java.util.List<? extends Number> values = null;\nString text = \"\"\"\n  preserve exact spaces\n  \"\"\";";
		assertTrue(RuleAuthoringDocument.containsJavaTextBlock(java));
		assertThrows(IllegalArgumentException.class,
				() -> RuleAuthoringDocument.fromChange(java, "modern();", ""));
	}

	@Test
	void javaLexicalModeStillSkipsEscapedQuotesAndCommentedTextBlocks() {
		String java = "java.util.List<? extends Number> values = null;\nString quotes = \"\\\"\\\"\\\"\";\n// \"\"\" comment\n/* \"\"\" comment */";
		assertFalse(RuleAuthoringDocument.containsJavaTextBlock(java));
	}
}
