/*
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.JUNIT_CLEANUP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.jdt.internal.ui.fix.MapCleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.junit.jupiter.api.Test;

class JUnitCleanUpInitializationTest {

	@Test
	void initializesOptionsBeforeSubclassCallbacksAndRetainsLaterOptionUpdates() {
		var cleanup= new StatefulCleanup(Map.of(JUNIT_CLEANUP, CleanUpOptions.TRUE));
		assertTrue(cleanup.requireAST());
		assertTrue(cleanup.setters.isEmpty());

		cleanup.setOptions(Map.of(JUNIT_CLEANUP, CleanUpOptions.FALSE));
		assertFalse(cleanup.requireAST());
		assertEquals(List.of(Setter.MAP, Setter.OPTIONS), cleanup.setters);

		cleanup.setOptions(new MapCleanUpOptions(Map.of(JUNIT_CLEANUP, CleanUpOptions.TRUE)));
		assertTrue(cleanup.requireAST());
		assertEquals(List.of(Setter.MAP, Setter.OPTIONS, Setter.OPTIONS), cleanup.setters);
	}

	@Test
	void preservesMapAndOptionsAliasesThroughTheCleanupInterface() {
		var settings= new HashMap<>(Map.of(JUNIT_CLEANUP, CleanUpOptions.TRUE));
		var cleanup= new ReportingJUnitCleanUpCore(settings);
		assertTrue(cleanup.requireAST());
		settings.put(JUNIT_CLEANUP, CleanUpOptions.FALSE);
		assertFalse(cleanup.requireAST());

		var options= new CleanUpOptions();
		options.setOption(JUNIT_CLEANUP, CleanUpOptions.TRUE);
		ICleanUp contract= cleanup;
		contract.setOptions(options);
		assertTrue(cleanup.requireAST());
		options.setOption(JUNIT_CLEANUP, CleanUpOptions.FALSE);
		assertFalse(cleanup.requireAST());
	}

	@Test
	void preservesUninitializedAndNullOptionContracts() {
		var cleanup= new JUnitCleanUpCore();
		assertThrows(AssertionFailedException.class, () -> cleanup.isEnabled(JUNIT_CLEANUP));
		ICleanUp contract= cleanup;
		assertThrows(IllegalArgumentException.class, () -> contract.setOptions(null));
		assertThrows(AssertionFailedException.class, () -> cleanup.isEnabled(JUNIT_CLEANUP));

		cleanup.setOptions(Map.of(JUNIT_CLEANUP, CleanUpOptions.TRUE));
		assertTrue(cleanup.requireAST());
		assertThrows(IllegalArgumentException.class, () -> contract.setOptions(null));
		assertTrue(cleanup.requireAST());
		assertThrows(IllegalArgumentException.class, () -> cleanup.isEnabled(null));
	}

	private enum Setter {
		MAP,
		OPTIONS
	}

	private static final class StatefulCleanup extends JUnitCleanUpCore {
		private final List<Setter> setters= new ArrayList<>();

		StatefulCleanup(Map<String, String> options) {
			super(options);
		}

		@Override
		public void setOptions(Map<String, String> options) {
			setters.add(Setter.MAP);
			super.setOptions(options);
		}

		@Override
		public void setOptions(CleanUpOptions options) {
			setters.add(Setter.OPTIONS);
			super.setOptions(options);
		}
	}
}
