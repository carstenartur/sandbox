/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.functional.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConcreteCollectTerminalTest {

	@Test
	void retainsExplicitCollectionFactory() {
		CollectTerminal terminal= new CollectTerminal(CollectTerminal.CollectorType.TO_LIST,
				"result", "java.util.ArrayList::new"); //$NON-NLS-1$ //$NON-NLS-2$

		assertThat(terminal.hasCollectionFactory()).isTrue();
		assertThat(terminal.collectionFactory()).isEqualTo("java.util.ArrayList::new"); //$NON-NLS-1$
	}

	@Test
	void keepsLegacyConstructorBehavior() {
		CollectTerminal terminal= new CollectTerminal(CollectTerminal.CollectorType.TO_LIST, "result"); //$NON-NLS-1$

		assertThat(terminal.hasCollectionFactory()).isFalse();
		assertThat(terminal.collectionFactory()).isNull();
	}

	@Test
	void blankFactoryDoesNotEnableConcreteCollectorRendering() {
		CollectTerminal terminal= new CollectTerminal(CollectTerminal.CollectorType.TO_SET, "result", "  "); //$NON-NLS-1$ //$NON-NLS-2$

		assertThat(terminal.hasCollectionFactory()).isFalse();
	}
}
