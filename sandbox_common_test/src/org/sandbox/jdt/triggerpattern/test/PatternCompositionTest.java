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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for Pattern Composition: allowing hint files to include rules from
 * other registered hint files via {@code <!include: id>} directives.
 * 
 * @see HintFile#getIncludes()
 * @see HintFileRegistry#resolveIncludes(HintFile)
 * @since 1.3.4
 */
public class PatternCompositionTest {
	
	private final HintFileParser parser = new HintFileParser();
	private final HintFileRegistry registry = HintFileRegistry.getInstance();
	
	@BeforeEach
	public void setUp() {
		registry.clear();
	}
	
	@AfterEach
	public void tearDown() {
		registry.clear();
	}
	
	@Test
	public void testParseIncludeDirective() throws HintParseException {
		String content = """
			<!id: composite.rule>
			<!include: base.rules>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			"""; //$NON-NLS-1$
		
		HintFile hintFile = parser.parse(content);
		
		assertNotNull(hintFile);
		assertEquals("composite.rule", hintFile.getId()); //$NON-NLS-1$
		assertEquals(1, hintFile.getRules().size());
		assertEquals(1, hintFile.getIncludes().size());
		assertEquals("base.rules", hintFile.getIncludes().get(0)); //$NON-NLS-1$
	}
	
	@Test
	public void testParseMultipleIncludes() throws HintParseException {
		String content = """
			<!id: combined>
			<!include: encoding-rules>
			<!include: performance-rules>
			<!include: collections-rules>
			
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals(3, hintFile.getIncludes().size());
		assertTrue(hintFile.getIncludes().contains("encoding-rules")); //$NON-NLS-1$
		assertTrue(hintFile.getIncludes().contains("performance-rules")); //$NON-NLS-1$
		assertTrue(hintFile.getIncludes().contains("collections-rules")); //$NON-NLS-1$
	}
	
	@Test
	public void testResolveIncludesMergesRules() throws HintParseException {
		// Register base rules
		String baseContent = """
			<!id: base>
			$x + 0
			=> $x
			;;
			
			$x * 1
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("base", baseContent); //$NON-NLS-1$
		
		// Register composite that includes base
		String compositeContent = """
			<!id: composite>
			<!include: base>
			
			$x - 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("composite", compositeContent); //$NON-NLS-1$
		
		HintFile composite = registry.getHintFile("composite"); //$NON-NLS-1$
		assertNotNull(composite);
		assertEquals(1, composite.getRules().size(), "Own rules only"); //$NON-NLS-1$
		
		List<TransformationRule> allRules = registry.resolveIncludes(composite);
		assertEquals(3, allRules.size(), "Should have own rule + 2 included rules"); //$NON-NLS-1$
	}
	
	@Test
	public void testResolveIncludesTransitive() throws HintParseException {
		// Register level-2 rules
		String level2Content = """
			<!id: level2>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("level2", level2Content); //$NON-NLS-1$
		
		// Register level-1 rules that includes level-2
		String level1Content = """
			<!id: level1>
			<!include: level2>
			
			$x * 1
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("level1", level1Content); //$NON-NLS-1$
		
		// Register top-level that includes level-1
		String topContent = """
			<!id: top>
			<!include: level1>
			
			$x - 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("top", topContent); //$NON-NLS-1$
		
		HintFile top = registry.getHintFile("top"); //$NON-NLS-1$
		List<TransformationRule> allRules = registry.resolveIncludes(top);
		
		assertEquals(3, allRules.size(), 
				"Should have own rule + level1 rule + level2 rule (transitive)"); //$NON-NLS-1$
	}
	
	@Test
	public void testResolveIncludesCircularReference() throws HintParseException {
		// Register A which includes B
		String contentA = """
			<!id: A>
			<!include: B>
			
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("A", contentA); //$NON-NLS-1$
		
		// Register B which includes A (circular!)
		String contentB = """
			<!id: B>
			<!include: A>
			
			$x * 1
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("B", contentB); //$NON-NLS-1$
		
		HintFile fileA = registry.getHintFile("A"); //$NON-NLS-1$
		List<TransformationRule> allRules = registry.resolveIncludes(fileA);
		
		// Should not loop infinitely, should contain A's rule + B's rule
		assertEquals(2, allRules.size(), 
				"Should have own rule + B's rule, circular reference broken"); //$NON-NLS-1$
	}
	
	@Test
	public void testResolveIncludesMissingReference() throws HintParseException {
		// Register composite that includes a non-existent file
		String content = """
			<!id: composite>
			<!include: non-existent>
			
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("composite", content); //$NON-NLS-1$
		
		HintFile composite = registry.getHintFile("composite"); //$NON-NLS-1$
		List<TransformationRule> allRules = registry.resolveIncludes(composite);
		
		assertEquals(1, allRules.size(), 
				"Should have only own rule, missing include silently skipped"); //$NON-NLS-1$
	}
	
	@Test
	public void testNoIncludesReturnsOwnRules() throws HintParseException {
		String content = """
			<!id: standalone>
			$x + 0
			=> $x
			;;
			"""; //$NON-NLS-1$
		registry.loadFromString("standalone", content); //$NON-NLS-1$
		
		HintFile standalone = registry.getHintFile("standalone"); //$NON-NLS-1$
		List<TransformationRule> allRules = registry.resolveIncludes(standalone);
		
		assertEquals(1, allRules.size());
		assertTrue(standalone.getIncludes().isEmpty());
	}
	
	@Test
	public void testHintFileDefaultIncludesEmpty() {
		HintFile hintFile = new HintFile();
		assertNotNull(hintFile.getIncludes());
		assertTrue(hintFile.getIncludes().isEmpty());
	}
	
	@Test
	public void testAddIncludeIgnoresBlank() {
		HintFile hintFile = new HintFile();
		hintFile.addInclude(null);
		hintFile.addInclude(""); //$NON-NLS-1$
		hintFile.addInclude("   "); //$NON-NLS-1$
		
		assertTrue(hintFile.getIncludes().isEmpty(), 
				"Null and blank includes should be ignored"); //$NON-NLS-1$
	}
	
	@Test
	public void testIncludeWithOtherMetadata() throws HintParseException {
		String content = """
			<!id: full-metadata>
			<!description: Complete file with includes>
			<!severity: warning>
			<!minJavaVersion: 11>
			<!tags: test, example>
			<!include: base-rules>
			
			$x.equals($y)
			=> java.util.Objects.equals($x, $y)
			;;
			"""; //$NON-NLS-1$
		
		HintFile hintFile = parser.parse(content);
		
		assertEquals("full-metadata", hintFile.getId()); //$NON-NLS-1$
		assertEquals("Complete file with includes", hintFile.getDescription()); //$NON-NLS-1$
		assertEquals("warning", hintFile.getSeverity()); //$NON-NLS-1$
		assertEquals(11, hintFile.getMinJavaVersion());
		assertFalse(hintFile.getTags().isEmpty());
		assertEquals(1, hintFile.getIncludes().size());
		assertEquals("base-rules", hintFile.getIncludes().get(0)); //$NON-NLS-1$
		assertEquals(1, hintFile.getRules().size());
	}
}
