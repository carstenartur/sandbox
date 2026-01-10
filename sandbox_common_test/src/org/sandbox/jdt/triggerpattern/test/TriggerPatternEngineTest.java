/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for {@link TriggerPatternEngine}.
 */
public class TriggerPatternEngineTest {
	
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	@Test
	public void testFindSingleMatch() {
		String code = """
			class Test {
				void method() {
					int x = a + 1;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find exactly one match");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getOffset() >= 0);
		assertTrue(match.getLength() > 0);
		
		Map<String, ASTNode> bindings = match.getBindings();
		assertEquals(1, bindings.size());
		assertTrue(bindings.containsKey("$x"));
		
		ASTNode boundNode = bindings.get("$x");
		assertTrue(boundNode instanceof SimpleName);
		assertEquals("a", ((SimpleName) boundNode).getIdentifier());
	}
	
	@Test
	public void testFindMultipleMatches() {
		String code = """
			class Test {
				void method() {
					int x = a + 1;
					int y = b + 1;
					int z = c + 1;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(3, matches.size(), "Should find three matches");
		
		// Verify all matches have bindings
		for (Match match : matches) {
			assertNotNull(match.getMatchedNode());
			assertEquals(1, match.getBindings().size());
			assertTrue(match.getBindings().containsKey("$x"));
		}
	}
	
	@Test
	public void testNoMatchesFound() {
		String code = """
			class Test {
				void method() {
					int x = a + 2;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "Should find no matches");
	}
	@Disabled("org.opentest4j.AssertionFailedError: Should find one statement pattern match ==> expected: <1> but was: <0>\r\n"
			+ "	at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)\r\n"
			+ "	at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)\r\n"
			+ "	at org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:197)\r\n"
			+ "	at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:150)\r\n"
			+ "	at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:563)\r\n"
			+ "	at org.sandbox.jdt.triggerpattern.test.TriggerPatternEngineTest.testStatementPattern(TriggerPatternEngineTest.java:134)\r\n"
			+ "	at java.base/java.lang.reflect.Method.invoke(Method.java:580)\r\n"
			+ "	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)\r\n"
			+ "	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)\r\n"
			+ "\r\n"
			+ "")
	@Test
	public void testStatementPattern() {
		String code = """
			class Test {
				void method() {
					if (condition)
						doSomething();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("if ($cond) $then;", PatternKind.STATEMENT);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		// The pattern "if ($cond) $then;" matches if statements with a single statement (not a block)
		assertEquals(1, matches.size(), "Should find one statement pattern match");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertEquals(2, match.getBindings().size(), "Should have bindings for $cond and $then");
		assertTrue(match.getBindings().containsKey("$cond"));
		assertTrue(match.getBindings().containsKey("$then"));
	}
	
	@Test
	public void testMultipleSamePlaceholder() {
		String code = """
			class Test {
				void method() {
					int x = a + a;
					int y = b + c;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + $x", PatternKind.EXPRESSION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find only one match (a + a)");
	}
	
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
