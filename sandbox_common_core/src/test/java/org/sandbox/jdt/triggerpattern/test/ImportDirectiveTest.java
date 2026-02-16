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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;

/**
 * Tests for {@link ImportDirective}, especially the FQN-based inference methods.
 */
public class ImportDirectiveTest {

	@Test
	public void testDetectFromPatternWithFqn() {
		ImportDirective directive = ImportDirective.detectFromPattern(
				"java.util.Objects.equals($x, $y)"); //$NON-NLS-1$
		assertFalse(directive.isEmpty());
		assertTrue(directive.getAddImports().contains("java.util.Objects")); //$NON-NLS-1$
	}

	@Test
	public void testDetectFromPatternWithoutFqn() {
		ImportDirective directive = ImportDirective.detectFromPattern(
				"Objects.equals($x, $y)"); //$NON-NLS-1$
		assertTrue(directive.isEmpty(), "Short name should not be detected as FQN"); //$NON-NLS-1$
	}

	@Test
	public void testDetectFromPatternWithMultipleFqns() {
		ImportDirective directive = ImportDirective.detectFromPattern(
				"java.util.Objects.equals($x, java.nio.charset.StandardCharsets.UTF_8)"); //$NON-NLS-1$
		assertEquals(2, directive.getAddImports().size());
		assertTrue(directive.getAddImports().contains("java.util.Objects")); //$NON-NLS-1$
		assertTrue(directive.getAddImports().contains("java.nio.charset.StandardCharsets")); //$NON-NLS-1$
	}

	@Test
	public void testDetectFromPatternNull() {
		ImportDirective directive = ImportDirective.detectFromPattern(null);
		assertTrue(directive.isEmpty());
	}

	@Test
	public void testDetectFromPatternEmpty() {
		ImportDirective directive = ImportDirective.detectFromPattern(""); //$NON-NLS-1$
		assertTrue(directive.isEmpty());
	}

	@Test
	public void testDetectRemovedTypesBasic() {
		List<RewriteAlternative> alternatives = List.of(
				RewriteAlternative.otherwise("java.nio.charset.StandardCharsets.UTF_8")); //$NON-NLS-1$
		ImportDirective directive = ImportDirective.detectRemovedTypes(
				"java.io.FileReader.create($path)", alternatives); //$NON-NLS-1$
		assertFalse(directive.isEmpty());
		assertTrue(directive.getRemoveImports().contains("java.io.FileReader"), //$NON-NLS-1$
				"FQN in source but not in replacement should be a removeImport candidate"); //$NON-NLS-1$
	}

	@Test
	public void testDetectRemovedTypesNoChange() {
		List<RewriteAlternative> alternatives = List.of(
				RewriteAlternative.otherwise("java.util.Objects.equals($x, $y)")); //$NON-NLS-1$
		ImportDirective directive = ImportDirective.detectRemovedTypes(
				"java.util.Objects.equals($x, $y)", alternatives); //$NON-NLS-1$
		assertTrue(directive.isEmpty(),
				"Same FQNs in source and replacement should produce no removeImport"); //$NON-NLS-1$
	}

	@Test
	public void testDetectRemovedTypesNoFqnInSource() {
		List<RewriteAlternative> alternatives = List.of(
				RewriteAlternative.otherwise("Assertions.assertEquals($a, $b)")); //$NON-NLS-1$
		ImportDirective directive = ImportDirective.detectRemovedTypes(
				"Assert.assertEquals($a, $b)", alternatives); //$NON-NLS-1$
		assertTrue(directive.isEmpty(),
				"Short names should not be detected"); //$NON-NLS-1$
	}

	@Test
	public void testDetectRemovedTypesNullSource() {
		List<RewriteAlternative> alternatives = List.of(
				RewriteAlternative.otherwise("$x")); //$NON-NLS-1$
		ImportDirective directive = ImportDirective.detectRemovedTypes(null, alternatives);
		assertTrue(directive.isEmpty());
	}

	@Test
	public void testDetectRemovedTypesNullAlternatives() {
		ImportDirective directive = ImportDirective.detectRemovedTypes("java.io.File", null); //$NON-NLS-1$
		assertTrue(directive.isEmpty());
	}

	@Test
	public void testDetectRemovedTypesEmptyAlternatives() {
		ImportDirective directive = ImportDirective.detectRemovedTypes(
				"java.io.File", List.of()); //$NON-NLS-1$
		assertTrue(directive.isEmpty());
	}
	
	// --- inferFromFqnPatterns tests ---
	
	@Test
	public void testInferFromFqnPatternsAddAndRemove() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"org.junit.Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				List.of("org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)")); //$NON-NLS-1$
		assertFalse(directive.isEmpty());
		assertTrue(directive.getAddImports().contains("org.junit.jupiter.api.Assertions"), //$NON-NLS-1$
				"Replacement FQN should be addImport"); //$NON-NLS-1$
		assertTrue(directive.getRemoveImports().contains("org.junit.Assert"), //$NON-NLS-1$
				"Source FQN not in replacement should be removeImport"); //$NON-NLS-1$
	}
	
	@Test
	public void testInferFromFqnPatternsReplaceStaticImport() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"org.junit.Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				List.of("org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)")); //$NON-NLS-1$
		assertFalse(directive.getReplaceStaticImports().isEmpty(),
				"Should infer replaceStaticImport from matching method names"); //$NON-NLS-1$
		assertEquals("org.junit.jupiter.api.Assertions", //$NON-NLS-1$
				directive.getReplaceStaticImports().get("org.junit.Assert")); //$NON-NLS-1$
	}
	
	@Test
	public void testInferFromFqnPatternsNoFqns() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"Assert.assertEquals($a, $b)", //$NON-NLS-1$
				List.of("Assertions.assertEquals($a, $b)")); //$NON-NLS-1$
		assertTrue(directive.isEmpty(),
				"Short names should not produce any import directives"); //$NON-NLS-1$
	}
	
	@Test
	public void testInferFromFqnPatternsOnlyReplacement() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"$x.equals($y)", //$NON-NLS-1$
				List.of("java.util.Objects.equals($x, $y)")); //$NON-NLS-1$
		assertFalse(directive.isEmpty());
		assertTrue(directive.getAddImports().contains("java.util.Objects"), //$NON-NLS-1$
				"FQN in replacement should be addImport"); //$NON-NLS-1$
		assertTrue(directive.getRemoveImports().isEmpty(),
				"No FQN in source means no removeImport"); //$NON-NLS-1$
	}
	
	@Test
	public void testInferFromFqnPatternsNullReplacements() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"org.junit.Assert.fail()", null); //$NON-NLS-1$
		assertTrue(directive.isEmpty());
	}
	
	@Test
	public void testInferFromFqnPatternsEmptyReplacements() {
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"org.junit.Assert.fail()", List.of()); //$NON-NLS-1$
		assertTrue(directive.isEmpty());
	}
	
	@Test
	public void testInferFromFqnPatternsAnnotationStyle() {
		// Annotation patterns: FQNs after @ sign
		ImportDirective directive = ImportDirective.inferFromFqnPatterns(
				"@org.junit.Before", //$NON-NLS-1$
				List.of("@org.junit.jupiter.api.BeforeEach")); //$NON-NLS-1$
		assertFalse(directive.isEmpty());
		assertTrue(directive.getAddImports().contains("org.junit.jupiter.api.BeforeEach"), //$NON-NLS-1$
				"Annotation FQN in replacement should be addImport"); //$NON-NLS-1$
		assertTrue(directive.getRemoveImports().contains("org.junit.Before"), //$NON-NLS-1$
				"Annotation FQN only in source should be removeImport"); //$NON-NLS-1$
	}
}
