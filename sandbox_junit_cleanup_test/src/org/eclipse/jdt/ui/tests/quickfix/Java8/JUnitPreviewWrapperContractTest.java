/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.core.IJavaProject;

import org.sandbox.jdt.internal.ui.fix.JUnitCleanUp;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Guards the optional patched-JDT preview contract on the registered JUnit wrapper. */
public class JUnitPreviewWrapperContractTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@Test
	public void registeredWrapperExposesTheReflectivePreviewContract() throws Exception {
		JUnitCleanUp cleanup= new JUnitCleanUp();
		Method method= cleanup.getClass().getMethod(
				"getCoordinatedCleanUpPreview", IJavaProject.class); //$NON-NLS-1$

		assertEquals(Collection.class, method.getReturnType());
		assertTrue(cleanup.getCoordinatedCleanUpPreview(
				context.getSourceFolder().getJavaProject()).isEmpty(),
				"A wrapper without a completed plan must expose an empty preview rather than fail");
	}
}
