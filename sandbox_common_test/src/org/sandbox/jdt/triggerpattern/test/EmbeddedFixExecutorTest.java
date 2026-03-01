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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedFixExecutor;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;

/**
 * Tests for {@link EmbeddedFixExecutor}.
 *
 * <p>Verifies that fix functions annotated with {@code @FixFunction} are
 * correctly registered, looked up, and can be unregistered.</p>
 *
 * @since 1.5.0
 */
public class EmbeddedFixExecutorTest {

	private static final String TEST_RULE_ID = "test.fix.executor"; //$NON-NLS-1$

	@AfterEach
	public void tearDown() {
		EmbeddedFixExecutor.unregisterFixes(TEST_RULE_ID);
	}

	@Test
	public void testFixFunctionIsRegistered() {
		String source = """
			@FixFunction
			public void customFix() { }
			"""; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 2, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedFixExecutor.registerFixes(result, TEST_RULE_ID);

		assertTrue(EmbeddedFixExecutor.hasFix("customFix"), //$NON-NLS-1$
				"Fix function should be registered"); //$NON-NLS-1$
	}

	@Test
	public void testUnregisterRemovesFixes() {
		String source = """
			@FixFunction
			public void removableFix() { }
			"""; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 2, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedFixExecutor.registerFixes(result, TEST_RULE_ID);
		assertTrue(EmbeddedFixExecutor.hasFix("removableFix")); //$NON-NLS-1$

		EmbeddedFixExecutor.unregisterFixes(TEST_RULE_ID);
		assertFalse(EmbeddedFixExecutor.hasFix("removableFix"), //$NON-NLS-1$
				"Fix should be removed after unregister"); //$NON-NLS-1$
	}

	@Test
	public void testNonAnnotatedMethodNotRegistered() {
		String source = "public void notAFix() { }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedFixExecutor.registerFixes(result, TEST_RULE_ID);

		assertFalse(EmbeddedFixExecutor.hasFix("notAFix"), //$NON-NLS-1$
				"Non-annotated method should not be a fix"); //$NON-NLS-1$
	}

	@Test
	public void testExecuteStubDoesNotThrow() {
		String source = """
			@FixFunction
			public void stableFix() { }
			"""; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 2, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedFixExecutor.registerFixes(result, TEST_RULE_ID);

		// execute() should not throw even as a stub
		EmbeddedFixExecutor.execute("stableFix"); //$NON-NLS-1$
	}

	@Test
	public void testHasFixReturnsFalseForUnknown() {
		assertFalse(EmbeddedFixExecutor.hasFix("nonExistentFix"), //$NON-NLS-1$
				"Unknown fix name should return false"); //$NON-NLS-1$
	}
}
