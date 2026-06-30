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
package org.sandbox.mining.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MiningConfigTest {

	@Test
	void parsesConfiguredSourceVersionForGuardEvaluation() {
		String yaml = """
				mining:
				  settings:
				    source-version: "21"
				"""; //$NON-NLS-1$

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertEquals("21", config.getSourceVersion()); //$NON-NLS-1$
	}

	@Test
	void defaultsSourceVersionToJava8WhenUnspecified() {
		String yaml = """
				mining:
				  settings:
				    max-files-per-repo: 10
				"""; //$NON-NLS-1$

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertEquals("1.8", config.getSourceVersion()); //$NON-NLS-1$
	}

	@Test
	void defaultsSourceVersionToJava8WhenBlank() {
		MiningConfig config = new MiningConfig();

		config.setSourceVersion(" "); //$NON-NLS-1$

		assertEquals("1.8", config.getSourceVersion()); //$NON-NLS-1$
	}
}
