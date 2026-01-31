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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for string simplification TriggerPattern hints.
 * 
 * <p>These tests verify that the patterns defined in 
 * {@code StringSimplificationHintProvider} correctly match and bind
 * placeholder variables in Java code.</p>
 */
public class StringSimplificationTest {

	private final TriggerPatternEngine engine = new TriggerPatternEngine();

	/**
	 * Tests pattern matching for {@code "" + $x}.
	 */
	@Test
	public void testEmptyStringPrefixPattern() {
		String code = """
			class Test {
				void method() {
					String result = "" + value;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for \"\" + $x");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests pattern matching for {@code $x + ""}.
	 */
	@Test
	public void testEmptyStringSuffixPattern() {
		String code = """
			class Test {
				void method() {
					String result = value + "";
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $x + \"\"");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests pattern matching for {@code $str.length() == 0}.
	 */
	@Test
	public void testStringLengthCheckPattern() {
		String code = """
			class Test {
				void method(String str) {
					boolean isEmpty = str.length() == 0;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.length() == 0", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $str.length() == 0");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $str");
		assertTrue(bindings.containsKey("$str"), "Should have binding for $str");
	}

	/**
	 * Tests pattern matching for {@code $str.equals("")}.
	 */
	@Test
	public void testEqualsEmptyStringPattern() {
		String code = """
			class Test {
				void method(String str) {
					boolean isEmpty = str.equals("");
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$str.equals(\"\")", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $str.equals(\"\")");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $str");
		assertTrue(bindings.containsKey("$str"), "Should have binding for $str");
	}

	/**
	 * Tests pattern matching for {@code $x == true}.
	 */
	@Test
	public void testBooleanComparisonTruePattern() {
		String code = """
			class Test {
				void method(boolean flag) {
					if (flag == true) {
						System.out.println("true");
					}
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x == true", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $x == true");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests pattern matching for {@code $x == false}.
	 */
	@Test
	public void testBooleanComparisonFalsePattern() {
		String code = """
			class Test {
				void method(boolean flag) {
					if (flag == false) {
						System.out.println("false");
					}
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x == false", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $x == false");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $x");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests pattern matching for {@code $cond ? true : false}.
	 */
	@Test
	public void testTernaryBooleanTrueFalsePattern() {
		String code = """
			class Test {
				boolean method() {
					return isValid() ? true : false;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$cond ? true : false", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $cond ? true : false");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $cond");
		assertTrue(bindings.containsKey("$cond"), "Should have binding for $cond");
	}

	/**
	 * Tests pattern matching for {@code $cond ? false : true}.
	 */
	@Test
	public void testTernaryBooleanFalseTruePattern() {
		String code = """
			class Test {
				boolean method() {
					return isValid() ? false : true;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$cond ? false : true", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find exactly one match for $cond ? false : true");

		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(1, bindings.size(), "Should have one binding for $cond");
		assertTrue(bindings.containsKey("$cond"), "Should have binding for $cond");
	}

	/**
	 * Tests that the pattern matches multiple occurrences.
	 */
	@Test
	public void testMultipleMatches() {
		String code = """
			class Test {
				void method() {
					String a = "" + x;
					String b = "" + y;
					String c = "" + z;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(3, matches.size(), "Should find three matches");
	}

	/**
	 * Tests that the pattern works with different expression types.
	 */
	@Test
	public void testVariousExpressionTypes() {
		String code = """
			class Test {
				void method() {
					String a = "" + variable;
					String b = "" + getValue();
					String c = "" + obj.field;
					String d = "" + (x + y);
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(4, matches.size(), "Should find four matches with different expression types");
	}

	/**
	 * Tests that pattern does not match when string is not empty.
	 */
	@Test
	public void testNoMatchForNonEmptyString() {
		String code = """
			class Test {
				void method() {
					String result = "prefix" + value;
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(0, matches.size(), "Should not match non-empty string concatenation");
	}

	/**
	 * Tests pattern with method invocation as operand.
	 */
	@Test
	public void testWithMethodInvocation() {
		String code = """
			class Test {
				void method() {
					String result = "" + toString();
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should match with method invocation");
	}

	/**
	 * Tests both prefix and suffix patterns in the same code.
	 */
	@Test
	public void testBothPrefixAndSuffix() {
		String code = """
			class Test {
				void method() {
					String a = "" + x;
					String b = y + "";
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Test prefix pattern
		Pattern prefixPattern = new Pattern("\"\" + $x", PatternKind.EXPRESSION);
		List<Match> prefixMatches = engine.findMatches(cu, prefixPattern);
		assertEquals(1, prefixMatches.size(), "Should find one prefix match");

		// Test suffix pattern
		Pattern suffixPattern = new Pattern("$x + \"\"", PatternKind.EXPRESSION);
		List<Match> suffixMatches = engine.findMatches(cu, suffixPattern);
		assertEquals(1, suffixMatches.size(), "Should find one suffix match");
	}

	/**
	 * Tests all patterns in a realistic code sample.
	 */
	@Test
	public void testAllPatternsInRealisticCode() {
		String code = """
			class Test {
				String convert(Object obj, String str, boolean flag) {
					// String patterns
					String s1 = "" + obj;
					String s2 = obj + "";
					
					// Empty checks
					if (str.length() == 0) {
						return "empty";
					}
					if (str.equals("")) {
						return "also empty";
					}
					
					// Boolean patterns
					if (flag == true) {
						return "yes";
					}
					if (flag == false) {
						return "no";
					}
					
					// Ternary patterns
					boolean result1 = isValid() ? true : false;
					boolean result2 = isValid() ? false : true;
					
					return s1 + s2;
				}
				
				boolean isValid() {
					return true;
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Count all pattern matches
		int totalMatches = 0;
		
		totalMatches += engine.findMatches(cu, new Pattern("\"\" + $x", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$x + \"\"", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$str.length() == 0", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$str.equals(\"\")", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$x == true", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$x == false", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$cond ? true : false", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$cond ? false : true", PatternKind.EXPRESSION)).size();

		assertEquals(8, totalMatches, "Should find all 8 pattern instances in realistic code");
	}

	/**
	 * Tests complex expressions work with patterns.
	 */
	@Test
	public void testComplexExpressions() {
		String code = """
			class Test {
				void method(boolean a, boolean b, String s) {
					// Complex boolean expressions
					if ((a && b) == true) {
						System.out.println("complex");
					}
					
					// Nested ternary
					boolean result = (a || b) ? true : false;
					
					// Method call in string check
					if (getValue().length() == 0) {
						System.out.println("empty");
					}
				}
				
				String getValue() {
					return "";
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Should match complex expressions
		assertEquals(1, engine.findMatches(cu, new Pattern("$x == true", PatternKind.EXPRESSION)).size());
		assertEquals(1, engine.findMatches(cu, new Pattern("$cond ? true : false", PatternKind.EXPRESSION)).size());
		assertEquals(1, engine.findMatches(cu, new Pattern("$str.length() == 0", PatternKind.EXPRESSION)).size());
	}

	/**
	 * Tests collection size check pattern.
	 */
	@Test
	public void testCollectionSizeCheck() {
		String code = """
			import java.util.List;
			class Test {
				void method(List<String> items) {
					if (items.size() == 0) {
						System.out.println("empty");
					}
					if (items.size() > 0) {
						System.out.println("not empty");
					}
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Test size() == 0 pattern
		Pattern sizeEqualsZeroPattern = new Pattern("$list.size() == 0", PatternKind.EXPRESSION);
		List<Match> sizeEqualsZeroMatches = engine.findMatches(cu, sizeEqualsZeroPattern);
		assertEquals(1, sizeEqualsZeroMatches.size(), "Should find size() == 0 pattern");

		// Test size() > 0 pattern
		Pattern sizeGreaterThanZeroPattern = new Pattern("$list.size() > 0", PatternKind.EXPRESSION);
		List<Match> sizeGreaterThanZeroMatches = engine.findMatches(cu, sizeGreaterThanZeroPattern);
		assertEquals(1, sizeGreaterThanZeroMatches.size(), "Should find size() > 0 pattern");
	}

	/**
	 * Tests StringBuilder single append pattern (complex chained method call).
	 */
	@Test
	public void testStringBuilderSingleAppend() {
		String code = """
			class Test {
				String method(Object obj) {
					return new StringBuilder().append(obj).toString();
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("new StringBuilder().append($x).toString()", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find StringBuilder single append pattern");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests String.format simplification pattern.
	 */
	@Test
	public void testStringFormatSimplification() {
		String code = """
			class Test {
				String method(Object obj) {
					return String.format("%s", obj);
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("String.format(\"%s\", $x)", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find String.format pattern");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
	}

	/**
	 * Tests Objects.equals pattern (complex chained call with null safety).
	 */
	@Test
	public void testObjectsEqualsPattern() {
		String code = """
			class Test {
				boolean method(Object obj, String str) {
					return obj.toString().equals(str);
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x.toString().equals($y)", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find toString().equals() pattern");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(2, bindings.size(), "Should have bindings for $x and $y");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
		assertTrue(bindings.containsKey("$y"), "Should have binding for $y");
	}

	/**
	 * Tests Objects.requireNonNullElse pattern (complex ternary with null check).
	 */
	@Test
	public void testRequireNonNullElsePattern() {
		String code = """
			class Test {
				String method(String value) {
					return value != null ? value : "default";
				}
			}
			""";

		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$x != null ? $x : $default", PatternKind.EXPRESSION);

		List<Match> matches = engine.findMatches(cu, pattern);

		assertEquals(1, matches.size(), "Should find null-check ternary pattern");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(2, bindings.size(), "Should have bindings for $x and $default");
		assertTrue(bindings.containsKey("$x"), "Should have binding for $x");
		assertTrue(bindings.containsKey("$default"), "Should have binding for $default");
	}

	/**
	 * Tests comprehensive real-world scenario with all complex patterns.
	 */
	@Test
	public void testComprehensiveComplexPatterns() {
		String code = """
			import java.util.List;
			class Test {
				String process(Object obj, String str, List<String> items) {
					// Collection patterns
					if (items.size() == 0) {
						return "empty list";
					}
					if (items.size() > 0) {
						System.out.println("has items");
					}
					
					// StringBuilder pattern
					String s1 = new StringBuilder().append(obj).toString();
					
					// String.format pattern
					String s2 = String.format("%s", obj);
					
					// Null-safety pattern
					boolean equal = obj.toString().equals(str);
					
					// Null-check ternary pattern
					String value = str != null ? str : "default";
					
					return value;
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// Count all complex pattern matches
		int totalMatches = 0;
		
		totalMatches += engine.findMatches(cu, new Pattern("$list.size() == 0", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$list.size() > 0", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("new StringBuilder().append($x).toString()", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("String.format(\"%s\", $x)", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$x.toString().equals($y)", PatternKind.EXPRESSION)).size();
		totalMatches += engine.findMatches(cu, new Pattern("$x != null ? $x : $default", PatternKind.EXPRESSION)).size();

		assertEquals(6, totalMatches, "Should find all 6 complex pattern instances");
	}

	/**
	 * Tests that complex patterns don't produce false positives.
	 */
	@Test
	public void testComplexPatternsPrecision() {
		String code = """
			class Test {
				void method(StringBuilder sb, Object obj) {
					// Should NOT match - multiple appends
					String s1 = new StringBuilder().append("prefix").append(obj).toString();
					
					// Should NOT match - format with multiple args
					String s2 = String.format("%s %s", "hello", "world");
					
					// Should NOT match - different null check
					String s3 = obj == null ? "null" : obj.toString();
				}
			}
			""";

		CompilationUnit cu = parse(code);

		// These should NOT match our patterns
		assertEquals(0, engine.findMatches(cu, new Pattern("new StringBuilder().append($x).toString()", PatternKind.EXPRESSION)).size(),
				"Should not match StringBuilder with multiple appends");
		assertEquals(0, engine.findMatches(cu, new Pattern("String.format(\"%s\", $x)", PatternKind.EXPRESSION)).size(),
				"Should not match String.format with different format string");
		assertEquals(0, engine.findMatches(cu, new Pattern("$x != null ? $x : $default", PatternKind.EXPRESSION)).size(),
				"Should not match when ternary branches are different");
	}

	/**
	 * Parses Java source code into a CompilationUnit.
	 */
	private CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
}
