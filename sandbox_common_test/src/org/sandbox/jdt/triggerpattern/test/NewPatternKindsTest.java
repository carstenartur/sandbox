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
package org.sandbox.jdt.triggerpattern.test;

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
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Tests for new pattern kinds: ANNOTATION, METHOD_CALL, IMPORT, FIELD.
 */
public class NewPatternKindsTest {
	
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	// ========== ANNOTATION PATTERN TESTS ==========
	
	@Test
	public void testSimpleMarkerAnnotation() {
		String code = """
			import org.junit.Before;
			
			class Test {
				@Before
				void setUp() {}
				
				@Before
				void setUp2() {}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("@Before", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find two @Before annotations");
		
		for (Match match : matches) {
			assertNotNull(match.getMatchedNode());
			assertTrue(match.getMatchedNode() instanceof Annotation);
		}
	}
	
	@Test
	public void testAnnotationWithParameters() {
		String code = """
			import org.junit.Test;
			
			class TestClass {
				@Test(expected=Exception.class)
				void testMethod() {}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("@Test(expected=$ex)", PatternKind.ANNOTATION);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one @Test annotation with expected parameter");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof Annotation);
		
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$ex"), "Should have binding for $ex");
	}
	
	@Test
	public void testMultipleAnnotationsOnSameElement() {
		String code = """
			import org.junit.Test;
			import org.junit.Ignore;
			
			class TestClass {
				@Test
				@Ignore
				void testMethod() {}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern testPattern = new Pattern("@Test", PatternKind.ANNOTATION);
		Pattern ignorePattern = new Pattern("@Ignore", PatternKind.ANNOTATION);
		
		List<Match> testMatches = engine.findMatches(cu, testPattern);
		List<Match> ignoreMatches = engine.findMatches(cu, ignorePattern);
		
		assertEquals(1, testMatches.size(), "Should find one @Test annotation");
		assertEquals(1, ignoreMatches.size(), "Should find one @Ignore annotation");
	}
	
	// ========== METHOD_CALL PATTERN TESTS ==========
	
	@Test
	public void testSimpleMethodCall() {
		String code = """
			class Test {
				void method() {
					System.out.println("test");
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("System.out.println($msg)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one println call");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof MethodInvocation);
		
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$msg"), "Should have binding for $msg");
	}
	
	@Test
	public void testMethodCallWithMultipleArguments() {
		String code = """
			class Test {
				void method() {
					assertEquals("message", expected, actual);
					assertEquals(1, 2);
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("assertEquals($msg, $expected, $actual)", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one assertEquals call with 3 arguments");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertEquals(5, bindings.size(), "Should have 5 bindings: $msg, $expected, $actual, $_, $this");
		assertTrue(bindings.containsKey("$msg"));
		assertTrue(bindings.containsKey("$expected"));
		assertTrue(bindings.containsKey("$actual"));
		assertTrue(bindings.containsKey("$_"), "Should have $_ auto-binding");
		assertTrue(bindings.containsKey("$this"), "Should have $this auto-binding");
	}
	
	@Test
	public void testMethodCallWithPlaceholderQualifier() {
		String code = """
			class Test {
				void method() {
					obj1.toString();
					obj2.toString();
				}
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("$obj.toString()", PatternKind.METHOD_CALL);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find two toString() calls");
		
		for (Match match : matches) {
			Map<String, Object> bindings = match.getBindings();
			assertTrue(bindings.containsKey("$obj"), "Should have binding for $obj");
		}
	}
	
	// ========== IMPORT PATTERN TESTS ==========
	
	@Test
	public void testSimpleImport() {
		String code = """
			import org.junit.Assert;
			import org.junit.Test;
			
			class Test {}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("import org.junit.Assert", PatternKind.IMPORT);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one import for org.junit.Assert");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof ImportDeclaration);
	}
	
	@Test
	public void testMultipleImports() {
		String code = """
			import org.junit.Before;
			import org.junit.After;
			import org.junit.Test;
			
			class Test {}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern beforePattern = new Pattern("import org.junit.Before", PatternKind.IMPORT);
		Pattern afterPattern = new Pattern("import org.junit.After", PatternKind.IMPORT);
		
		List<Match> beforeMatches = engine.findMatches(cu, beforePattern);
		List<Match> afterMatches = engine.findMatches(cu, afterPattern);
		
		assertEquals(1, beforeMatches.size(), "Should find one Before import");
		assertEquals(1, afterMatches.size(), "Should find one After import");
	}
	
	@Test
	public void testStaticImport() {
		String code = """
			import static org.junit.Assert.assertEquals;
			
			class Test {}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("import static org.junit.Assert.assertEquals", PatternKind.IMPORT);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one static import");
	}
	
	// ========== FIELD PATTERN TESTS ==========
	
	@Test
	public void testSimpleField() {
		String code = """
			class Test {
				private String name;
				public int count;
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("private String $name", PatternKind.FIELD);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one private String field");
		
		Match match = matches.get(0);
		assertNotNull(match.getMatchedNode());
		assertTrue(match.getMatchedNode() instanceof FieldDeclaration);
		
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$name"), "Should have binding for $name");
		ASTNode nameNode = match.getBinding("$name");
		assertTrue(nameNode instanceof SimpleName);
		assertEquals("name", ((SimpleName) nameNode).getIdentifier());
	}
	
	@Test
	public void testFieldWithAnnotation() {
		String code = """
			class Test {
				@Rule
				public TemporaryFolder folder = new TemporaryFolder();
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("@Rule public TemporaryFolder $name", PatternKind.FIELD);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(1, matches.size(), "Should find one @Rule field");
		
		Match match = matches.get(0);
		Map<String, Object> bindings = match.getBindings();
		assertTrue(bindings.containsKey("$name"), "Should have binding for $name");
	}
	
	@Test
	public void testFieldWithPlaceholderType() {
		String code = """
			class Test {
				public String name;
				public Integer count;
			}
			""";
		
		CompilationUnit cu = parse(code);
		Pattern pattern = new Pattern("public $type $name", PatternKind.FIELD);
		
		List<Match> matches = engine.findMatches(cu, pattern);
		
		assertEquals(2, matches.size(), "Should find two public fields");
		
		for (Match match : matches) {
			Map<String, Object> bindings = match.getBindings();
			assertTrue(bindings.containsKey("$type"), "Should have binding for $type");
			assertTrue(bindings.containsKey("$name"), "Should have binding for $name");
		}
	}
	
	// ========== HELPER METHODS ==========
	
	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		return (CompilationUnit) parser.createAST(null);
	}
}
