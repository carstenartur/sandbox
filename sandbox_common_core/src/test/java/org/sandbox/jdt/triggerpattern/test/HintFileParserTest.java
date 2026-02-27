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
import org.sandbox.jdt.triggerpattern.api.Severity;
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
		assertEquals(Severity.WARNING, hintFile.getSeverity());
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
		
		assertEquals(Severity.INFO, hintFile.getSeverity(), "Default severity should be 'info'");
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
		assertEquals(Severity.WARNING, hintFile.getSeverity());
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
	public void testParseReplaceStaticImportInferred() throws HintParseException {
		// FQN-based inference: replaceStaticImport is automatically derived
		// from matching method names with different FQN types
		String content = """
			org.junit.Assert.assertEquals($expected, $actual)
			=> org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertNotNull(rule.getImportDirective(), "Rule should have import directives");
		assertFalse(rule.getImportDirective().getReplaceStaticImports().isEmpty(),
				"Rule should have replaceStaticImport directives inferred from FQNs");
		assertEquals("org.junit.jupiter.api.Assertions",
				rule.getImportDirective().getReplaceStaticImports().get("org.junit.Assert"));
	}

	@Test
	public void testParseMultipleReplaceStaticImportInferred() throws HintParseException {
		// FQN-based inference for Assume → Assumptions
		String content = """
			org.junit.Assume.assumeTrue($cond)
			=> org.junit.jupiter.api.Assumptions.assumeTrue($cond)
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
	public void testParseAnnotationRuleWithFqnInference() throws HintParseException {
		// FQN-based inference for annotation rules
		String content = """
			@org.junit.Before
			=> @org.junit.jupiter.api.BeforeEach
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.ANNOTATION, rule.sourcePattern().getKind());
		assertEquals("@org.junit.Before", rule.sourcePattern().getValue());
		assertFalse(rule.isHintOnly());
		assertEquals(1, rule.alternatives().size());
		assertEquals("@org.junit.jupiter.api.BeforeEach", rule.alternatives().get(0).replacementPattern());
		// FQN inference detects the annotation FQN types
		// Note: annotation FQNs start with @ which is outside the FQN regex,
		// but the type part after @ is detected
		assertNotNull(rule.getImportDirective());
		assertTrue(rule.getImportDirective().getAddImports().contains("org.junit.jupiter.api.BeforeEach"));
		assertTrue(rule.getImportDirective().getRemoveImports().contains("org.junit.Before"));
	}

	@Test
	public void testParseAnnotations5FqnFile() throws HintParseException {
		// FQN-based annotations5 file format
		String content = """
			<!id: annotations5>
			<!description: JUnit 4 to JUnit 5 annotation migration>
			<!severity: warning>
			<!minJavaVersion: 8>
			<!tags: junit, testing, migration>

			@org.junit.Before
			=> @org.junit.jupiter.api.BeforeEach
			;;

			@org.junit.After
			=> @org.junit.jupiter.api.AfterEach
			;;

			@org.junit.Ignore
			=> @org.junit.jupiter.api.Disabled
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
		assertEquals("@org.junit.jupiter.api.BeforeEach", hintFile.getRules().get(0).alternatives().get(0).replacementPattern());

		assertEquals("@org.junit.After", hintFile.getRules().get(1).sourcePattern().getValue());
		assertEquals("@org.junit.jupiter.api.AfterEach", hintFile.getRules().get(1).alternatives().get(0).replacementPattern());

		assertEquals("@org.junit.Ignore", hintFile.getRules().get(2).sourcePattern().getValue());
		assertEquals("@org.junit.jupiter.api.Disabled", hintFile.getRules().get(2).alternatives().get(0).replacementPattern());
	}

	@Test
	public void testParseAssertThatHamcrestFqnRule() throws HintParseException {
		// FQN-based Hamcrest rule
		String content = """
			org.junit.Assert.assertThat($actual, $matcher)
			=> org.hamcrest.MatcherAssert.assertThat($actual, $matcher)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_CALL, rule.sourcePattern().getKind());
		assertEquals("org.hamcrest.MatcherAssert.assertThat($actual, $matcher)",
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
	public void testFqnInferenceRemoveImportFromSourceDiff() throws HintParseException {
		// FQN-based inference: types in source but not in replacement become removeImport
		String content = """
			java.io.FileReader.create($path)
			=> java.nio.charset.StandardCharsets.UTF_8
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should be inferred");
		// java.io.FileReader is in source but not in replacement → removeImport
		assertTrue(rule.getImportDirective().getRemoveImports().contains("java.io.FileReader"),
				"FQN in source but not in replacement should be inferred as removeImport");
		// java.nio.charset.StandardCharsets is in replacement but not in source → addImport
		assertTrue(rule.getImportDirective().getAddImports().contains("java.nio.charset.StandardCharsets"),
				"FQN in replacement but not in source should be inferred as addImport");
	}
	
	@Test
	public void testFqnInferenceNoImportForSharedTypes() throws HintParseException {
		// Types that appear in both source and replacement should not be added or removed
		String content = """
			java.util.Objects.equals($x, $y)
			=> java.util.Objects.hash($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Import directive should be inferred");
		// java.util.Objects is in both → addImport but not removeImport
		assertTrue(rule.getImportDirective().getAddImports().contains("java.util.Objects"),
				"FQN in replacement should be addImport");
		assertFalse(rule.getImportDirective().getRemoveImports().contains("java.util.Objects"),
				"FQN in both source and replacement should not be removeImport");
	}
	
	@Test
	public void testForeachExpandsRules() throws HintParseException {
		String content = """
			<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>
			
			$s.getBytes("${CHARSET}") :: sourceVersionGE(7)
			=> $s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(2, hintFile.getRules().size(), "foreach should expand to 2 rules");
		
		TransformationRule rule1 = hintFile.getRules().get(0);
		assertEquals("$s.getBytes(\"UTF-8\")", rule1.sourcePattern().getValue());
		assertEquals("$s.getBytes(java.nio.charset.StandardCharsets.UTF_8)",
				rule1.alternatives().get(0).replacementPattern());
		
		TransformationRule rule2 = hintFile.getRules().get(1);
		assertEquals("$s.getBytes(\"ISO-8859-1\")", rule2.sourcePattern().getValue());
		assertEquals("$s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)",
				rule2.alternatives().get(0).replacementPattern());
	}
	
	@Test
	public void testForeachWithAllSixCharsets() throws HintParseException {
		String content = """
			<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1, "US-ASCII" -> US_ASCII, "UTF-16" -> UTF_16, "UTF-16BE" -> UTF_16BE, "UTF-16LE" -> UTF_16LE>
			
			java.nio.charset.Charset.forName("${CHARSET}") :: sourceVersionGE(7)
			=> java.nio.charset.StandardCharsets.${CHARSET_CONSTANT}
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(6, hintFile.getRules().size(), "foreach should expand to 6 rules");
		
		// Verify first and last
		assertEquals("java.nio.charset.Charset.forName(\"UTF-8\")",
				hintFile.getRules().get(0).sourcePattern().getValue());
		assertEquals("java.nio.charset.Charset.forName(\"UTF-16LE\")",
				hintFile.getRules().get(5).sourcePattern().getValue());
	}
	
	@Test
	public void testForeachWithConstructor() throws HintParseException {
		String content = """
			<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>
			
			new java.io.InputStreamReader($in, "${CHARSET}") :: sourceVersionGE(7)
			=> new java.io.InputStreamReader($in, java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(2, hintFile.getRules().size());
		
		TransformationRule rule1 = hintFile.getRules().get(0);
		assertEquals(PatternKind.CONSTRUCTOR, rule1.sourcePattern().getKind());
		assertEquals("new java.io.InputStreamReader($in, \"UTF-8\")", rule1.sourcePattern().getValue());
	}
	
	@Test
	public void testForeachWithMultipleTemplates() throws HintParseException {
		String content = """
			<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>
			
			$s.getBytes("${CHARSET}") :: sourceVersionGE(7)
			=> $s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
			;;
			
			new java.lang.String($b, "${CHARSET}") :: sourceVersionGE(7)
			=> new java.lang.String($b, java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(4, hintFile.getRules().size(), "2 templates × 2 charsets = 4 rules");
	}
	
	@Test
	public void testForeachMixedWithNonForeachRules() throws HintParseException {
		String content = """
			<!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>
			
			// Non-foreach rule (no ${CHARSET} variable)
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			
			// Foreach rule
			$s.getBytes("${CHARSET}") :: sourceVersionGE(7)
			=> $s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(3, hintFile.getRules().size(), "1 non-foreach + 2 foreach = 3 rules");
		
		// First rule is the non-foreach one
		assertEquals("$x.equals($y)", hintFile.getRules().get(0).sourcePattern().getValue());
	}
	
	@Test
	public void testForeachErrorEmptyEntries() {
		String content = """
			<!foreach CHARSET: >
			
			$s.getBytes("${CHARSET}")
			=> $s.getBytes(StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		assertThrows(HintParseException.class, () -> parser.parse(content));
	}
	
	@Test
	public void testForeachErrorMissingArrow() {
		String content = """
			<!foreach CHARSET: "UTF-8" UTF_8>
			
			$s.getBytes("${CHARSET}")
			=> $s.getBytes(StandardCharsets.${CHARSET_CONSTANT})
			;;
			""";
		
		assertThrows(HintParseException.class, () -> parser.parse(content));
	}

	// ---- NetBeans compatibility tests ----

	@Test
	public void testSkipCustomCodeBlockSingleLine() throws HintParseException {
		String content = """
			<? import java.util.Arrays; ?>
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals("$x.equals($y)", hintFile.getRules().get(0).sourcePattern().getValue());
	}

	@Test
	public void testSkipCustomCodeBlockMultiLine() throws HintParseException {
		String content = """
			<?
			import java.util.Arrays;
			import java.util.List;
			
			public boolean isLiteral(Variable v) {
			    return v.getKind() == Kind.LITERAL;
			}
			?>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals("$x.equals($y)", hintFile.getRules().get(0).sourcePattern().getValue());
	}

	@Test
	public void testSkipMultipleCustomCodeBlocks() throws HintParseException {
		String content = """
			<? import java.util.Arrays; ?>
			<? import java.util.List; ?>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
	}

	@Test
	public void testNetBeansDescriptionEqualsFormat() throws HintParseException {
		String content = """
			<!description="Replace with Objects.equals">
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("Replace with Objects.equals", hintFile.getDescription());
		assertEquals(1, hintFile.getRules().size());
	}

	@Test
	public void testNetBeansMetadataEqualsFormatWithoutQuotes() throws HintParseException {
		String content = """
			<!description=Simple description>
			<!severity=warning>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("Simple description", hintFile.getDescription());
		assertEquals(Severity.WARNING, hintFile.getSeverity());
	}

	@Test
	public void testNetBeansMixedFormatMetadata() throws HintParseException {
		// Mix of sandbox format (key: value) and NetBeans format (key="value")
		String content = """
			<!id: my.rule.id>
			<!description="NetBeans style description">
			<!severity: warning>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("my.rule.id", hintFile.getId());
		assertEquals("NetBeans style description", hintFile.getDescription());
		assertEquals(Severity.WARNING, hintFile.getSeverity());
	}

	@Test
	public void testOtherwiseAsGuardKeyword() throws HintParseException {
		String content = """
			$x.method() :: sourceVersionGE(11)
			=> $x.newMethod1() :: sourceVersionGE(17)
			=> $x.newMethod2() :: otherwise
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(2, rule.alternatives().size());
		assertNotNull(rule.alternatives().get(0).condition());
		assertTrue(rule.alternatives().get(1).isOtherwise());
	}

	@Test
	public void testCustomCodeBlockWithRulesAfter() throws HintParseException {
		// Realistic NetBeans hint file with custom code block then rules
		String content = """
			<?
			import org.netbeans.spi.java.hints.ConstraintVariableType;
			import org.netbeans.spi.java.hints.TriggerTreeKind;
			
			public boolean isLiteral(Variable v) {
			    return true;
			}
			?>
			
			<!description="Use StandardCharsets constant">
			
			java.nio.charset.Charset.forName("UTF-8") :: sourceVersionGE(7)
			=> java.nio.charset.StandardCharsets.UTF_8
			;;
			
			java.nio.charset.Charset.forName("ISO-8859-1") :: sourceVersionGE(7)
			=> java.nio.charset.StandardCharsets.ISO_8859_1
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("Use StandardCharsets constant", hintFile.getDescription());
		assertEquals(2, hintFile.getRules().size());
	}

	@Test
	public void testMultipleCustomCodeBlocksOnSingleLine() throws HintParseException {
		String content = """
			<? int a = 1; ?><? int b = 2; ?>
			
			$x == $y
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertNotNull(hintFile);
		assertEquals(1, hintFile.getRules().size());
	}

	@Test
	public void testUnclosedCustomCodeBlock() throws HintParseException {
		String content = """
			<?
			int x = 1;
			
			$x == $y
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		// Unclosed <? block causes all remaining lines to be treated as
		// custom code, resulting in no rules parsed (empty hint file)
		HintFile hintFile = parser.parse(content);
		assertTrue(hintFile.getRules().isEmpty(), "Unclosed custom code block should result in no rules");
	}

	@Test
	public void testMetadataColonFormatPreferredOverEquals() throws HintParseException {
		// Colon format should be preferred for backward compatibility
		// even when the value contains an equals sign
		String content = """
			<!description: equation is x=y>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("equation is x=y", hintFile.getDescription());
	}

	// ---- Multi-line metadata tests ----

	@Test
	public void testMultiLineDescriptionMetadata() throws HintParseException {
		String content = """
			<!description: Add guard for unhandled or disabled Eclipse commands after declaration.
			Prevents showing key bindings for commands that are not handled or enabled.>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertTrue(hintFile.getDescription().contains("Add guard for unhandled"));
		assertTrue(hintFile.getDescription().contains("not handled or enabled."));
		assertEquals(1, hintFile.getRules().size());
	}

	@Test
	public void testMultiLineDescriptionThreeLines() throws HintParseException {
		String content = """
			<!description: First line of description.
			Second line of description.
			Third line with closing bracket.>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertTrue(hintFile.getDescription().contains("First line"));
		assertTrue(hintFile.getDescription().contains("Third line"));
		assertEquals(1, hintFile.getRules().size());
	}

	// ---- Arrow on own line tests ----

	@Test
	public void testArrowOnOwnLineWithReplacementOnNextLine() throws HintParseException {
		String content = """
			@java.lang.Deprecated :: sourceVersionGE(9)
			=>
			@java.lang.Deprecated(forRemoval = true)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.ANNOTATION, rule.sourcePattern().getKind());
		assertEquals("@java.lang.Deprecated(forRemoval = true)",
				rule.alternatives().get(0).replacementPattern());
	}

	@Test
	public void testArrowOnOwnLineSimpleRule() throws HintParseException {
		String content = """
			org.junit.Assert.assertEquals($expected, $actual)
			=>
			org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)",
				rule.alternatives().get(0).replacementPattern());
	}

	@Test
	public void testArrowOnSameLineStillWorks() throws HintParseException {
		// Ensure backward compatibility: => replacement on same line
		String content = """
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		assertEquals("java.util.Objects.equals($x, $y)",
				hintFile.getRules().get(0).alternatives().get(0).replacementPattern());
	}

	@Test
	public void testBareArrowWithNoReplacementThrowsError() {
		// => on its own line with no replacement before ;; should be a parse error
		String content = """
			$x.equals($y)
			=>
			;;
			""";
		
		assertThrows(HintParseException.class, () -> parser.parse(content));
	}

	@Test
	public void testMethodDeclarationPatternKindInference() throws HintParseException {
		// Natural syntax: replacement is a method declaration with annotation
		String content = """
			void $name($params$) :: methodNameMatches($name, "test.*")
			=> @org.junit.jupiter.api.Test void $name($params$)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_DECLARATION, rule.sourcePattern().getKind(),
				"void $name($params$) should be inferred as METHOD_DECLARATION"); //$NON-NLS-1$
		assertNotNull(rule.sourceGuard(), "Guard should be parsed"); //$NON-NLS-1$
		assertFalse(rule.isHintOnly(), "Should have a replacement alternative"); //$NON-NLS-1$
		assertEquals("@org.junit.jupiter.api.Test void $name($params$)",
				rule.alternatives().get(0).replacementPattern(),
				"Replacement should be the annotated method declaration"); //$NON-NLS-1$
	}

	@Test
	public void testNaturalSyntaxWithSetUpMethod() throws HintParseException {
		// Natural syntax: setUp → @BeforeEach
		String content = """
			void $name($params$) :: methodNameMatches($name, "setUp")
			=> @org.junit.jupiter.api.BeforeEach void $name($params$)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_DECLARATION, rule.sourcePattern().getKind());
		assertEquals("@org.junit.jupiter.api.BeforeEach void $name($params$)",
				rule.alternatives().get(0).replacementPattern());
	}

	@Test
	public void testMethodDeclarationWithReturnTypeInference() throws HintParseException {
		// String return type should also be inferred as METHOD_DECLARATION
		String content = """
			String $name()
			=> @org.junit.jupiter.api.Test String $name()
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_DECLARATION, rule.sourcePattern().getKind(),
				"String $name() should be inferred as METHOD_DECLARATION"); //$NON-NLS-1$
	}

	@Test
	public void testMultipleAnnotationRulesNaturalSyntax() throws HintParseException {
		// Multiple rules using natural syntax
		String content = """
			<!id: junit3-migration>
			<!description: JUnit 3 to 5 migration>

			void $name($params$) :: methodNameMatches($name, "test.*")
			=> @org.junit.jupiter.api.Test void $name($params$)
			;;

			void $name($params$) :: methodNameMatches($name, "setUp")
			=> @org.junit.jupiter.api.BeforeEach void $name($params$)
			;;

			void $name($params$) :: methodNameMatches($name, "tearDown")
			=> @org.junit.jupiter.api.AfterEach void $name($params$)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("junit3-migration", hintFile.getId()); //$NON-NLS-1$
		assertEquals(3, hintFile.getRules().size(), "Should have 3 rules"); //$NON-NLS-1$
		
		for (TransformationRule rule : hintFile.getRules()) {
			assertEquals(PatternKind.METHOD_DECLARATION, rule.sourcePattern().getKind());
			assertTrue(rule.alternatives().get(0).replacementPattern().contains("void $name"), //$NON-NLS-1$
					"Each rule should have a method declaration replacement"); //$NON-NLS-1$
		}
	}

	@Test
	public void testMultilineReplacement() throws HintParseException {
		// Multiline replacement: continuation lines after => are joined with newlines
		String content = """
			$x.method()
			=>
			$x.firstCall()
			$x.secondCall()
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		String replacement = rule.alternatives().get(0).replacementPattern();
		assertTrue(replacement.contains("\n"), //$NON-NLS-1$
				"Multiline replacement should contain newline"); //$NON-NLS-1$
		assertTrue(replacement.contains("$x.firstCall()"), //$NON-NLS-1$
				"Should contain first line"); //$NON-NLS-1$
		assertTrue(replacement.contains("$x.secondCall()"), //$NON-NLS-1$
				"Should contain second line"); //$NON-NLS-1$
	}

	@Test
	public void testMultilineReplacementWithInlineArrow() throws HintParseException {
		// Multiline replacement where => has content on the same line
		String content = """
			$x.method()
			=> $x.firstCall()
			$x.secondCall()
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		String replacement = rule.alternatives().get(0).replacementPattern();
		assertTrue(replacement.contains("$x.firstCall()"), //$NON-NLS-1$
				"Should contain first line"); //$NON-NLS-1$
		assertTrue(replacement.contains("$x.secondCall()"), //$NON-NLS-1$
				"Should contain second line from continuation"); //$NON-NLS-1$
	}

	@Test
	public void testMultilineReplacementDoesNotBreakMultiRewrite() throws HintParseException {
		// Multi-rewrite rules should NOT accumulate across => boundaries
		String content = """
			$x.method()
			=> $x.newMethod() :: sourceVersionGE(11)
			=> $x.fallback() :: otherwise
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(2, rule.alternatives().size(),
				"Should have 2 alternatives, not merged into one multiline"); //$NON-NLS-1$
		assertEquals("$x.newMethod()", rule.alternatives().get(0).replacementPattern()); //$NON-NLS-1$
		assertEquals("$x.fallback()", rule.alternatives().get(1).replacementPattern()); //$NON-NLS-1$
	}

	@Test
	public void testStaticGuardInMethodDeclaration() throws HintParseException {
		// Verify !isStatic negation works in guard parsing
		String content = """
			void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name)
			=> @org.junit.jupiter.api.Test void $name($params$)
			;;
			""";
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(PatternKind.METHOD_DECLARATION, rule.sourcePattern().getKind());
		assertNotNull(rule.sourceGuard(), "Guard with !isStatic should be parsed"); //$NON-NLS-1$
	}

	@Test
	public void testParseSuppressWarningsDirective() throws HintParseException {
		String content = """
			<!suppressWarnings: collection-size-check>
			$list.size() == 0
			=> $list.isEmpty()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		assertEquals(1, hintFile.getSuppressWarnings().size());
		assertEquals("collection-size-check", hintFile.getSuppressWarnings().get(0)); //$NON-NLS-1$
	}

	@Test
	public void testParseSuppressWarningsMultipleKeys() throws HintParseException {
		String content = """
			<!suppressWarnings: collection-size-check, string-equals>
			$list.size() == 0
			=> $list.isEmpty()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		assertEquals(2, hintFile.getSuppressWarnings().size());
		assertTrue(hintFile.getSuppressWarnings().contains("collection-size-check")); //$NON-NLS-1$
		assertTrue(hintFile.getSuppressWarnings().contains("string-equals")); //$NON-NLS-1$
	}

	@Test
	public void testParseSeverityDirective() throws HintParseException {
		String content = """
			<!severity: error>
			$list.size() == 0
			=> $list.isEmpty()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		assertEquals(Severity.ERROR, hintFile.getSeverity());
	}

	@Test
	public void testParseSeverityDirectiveWarning() throws HintParseException {
		String content = """
			<!severity: warning>
			$list.size() == 0
			=> $list.isEmpty()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		assertEquals(Severity.WARNING, hintFile.getSeverity());
	}

	@Test
	public void testDefaultSeverityIsInfo() throws HintParseException {
		String content = """
			$list.size() == 0
			=> $list.isEmpty()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		assertEquals(Severity.INFO, hintFile.getSeverity());
	}

	// --- treeKind directive tests ---

	@Test
	public void testParseTreeKindDirective() throws HintParseException {
		String content = """
			<!treeKind: METHOD_DECLARATION, IF_STATEMENT>
			void $name($params$)
			=> void $name($params$)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		List<Integer> treeKinds = hintFile.getTreeKindNodeTypes();
		assertEquals(2, treeKinds.size());
		assertEquals(org.eclipse.jdt.core.dom.ASTNode.METHOD_DECLARATION, treeKinds.get(0).intValue());
		assertEquals(org.eclipse.jdt.core.dom.ASTNode.IF_STATEMENT, treeKinds.get(1).intValue());
	}

	@Test
	public void testParseTreeKindSingleValue() throws HintParseException {
		String content = """
			<!treeKind: FOR_STATEMENT>
			for ($init; $cond; $update) { $body$ }
			=> $body$
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertNotNull(hintFile);
		List<Integer> treeKinds = hintFile.getTreeKindNodeTypes();
		assertEquals(1, treeKinds.size());
		assertEquals(org.eclipse.jdt.core.dom.ASTNode.FOR_STATEMENT, treeKinds.get(0).intValue());
	}

	@Test
	public void testParseTreeKindInvalidNodeType() {
		String content = """
			<!treeKind: INVALID_NODE_TYPE>
			$x.foo()
			=> $x.bar()
			;;
			""";

		assertThrows(HintParseException.class, () -> parser.parse(content));
	}

	@Test
	public void testParseTreeKindEmptyValue() {
		String content = """
			<!treeKind: >
			$x.foo()
			=> $x.bar()
			;;
			""";

		assertThrows(HintParseException.class, () -> parser.parse(content));
	}

	@Test
	public void testTreeKindDefaultEmpty() throws HintParseException {
		String content = """
			$x.foo()
			=> $x.bar()
			;;
			""";

		HintFile hintFile = parser.parse(content);
		assertTrue(hintFile.getTreeKindNodeTypes().isEmpty(), "Default treeKindNodeTypes should be empty");
	}

	// --- Per-rule @id: and @severity: annotation tests ---

	@Test
	public void testPerRuleIdAnnotation() throws HintParseException {
		String content = """
			@id: encoding.fileReader
			new FileReader($path)
			=> new FileReader($path, StandardCharsets.UTF_8)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("encoding.fileReader", rule.getRuleId());
	}

	@Test
	public void testPerRuleSeverityAnnotation() throws HintParseException {
		String content = """
			@severity: error
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals(Severity.ERROR, rule.getSeverity());
	}

	@Test
	public void testPerRuleIdAndSeverityCombined() throws HintParseException {
		String content = """
			@id: collections.emptyList
			@severity: warning
			java.util.Collections.EMPTY_LIST
			=> java.util.Collections.emptyList()
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("collections.emptyList", rule.getRuleId());
		assertEquals(Severity.WARNING, rule.getSeverity());
	}

	@Test
	public void testPerRuleIdWithDescriptionPrefix() throws HintParseException {
		String content = """
			@id: my.rule
			"Replace empty equals":
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertEquals("my.rule", rule.getRuleId());
		assertEquals("Replace empty equals", rule.getDescription());
	}

	@Test
	public void testRuleWithoutIdHasNullRuleId() throws HintParseException {
		String content = """
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			""";

		HintFile hintFile = parser.parse(content);

		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		assertNull(rule.getRuleId());
		assertNull(rule.getSeverity());
	}

	@Test
	public void testInvalidPerRuleSeverity() {
		String content = """
			@severity: invalid_level
			$x.foo()
			=> $x.bar()
			;;
			""";

		assertThrows(HintParseException.class, () -> parser.parse(content));
	}
}
