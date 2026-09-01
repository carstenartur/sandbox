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
package org.sandbox.jdt.ui.helper.views;

import org.junit.jupiter.api.Test;

/** Generates and verifies the PDE XML Problems-view Quick Fix screenshot. */
public class PdeXmlQuickFixScreenshotSWTBotTest {

	@Test
	public void captureProblemsViewQuickFix() throws Exception {
		PdeXmlQuickFixScreenshot.capture();
	}
}
