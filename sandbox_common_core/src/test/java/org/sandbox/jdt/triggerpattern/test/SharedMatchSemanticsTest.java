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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/** The editor and batch matcher must bind the same original source nodes. */
class SharedMatchSemanticsTest {

	@Test
	void importedCastHasTheSameMeaningInSingleAndBatchMatching() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { Object cast(Object input) { return (List) input; } }
				""");
		assertEquivalent(cu, Pattern.of("(java.util.List) $value", PatternKind.EXPRESSION), 1);
	}

	@Test
	void declarationKindIsAvailableInBothExecutionPaths() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { void copy(List source) { List items = source; } }
				""");
		assertEquivalent(cu, Pattern.of("java.util.List $name = $value;", PatternKind.DECLARATION), 1);
	}

	@Test
	void typeHeaderKindIsAvailableInBothExecutionPaths() {
		CompilationUnit cu = parse("class Example { int value; void run() {} }");
		assertEquivalent(cu, Pattern.of("class $name {}", PatternKind.TYPE_DECLARATION), 1);
	}

	@Test
	void repeatedPlaceholdersRejectDifferentExpressionsInBothPaths() {
		CompilationUnit cu = parse("""
				class Example { int add(int a, int b) { int x = a + a; return a + b; } }
				""");
		assertEquivalent(cu, Pattern.of("$x + $x", PatternKind.EXPRESSION), 1);
	}

	@Test
	void argumentAndBlockVariadicsKeepOriginalNodesInBothPaths() {
		CompilationUnit cu = parse("""
				class Example { int f() { before(); consume(1, 2, 3); return 4; }
				  void before() {} void consume(int... values) {} }
				""");
		List<Match> calls = assertEquivalent(cu, Pattern.of("consume($args$)", PatternKind.METHOD_CALL), 1);
		assertEquals(3, calls.get(0).getListBinding("$args$").size());
		List<Match> blocks = assertEquivalent(cu, Pattern.of("{ $before$; return $value; }", PatternKind.BLOCK), 1);
		assertEquals(2, blocks.get(0).getListBinding("$before$").size());
	}

	@Test
	void statementSequenceRetainsResolvedBindingsAndOriginalIdentity() {
		String source = """
				import java.util.List;
				class Example { int size(List<String> values) {
				  int count = values.size();
				  return count;
				} }
				""";
		CompilationUnit cu = parse(source);
		List<Match> matches = assertEquivalent(cu,
				Pattern.of("{ int $count = $receiver.size(); return $count; }", PatternKind.STATEMENT_SEQUENCE), 1);
		Match match = matches.get(0);
		Expression receiver = (Expression) match.getBinding("$receiver");
		assertNotNull(receiver.resolveTypeBinding());
		assertFalse(receiver.resolveTypeBinding().isRecovered());
		assertEquals("java.util.List", receiver.resolveTypeBinding().getErasure().getQualifiedName());
		MethodInvocation invocation = (MethodInvocation) receiver.getParent();
		assertNotNull(invocation.resolveMethodBinding());
		assertFalse(invocation.resolveMethodBinding().isRecovered());
		assertEquals("java.util.List", invocation.resolveMethodBinding().getDeclaringClass().getErasure().getQualifiedName());
		assertEquals(source.indexOf("int count"), match.getOffset());
		assertEquals("int count = values.size();\n  return count;",
				source.substring(match.getOffset(), match.getOffset() + match.getLength()));
		assertSame(match.getMatchedNode(), match.getBinding("$_"));
	}

	@Test
	void sequenceCanCaptureZeroOrSeveralOriginalStatements() {
		CompilationUnit cu = parse("""
				class Example { void run() {
				  begin(); end();
				  begin(); work(); work(); end();
				} void begin() {} void end() {} void work() {} }
				""");
		List<Match> matches = assertEquivalent(cu,
				Pattern.of("{ begin(); $middle$; end(); }", PatternKind.STATEMENT_SEQUENCE), 3);
		assertTrue(matches.stream().anyMatch(match -> match.getListBinding("$middle$").isEmpty()));
		assertTrue(matches.stream().anyMatch(match -> match.getListBinding("$middle$").size() == 2));
	}

	@Test
	void resolvedShadowingCannotMasqueradeAsJavaLangType() {
		CompilationUnit cu = parse("""
				class Example { static class String {} Object make() { return new String(); } }
				""");
		assertEquivalent(cu, Pattern.of("new java.lang.String()", PatternKind.CONSTRUCTOR), 0);
	}

	@Test
	void resolvedLocalReceiverCannotMasqueradeAsImportedStaticType() {
		CompilationUnit cu = parse("""
				import java.util.Collections;
				class Example { static class Other { Object emptyList() { return null; } }
				  Object make(Other Collections) { return Collections.emptyList(); } }
				""");
		assertEquivalent(cu, Pattern.of("java.util.Collections.emptyList()", PatternKind.METHOD_CALL), 0);
	}

	@Test
	void qualifiedFieldReceiversRetainTheirResolvedIdentity() {
		CompilationUnit cu = parse("class Example { void print() { System.out.println(\"hello\"); } }");
		assertEquivalent(cu, Pattern.of("java.lang.System.out.println($message)", PatternKind.METHOD_CALL), 1);
	}

	@Test
	void constructorPatternDoesNotDiscardAnAnonymousClassBody() {
		CompilationUnit cu = parse("""
				class Example { Object make() { return new java.util.ArrayList<String>() {
				  @Override public int size() { return 42; }
				}; } }
				""");
		assertEquivalent(cu, Pattern.of("new java.util.ArrayList<String>()", PatternKind.CONSTRUCTOR), 0);
	}

	private List<Match> assertEquivalent(CompilationUnit cu, Pattern pattern, int expectedCount) {
		List<Match> editor = new TriggerPatternEngine().findMatches(cu, pattern);
		TransformationRule rule = TransformationRule.of(pattern, List.of());
		List<Match> batch = new PatternIndex(List.of(rule)).findAllMatches(cu).getOrDefault(rule, List.of());
		assertEquals(expectedCount, editor.size(), "single-pattern matches");
		assertEquals(expectedCount, batch.size(), "batch matches");
		for (int i = 0; i < editor.size(); i++) {
			Match left = editor.get(i);
			Match right = batch.get(i);
			assertSame(left.getMatchedNode(), right.getMatchedNode());
			assertEquals(left.getOffset(), right.getOffset());
			assertEquals(left.getLength(), right.getLength());
			assertEquals(left.getBindings(), right.getBindings());
			for (Object binding : right.getBindings().values()) {
				if (binding instanceof ASTNode node) {
					assertSame(cu, node.getRoot(), "Bindings must belong to the original compilation unit");
				} else if (binding instanceof List<?> nodes) {
					for (Object item : nodes) {
						assertSame(cu, ((ASTNode) item).getRoot());
					}
				}
			}
		}
		return batch;
	}

	private CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setUnitName("Example.java");
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		var options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		assertEquals(0, java.util.Arrays.stream(cu.getProblems()).filter(IProblem::isError).count(),
				"Regression fixtures must compile with genuinely resolved bindings");
		return cu;
	}
}
