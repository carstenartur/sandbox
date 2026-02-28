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
}
