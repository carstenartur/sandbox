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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Tests for Phase 5: DSL file format parser ({@code .sandbox-hint}).
 * 
 * @see HintFileParser
 * @see HintFile
 */
public class HintFileParserTest {
	
	private final HintFileParser parser = new HintFileParser();
	
	@Test
	public void testParseSimpleRule() throws HintParseException {
		String content = """
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertNotNull(hintFile);
		assertEquals(1, hintFile.getRules().size());
		
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("$x.equals($y)", rule.sourcePattern().getValue());
		assertEquals(PatternKind.METHOD_CALL, rule.sourcePattern().getKind());
		assertNull(rule.sourceGuard());
		assertEquals(1, rule.alternatives().size());
		assertEquals("java.util.Objects.equals($x, $y)", rule.alternatives().get(0).replacementPattern());
	}
	
	@Test
	public void testParseRuleWithGuard() throws HintParseException {
		String content = """
			new FileReader($path) :: sourceVersionGE(11)
			=> new FileReader($path, StandardCharsets.UTF_8)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertNotNull(rule.sourceGuard(), "Guard should be parsed");
		assertFalse(rule.isHintOnly());
	}
	
	@Test
	public void testParseRuleWithMultiRewrite() throws HintParseException {
		String content = """
			new String($bytes, "UTF-8")
			=> new String($bytes, StandardCharsets.UTF_8) :: sourceVersionGE(7)
			=> new String($bytes, Charset.forName("UTF-8")) :: otherwise
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(2, rule.alternatives().size());
		
		RewriteAlternative alt1 = rule.alternatives().get(0);
		assertNotNull(alt1.condition(), "First alternative should have a guard");
		
		RewriteAlternative alt2 = rule.alternatives().get(1);
		assertTrue(alt2.isOtherwise(), "Second alternative should be otherwise");
	}
	
	@Test
	public void testParseHintOnlyRule() throws HintParseException {
		String content = """
			"Avoid Thread.sleep in tests":
			Thread.sleep($t)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("Avoid Thread.sleep in tests", rule.getDescription());
		assertTrue(rule.isHintOnly(), "Rule without => should be hint-only");
	}
	
	@Test
	public void testParseMetadata() throws HintParseException {
		String content = """
			<!id: encoding.utf8>
			<!description: Replace String encoding literals>
			<!severity: warning>
			<!minJavaVersion: 11>
			<!tags: encoding, modernization>
			
			$x.getBytes("UTF-8")
			=> $x.getBytes(StandardCharsets.UTF_8)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("encoding.utf8", hintFile.getId());
		assertEquals("Replace String encoding literals", hintFile.getDescription());
		assertEquals("warning", hintFile.getSeverity());
		assertEquals(11, hintFile.getMinJavaVersion());
		assertEquals(List.of("encoding", "modernization"), hintFile.getTags());
		assertEquals(1, hintFile.getRules().size());
	}
	
	@Test
	public void testParseMultipleRules() throws HintParseException {
		String content = """
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			
			$x.toString()
			=> String.valueOf($x)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(2, hintFile.getRules().size());
	}
	
	@Test
	public void testParseWithLineComments() throws HintParseException {
		String content = """
			// This is a comment
			$x.equals($y)
			// Another comment
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
	}
	
	@Test
	public void testParseWithBlockComments() throws HintParseException {
		String content = """
			/* This is a block comment
			   spanning multiple lines */
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
	}
	
	@Test
	public void testParseAnnotationPattern() throws HintParseException {
		String content = """
			@Before
			=> @BeforeEach
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals(PatternKind.ANNOTATION, hintFile.getRules().get(0).sourcePattern().getKind());
	}
	
	@Test
	public void testParseImportPattern() throws HintParseException {
		String content = """
			import org.junit.Assert;
			=> import org.junit.jupiter.api.Assertions;
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals(PatternKind.IMPORT, hintFile.getRules().get(0).sourcePattern().getKind());
	}
	
	@Test
	public void testParseConstructorPattern() throws HintParseException {
		String content = """
			new FileReader($path)
			=> new FileReader($path, StandardCharsets.UTF_8)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals(PatternKind.CONSTRUCTOR, hintFile.getRules().get(0).sourcePattern().getKind());
	}
	
	@Test
	public void testParseBlockPattern() throws HintParseException {
		String content = """
			{ $before$; return $x; }
			=> { return $x; }
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals(PatternKind.BLOCK, hintFile.getRules().get(0).sourcePattern().getKind());
	}
	
	@Test
	public void testParseStatementPattern() throws HintParseException {
		String content = """
			return null;
			=> return Optional.empty();
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals(PatternKind.STATEMENT, hintFile.getRules().get(0).sourcePattern().getKind());
	}
	
	@Test
	public void testErrorMissingTerminator() {
		String content = """
			$x.equals($y)
			=> Objects.equals($x, $y)
			""";
		
		assertThrows(HintParseException.class, () -> parser.parse(content));
	}
	
	@Test
	public void testErrorEmptyContent() {
		assertThrows(HintParseException.class, () -> parser.parse(""));
		assertThrows(HintParseException.class, () -> parser.parse((String) null));
		assertThrows(HintParseException.class, () -> parser.parse("   "));
	}
	
	@Test
	public void testErrorInvalidMetadata() {
		String content = """
			<!invalid>
			$x
			=> $y
			;;
			""";
		
		assertThrows(HintParseException.class, () -> parser.parse(content));
	}
	
	@Test
	public void testDefaultSeverity() throws HintParseException {
		String content = """
			$x.equals($y)
			=> Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("info", hintFile.getSeverity(), "Default severity should be 'info'");
	}
	
	@Test
	public void testCompleteFileWithMultipleFeatures() throws HintParseException {
		String content = """
			<!id: test.complete>
			<!description: Complete test file>
			<!severity: warning>
			<!minJavaVersion: 11>
			<!tags: test, example>
			
			// Simple rule
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			
			// Rule with guard
			new FileReader($path) :: sourceVersionGE(11)
			=> new FileReader($path, StandardCharsets.UTF_8)
			;;
			
			/* Hint-only rule */
			"Avoid raw types":
			new ArrayList()
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("test.complete", hintFile.getId());
		assertEquals("warning", hintFile.getSeverity());
		assertEquals(11, hintFile.getMinJavaVersion());
		assertEquals(3, hintFile.getRules().size());
		
		// First rule: simple
		assertFalse(hintFile.getRules().get(0).isHintOnly());
		assertNull(hintFile.getRules().get(0).sourceGuard());
		
		// Second rule: with guard
		assertNotNull(hintFile.getRules().get(1).sourceGuard());
		
		// Third rule: hint-only
		assertTrue(hintFile.getRules().get(2).isHintOnly());
		assertEquals("Avoid raw types", hintFile.getRules().get(2).getDescription());
	}

	@Test
	public void testParseReplaceStaticImportDirective() throws HintParseException {
		String content = """
			Assert.assertEquals($expected, $actual)
			=> Assertions.assertEquals($expected, $actual)
			addImport org.junit.jupiter.api.Assertions
			removeImport org.junit.Assert
			replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertNotNull(rule.getImportDirective(), "Rule should have import directives");
		assertFalse(rule.getImportDirective().getReplaceStaticImports().isEmpty(),
				"Rule should have replaceStaticImport directives");
		assertEquals("org.junit.jupiter.api.Assertions",
				rule.getImportDirective().getReplaceStaticImports().get("org.junit.Assert"));
	}

	@Test
	public void testParseMultipleReplaceStaticImportDirectives() throws HintParseException {
		String content = """
			Assume.assumeTrue($cond)
			=> Assumptions.assumeTrue($cond)
			addImport org.junit.jupiter.api.Assumptions
			removeImport org.junit.Assume
			replaceStaticImport org.junit.Assume org.junit.jupiter.api.Assumptions
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertNotNull(rule.getImportDirective());
		assertEquals("org.junit.jupiter.api.Assumptions",
				rule.getImportDirective().getReplaceStaticImports().get("org.junit.Assume"));
		assertTrue(rule.getImportDirective().getAddImports().contains("org.junit.jupiter.api.Assumptions"));
		assertTrue(rule.getImportDirective().getRemoveImports().contains("org.junit.Assume"));
	}

	@Test
	public void testParseAnnotationRuleWithImports() throws HintParseException {
		String content = """
			@org.junit.Before
			=> @BeforeEach
			addImport org.junit.jupiter.api.BeforeEach
			removeImport org.junit.Before
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.ANNOTATION, rule.sourcePattern().getKind());
		assertEquals("@org.junit.Before", rule.sourcePattern().getValue());
		assertFalse(rule.isHintOnly());
		assertEquals(1, rule.alternatives().size());
		assertEquals("@BeforeEach", rule.alternatives().get(0).replacementPattern());
		assertNotNull(rule.getImportDirective());
		assertTrue(rule.getImportDirective().getAddImports().contains("org.junit.jupiter.api.BeforeEach"));
		assertTrue(rule.getImportDirective().getRemoveImports().contains("org.junit.Before"));
	}

	@Test
	public void testParseAnnotations5File() throws HintParseException {
		String content = """
			<!id: annotations5>
			<!description: JUnit 4 to JUnit 5 annotation migration>
			<!severity: warning>
			<!minJavaVersion: 8>
			<!tags: junit, testing, migration>

			@org.junit.Before
			=> @BeforeEach
			addImport org.junit.jupiter.api.BeforeEach
			removeImport org.junit.Before
			;;

			@org.junit.After
			=> @AfterEach
			addImport org.junit.jupiter.api.AfterEach
			removeImport org.junit.After
			;;

			@org.junit.Ignore
			=> @Disabled
			addImport org.junit.jupiter.api.Disabled
			removeImport org.junit.Ignore
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals("annotations5", hintFile.getId());
		assertEquals(3, hintFile.getRules().size());

		// All rules should be annotation patterns
		for (TransformationRule rule : hintFile.getRules()) {
			assertEquals(PatternKind.ANNOTATION, rule.sourcePattern().getKind());
			assertFalse(rule.isHintOnly());
			assertNotNull(rule.getImportDirective());
		}

		// Check specific rules
		assertEquals("@org.junit.Before", hintFile.getRules().get(0).sourcePattern().getValue());
		assertEquals("@BeforeEach", hintFile.getRules().get(0).alternatives().get(0).replacementPattern());

		assertEquals("@org.junit.After", hintFile.getRules().get(1).sourcePattern().getValue());
		assertEquals("@AfterEach", hintFile.getRules().get(1).alternatives().get(0).replacementPattern());

		assertEquals("@org.junit.Ignore", hintFile.getRules().get(2).sourcePattern().getValue());
		assertEquals("@Disabled", hintFile.getRules().get(2).alternatives().get(0).replacementPattern());
	}

	@Test
	public void testParseAssertThatHamcrestRule() throws HintParseException {
		String content = """
			Assert.assertThat($actual, $matcher)
			=> MatcherAssert.assertThat($actual, $matcher)
			addImport org.hamcrest.MatcherAssert
			removeImport org.junit.Assert
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_CALL, rule.sourcePattern().getKind());
		assertEquals("MatcherAssert.assertThat($actual, $matcher)",
				rule.alternatives().get(0).replacementPattern());
		assertTrue(rule.getImportDirective().getAddImports().contains("org.hamcrest.MatcherAssert"));
		assertTrue(rule.getImportDirective().getRemoveImports().contains("org.junit.Assert"));
	}
	
	@Test
	public void testAutoDetectAddImportFromFqnInReplacement() throws HintParseException {
		String content = """
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should be auto-detected");
		assertTrue(rule.getImportDirective().getAddImports().contains("java.util.Objects"),
				"java.util.Objects should be auto-detected from FQN in replacement");
	}
	
	@Test
	public void testAutoDetectMergesWithExplicitImports() throws HintParseException {
		// Phase 5: Explicit removeImport + auto-detected addImport from FQN should merge
		String content = """
			new FileReader($path)
			=> new FileReader($path, java.nio.charset.StandardCharsets.UTF_8)
			removeImport java.io.FileReader
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should exist");
		// Explicit removeImport should be present
		assertTrue(rule.getImportDirective().getRemoveImports().contains("java.io.FileReader"),
				"Explicit removeImport should be preserved");
		// Auto-detected addImport from FQN in replacement should also be present
		assertTrue(rule.getImportDirective().getAddImports().contains("java.nio.charset.StandardCharsets"),
				"addImport should be auto-detected from FQN even when explicit removeImport is given");
	}
	
	@Test
	public void testAutoDetectRemoveImportFromSourceReplacementDiff() throws HintParseException {
		// Phase 2 auto-detection of removeImport from source/replacement FQN diff
		// is intentionally disabled because it's unsafe (import may be used elsewhere
		// in the compilation unit). Only addImport auto-detection is active.
		String content = """
			java.io.FileReader.create($path)
			=> java.nio.charset.StandardCharsets.UTF_8
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should be auto-detected");
		// java.io.FileReader is in source but not in replacement — NOT auto-removed (unsafe)
		assertFalse(rule.getImportDirective().getRemoveImports().contains("java.io.FileReader"),
				"removeImport should NOT be auto-detected from source FQN diff (unsafe)");
		// java.nio.charset.StandardCharsets is in replacement but not in source → addImport
		assertTrue(rule.getImportDirective().getAddImports().contains("java.nio.charset.StandardCharsets"),
				"FQN in replacement but not in source should be auto-detected as addImport");
	}
	
	@Test
	public void testExplicitAddImportsPrecludeAutoDetection() throws HintParseException {
		// When explicit addImports are given, auto-detection should not add more
		String content = """
			$x.getBytes("UTF-8")
			=> $x.getBytes(java.nio.charset.StandardCharsets.UTF_8)
			addImport com.example.CustomCharset
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should exist");
		// Explicit addImport should be present
		assertTrue(rule.getImportDirective().getAddImports().contains("com.example.CustomCharset"),
				"Explicit addImport should be preserved");
		// Auto-detected addImport should NOT be added since explicit addImport was given
		assertFalse(rule.getImportDirective().getAddImports().contains("java.nio.charset.StandardCharsets"),
				"Auto-detected addImport should not be added when explicit addImports are specified");
	}
}
