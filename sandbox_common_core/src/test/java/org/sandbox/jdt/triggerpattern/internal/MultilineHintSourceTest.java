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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

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
}
