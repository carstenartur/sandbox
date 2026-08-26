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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Maven entry point used by the pinned JDT UI before/after runner to classify
 * the produced evidence with the same Java verifier exercised by ordinary
 * repository tests.
 *
 * @since 1.3.4
 */
public class JdtUiCorpusEvidenceExecutionTest {

	private static final String ENABLED = "sandbox.jdt.ui.evidence.enabled"; //$NON-NLS-1$

	@Test
	@EnabledIfSystemProperty(named = ENABLED, matches = "true")
	public void verifyConfiguredEvidence() throws Exception {
		JdtUiCorpusEvidenceVerifier.verify(
				requiredPath("sandbox.jdt.ui.evidence.repository"), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.baselineSources"), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.contract"), //$NON-NLS-1$
				JdtUiCorpusEvidenceVerifier.Mode.parse(
						requiredProperty("sandbox.jdt.ui.evidence.mode")), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.changedFiles"), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.checkReport"), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.applyReport"), //$NON-NLS-1$
				requiredPath("sandbox.jdt.ui.evidence.output")); //$NON-NLS-1$
	}

	private static Path requiredPath(String name) {
		return Path.of(requiredProperty(name)).toAbsolutePath().normalize();
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing required system property: " + name); //$NON-NLS-1$
		}
		return value;
	}
}
