/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.ui.fix.SimplifyPlatformStatusCleanUpCore;

/** Prevents bundled Platform Status rules from dropping explicit identity. */
public class PlatformStatusHintSafetyTest {

	private static final String RESOURCE=
			"/org/sandbox/jdt/internal/corext/fix/hints/platform-status.sandbox-hint"; //$NON-NLS-1$

	@Test
	public void everyReplacementRetainsThePluginIdentity() throws Exception {
		String content;
		try (InputStream stream= SimplifyPlatformStatusCleanUpCore.class.getResourceAsStream(RESOURCE)) {
			assertNotNull(stream, "Platform Status hint resource must be packaged in the cleanup bundle"); //$NON-NLS-1$
			content= new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		List<String> replacements= content.lines()
				.map(String::trim)
				.filter(line -> line.startsWith("=>")) //$NON-NLS-1$
				.toList();
		assertEquals(3, replacements.size(),
				"Only the three five-argument INFO/WARNING/ERROR rules should be active"); //$NON-NLS-1$
		assertTrue(replacements.stream().allMatch(line ->
				line.startsWith("=> new org.eclipse.core.runtime.Status(") //$NON-NLS-1$
						&& line.contains("$pluginId")), //$NON-NLS-1$
				"Every Status replacement must remain a constructor and retain $pluginId"); //$NON-NLS-1$
		assertTrue(replacements.stream().noneMatch(line ->
				line.contains("Status.error(") //$NON-NLS-1$
						|| line.contains("Status.warning(") //$NON-NLS-1$
						|| line.contains("Status.info(")), //$NON-NLS-1$
				"Bundled hint replacements must not infer a different caller identity"); //$NON-NLS-1$
	}
}
