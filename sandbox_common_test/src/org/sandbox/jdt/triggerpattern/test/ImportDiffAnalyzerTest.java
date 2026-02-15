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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.mining.analysis.ImportDiffAnalyzer;

/**
 * Tests for {@link ImportDiffAnalyzer}.
 */
public class ImportDiffAnalyzerTest {

	private final ImportDiffAnalyzer analyzer = new ImportDiffAnalyzer();

	@Test
	public void testNoImportChangesReturnsNull() {
		String code = """
				import java.util.List;
				class T { }
				"""; //$NON-NLS-1$
		CompilationUnit before = parse(code);
		CompilationUnit after = parse(code);

		ImportDirective result = analyzer.analyzeImportChanges(before, after);
		assertNull(result, "No import changes should return null"); //$NON-NLS-1$
	}

	@Test
	public void testAddedImportDetected() {
		String codeBefore = """
				class T { }
				"""; //$NON-NLS-1$
		String codeAfter = """
				import java.nio.charset.StandardCharsets;
				class T { }
				"""; //$NON-NLS-1$
		CompilationUnit before = parse(codeBefore);
		CompilationUnit after = parse(codeAfter);

		ImportDirective result = analyzer.analyzeImportChanges(before, after);
		assertNotNull(result, "Should detect added import"); //$NON-NLS-1$
		assertEquals(1, result.getAddImports().size());
		assertTrue(result.getAddImports().contains("java.nio.charset.StandardCharsets")); //$NON-NLS-1$
	}

	@Test
	public void testRemovedImportDetected() {
		String codeBefore = """
				import java.io.UnsupportedEncodingException;
				class T { }
				"""; //$NON-NLS-1$
		String codeAfter = """
				class T { }
				"""; //$NON-NLS-1$
		CompilationUnit before = parse(codeBefore);
		CompilationUnit after = parse(codeAfter);

		ImportDirective result = analyzer.analyzeImportChanges(before, after);
		assertNotNull(result, "Should detect removed import"); //$NON-NLS-1$
		assertEquals(1, result.getRemoveImports().size());
		assertTrue(result.getRemoveImports().contains("java.io.UnsupportedEncodingException")); //$NON-NLS-1$
	}

	@Test
	public void testAddedAndRemovedImportsDetected() {
		String codeBefore = """
				import java.io.UnsupportedEncodingException;
				import java.util.List;
				class T { }
				"""; //$NON-NLS-1$
		String codeAfter = """
				import java.nio.charset.StandardCharsets;
				import java.util.List;
				class T { }
				"""; //$NON-NLS-1$
		CompilationUnit before = parse(codeBefore);
		CompilationUnit after = parse(codeAfter);

		ImportDirective result = analyzer.analyzeImportChanges(before, after);
		assertNotNull(result, "Should detect import changes"); //$NON-NLS-1$
		assertEquals(1, result.getAddImports().size());
		assertEquals(1, result.getRemoveImports().size());
		assertTrue(result.getAddImports().contains("java.nio.charset.StandardCharsets")); //$NON-NLS-1$
		assertTrue(result.getRemoveImports().contains("java.io.UnsupportedEncodingException")); //$NON-NLS-1$
	}

	@Test
	public void testStaticImportChangesDetected() {
		String codeBefore = """
				import static org.junit.Assert.assertEquals;
				class T { }
				"""; //$NON-NLS-1$
		String codeAfter = """
				import static org.junit.jupiter.api.Assertions.assertEquals;
				class T { }
				"""; //$NON-NLS-1$
		CompilationUnit before = parse(codeBefore);
		CompilationUnit after = parse(codeAfter);

		ImportDirective result = analyzer.analyzeImportChanges(before, after);
		assertNotNull(result, "Should detect static import changes"); //$NON-NLS-1$
		assertEquals(1, result.getAddStaticImports().size());
		assertEquals(1, result.getRemoveStaticImports().size());
	}

	// ---- helpers ----

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
