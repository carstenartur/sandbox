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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for extension point loading in {@link GuardRegistry} and
 * {@link HintFileRegistry}.
 *
 * <p>Note: In a standalone test environment (without the Eclipse OSGi runtime),
 * {@code Platform.getExtensionRegistry()} returns {@code null}. These tests
 * verify that the methods handle this gracefully and that the manual
 * registration API continues to work.</p>
 *
 * @since 1.3.6
 */
public class ExtensionPointLoadingTest {

	@Test
	public void testGuardRegistryLoadExtensionsReturnsEmptyWithoutOSGi() {
		GuardRegistry registry = GuardRegistry.getInstance();
		List<String> loaded = registry.loadExtensions();
		assertNotNull(loaded, "loadExtensions should never return null"); //$NON-NLS-1$
		// Without OSGi, no extensions can be loaded
		assertTrue(loaded.isEmpty(), "Without OSGi runtime, no extensions should load"); //$NON-NLS-1$
	}

	@Test
	public void testGuardRegistryManualRegistrationStillWorks() {
		GuardRegistry registry = GuardRegistry.getInstance();
		
		// Register a custom guard
		GuardFunction customGuard = (ctx, args) -> true;
		registry.register("testCustomGuard", customGuard); //$NON-NLS-1$
		
		GuardFunction retrieved = registry.get("testCustomGuard"); //$NON-NLS-1$
		assertNotNull(retrieved, "Custom guard should be retrievable"); //$NON-NLS-1$
		assertEquals(customGuard, retrieved, "Should be the same instance"); //$NON-NLS-1$
	}

	@Test
	public void testGuardRegistryBuiltinsPresent() {
		GuardRegistry registry = GuardRegistry.getInstance();
		
		// Verify all built-in guards are still present
		assertNotNull(registry.get("instanceof"), "instanceof guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("matchesAny"), "matchesAny guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("matchesNone"), "matchesNone guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("sourceVersionGE"), "sourceVersionGE guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("sourceVersionLE"), "sourceVersionLE guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("sourceVersionBetween"), "sourceVersionBetween guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("isStatic"), "isStatic guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("isFinal"), "isFinal guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("hasAnnotation"), "hasAnnotation guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("isDeprecated"), "isDeprecated guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("contains"), "contains guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
		assertNotNull(registry.get("notContains"), "notContains guard should be present"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testHintFileRegistryLoadFromExtensionsReturnsEmptyWithoutOSGi() {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		List<String> loaded = registry.loadFromExtensions();
		assertNotNull(loaded, "loadFromExtensions should never return null"); //$NON-NLS-1$
		// Without OSGi, no extensions can be loaded
		assertTrue(loaded.isEmpty(), "Without OSGi runtime, no extensions should load"); //$NON-NLS-1$
	}

	@Test
	public void testHintFileRegistryManualRegistrationStillWorks() throws Exception {
		HintFileRegistry registry = HintFileRegistry.getInstance();
		
		String hintContent = """
				<!id: ext-test>
				<!description: Extension test>
				
				$x + 0
				=> $x
				;;
				"""; //$NON-NLS-1$
		
		registry.loadFromString("ext-test", hintContent); //$NON-NLS-1$
		assertNotNull(registry.getHintFile("ext-test"), //$NON-NLS-1$
				"Manually loaded hint file should be retrievable"); //$NON-NLS-1$
		
		// Clean up
		registry.unregister("ext-test"); //$NON-NLS-1$
	}
}
