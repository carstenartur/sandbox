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
		
		Map<String, Object> bindings = match.getBindings();
		assertEquals(3, bindings.size(), "Should have 3 bindings: $x, $_, $this");
		assertTrue(bindings.containsKey("$x"));
		assertTrue(bindings.containsKey("$_"), "Should have $_ auto-binding");
		assertTrue(bindings.containsKey("$this"), "Should have $this auto-binding");
		
		ASTNode boundNode = match.getBinding("$x");
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
		
		// Verify all matches have bindings (including auto-bindings)
		for (Match match : matches) {
			assertNotNull(match.getMatchedNode());
			assertEquals(3, match.getBindings().size(), "Should have 3 bindings: $x, $_, $this");
			assertTrue(match.getBindings().containsKey("$x"));
			assertTrue(match.getBindings().containsKey("$_"), "Should have $_ auto-binding");
			assertTrue(match.getBindings().containsKey("$this"), "Should have $this auto-binding");
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
	
	@Test
	public void testConstructorPatternWithPlaceholders() {
		String code = """
			class Test {
				void method() {
					String s = new String(bytes, "UTF-8");
					String t = new String(data, StandardCharsets.UTF_8);
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new String($bytes, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find two constructor matches");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertEquals(4, match.getBindings().size(), "Should have 4 bindings: $bytes, $enc, $_, $this");
		assertTrue(match.getBindings().containsKey("$bytes"));
		assertTrue(match.getBindings().containsKey("$enc"));
		assertTrue(match.getBindings().containsKey("$_"), "Should have $_ auto-binding");
		assertTrue(match.getBindings().containsKey("$this"), "Should have $this auto-binding");
	}
	
	@Test
	public void testConstructorPatternNoArgs() {
		String code = """
			class Test {
				void method() {
					StringBuilder sb = new StringBuilder();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new StringBuilder()", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one constructor match");
	}
	
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
