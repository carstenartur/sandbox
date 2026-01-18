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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

/**
 * Fast test adapted from MatcherTest in sandbox_common_test.
 * This version runs without OSGi runtime and uses standard Maven Surefire.
 */
public class FastMatcherTest {

	@Test
	public void testMatchEnhancedForLoop() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		String code = """
			import java.util.Collection;

			public class E {
				public void hui(Collection<String> arr) {
					Collection coll = null;
					for (String var : arr) {
						 coll.add(var);
					}
				}
			}""";
		
		char[] source = code.toCharArray();
		parser.setSource(source);
		
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
		parser.setCompilerOptions(options);
		
		CompilationUnit result = (CompilationUnit) parser.createAST(null);
		
		assertNotNull(result, "CompilationUnit should not be null");
		assertTrue(result.types().size() > 0, "Should have at least one type");
		
		TypeDeclaration type = (TypeDeclaration) result.types().get(0);
		assertTrue(type.getMethods().length > 0, "Should have at least one method");
		
		MethodDeclaration method = type.getMethods()[0];
		assertNotNull(method, "Method should not be null");
		
		// Verify we can traverse the AST and find the enhanced for loop
		boolean[] foundEnhancedFor = {false};
		method.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement node) {
				foundEnhancedFor[0] = true;
				return super.visit(node);
			}
		});
		
		assertTrue(foundEnhancedFor[0], "Should find an enhanced for statement");
	}
	
	@Test
	public void testParseWithMultipleCompilerVersions() {
		// Test that we can parse code with different Java versions
		String code = """
			public class VersionTest {
				public void test() {
					var list = java.util.List.of(1, 2, 3);
				}
			}""";
		
		// Test with Java 11
		ASTParser parser11 = ASTParser.newParser(AST.getJLSLatest());
		parser11.setSource(code.toCharArray());
		Map<String, String> options11 = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_11, options11);
		parser11.setCompilerOptions(options11);
		CompilationUnit cu11 = (CompilationUnit) parser11.createAST(null);
		assertNotNull(cu11, "Java 11 compilation should succeed");
		
		// Test with Java 17
		ASTParser parser17 = ASTParser.newParser(AST.getJLSLatest());
		parser17.setSource(code.toCharArray());
		Map<String, String> options17 = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_17, options17);
		parser17.setCompilerOptions(options17);
		CompilationUnit cu17 = (CompilationUnit) parser17.createAST(null);
		assertNotNull(cu17, "Java 17 compilation should succeed");
		
		// Test with Java 21
		ASTParser parser21 = ASTParser.newParser(AST.getJLSLatest());
		parser21.setSource(code.toCharArray());
		Map<String, String> options21 = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options21);
		parser21.setCompilerOptions(options21);
		CompilationUnit cu21 = (CompilationUnit) parser21.createAST(null);
		assertNotNull(cu21, "Java 21 compilation should succeed");
	}
}
