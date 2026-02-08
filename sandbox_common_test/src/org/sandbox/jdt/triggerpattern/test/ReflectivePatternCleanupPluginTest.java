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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternContext;
import org.sandbox.jdt.triggerpattern.api.PatternHandler;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.cleanup.ReflectivePatternCleanupPlugin;

/**
 * Tests for {@link ReflectivePatternCleanupPlugin}.
 */
public class ReflectivePatternCleanupPluginTest {
	
	/**
	 * Test plugin with a single handler
	 */
	static class SingleHandlerPlugin extends ReflectivePatternCleanupPlugin {
		List<String> invocations = new ArrayList<>();
		
		@PatternHandler(pattern = "$x + $y", kind = PatternKind.EXPRESSION)
		public void handleAddition(PatternContext context) {
			invocations.add("handleAddition");
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return "preview";
		}
	}
	
	/**
	 * Test plugin with multiple handlers at different priorities
	 */
	static class MultiHandlerPlugin extends ReflectivePatternCleanupPlugin {
		List<String> invocations = new ArrayList<>();
		
		@PatternHandler(pattern = "$x + $y", kind = PatternKind.EXPRESSION, priority = 2)
		public void handleLowPriority(PatternContext context) {
			invocations.add("low");
		}
		
		@PatternHandler(pattern = "$x - $y", kind = PatternKind.EXPRESSION, priority = 1)
		public void handleHighPriority(PatternContext context) {
			invocations.add("high");
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return "preview";
		}
	}
	
	/**
	 * Test plugin with boolean return to test short-circuiting
	 */
	static class BooleanReturnPlugin extends ReflectivePatternCleanupPlugin {
		List<String> invocations = new ArrayList<>();
		
		@PatternHandler(pattern = "$x + $y", kind = PatternKind.EXPRESSION, priority = 1)
		public boolean handleFirst(PatternContext context) {
			invocations.add("first");
			return true; // Stop processing
		}
		
		@PatternHandler(pattern = "$x + $y", kind = PatternKind.EXPRESSION, priority = 2)
		public void handleSecond(PatternContext context) {
			invocations.add("second"); // Should not be called
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return "preview";
		}
	}
	
	/**
	 * Test plugin with invalid handler signature
	 */
	static class InvalidSignaturePlugin extends ReflectivePatternCleanupPlugin {
		@PatternHandler(pattern = "$x", kind = PatternKind.EXPRESSION)
		public void invalidHandler(String wrongParam) { // Wrong parameter type
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return "preview";
		}
	}
	
	/**
	 * Test plugin with invalid return type
	 */
	static class InvalidReturnTypePlugin extends ReflectivePatternCleanupPlugin {
		@PatternHandler(pattern = "$x", kind = PatternKind.EXPRESSION)
		public String invalidReturn(PatternContext context) { // Wrong return type
			return "invalid";
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return "preview";
		}
	}
	
	@Test
	public void testHandlerDiscovery() {
		SingleHandlerPlugin plugin = new SingleHandlerPlugin();
		List<Pattern> patterns = plugin.getPatterns();
		
		assertNotNull(patterns);
		assertEquals(1, patterns.size());
		assertEquals("$x + $y", patterns.get(0).getPatternString());
		assertEquals(PatternKind.EXPRESSION, patterns.get(0).getKind());
	}
	
	@Test
	public void testMultipleHandlersSortedByPriority() {
		MultiHandlerPlugin plugin = new MultiHandlerPlugin();
		List<Pattern> patterns = plugin.getPatterns();
		
		assertNotNull(patterns);
		assertEquals(2, patterns.size());
		// Handlers should be sorted by priority (ascending)
		// Priority 1 should come before priority 2
		assertEquals("$x - $y", patterns.get(0).getPatternString());
		assertEquals("$x + $y", patterns.get(1).getPatternString());
	}
	
	@Test
	public void testInvalidHandlerSignatureThrows() {
		InvalidSignaturePlugin plugin = new InvalidSignaturePlugin();
		
		// Attempting to get patterns should trigger handler discovery and validation
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			plugin.getPatterns();
		});
		
		assertTrue(exception.getMessage().contains("PatternContext"));
	}
	
	@Test
	public void testInvalidReturnTypeThrows() {
		InvalidReturnTypePlugin plugin = new InvalidReturnTypePlugin();
		
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			plugin.getPatterns();
		});
		
		assertTrue(exception.getMessage().contains("void or boolean"));
	}
	
	@Test
	public void testCleanupIdLocaleIndependent() {
		SingleHandlerPlugin plugin = new SingleHandlerPlugin();
		String id = plugin.getCleanupId();
		
		// Should use Locale.ROOT to avoid Turkish locale issues
		assertNotNull(id);
		assertTrue(id.startsWith("cleanup.reflective."));
		
		// Verify it's consistent regardless of locale
		Locale originalLocale = Locale.getDefault();
		try {
			Locale.setDefault(new Locale("tr", "TR")); // Turkish locale
			String idTurkish = new SingleHandlerPlugin().getCleanupId();
			assertEquals(id, idTurkish);
		} finally {
			Locale.setDefault(originalLocale);
		}
	}
	
	@Test
	public void testDefaultDescription() {
		SingleHandlerPlugin plugin = new SingleHandlerPlugin();
		String description = plugin.getDescription();
		
		assertNotNull(description);
		assertFalse(description.isEmpty());
		// Should contain the class name in a readable format
		assertTrue(description.contains("Single") || description.contains("Handler"));
	}
}
