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

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.debug.SandboxHintLaunchConfigurationType;
import org.sandbox.jdt.triggerpattern.debug.SandboxHintSourceLocator;
import org.sandbox.jdt.triggerpattern.debug.SandboxHintSourcePathComputer;

/**
 * Tests for the debug launch infrastructure.
 *
 * <p>Verifies that {@link SandboxHintSourceLocator}, 
 * {@link SandboxHintLaunchConfigurationType}, and
 * {@link SandboxHintSourcePathComputer} can be instantiated correctly
 * and their contracts are met.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintSourceLocatorTest {

	@Test
	public void testSourceLocatorCanBeInstantiated() {
		SandboxHintSourceLocator locator = new SandboxHintSourceLocator();
		assertNotNull(locator);
	}

	@Test
	public void testSourceLocatorReturnsNullForNullFrame() {
		SandboxHintSourceLocator locator = new SandboxHintSourceLocator();
		// With no mapping set up, getSourceElement should return null
		assertNull(locator.getSourceElement(null));
	}

	@Test
	public void testLaunchConfigurationTypeId() {
		assertNotNull(SandboxHintLaunchConfigurationType.TYPE_ID);
	}

	@Test
	public void testSourcePathComputerCanBeInstantiated() {
		SandboxHintSourcePathComputer computer = new SandboxHintSourcePathComputer();
		assertNotNull(computer);
	}
}
