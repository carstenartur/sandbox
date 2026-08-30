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

import org.junit.jupiter.api.Test;

/**
 * Guards the checked-in JDT UI JUnit 4 real-corpus contract.
 *
 * @since 1.3.4
 */
public class JdtUiCorpusContractTest {

	@Test
	public void checkedInContractUsesOneMavenJUnitAuthority() throws Exception {
		JdtUiCorpusEvidenceVerifier.verifyCheckedInContract(
				JdtUiCorpusEvidenceVerifier.repositoryRoot());
	}
}
