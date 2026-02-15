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
}
