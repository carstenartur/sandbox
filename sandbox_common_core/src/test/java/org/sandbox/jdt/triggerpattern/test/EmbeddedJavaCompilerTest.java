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

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.SourceLineMapping;

/**
 * Tests for {@link EmbeddedJavaCompiler}.
 *
 * @since 1.5.0
 */
public class EmbeddedJavaCompilerTest {

	@Test
	public void testCompileSimpleGuardMethod() {
		String source = """
			public boolean isPositive(int x) {
			    return x > 0;
			}
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 3, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "test.rule");

		assertNotNull(result);
		assertNotNull(result.compilationUnit());
		assertFalse(result.guardMethods().isEmpty(), "Should find guard method");
		assertEquals("isPositive", result.guardMethods().get(0).getName().getIdentifier());
	}

	@Test
	public void testCompileMultipleMethods() {
		String source = """
			public boolean guardA() { return true; }
			public boolean guardB() { return false; }
			public void helperMethod() { }
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 3, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "multi");

		// guardA and guardB are guard methods (return boolean), helperMethod is not
		assertEquals(2, result.guardMethods().size());
	}

	@Test
	public void testCompileEmptyBlock() {
		EmbeddedJavaBlock block = new EmbeddedJavaBlock("", 1, 1, 0, 0);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "empty");

		assertNotNull(result);
		assertTrue(result.guardMethods().isEmpty());
	}

	@Test
	public void testCompileWithSyntaxError() {
		String source = "public boolean broken( { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "error");

		assertNotNull(result);
		assertTrue(result.hasErrors(), "Should have compilation errors");
	}

	@Test
	public void testCompileNullRuleId() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, null);

		assertNotNull(result);
		assertFalse(result.guardMethods().isEmpty());
	}

	// --- Phase 6: Source line mapping tests ---

	@Test
	public void testSyntheticClassName() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 5, 5, 100, 140);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "my.rule");

		assertNotNull(result.syntheticClassName());
		assertTrue(result.syntheticClassName().startsWith("org.sandbox.generated.HintCode_"),
				"Synthetic class should have the expected prefix");
	}

	@Test
	public void testLineMappingsPresent() {
		String source = """
			public boolean guardA() { return true; }
			public boolean guardB() { return false; }
			public void helper() { }
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 10, 12, 200, 300);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "mapping.test");

		assertNotNull(result.lineMappings());
		assertEquals(3, result.lineMappings().size(), "Should have 3 line mappings for 3-line block");
	}

	@Test
	public void testLineMappingsHintLinesAreCorrect() {
		String source = """
			public boolean guard() {
			    return true;
			}
			""";
		// Block starts at line 20 in the hint file, ends at line 22
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 20, 22, 400, 500);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "linetest");

		// Verify hint file lines are mapped
		assertEquals(20, result.lineMappings().get(0).hintFileLine());
		assertEquals(21, result.lineMappings().get(1).hintFileLine());
		assertEquals(22, result.lineMappings().get(2).hintFileLine());
	}

	@Test
	public void testToSyntheticLine() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 15, 15, 300, 340);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "synth.line");

		int syntheticLine = result.toSyntheticLine(15);
		assertTrue(syntheticLine > 0, "Mapped synthetic line should be positive");
	}

	@Test
	public void testToHintLine() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 15, 15, 300, 340);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "hint.line");

		// Map to synthetic and back
		int syntheticLine = result.toSyntheticLine(15);
		assertTrue(syntheticLine > 0);
		int hintLine = result.toHintLine(syntheticLine);
		assertEquals(15, hintLine, "Round-trip mapping should return original hint line");
	}

	@Test
	public void testToSyntheticLineNotFound() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 10, 10, 200, 240);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "notfound");

		assertEquals(-1, result.toSyntheticLine(999), "Non-existent line should return -1");
	}

	@Test
	public void testToHintLineNotFound() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 10, 10, 200, 240);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "notfound2");

		assertEquals(-1, result.toHintLine(999), "Non-existent synthetic line should return -1");
	}

	@Test
	public void testSourceLineMappingRecord() {
		SourceLineMapping mapping = new SourceLineMapping(10, 5);
		assertEquals(10, mapping.hintFileLine());
		assertEquals(5, mapping.syntheticClassLine());
	}
}
