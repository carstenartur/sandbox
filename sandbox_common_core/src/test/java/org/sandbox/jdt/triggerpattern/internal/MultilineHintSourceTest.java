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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

class MultilineHintSourceTest {

	@Test
	void bothJavaSidesAndAllAlternativeGuardsSurviveTheParserSerializerRoundTrip() throws Exception {
		String dsl = """
				<!id: multiline>
				@id: prepare-and-send
				$receiver.prepare();
				$receiver.send($message);
				:: sourceVersionGE(11)
				:: hasNoSideEffect($message)
				=> $receiver.sendPrepared($message);
				$receiver.finish();
				:: sourceVersionGE(17)
				=> $receiver.prepare();
				$receiver.send($message);
				:: otherwise
				;;
				""";
		var parser = new HintFileParser();
		var first = parser.parse(dsl).getRules().getFirst();
		var reparsed = parser.parse(new HintFileSerializer().serialize(parser.parse(dsl))).getRules().getFirst();

		assertEquals(PatternKind.STATEMENT_SEQUENCE, first.sourcePattern().getKind());
		assertEquals("$receiver.prepare();\n$receiver.send($message);", first.sourcePattern().getValue());
		assertInstanceOf(GuardExpression.And.class, first.sourceGuard());
		assertEquals(2, first.alternatives().size());
		assertTrue(first.alternatives().getFirst().replacementPattern().contains("$receiver.finish();"));
		assertTrue(first.alternatives().getLast().isOtherwise());
		assertEquals(first.sourcePattern().getValue(), reparsed.sourcePattern().getValue());
		assertEquals(first.sourceGuard(), reparsed.sourceGuard());
		assertEquals(first.alternatives(), reparsed.alternatives());
	}

	@Test
	void multilineHintOnlySourceKeepsTheWholeSequence() throws Exception {
		var rule = new HintFileParser().parse("first();\nsecond();\n;;\n").getRules().getFirst();
		assertEquals("first();\nsecond();", rule.sourcePattern().getValue());
		assertEquals(PatternKind.STATEMENT_SEQUENCE, rule.sourcePattern().getKind());
		assertTrue(rule.isHintOnly());
	}

	@Test
	void multilineInvocationRemainsAnExpressionRatherThanAStatementSequence() throws Exception {
		var rule = new HintFileParser().parse("$receiver.old(\n$message,\n7)\n=> $receiver.modern($message, 7)\n;;\n")
				.getRules().getFirst();
		assertEquals(PatternKind.METHOD_CALL, rule.sourcePattern().getKind());
		assertTrue(rule.sourcePattern().getValue().contains("\n$message,"));
	}

	@Test
	void sourceAndReplacementTextBlocksAreExplicitlyRejectedRatherThanTrimmed() {
		String textBlock = "return \"\"\"\n    meaningful indentation\n      more indentation\n    \"\"\";";
		var sourceError = assertThrows(HintFileParser.HintParseException.class,
				() -> new HintFileParser().parse(textBlock + "\n=> return modern();\n;;\n"));
		assertTrue(sourceError.getMessage().contains("text blocks"));
		var replacementError = assertThrows(HintFileParser.HintParseException.class,
				() -> new HintFileParser().parse("return previous();\n=> " + textBlock + "\n;;\n"));
		assertTrue(replacementError.getMessage().contains("text blocks"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"consume('(');",
			"consume(')');",
			"consume(\"\\\"(\");",
			"consume(\"/* ( :: notAGuard */\");",
			"consume(\"// ( :: notAGuard\");"
	})
	void javaLiteralsCannotHideOrAlterAStandaloneSourceGuard(String source) throws Exception {
		var rule = new HintFileParser().parse(source + "\n:: sourceVersionGE(99)\n=> modern();\n;;\n")
				.getRules().getFirst();
		assertEquals(source, rule.sourcePattern().getValue());
		var guard = assertInstanceOf(GuardExpression.FunctionCall.class, rule.sourceGuard());
		assertEquals("sourceVersionGE", guard.name());
		assertEquals(java.util.List.of("99"), guard.args());
	}

	@Test
	void javaCommentsDoNotChangeTheStandaloneGuardNesting() throws Exception {
		var rule = new HintFileParser().parse("consume($value); /* ( :: notAGuard */\n"
				+ "// ) :: notAGuard\n:: sourceVersionGE(99)\n=> modern();\n;;\n")
				.getRules().getFirst();
		assertEquals("consume($value);", rule.sourcePattern().getValue());
		assertNotNull(rule.sourceGuard());
	}

	@Test
	void methodReferencesAreJavaRatherThanGuardSeparators() throws Exception {
		var rule = new HintFileParser().parse("String::trim\n:: sourceVersionGE(99)\n"
				+ "=> String::strip\n:: sourceVersionGE(11)\n;;\n").getRules().getFirst();
		assertEquals("String::trim", rule.sourcePattern().getValue());
		assertEquals("String::strip", rule.alternatives().getFirst().replacementPattern());
		assertNotNull(rule.sourceGuard());
		assertNotNull(rule.alternatives().getFirst().condition());
	}

	@ParameterizedTest
	@ValueSource(strings = { "$x instanceof String", "enabled", "enabled && sourceVersionGE(99)",
			"!hasNoSideEffect($x)", "(hasNoSideEffect($x) || sourceVersionGE(99))" })
	void methodReferenceRecognitionPreservesExistingInlineGuardForms(String guard) throws Exception {
		var rule = new HintFileParser().parse("$x.old() :: " + guard + "\n=> modern();\n;;\n")
				.getRules().getFirst();
		assertEquals("$x.old()", rule.sourcePattern().getValue());
		assertEquals(new GuardExpressionParser().parse(guard), rule.sourceGuard());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"if ($ready) { after(); }",
			"while ($ready) { after(); }",
			"for (; $ready;) { after(); }",
			"switch ($value) { default: after(); }",
			"try { after(); } finally { finish(); }",
			"synchronized ($lock) { after(); }",
			"{ after(); }"
	})
	void aSequenceEndingInACompoundStatementCannotBecomeJustItsFirstCall(String compound) throws Exception {
		String source = "before();\n" + compound;
		var rule = new HintFileParser().parse(source + "\n=> modern();\n;;\n").getRules().getFirst();
		assertEquals(PatternKind.STATEMENT_SEQUENCE, rule.sourcePattern().getKind());
		var javaParser = ASTParser.newParser(AST.getJLSLatest());
		javaParser.setSource("class Example { void run() { before(); } }".toCharArray());
		var compilationUnit = (CompilationUnit) javaParser.createAST(null);
		assertTrue(new TriggerPatternEngine().findMatches(compilationUnit, rule.sourcePattern()).isEmpty(),
				"The sequence must never match a lone before() call");
	}

	@Test
	void aConstructorPrefixCannotDiscardTheRestOfACompoundEndingSequence() throws Exception {
		var rule = new HintFileParser().parse("new Example();\nif ($ready) { after(); }\n"
				+ "=> modern();\n;;\n").getRules().getFirst();
		assertEquals(PatternKind.STATEMENT_SEQUENCE, rule.sourcePattern().getKind());
	}

	@ParameterizedTest
	@ValueSource(strings = { "int value = before();", "int value;", "String value = before();" })
	void methodDeclarationPriorityCannotConsumeASequenceBeginningWithAVariable(String declaration) throws Exception {
		var rule = new HintFileParser().parse(declaration + "\nif ($ready) { after(); }\n"
				+ "=> modern();\n;;\n").getRules().getFirst();
		assertEquals(PatternKind.STATEMENT_SEQUENCE, rule.sourcePattern().getKind());
	}

	@ParameterizedTest
	@ValueSource(strings = { "if ($ready) { after(); }", "after();", "{ after(); }" })
	void leadingBlockCannotHideTrailingStatements(String followingStatement) {
		var error = assertThrows(HintFileParser.HintParseException.class,
				() -> new HintFileParser().parse("{ before(); }\n" + followingStatement + "\n=> modern();\n;;\n"));
		assertTrue(error.getMessage().contains("complete block"));
	}

	@Test
	void aBlockFollowedByAnotherStatementCannotAuthorizeItsLeadingBlockAlone() throws Exception {
		try {
			var rule = new HintFileParser().parse("{ before(); }\nif ($ready) { after(); }\n=> modern();\n;;\n")
					.getRules().getFirst();
			var javaParser = ASTParser.newParser(AST.getJLSLatest());
			javaParser.setSource("class Example { void run() { { before(); } } }".toCharArray());
			var java = (CompilationUnit) javaParser.createAST(null);
			assertTrue(new TriggerPatternEngine().findMatches(java, rule.sourcePattern()).isEmpty(),
					"A rule containing a trailing if must not match only the leading block");
		} catch (HintFileParser.HintParseException rejectedIncompleteBlock) {
			assertTrue(rejectedIncompleteBlock.getMessage().contains("complete block"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{ before(); }", "{ { before(); } }", "{ consume(\"}\"); }",
			"{ before(); /* } inside comment */ }", "{ $body$; }" })
	void completeSingleBlocksRemainBlockPatterns(String block) throws Exception {
		var rule = new HintFileParser().parse(block + "\n=> modern();\n;;\n").getRules().getFirst();
		assertEquals(PatternKind.BLOCK, rule.sourcePattern().getKind());
	}
}
