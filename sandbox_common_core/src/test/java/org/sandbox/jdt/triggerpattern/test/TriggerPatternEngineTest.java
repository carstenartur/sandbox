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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
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
		assertEquals(4, match.getBindings().size(), "Should have 4 bindings: $cond, $then, $_, $this");
		assertTrue(match.getBindings().containsKey("$cond"));
		assertTrue(match.getBindings().containsKey("$then"));
		assertTrue(match.getBindings().containsKey("$_"), "Should have $_ auto-binding");
		assertTrue(match.getBindings().containsKey("$this"), "Should have $this auto-binding");
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
	
	@Test
	public void testFqnPatternMatchesSimpleNameUsage() {
		// Source code uses imported simple name "Charset"
		String code = """
			import java.nio.charset.Charset;
			class Test {
				void method() {
					Charset cs = Charset.forName("UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN "java.nio.charset.Charset.forName(...)"
		Pattern pattern = new Pattern("java.nio.charset.Charset.forName($arg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "FQN pattern should match simple-name usage in source");
	}
	
	@Test
	public void testSimpleNamePatternDoesNotMatchFqnUsage() {
		// Source code uses FQN directly (no import)
		String code = """
			class Test {
				void method() {
					Object cs = java.nio.charset.Charset.forName("UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses simple name "Charset.forName(...)" — patterns should use FQN
		Pattern pattern = new Pattern("Charset.forName($arg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "Simple-name pattern should NOT match FQN usage in source — patterns must use FQN");
	}
	
	@Test
	public void testFqnPatternAlsoMatchesFqnUsage() {
		// Source code uses FQN directly
		String code = """
			class Test {
				void method() {
					Object cs = java.nio.charset.Charset.forName("UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN
		Pattern pattern = new Pattern("java.nio.charset.Charset.forName($arg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "FQN pattern should match FQN usage in source");
	}
	
	@Test
	public void testFqnPatternDoesNotMatchSimpleNameWithWrongImport() {
		// Source code imports a different "Charset" class
		String code = """
			import com.example.Charset;
			class Test {
				void method() {
					Object cs = Charset.forName("UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses java.nio.charset.Charset FQN — different from the import
		Pattern pattern = new Pattern("java.nio.charset.Charset.forName($arg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "FQN pattern should NOT match SimpleName with a different import");
	}
	
	@Test
	public void testFqnPatternDoesNotMatchSimpleNameWithoutImport() {
		// Source code uses SimpleName without any import
		String code = """
			class Test {
				void method() {
					Object cs = Charset.forName("UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN
		Pattern pattern = new Pattern("java.nio.charset.Charset.forName($arg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "FQN pattern should NOT match SimpleName without a matching import");
	}
	
	@Test
	public void testFqnConstructorPatternMatchesSimpleNameWithImport() {
		// Source code uses imported simple name "InputStreamReader"
		String code = """
			import java.io.InputStreamReader;
			class Test {
				void method() {
					Object r = new InputStreamReader(System.in, "UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN
		Pattern pattern = new Pattern("new java.io.InputStreamReader($in, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "FQN constructor pattern should match imported simple name");
	}
	
	@Test
	public void testFqnConstructorPatternDoesNotMatchWithoutImport() {
		// Source code uses SimpleName without import
		String code = """
			class Test {
				void method() {
					Object r = new InputStreamReader(System.in, "UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN
		Pattern pattern = new Pattern("new java.io.InputStreamReader($in, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "FQN constructor pattern should NOT match SimpleName without import");
	}
	
	@Test
	public void testFqnConstructorPatternDoesNotMatchWrongImport() {
		// Source code imports a different InputStreamReader from another package
		String code = """
			import com.example.InputStreamReader;
			class Test {
				void method() {
					Object r = new InputStreamReader(System.in, "UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses java.io FQN
		Pattern pattern = new Pattern("new java.io.InputStreamReader($in, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "FQN constructor pattern should NOT match SimpleName with wrong import");
	}
	
	@Test
	public void testFqnConstructorPatternMatchesFqnUsage() {
		// Source code uses FQN directly (no import needed)
		String code = """
			class Test {
				void method() {
					Object r = new java.io.InputStreamReader(System.in, "UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses FQN
		Pattern pattern = new Pattern("new java.io.InputStreamReader($in, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "FQN constructor pattern should match FQN usage in source");
	}
	
	@Test
	public void testJavaLangConstructorMatchesWithoutImport() {
		// java.lang.String doesn't need an explicit import
		String code = """
			class Test {
				void method() {
					String s = new String(bytes, "UTF-8");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern uses java.lang.String FQN
		Pattern pattern = new Pattern("new java.lang.String($bytes, $enc)", PatternKind.CONSTRUCTOR);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "java.lang.String FQN pattern should match String without explicit import");
	}
	
	@Test
	public void testFindMatchesByNodeType() {
		String code = """
			class Test {
				void method1() { }
				void method2() { }
			}
			""";

		CompilationUnit cu = parse(code);
		List<Match> matches = engine.findMatchesByNodeType(cu, ASTNode.METHOD_DECLARATION);

		assertEquals(2, matches.size(), "Should find two method declarations");
	}

	@Test
	public void testPatternConstraintsProperty() {
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		assertNull(pattern.getConstraints(), "Default constraints should be null");

		Pattern patternWithConstraints = new Pattern("$x.toString()", PatternKind.EXPRESSION,
				null, null, null, null, null);
		assertNull(patternWithConstraints.getConstraints(), "Null constraints should remain null");
	}

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
