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
package org.sandbox.jdt.triggerpattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for TriggerPattern integration with JUnit cleanup plugins.
 */
class TriggerPatternCleanupTest {
	
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	@Test
	void testAnnotationPatternMatching() {
		String source = """
			import org.junit.Before;
			public class MyTest {
				@Before
				public void setUp() {}
			}
			""";
		
		CompilationUnit cu = parse(source);
		Pattern pattern = new Pattern("@Before", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one @Before annotation");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof Annotation);
	}
	
	@Test
	void testAnnotationWithQualifiedType() {
		String source = """
			import org.junit.Before;
			public class MyTest {
				@Before
				public void setUp() {}
			}
			""";
		
		CompilationUnit cu = parse(source);
		Pattern pattern = new Pattern("@Before", PatternKind.ANNOTATION, "org.junit.Before");
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one @Before annotation with qualified type");
	}
	
	@Test
	void testAnnotationWithPlaceholder() {
		String source = """
			import org.junit.Test;
			public class MyTest {
				@Test(expected = IllegalArgumentException.class)
				public void testException() {}
			}
			""";
		
		CompilationUnit cu = parse(source);
		Pattern pattern = new Pattern("@Test(expected=$ex)", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one @Test annotation with expected parameter");
		
		Match match = matches.get(0);
		Map<String, ASTNode> bindings = match.getBindings();
		
		assertTrue(bindings.containsKey("$ex"), "Should have binding for $ex");
		
		ASTNode boundNode = bindings.get("$ex");
		assertTrue(boundNode instanceof TypeLiteral, "Bound node should be a TypeLiteral");
	}
	
	@Test
	void testMultipleAnnotationMatches() {
		String source = """
			import org.junit.Before;
			public class MyTest {
				@Before
				public void setUp1() {}
				
				@Before
				public void setUp2() {}
			}
			""";
		
		CompilationUnit cu = parse(source);
		Pattern pattern = new Pattern("@Before", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find two @Before annotations");
	}
	
	@Test
	void testNoMatchesForDifferentAnnotation() {
		String source = """
			import org.junit.After;
			public class MyTest {
				@After
				public void tearDown() {}
			}
			""";
		
		CompilationUnit cu = parse(source);
		Pattern pattern = new Pattern("@Before", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(0, matches.size(), "Should find no @Before annotations");
	}
	
	/**
	 * Parses Java source code into a CompilationUnit.
	 */
	private CompilationUnit parse(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Test.java");
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		return (CompilationUnit) parser.createAST(null);
	}
}
