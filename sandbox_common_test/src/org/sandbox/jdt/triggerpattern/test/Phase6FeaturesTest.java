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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.PreviewGenerator;
import org.sandbox.jdt.triggerpattern.api.PreviewGenerator.Preview;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for Phase 6 features: Import Management, Pattern Libraries, 
 * Preview Generation.
 * 
 * @see ImportDirective
 * @see HintFileRegistry
 * @see PreviewGenerator
 */
public class Phase6FeaturesTest {
	
	// --- ImportDirective tests ---
	
	@Test
	public void testImportDirectiveEmpty() {
		ImportDirective directive = new ImportDirective();
		assertTrue(directive.isEmpty());
		assertTrue(directive.getAddImports().isEmpty());
		assertTrue(directive.getRemoveImports().isEmpty());
		assertTrue(directive.getAddStaticImports().isEmpty());
		assertTrue(directive.getRemoveStaticImports().isEmpty());
	}
	
	@Test
	public void testImportDirectiveAddAndRemove() {
		ImportDirective directive = new ImportDirective();
		directive.addImport("java.util.Objects"); //$NON-NLS-1$
		directive.addImport("java.nio.charset.StandardCharsets"); //$NON-NLS-1$
		directive.removeImport("java.io.UnsupportedEncodingException"); //$NON-NLS-1$
		directive.addStaticImport("java.util.Objects.requireNonNull"); //$NON-NLS-1$
		directive.removeStaticImport("org.junit.Assert.assertEquals"); //$NON-NLS-1$
		
		assertFalse(directive.isEmpty());
		assertEquals(2, directive.getAddImports().size());
		assertEquals(1, directive.getRemoveImports().size());
		assertEquals(1, directive.getAddStaticImports().size());
		assertEquals(1, directive.getRemoveStaticImports().size());
		
		assertTrue(directive.getAddImports().contains("java.util.Objects")); //$NON-NLS-1$
		assertTrue(directive.getRemoveImports().contains("java.io.UnsupportedEncodingException")); //$NON-NLS-1$
	}
	
	@Test
	public void testImportDirectiveDetectFromPattern() {
		String replacement = "new String($bytes, java.nio.charset.StandardCharsets.UTF_8)"; //$NON-NLS-1$
		ImportDirective detected = ImportDirective.detectFromPattern(replacement);
		
		assertFalse(detected.isEmpty());
		assertTrue(detected.getAddImports().contains("java.nio.charset.StandardCharsets"), //$NON-NLS-1$
				"Should detect StandardCharsets import from pattern");
	}
	
	@Test
	public void testImportDirectiveDetectFromPatternNoFqn() {
		String replacement = "$x + 1"; //$NON-NLS-1$
		ImportDirective detected = ImportDirective.detectFromPattern(replacement);
		assertTrue(detected.isEmpty(), "No FQN in simple expression");
	}
	
	@Test
	public void testImportDirectiveMerge() {
		ImportDirective d1 = new ImportDirective();
		d1.addImport("java.util.Objects"); //$NON-NLS-1$
		
		ImportDirective d2 = new ImportDirective();
		d2.addImport("java.util.List"); //$NON-NLS-1$
		d2.removeImport("java.util.Vector"); //$NON-NLS-1$
		
		d1.merge(d2);
		assertEquals(2, d1.getAddImports().size());
		assertEquals(1, d1.getRemoveImports().size());
	}
	
	@Test
	public void testImportDirectiveConstructorWithLists() {
		ImportDirective directive = new ImportDirective(
				List.of("java.util.Objects"), //$NON-NLS-1$
				List.of("java.util.Vector"), //$NON-NLS-1$
				List.of("java.util.Objects.requireNonNull"), //$NON-NLS-1$
				List.of("org.junit.Assert.assertEquals") //$NON-NLS-1$
		);
		
		assertFalse(directive.isEmpty());
		assertEquals(1, directive.getAddImports().size());
		assertEquals(1, directive.getRemoveImports().size());
		assertEquals(1, directive.getAddStaticImports().size());
		assertEquals(1, directive.getRemoveStaticImports().size());
	}
	
	// --- PreviewGenerator tests ---
	
	@Test
	public void testPreviewGeneratorSimple() {
		Preview preview = PreviewGenerator.generatePreview(
				"$x + 1", //$NON-NLS-1$
				"++$x" //$NON-NLS-1$
		);
		
		assertNotNull(preview);
		assertEquals("x + 1", preview.before()); //$NON-NLS-1$
		assertEquals("++x", preview.after()); //$NON-NLS-1$
		assertTrue(preview.hasTransformation());
	}
	
	@Test
	public void testPreviewGeneratorHintOnly() {
		Preview preview = PreviewGenerator.generatePreview(
				"$x + 1", //$NON-NLS-1$
				null
		);
		
		assertNotNull(preview);
		assertEquals("x + 1", preview.before()); //$NON-NLS-1$
		assertNull(preview.after());
		assertFalse(preview.hasTransformation());
	}
	
	@Test
	public void testPreviewGeneratorMultiplePlaceholders() {
		Preview preview = PreviewGenerator.generatePreview(
				"$a + $b", //$NON-NLS-1$
				"$b + $a" //$NON-NLS-1$
		);
		
		assertNotNull(preview);
		assertEquals("a + b", preview.before()); //$NON-NLS-1$
		assertEquals("b + a", preview.after()); //$NON-NLS-1$
	}
	
	@Test
	public void testPreviewGeneratorFromRule() {
		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		RewriteAlternative alt = new RewriteAlternative("$x", null); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule(
				"Remove addition of zero", srcPattern, null, List.of(alt)); //$NON-NLS-1$
		
		Preview preview = PreviewGenerator.generatePreview(rule);
		assertNotNull(preview);
		assertEquals("x + 0", preview.before()); //$NON-NLS-1$
		assertEquals("x", preview.after()); //$NON-NLS-1$
		assertEquals("Remove addition of zero", preview.description()); //$NON-NLS-1$
	}
	
	@Test
	public void testPreviewFormat() {
		Preview preview = PreviewGenerator.generatePreview(
				"$x + 1", //$NON-NLS-1$
				"++$x" //$NON-NLS-1$
		);
		
		String formatted = preview.format();
		assertTrue(formatted.contains("Before: x + 1")); //$NON-NLS-1$
		assertTrue(formatted.contains("After:  ++x")); //$NON-NLS-1$
	}
	
	@Test
	public void testPreviewGeneratorVariadicPlaceholder() {
		Preview preview = PreviewGenerator.generatePreview(
				"method($args$)", //$NON-NLS-1$
				"method2($args$)" //$NON-NLS-1$
		);
		
		assertNotNull(preview);
		// Variadic placeholders should be substituted with example values
		assertTrue(preview.hasTransformation());
		assertFalse(preview.before().contains("$"), "Placeholders should be substituted"); //$NON-NLS-1$
	}
	
	// --- HintFileRegistry tests ---
	
	@Test
	public void testHintFileRegistryLoadFromString() throws HintParseException {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.clear();
		
		String content = "<!id: test.rule>\n" //$NON-NLS-1$
				+ "<!description: Test rule>\n" //$NON-NLS-1$
				+ "$x + 0\n" //$NON-NLS-1$
				+ "=> $x\n" //$NON-NLS-1$
				+ ";;\n"; //$NON-NLS-1$
		
		registry.loadFromString("test-rule", content); //$NON-NLS-1$
		
		HintFile hintFile = registry.getHintFile("test-rule"); //$NON-NLS-1$
		assertNotNull(hintFile);
		assertEquals("test.rule", hintFile.getId()); //$NON-NLS-1$
		assertEquals(1, hintFile.getRules().size());
		
		// Cleanup
		registry.clear();
	}
	
	@Test
	public void testHintFileRegistryGetAll() throws HintParseException {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.clear();
		
		String content1 = "$x + 0\n=> $x\n;;\n"; //$NON-NLS-1$
		String content2 = "$x * 1\n=> $x\n;;\n"; //$NON-NLS-1$
		
		registry.loadFromString("rule1", content1); //$NON-NLS-1$
		registry.loadFromString("rule2", content2); //$NON-NLS-1$
		
		assertEquals(2, registry.getAllHintFiles().size());
		assertEquals(2, registry.getRegisteredIds().size());
		assertTrue(registry.getRegisteredIds().contains("rule1")); //$NON-NLS-1$
		assertTrue(registry.getRegisteredIds().contains("rule2")); //$NON-NLS-1$
		
		// Cleanup
		registry.clear();
	}
	
	@Test
	public void testHintFileRegistryUnregister() throws HintParseException {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		registry.clear();
		
		registry.loadFromString("to-remove", "$x + 0\n=> $x\n;;\n"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.getHintFile("to-remove")); //$NON-NLS-1$
		
		HintFile removed = registry.unregister("to-remove"); //$NON-NLS-1$
		assertNotNull(removed);
		assertNull(registry.getHintFile("to-remove")); //$NON-NLS-1$
		
		// Cleanup
		registry.clear();
	}
	
	@Test
	public void testHintFileRegistryBundledLibraryNames() {
		String[] names = HintFileRegistry.getBundledLibraryNames();
		assertNotNull(names);
		assertTrue(names.length > 0, "Should have bundled library names");
	}
	
	// --- HintFileParser import directive tests ---
	
	@Test
	public void testParseRuleWithImportDirectives() throws HintParseException {
		String content = "new String($bytes, \"UTF-8\")\n" //$NON-NLS-1$
				+ "=> new String($bytes, StandardCharsets.UTF_8)\n" //$NON-NLS-1$
				+ "addImport java.nio.charset.StandardCharsets\n" //$NON-NLS-1$
				+ "removeImport java.io.UnsupportedEncodingException\n" //$NON-NLS-1$
				+ ";;\n"; //$NON-NLS-1$
		
		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		
		assertTrue(rule.hasImportDirective(), "Rule should have import directives");
		ImportDirective importDir = rule.getImportDirective();
		assertNotNull(importDir);
		assertTrue(importDir.getAddImports().contains("java.nio.charset.StandardCharsets")); //$NON-NLS-1$
		assertTrue(importDir.getRemoveImports().contains("java.io.UnsupportedEncodingException")); //$NON-NLS-1$
	}
	
	@Test
	public void testParseRuleWithStaticImportDirectives() throws HintParseException {
		String content = "Assert.assertEquals($expected, $actual)\n" //$NON-NLS-1$
				+ "=> assertEquals($expected, $actual)\n" //$NON-NLS-1$
				+ "addStaticImport org.junit.jupiter.api.Assertions.assertEquals\n" //$NON-NLS-1$
				+ "removeStaticImport org.junit.Assert.assertEquals\n" //$NON-NLS-1$
				+ ";;\n"; //$NON-NLS-1$
		
		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(content);
		
		assertEquals(1, hintFile.getRules().size());
		TransformationRule rule = hintFile.getRules().get(0);
		
		assertTrue(rule.hasImportDirective());
		ImportDirective importDir = rule.getImportDirective();
		assertTrue(importDir.getAddStaticImports().contains("org.junit.jupiter.api.Assertions.assertEquals")); //$NON-NLS-1$
		assertTrue(importDir.getRemoveStaticImports().contains("org.junit.Assert.assertEquals")); //$NON-NLS-1$
	}
	
	@Test
	public void testParseRuleAutoDetectsImports() throws HintParseException {
		// When no explicit import directives, auto-detect from FQN in replacement
		String content = "new String($bytes, \"UTF-8\")\n" //$NON-NLS-1$
				+ "=> new String($bytes, java.nio.charset.StandardCharsets.UTF_8)\n" //$NON-NLS-1$
				+ ";;\n"; //$NON-NLS-1$
		
		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(content);
		
		TransformationRule rule = hintFile.getRules().get(0);
		
		// Should auto-detect StandardCharsets import
		assertTrue(rule.hasImportDirective(), "Should auto-detect import from FQN");
		ImportDirective importDir = rule.getImportDirective();
		assertTrue(importDir.getAddImports().contains("java.nio.charset.StandardCharsets"), //$NON-NLS-1$
				"Should detect java.nio.charset.StandardCharsets");
	}
	
	// --- TransformationRule import directive tests ---
	
	@Test
	public void testTransformationRuleWithoutImports() {
		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		TransformationRule rule = new TransformationRule("test", srcPattern, null, List.of()); //$NON-NLS-1$
		
		assertFalse(rule.hasImportDirective());
		assertNull(rule.getImportDirective());
	}
	
	@Test
	public void testTransformationRuleWithImports() {
		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		ImportDirective imports = new ImportDirective();
		imports.addImport("java.util.Objects"); //$NON-NLS-1$
		
		TransformationRule rule = new TransformationRule(
				"test", srcPattern, null, List.of(), imports); //$NON-NLS-1$
		
		assertTrue(rule.hasImportDirective());
		assertNotNull(rule.getImportDirective());
		assertEquals(1, rule.getImportDirective().getAddImports().size());
	}
}
