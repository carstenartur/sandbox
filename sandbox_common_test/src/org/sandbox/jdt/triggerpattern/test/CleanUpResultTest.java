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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IMarker;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;
import org.sandbox.jdt.triggerpattern.eclipse.HintFinding;

/**
 * Tests for {@link CleanUpResult} — the combined holder for
 * rewrite operations and hint-only findings.
 */
public class CleanUpResultTest {

	@Test
	public void testEmptyResult() {
		CleanUpResult result = new CleanUpResult();
		assertFalse(result.hasOperations());
		assertFalse(result.hasFindings());
		assertTrue(result.getOperations().isEmpty());
		assertTrue(result.getFindings().isEmpty());
	}

	@Test
	public void testAddFinding() {
		CleanUpResult result = new CleanUpResult();
		result.addFinding(new HintFinding("test message", 1, 0, 10, IMarker.SEVERITY_WARNING)); //$NON-NLS-1$

		assertTrue(result.hasFindings());
		assertFalse(result.hasOperations());
		assertEquals(1, result.getFindings().size());
		assertEquals("test message", result.getFindings().get(0).message()); //$NON-NLS-1$
	}

	@Test
	public void testFindingsListIsModifiable() {
		CleanUpResult result = new CleanUpResult();
		result.getFindings().add(new HintFinding("direct add", 5, 20, 30, IMarker.SEVERITY_INFO)); //$NON-NLS-1$

		assertTrue(result.hasFindings());
		assertEquals(1, result.getFindings().size());
		assertEquals("direct add", result.getFindings().get(0).message()); //$NON-NLS-1$
	}

	@Test
	public void testOperationsSetIsModifiable() {
		CleanUpResult result = new CleanUpResult();
		// Operations set is modifiable but we can't easily create a mock
		// CompilationUnitRewriteOperation without Eclipse runtime, so just verify
		// the set is returned and empty
		assertTrue(result.getOperations().isEmpty());
		assertFalse(result.hasOperations());
	}

	@Test
	public void testMultipleFindings() {
		CleanUpResult result = new CleanUpResult();
		result.addFinding(new HintFinding("finding 1", 1, 0, 5, IMarker.SEVERITY_WARNING)); //$NON-NLS-1$
		result.addFinding(new HintFinding("finding 2", 3, 10, 20, IMarker.SEVERITY_INFO)); //$NON-NLS-1$
		result.addFinding(new HintFinding("finding 3", 7, 30, 40, IMarker.SEVERITY_ERROR)); //$NON-NLS-1$

		assertEquals(3, result.getFindings().size());
		assertEquals("finding 1", result.getFindings().get(0).message()); //$NON-NLS-1$
		assertEquals("finding 2", result.getFindings().get(1).message()); //$NON-NLS-1$
		assertEquals("finding 3", result.getFindings().get(2).message()); //$NON-NLS-1$
	}
}
