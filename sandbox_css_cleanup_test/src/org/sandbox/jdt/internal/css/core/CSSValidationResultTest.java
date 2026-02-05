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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CSSValidationResult}.
 */
public class CSSValidationResultTest {

	@Test
	public void testValidResult() {
		CSSValidationResult result = new CSSValidationResult(true, Collections.emptyList());
		
		assertTrue(result.isValid());
		assertNotNull(result.getIssues());
		assertTrue(result.getIssues().isEmpty());
	}

	@Test
	public void testInvalidResultWithIssues() {
		CSSValidationResult.Issue issue1 = new CSSValidationResult.Issue(
				10, 5, "error", "color-no-invalid-hex", "Unexpected invalid hex color"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		CSSValidationResult.Issue issue2 = new CSSValidationResult.Issue(
				15, 1, "warning", "declaration-block-no-duplicate-properties", "Duplicate property"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		List<CSSValidationResult.Issue> issues = Arrays.asList(issue1, issue2);
		CSSValidationResult result = new CSSValidationResult(false, issues);
		
		assertFalse(result.isValid());
		assertEquals(2, result.getIssues().size());
	}

	@Test
	public void testIssueProperties() {
		CSSValidationResult.Issue issue = new CSSValidationResult.Issue(
				42, 15, "error", "selector-type-no-unknown", "Unknown type selector"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		assertEquals(42, issue.line);
		assertEquals(15, issue.column);
		assertEquals("error", issue.severity); //$NON-NLS-1$
		assertEquals("selector-type-no-unknown", issue.rule); //$NON-NLS-1$
		assertEquals("Unknown type selector", issue.message); //$NON-NLS-1$
	}

	@Test
	public void testNullIssuesListHandling() {
		CSSValidationResult result = new CSSValidationResult(true, null);
		
		assertTrue(result.isValid());
		assertNotNull(result.getIssues());
		assertTrue(result.getIssues().isEmpty());
	}

	@Test
	public void testIssuesListIsUnmodifiable() {
		CSSValidationResult.Issue issue = new CSSValidationResult.Issue(
				1, 1, "warning", "test-rule", "Test message"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		List<CSSValidationResult.Issue> issues = Arrays.asList(issue);
		CSSValidationResult result = new CSSValidationResult(false, issues);
		
		List<CSSValidationResult.Issue> returnedIssues = result.getIssues();
		
		// Verify that attempting to modify the list throws UnsupportedOperationException
		assertThrows(UnsupportedOperationException.class, () -> {
			returnedIssues.add(new CSSValidationResult.Issue(2, 2, "error", "another", "another")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}, "Issues list should be unmodifiable"); //$NON-NLS-1$
	}
}
