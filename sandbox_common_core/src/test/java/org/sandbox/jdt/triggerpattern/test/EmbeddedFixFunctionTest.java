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
 * Tests for the fix method extraction in {@link EmbeddedJavaCompiler}
 * and the {@code @FixFunction} annotation support.
 *
 * @since 1.5.0
 */
public class EmbeddedFixFunctionTest {

	@Test
	public void testExtractFixMethods() {
		String source = """
			@FixFunction
			public void customFix() {
			    // fix logic
			}
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 3, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "fix.test");

		assertNotNull(result);
		// FixFunction annotation is found even though it cannot be resolved
		// at the AST level (it's a MarkerAnnotation). The compiler checks for
		// void return + @FixFunction annotation name.
		assertFalse(result.fixMethods().isEmpty(), "Should find fix method annotated with @FixFunction");
		assertEquals("customFix", result.fixMethods().get(0).getName().getIdentifier());
	}

	@Test
	public void testFixMethodMustReturnVoid() {
		String source = """
			@FixFunction
			public boolean notAFix() {
			    return true;
			}
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 3, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "fix.nonvoid");

		assertNotNull(result);
		assertTrue(result.fixMethods().isEmpty(), "Non-void method should not be a fix method");
		// It should be a guard method though (returns boolean)
		assertFalse(result.guardMethods().isEmpty());
	}

	@Test
	public void testGuardAndFixMethodsSeparated() {
		String source = """
			public boolean myGuard() { return true; }

			@FixFunction
			public void myFix() { }

			public void helperMethod() { }
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 5, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "mixed");

		assertEquals(1, result.guardMethods().size(), "Should find 1 guard method");
		assertEquals("myGuard", result.guardMethods().get(0).getName().getIdentifier());
		assertEquals(1, result.fixMethods().size(), "Should find 1 fix method");
		assertEquals("myFix", result.fixMethods().get(0).getName().getIdentifier());
	}

	@Test
	public void testNoFixMethodsInEmptyBlock() {
		EmbeddedJavaBlock block = new EmbeddedJavaBlock("", 1, 1, 0, 0);

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "empty");

		assertNotNull(result);
		assertTrue(result.fixMethods().isEmpty());
	}

	@Test
	public void testVoidMethodWithoutAnnotationIsNotFix() {
		String source = """
			public void regularMethod() {
			    // Not a fix function
			}
			""";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 3, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "noannotation");

		assertTrue(result.fixMethods().isEmpty(), "Method without @FixFunction should not be a fix");
	}

	@Test
	public void testDebugCompileOverload() {
		String source = "public boolean guard() { return true; }";
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, "debug.test", true);

		assertNotNull(result);
		assertFalse(result.guardMethods().isEmpty());
	}
}
