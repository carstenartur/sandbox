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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link NodeExecutor}.
 */
public class NodeExecutorTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	public void testPinnedToolchainPropertiesAreProvidedByMaven() {
		String nodeHome = System.getProperty(NodeExecutor.NODE_HOME_PROPERTY);
		String nodeModules = System.getProperty(NodeExecutor.NODE_MODULES_PROPERTY);

		assertNotNull(nodeHome, "Maven must provide the pinned Node.js home"); //$NON-NLS-1$
		assertFalse(nodeHome.isBlank(), "The pinned Node.js home must not be blank"); //$NON-NLS-1$
		assertNotNull(nodeModules, "Maven must provide the pinned node_modules directory"); //$NON-NLS-1$
		assertFalse(nodeModules.isBlank(), "The pinned node_modules directory must not be blank"); //$NON-NLS-1$
	}

	@Test
	public void testPinnedNodeVersion() throws IOException, InterruptedException {
		assertTrue(NodeExecutor.isNodeAvailable(), "The Maven-managed Node.js runtime must be available"); //$NON-NLS-1$
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNode("--version"); //$NON-NLS-1$
		assertTrue(result.isSuccess(), result.stderr);
		assertEquals("v24.20.0", result.stdout.trim()); //$NON-NLS-1$
	}

	@Test
	public void testPinnedPrettierVersion() throws IOException, InterruptedException {
		NodeExecutor.ExecutionResult result = NodeExecutor.executeTool(
				NodeExecutor.Tool.PRETTIER, "--version"); //$NON-NLS-1$
		assertTrue(result.isSuccess(), result.stderr);
		assertEquals("3.9.6", result.stdout.trim()); //$NON-NLS-1$
	}

	@Test
	public void testPinnedStylelintVersion() throws IOException, InterruptedException {
		NodeExecutor.ExecutionResult result = NodeExecutor.executeTool(
				NodeExecutor.Tool.STYLELINT, "--version"); //$NON-NLS-1$
		assertTrue(result.isSuccess(), result.stderr);
		assertEquals("17.14.1", result.stdout.trim()); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredToolCommandUsesNodeAndPackageEntryPointDirectly() throws IOException {
		Path node = Files.createFile(temporaryDirectory.resolve("node")); //$NON-NLS-1$
		Path nodeModules = temporaryDirectory.resolve("node_modules"); //$NON-NLS-1$
		Path prettier = nodeModules.resolve(Path.of("prettier", "bin", "prettier.cjs")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Files.createDirectories(prettier.getParent());
		Files.createFile(prettier);

		List<String> command = NodeExecutor.configuredToolCommand(
				NodeExecutor.Tool.PRETTIER, node, nodeModules, "--version"); //$NON-NLS-1$

		assertEquals(node.toAbsolutePath().normalize().toString(), command.get(0));
		assertEquals(prettier.toAbsolutePath().normalize().toString(), command.get(1));
		assertEquals("--version", command.get(2)); //$NON-NLS-1$
	}

	@Test
	public void testConfiguredToolCommandFailsClosedWhenNodeIsMissing() {
		Path missingNode = temporaryDirectory.resolve("missing-node"); //$NON-NLS-1$
		Path nodeModules = temporaryDirectory.resolve("node_modules"); //$NON-NLS-1$

		IOException failure = assertThrows(IOException.class,
				() -> NodeExecutor.configuredToolCommand(
						NodeExecutor.Tool.PRETTIER, missingNode, nodeModules, "--version")); //$NON-NLS-1$
		assertTrue(failure.getMessage().contains(missingNode.toAbsolutePath().normalize().toString()));
	}

	@Test
	public void testExecutionResultReportsSuccessFromExitCode() {
		NodeExecutor.ExecutionResult success = new NodeExecutor.ExecutionResult(0, "output", ""); //$NON-NLS-1$ //$NON-NLS-2$
		NodeExecutor.ExecutionResult failure = new NodeExecutor.ExecutionResult(1, "", "error"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(success.isSuccess());
		assertFalse(failure.isSuccess());
	}
}
