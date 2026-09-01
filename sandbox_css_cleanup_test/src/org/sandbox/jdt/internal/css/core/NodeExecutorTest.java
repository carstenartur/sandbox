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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NodeExecutor}.
 */
public class NodeExecutorTest {

	@Test
	public void testIsNodeAvailableDoesNotThrow() {
		assertDoesNotThrow(() -> NodeExecutor.isNodeAvailable());
	}

	@Test
	public void testIsNpxAvailableDoesNotThrow() {
		assertDoesNotThrow(() -> NodeExecutor.isNpxAvailable());
	}

	@Test
	public void testNodeAvailabilityCheckIsConsistent() {
		boolean first = NodeExecutor.isNodeAvailable();
		boolean second = NodeExecutor.isNodeAvailable();

		assertEquals(first, second, "Node availability check should be consistent"); //$NON-NLS-1$
	}

	@Test
	public void testNpxAvailabilityCheckIsConsistent() {
		boolean first = NodeExecutor.isNpxAvailable();
		boolean second = NodeExecutor.isNpxAvailable();

		assertEquals(first, second, "Npx availability check should be consistent"); //$NON-NLS-1$
	}

	@Test
	public void testExecutionResultClassExists() {
		assertNotNull(NodeExecutor.ExecutionResult.class);
	}

	@Test
	public void testNodeIsActuallyAvailable() {
		assertTrue(NodeExecutor.isNodeAvailable(), "Maven-owned Node.js should be available"); //$NON-NLS-1$
	}

	@Test
	public void testPinnedCssToolsAreActuallyAvailable() {
		assertTrue(NodeExecutor.isNpxAvailable(), "Maven-owned Prettier and Stylelint should be available"); //$NON-NLS-1$
	}

	@Test
	public void testMavenOwnedCssToolsRemainBelowTarget() {
		Path installDirectory = Path.of(System.getProperty(NodeExecutor.NODE_INSTALL_DIRECTORY_PROPERTY)).normalize();
		Path modulesDirectory = Path.of(System.getProperty(NodeExecutor.NODE_MODULES_DIRECTORY_PROPERTY)).normalize();

		assertTrue(installDirectory.endsWith(Path.of("target", "frontend")), //$NON-NLS-1$ //$NON-NLS-2$
				() -> "Node.js installation escaped the Maven target directory: " + installDirectory); //$NON-NLS-1$
		assertTrue(modulesDirectory.endsWith(Path.of("target", "node-tools", "node_modules")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				() -> "CSS test dependencies escaped the Maven target directory: " + modulesDirectory); //$NON-NLS-1$
	}

	@Test
	public void testExecutePrettierWithVersion() throws IOException, InterruptedException {
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("prettier", "--version"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(result);
		assertTrue(result.isSuccess(), result.stderr);
		assertFalse(result.stdout.isEmpty(), "Prettier --version should produce output"); //$NON-NLS-1$
	}

	@Test
	public void testExecuteStylelintWithInvalidOption() throws IOException, InterruptedException {
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx(
				"stylelint", "--this-option-does-not-exist"); //$NON-NLS-1$ //$NON-NLS-2$

		assertNotNull(result);
		assertFalse(result.isSuccess(), "Invalid Stylelint option should not succeed"); //$NON-NLS-1$
		assertTrue(result.exitCode != 0, "Invalid Stylelint option should have non-zero exit code"); //$NON-NLS-1$
	}

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
