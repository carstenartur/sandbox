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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

/**
 * Fast test for basic AST parsing functionality without requiring OSGi runtime.
 * This test demonstrates that we can parse Java code using JDT Core from Maven Central.
 */
public class SimpleASTParsingTest {

	@Test
	public void testParseSimpleClass() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		String code = """
			public class HelloWorld {
				public static void main(String[] args) {
					System.out.println("Hello, World!");
				}
			}""";
		
		char[] source = code.toCharArray();
		parser.setSource(source);
		
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		assertNotNull(cu, "CompilationUnit should not be null");
		assertTrue(cu.types().size() > 0, "Should have at least one type");
		
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		assertNotNull(type, "TypeDeclaration should not be null");
		assertTrue(type.getName().getIdentifier().equals("HelloWorld"), 
				"Class name should be HelloWorld");
	}
	
	@Test
	public void testParseWithForEachLoop() {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		String code = """
			import java.util.List;
			
			public class LoopExample {
				public void process(List<String> items) {
					for (String item : items) {
						System.out.println(item);
					}
				}
			}""";
		
		char[] source = code.toCharArray();
		parser.setSource(source);
		
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		
		assertNotNull(cu, "CompilationUnit should not be null");
		assertTrue(cu.types().size() > 0, "Should have at least one type");
		assertTrue(cu.imports().size() > 0, "Should have at least one import");
	}
}
