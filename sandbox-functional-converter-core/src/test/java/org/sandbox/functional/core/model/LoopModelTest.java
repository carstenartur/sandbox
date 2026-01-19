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
package org.sandbox.functional.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LoopModel}.
 * 
 * <p>This is a basic test to verify the module builds correctly.
 * Full test coverage will be implemented in subsequent phases.</p>
 */
class LoopModelTest {
	
	@Test
	void testLoopModelCanBeInstantiated() {
		LoopModel model = new LoopModel();
		assertNotNull(model, "LoopModel should not be null");
	}
	
	@Test
	void testLoopModelToString() {
		LoopModel model = new LoopModel();
		String result = model.toString();
		assertThat(result)
			.isNotNull()
			.contains("LoopModel");
	}
}
