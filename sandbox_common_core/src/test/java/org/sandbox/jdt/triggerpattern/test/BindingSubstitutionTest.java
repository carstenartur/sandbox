/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

class BindingSubstitutionTest {

	@Test
	void overlappingNamesAreReplacedAsWholeTokens() throws Exception {
		assertReplacement("old($x, $xyz)", "modern($xyz, $x)",
				"old(first, second)", "modern(second, first)");
	}

	@Test
	void sourceIdentifiersContainingDollarSignsArePreserved() throws Exception {
		assertReplacement("old($x)", "modern($x)", "old(customer$value)", "modern(customer$value)");
	}

	@Test
	void sourceEscapedStringLiteralsArePreserved() throws Exception {
		assertReplacement("old($x)", "modern($x)", "old(\"C:\\\\temp\")", "modern(\"C:\\\\temp\")");
	}

	@Test
	void placeholderTextInsideStringLiteralsIsNotCode() throws Exception {
		assertReplacement("old($x)", "modern(\"$x\", $x)", "old(value)", "modern(\"$x\", value)");
	}

	@Test
	void bindingsAreNotExpandedAgainWhenSourceNamesLookLikePlaceholders() throws Exception {
		assertReplacement("old($x, $xyz)", "modern($xyz, $x)", "old($xyz, value)", "modern(value, $xyz)");
	}

	@Test
	void compoundExpressionsRetainTheirGroupingAndEvaluation() throws Exception {
		assertReplacement("old($x)", "$x * 2", "old(a + b)", "(a + b) * 2");
		assertReplacement("old($x)", "$x * 2", "old(test ? a : b)", "(test ? a : b) * 2");
		assertReplacement("old($x)", "$x * 2", "old((int) number)", "((int)number) * 2");
		assertReplacement("old($x)", "-$x", "old(-number)", "-(-number)");
		assertReplacement("old($x)", "$x * 2", "old(number = other)", "(number=other) * 2");
		assertReplacement("old($x)", "$x.toString()", "old(test ? a : b)", "(test ? a : b).toString()");
	}

	@Test
	void variadicStatementsUseStatementSeparators() throws Exception {
		String before = "before(); first(); second(); after();";
		TransformationRule rule = TransformationRule.of(
				Pattern.of("{ before(); $body$; after(); }", PatternKind.BLOCK),
				List.of(new RewriteAlternative("{ $body$; }", null)));
		var file = new HintFileParser().parse("$x\n;;\n");
		var results = new BatchTransformationProcessor(file, List.of(rule))
				.process(parse(before));
		assertEquals(1, results.size());
		assertEquals("{ first();\nsecond(); }", results.getFirst().replacement());
	}

	private void assertReplacement(String sourcePattern, String replacement, String source, String expected)
			throws Exception {
		var file = new HintFileParser().parse(sourcePattern + "\n=> " + replacement + "\n;;\n");
		var results = new BatchTransformationProcessor(file).process(parse(source + ";"));
		assertEquals(1, results.size());
		assertEquals(expected, results.getFirst().replacement());
	}

	private CompilationUnit parse(String statements) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(("class Example { void run() { " + statements + " } }").toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
