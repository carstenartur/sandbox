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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedGuardRegistrar;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler;
import org.sandbox.jdt.triggerpattern.internal.EmbeddedJavaCompiler.CompilationResult;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;

/**
 * Tests for {@link EmbeddedGuardRegistrar}.
 *
 * <p>Verifies that guard methods defined in {@code <? ?>} blocks are correctly
 * registered in the {@link GuardRegistry} and that built-in guards take
 * precedence over embedded ones.</p>
 *
 * @since 1.5.0
 */
public class EmbeddedGuardRegistrarTest {

	private static final String TEST_RULE_ID = "test.guard.registrar"; //$NON-NLS-1$

	@AfterEach
	public void tearDown() {
		EmbeddedGuardRegistrar.unregisterGuards(TEST_RULE_ID);
	}

	@Test
	public void testGuardMethodIsRegistered() {
		String source = "public boolean myCustomGuard() { return true; }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedGuardRegistrar.registerGuards(result, TEST_RULE_ID);

		GuardFunction fn = GuardRegistry.getInstance().get("myCustomGuard"); //$NON-NLS-1$
		assertNotNull(fn, "Guard function should be registered"); //$NON-NLS-1$
	}

	@Test
	public void testRegisteredGuardReturnsTrueAsStub() {
		String source = "public boolean alwaysTrueGuard() { return false; }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedGuardRegistrar.registerGuards(result, TEST_RULE_ID);

		GuardFunction fn = GuardRegistry.getInstance().get("alwaysTrueGuard"); //$NON-NLS-1$
		assertNotNull(fn);
		// Stub always returns true regardless of the method body
		assertTrue(fn.evaluate(null), "Stub guard should return true"); //$NON-NLS-1$
	}

	@Test
	public void testBuiltInGuardNotOverridden() {
		// "sourceVersionGE" is a built-in guard
		GuardFunction builtIn = GuardRegistry.getInstance().get("sourceVersionGE"); //$NON-NLS-1$
		assertNotNull(builtIn, "Built-in guard should exist"); //$NON-NLS-1$

		String source = "public boolean sourceVersionGE() { return false; }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedGuardRegistrar.registerGuards(result, TEST_RULE_ID);

		// The built-in should still be the same instance
		GuardFunction afterRegister = GuardRegistry.getInstance().get("sourceVersionGE"); //$NON-NLS-1$
		assertTrue(builtIn == afterRegister, "Built-in guard should not be overridden"); //$NON-NLS-1$
	}

	@Test
	public void testUnregisterGuardsRemovesOnlySpecificRuleId() {
		String source = "public boolean guardForUnregisterTest() { return true; }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		EmbeddedGuardRegistrar.registerGuards(result, TEST_RULE_ID);

		assertNotNull(GuardRegistry.getInstance().get("guardForUnregisterTest")); //$NON-NLS-1$

		EmbeddedGuardRegistrar.unregisterGuards(TEST_RULE_ID);

		assertNull(GuardRegistry.getInstance().get("guardForUnregisterTest"), //$NON-NLS-1$
				"Guard should be removed after unregister"); //$NON-NLS-1$
	}

	@Test
	public void testNonBooleanMethodNotRegistered() {
		String source = "public void notAGuard() { }"; //$NON-NLS-1$
		EmbeddedJavaBlock block = new EmbeddedJavaBlock(source, 1, 1, 0, source.length());

		CompilationResult result = EmbeddedJavaCompiler.compile(block, TEST_RULE_ID);
		assertTrue(result.guardMethods().isEmpty(), "void method should not be a guard"); //$NON-NLS-1$

		EmbeddedGuardRegistrar.registerGuards(result, TEST_RULE_ID);

		assertNull(GuardRegistry.getInstance().get("notAGuard")); //$NON-NLS-1$
	}
}
