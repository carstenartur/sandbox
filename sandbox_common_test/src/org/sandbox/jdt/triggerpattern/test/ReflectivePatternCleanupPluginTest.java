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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
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

import java.util.List;

/**
 * Tests for {@link ReflectivePatternCleanupPlugin}.
 */
public class ReflectivePatternCleanupPluginTest {
	
	/**
	 * Test plugin that uses @PatternHandler annotation
	 */
	static class TestPlugin extends ReflectivePatternCleanupPlugin {
		int handleCount = 0;
		
		@PatternHandler(value = "$x + 1", kind = PatternKind.EXPRESSION)
		public void handleAddition(PatternContext context) {
			handleCount++;
			assertNotNull(context.getMatch());
			assertNotNull(context.getAST());
			assertNotNull(context.getRewrite());
		}
		
		@Override
		public String getPreview(boolean afterRefactoring) {
			return afterRefactoring ? "// after" : "// before";
		}
	}
	
	@Test
	public void testDiscoverHandlers() {
		TestPlugin plugin = new TestPlugin();
		
		// Get patterns - should discover the @PatternHandler annotated method
		List<Pattern> patterns = plugin.getPatterns();
		
		assertEquals(1, patterns.size(), "Should discover one pattern from @PatternHandler");
		Pattern pattern = patterns.get(0);
		assertEquals("$x + 1", pattern.getValue());
		assertEquals(PatternKind.EXPRESSION, pattern.getKind());
	}
	
	@Test
	public void testFindMatches() {
		String code = """
			class Test {
				void method() {
					int x = a + 1;
					int y = b + 1;
				}
			}
			""";
		
		TestPlugin plugin = new TestPlugin();
		CompilationUnit cu = parse(code);
		List<Pattern> patterns = plugin.getPatterns();
		
		List<Match> matches = plugin.findAllMatches(cu, patterns);
		
		assertEquals(2, matches.size(), "Should find two matches");
	}
	
	@Test
	public void testHandlerInvocation() {
		String code = """
			class Test {
				void method() {
					int x = a + 1;
				}
			}
			""";
		
		TestPlugin plugin = new TestPlugin();
		CompilationUnit cu = parse(code);
		List<Pattern> patterns = plugin.getPatterns();
		List<Match> matches = plugin.findAllMatches(cu, patterns);
		
		assertEquals(1, matches.size());
		Match match = matches.get(0);
		
		// Create AST rewrite context
		AST ast = cu.getAST();
		ASTRewrite rewrite = ASTRewrite.create(ast);
		ImportRewrite importRewrite = ImportRewrite.create(cu, true);
		TextEditGroup editGroup = new TextEditGroup("Test");
		
		// Process the match - this should invoke the handler
		plugin.processMatchWithContext(match, editGroup, rewrite, ast, importRewrite);
		
		// Verify handler was called
		assertEquals(1, plugin.handleCount, "Handler should have been invoked once");
	}
	
	@Test
	public void testMultipleHandlers() {
		/**
		 * Plugin with multiple handlers
		 */
		class MultiHandlerPlugin extends ReflectivePatternCleanupPlugin {
			int addCount = 0;
			int subtractCount = 0;
			
			@PatternHandler(value = "$x + 1", kind = PatternKind.EXPRESSION)
			public void handleAdd(PatternContext context) {
				addCount++;
			}
			
			@PatternHandler(value = "$x - 1", kind = PatternKind.EXPRESSION)
			public void handleSubtract(PatternContext context) {
				subtractCount++;
			}
			
			@Override
			public String getPreview(boolean afterRefactoring) {
				return "";
			}
		}
		
		MultiHandlerPlugin plugin = new MultiHandlerPlugin();
		List<Pattern> patterns = plugin.getPatterns();
		
		assertEquals(2, patterns.size(), "Should discover two patterns");
	}
	
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
}
