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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Tests for {@link NodeExecutor}.
 */
public class NodeExecutorTest {

	@Test
	public void testIsNodeAvailableDoesNotThrow() {
		// The method should return a boolean without throwing
		assertDoesNotThrow(() -> NodeExecutor.isNodeAvailable());
	}

	@Test
	public void testIsNpxAvailableDoesNotThrow() {
		// The method should return a boolean without throwing
		assertDoesNotThrow(() -> NodeExecutor.isNpxAvailable());
	}

	@Test
	public void testNodeAvailabilityCheckIsConsistent() {
		// Running the check multiple times should give consistent results
		boolean first = NodeExecutor.isNodeAvailable();
		boolean second = NodeExecutor.isNodeAvailable();
		
		assertEquals(first, second, "Node availability check should be consistent"); //$NON-NLS-1$
	}

	@Test
	public void testNpxAvailabilityCheckIsConsistent() {
		// Running the check multiple times should give consistent results
		boolean first = NodeExecutor.isNpxAvailable();
		boolean second = NodeExecutor.isNpxAvailable();
		
		assertEquals(first, second, "Npx availability check should be consistent"); //$NON-NLS-1$
	}

	@Test
	public void testExecutionResultClassExists() {
		// Verify that the ExecutionResult inner class is accessible
		assertNotNull(NodeExecutor.ExecutionResult.class);
	}

	// ========== Integration tests (require Node.js) ==========

	@Test
	@EnabledIf("isNodeAvailable")
	public void testNodeIsActuallyAvailable() {
		assertTrue(NodeExecutor.isNodeAvailable(), "Node.js should be available in CI environment"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isNpxAvailable")
	public void testNpxIsActuallyAvailable() {
		assertTrue(NodeExecutor.isNpxAvailable(), "npx should be available in CI environment"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isNpxAvailable")
	public void testExecuteNpxWithVersion() throws IOException, InterruptedException {
		// Execute a simple npx command that should always work
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("--version"); //$NON-NLS-1$
		
		assertNotNull(result);
		assertNotNull(result.stdout);
		assertFalse(result.stdout.isEmpty(), "npx --version should produce output"); //$NON-NLS-1$
	}

	@Test
	@EnabledIf("isNpxAvailable")
	public void testExecuteNpxWithInvalidCommand() throws IOException, InterruptedException {
		// Execute an npx command that should fail
		NodeExecutor.ExecutionResult result = NodeExecutor.executeNpx("this-package-definitely-does-not-exist-12345"); //$NON-NLS-1$
		
		assertNotNull(result);
		assertFalse(result.isSuccess(), "Invalid command should not succeed"); //$NON-NLS-1$
		assertTrue(result.exitCode != 0, "Invalid command should have non-zero exit code"); //$NON-NLS-1$
	}

	/**
	 * Condition method for EnabledIf annotation.
	 */
	static boolean isNodeAvailable() {
		return NodeExecutor.isNodeAvailable();
	}

	/**
	 * Condition method for EnabledIf annotation.
	 */
	static boolean isNpxAvailable() {
		return NodeExecutor.isNpxAvailable();
	}
}