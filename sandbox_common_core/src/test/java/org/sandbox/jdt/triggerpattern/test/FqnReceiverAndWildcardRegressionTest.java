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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/** Regressions for recursively matched wildcard bounds and resolved receivers. */
class FqnReceiverAndWildcardRegressionTest {

	@Test
	void upperWildcardBoundBindsItsPlaceholder() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { Object cast(Object value) { return (List<? extends Number>) value; } }
				""");
		Match match = assertMatches(cu, "(java.util.List<? extends $T>) $value", PatternKind.EXPRESSION, 1).get(0);
		assertEquals("Number", match.getBinding("$T").toString());
		assertSame(cu, ((ASTNode) match.getBinding("$T")).getRoot());
	}

	@Test
	void lowerWildcardBoundBindsItsPlaceholder() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { Object cast(Object value) { return (List<? super Number>) value; } }
				""");
		Match match = assertMatches(cu, "(java.util.List<? super $T>) $value", PatternKind.EXPRESSION, 1).get(0);
		assertEquals("Number", match.getBinding("$T").toString());
	}

	@Test
	void wildcardBoundDirectionRemainsSignificant() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { Object cast(Object value) { return (List<? super Number>) value; } }
				""");
		assertMatches(cu, "(java.util.List<? extends $T>) $value", PatternKind.EXPRESSION, 0);
	}

	@Test
	void qualifiedReceiverCanBindItsOwnerPlaceholder() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { static class Holder { List<String> field; }
				  void clear(Holder holder) { holder.field.clear(); } }
				""");
		Match match = assertMatches(cu, "$owner.field.clear()", PatternKind.METHOD_CALL, 1).get(0);
		assertEquals("holder", match.getBinding("$owner").toString());
		assertSame(cu, ((ASTNode) match.getBinding("$owner")).getRoot());
	}

	@Test
	void literalDottedValueReceiverDoesNotBypassSemanticIdentity() {
		CompilationUnit cu = parse("""
				import java.util.List;
				class Example { static class Holder { List<String> field; }
				  void clear(Holder holder) { holder.field.clear(); } }
				""");
		// A literal dotted name can also denote a type in package holder.
		// Value receivers use the explicit owner placeholder tested above.
		assertMatches(cu, "holder.field.clear()", PatternKind.METHOD_CALL, 0);
	}

	@Test
	void relativeQualifiedApiReceiverMatchesItsResolvedIdentity() {
		CompilationUnit cu = parse("class Example { void print() { System.out.println(\"hello\"); } }");
		assertMatches(cu, "System.out.println($message)", PatternKind.METHOD_CALL, 1);
	}

	@Test
	void fullyQualifiedApiReceiverRejectsAResolvedShadow() {
		CompilationUnit cu = parse("""
				class Example { static class Other { java.io.PrintStream out; }
				  void print(Other System) { System.out.println("hello"); } }
				""");
		assertMatches(cu, "java.lang.System.out.println($message)", PatternKind.METHOD_CALL, 0);
	}

	@Test
	void inheritedStaticMethodUsesTheResolvedReceiverType() {
		CompilationUnit cu = parse("""
				import java.util.GregorianCalendar;
				class Example { Object calendar() { return GregorianCalendar.getInstance(); } }
				""");
		Match match = assertMatches(cu, "java.util.GregorianCalendar.getInstance()", PatternKind.METHOD_CALL, 1).get(0);
		MethodInvocation invocation = (MethodInvocation) match.getMatchedNode();
		assertEquals("java.util.Calendar", invocation.resolveMethodBinding().getDeclaringClass().getQualifiedName());
	}

	private List<Match> assertMatches(CompilationUnit cu, String text, PatternKind kind, int expectedCount) {
		Pattern pattern = Pattern.of(text, kind);
		List<Match> single = new TriggerPatternEngine().findMatches(cu, pattern);
		TransformationRule rule = TransformationRule.of(pattern, List.of());
		List<Match> batch = new PatternIndex(List.of(rule)).findAllMatches(cu).getOrDefault(rule, List.of());
		assertEquals(expectedCount, single.size(), "single-pattern matches for " + text);
		assertEquals(expectedCount, batch.size(), "batch matches for " + text);
		for (int i = 0; i < single.size(); i++) {
			assertSame(single.get(i).getMatchedNode(), batch.get(i).getMatchedNode());
			assertEquals(single.get(i).getBindings(), batch.get(i).getBindings());
			assertTrue(single.get(i).getUnknownSemanticRequirements().isEmpty(), "Resolved fixture has no unknown requirement");
			assertTrue(batch.get(i).getUnknownSemanticRequirements().isEmpty(), "Resolved batch fixture has no unknown requirement");
		}
		assertEquals(expectedCount, new TriggerPatternEngine().findExecutableMatches(cu, pattern).size());
		return single;
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
		assertEquals(0, Arrays.stream(cu.getProblems()).filter(IProblem::isError).count(),
				"Regression fixtures must compile with genuinely resolved bindings");
		return cu;
	}
}
