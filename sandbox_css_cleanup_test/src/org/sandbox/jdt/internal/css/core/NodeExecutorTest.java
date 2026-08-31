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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.css.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Tests for the cross-platform command resolution in {@link NodeExecutor}. */
public class NodeExecutorTest {

	@Test
	public void testWindowsUsesCmdForUserNpx() {
		assertEquals(List.of("npx.cmd", "prettier", "--version"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				NodeExecutor.buildNpxCommand("Windows 11", null, null, "prettier", "--version")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testUnixUsesNpxForUserToolchain() {
		assertEquals(List.of("npx", "stylelint", "--version"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				NodeExecutor.buildNpxCommand("Linux", null, null, "stylelint", "--version")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testPinnedPrettierRunsThroughPinnedNode() {
		List<String> command = NodeExecutor.buildNpxCommand(
				"Linux", "target/frontend", "node_modules", "prettier", "--version"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

		assertEquals(Path.of("target", "frontend", "node", "node").toString(), command.get(0)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertEquals(Path.of("node_modules", "prettier", "bin", "prettier.cjs").toString(), command.get(1)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		assertEquals("--version", command.get(2)); //$NON-NLS-1$
	}

	@Test
	public void testPinnedWindowsNodeUsesExe() {
		List<String> command = NodeExecutor.buildNpxCommand(
				"Windows 11", "target/frontend", "node_modules", "stylelint", "--version"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

		assertEquals("node.exe", Path.of(command.get(0)).getFileName().toString()); //$NON-NLS-1$
		assertEquals("stylelint.mjs", Path.of(command.get(1)).getFileName().toString()); //$NON-NLS-1$
	}

	@Test
	public void testPartialPinnedConfigurationIsRejected() {
		assertThrows(IllegalStateException.class,
				() -> NodeExecutor.buildNpxCommand("Linux", "target/frontend", null, "prettier")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	public void testPinnedToolchainRejectsUnknownCommands() {
		assertThrows(IllegalArgumentException.class,
				() -> NodeExecutor.buildNpxCommand(
						"Linux", "target/frontend", "node_modules", "unknown-tool")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
