/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for Phase 2: Multi-Statement Pattern Matching with sliding window.
 * 
 * @see PatternKind#STATEMENT_SEQUENCE
 * @see TriggerPatternEngine
 */
public class StatementSequenceTest {
	
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	@Test
	public void testTwoConsecutiveStatements() {
		String code = """
			class Test {
				void m() {
					int x = 1;
					int y = 2;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one two-statement match");
		assertNotNull(matches.get(0).getBinding("$a"));
		assertNotNull(matches.get(0).getBinding("$b"));
	}
	
	@Test
	public void testSequenceWithinLargerBlock() {
		String code = """
			class Test {
				void m() {
					setup();
					int x = 1;
					int y = 2;
					cleanup();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find the sequence within a larger block");
	}
	
	@Test
	public void testNoMatchForNonConsecutive() {
		String code = """
			class Test {
				void m() {
					int x = 1;
					other();
					int y = 2;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Pattern requires two consecutive int declarations
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "Should not match non-consecutive statements");
	}
	
	@Test
	public void testMultipleMatchesInSameBlock() {
		String code = """
			class Test {
				void m() {
					int a = 1;
					int b = 2;
					int c = 3;
					int d = 4;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		// Window slides: (a,b), (b,c), (c,d) = 3 matches
		assertEquals(3, matches.size(), "Should find three overlapping two-statement matches");
	}
	
	@Test
	public void testMatchSpansCorrectOffsetLength() {
		String code = """
			class Test {
				void m() {
					int x = 1;
					int y = 2;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		assertTrue(match.getLength() > 0, "Match length should span both statements");
		assertTrue(match.getOffset() >= 0, "Match offset should be valid");
	}
	
	@Test
	public void testSequenceInMultipleMethods() {
		String code = """
			class Test {
				void m1() {
					int x = 1;
					int y = 2;
				}
				void m2() {
					int a = 3;
					int b = 4;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("{ int $a = $v1; int $b = $v2; }", PatternKind.STATEMENT_SEQUENCE);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find matches in both methods");
	}
	
	@Test
	public void testCombinationWithVariadicPlaceholders() {
		String code = """
			class Test {
				int method1() {
					a();
					b();
					return 1;
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		// Use BLOCK pattern kind with variadic placeholder for statements before return
		Pattern pattern = new Pattern("{ $before$; return $x; }", PatternKind.BLOCK);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should match block with variadic before return");
	}
	
	// --- Helper methods ---
	
	private CompilationUnit parse(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) astParser.createAST(null);
	}
}
