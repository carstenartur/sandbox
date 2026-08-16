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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Executes the repository policy as part of the ordinary Maven/JUnit suite.
 *
 * @since 1.3.4
 */
public class RepositoryPolicyTest {

	@Test
	public void testRepositoryPolicy() throws Exception {
		try (RepositoryPolicy policy = RepositoryPolicy.openFromWorkingDirectory()) {
			RepositoryPolicy.PolicyReport report = policy.evaluate();
			assertTrue(report.isCompliant(), report::format);
		}
	}
}
