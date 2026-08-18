/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.views.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** Ensures provider details never become user-visible mining status text. */
public class CommitAnalysisJobFailureMessageTest {

	@Test
	public void exposesOnlyTheFailureTypeAndNotTheProviderMessage() {
		String sensitiveMessage= "request contained secret prompt and API details"; //$NON-NLS-1$

		String summary= CommitAnalysisJob.safeFailureMessage(new IllegalStateException(sensitiveMessage));

		assertEquals("Commit analysis failed (IllegalStateException)", summary); //$NON-NLS-1$
		assertFalse(summary.contains(sensitiveMessage));
	}

	@Test
	public void anonymousFailureTypeFallsBackToGenericSummary() {
		Exception anonymousFailure= new Exception("sensitive") { //$NON-NLS-1$
			private static final long serialVersionUID= 1L;
		};

		assertEquals("Commit analysis failed", //$NON-NLS-1$
				CommitAnalysisJob.safeFailureMessage(anonymousFailure));
	}
}
