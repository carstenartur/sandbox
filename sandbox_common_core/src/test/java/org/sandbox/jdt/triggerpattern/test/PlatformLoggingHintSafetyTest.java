/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;

/** Guards the bundled logging library against identity-changing Status rules. */
class PlatformLoggingHintSafetyTest extends HintRuleTestSupport {

	private HintFile hintFile;

	@BeforeEach
	void setUp() throws Exception {
		registerBuiltInGuards();
		hintFile= loadBundledHint("platform-logging.sandbox-hint"); //$NON-NLS-1$
	}

	@Test
	void modernizesPlatformLogLookup() {
		assertFullReplacement(hintFile,
				"class Test { Object m(Object bundle) { return org.eclipse.core.runtime.Platform.getLog(bundle); } }", //$NON-NLS-1$
				"class Test { Object m(Object bundle) { return org.eclipse.core.runtime.ILog.of(bundle); } }"); //$NON-NLS-1$
	}

	@Test
	void doesNotDiscardStatusIdentityFromThreeArgumentConstructor() {
		assertNoMatch(hintFile,
				"class Test { Object m() { return new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR, \"delegated.plugin\", \"failed\"); } }"); //$NON-NLS-1$
	}

	@Test
	void doesNotDiscardStatusIdentityFromFourArgumentConstructor() {
		assertNoMatch(hintFile,
				"class Test { Object m(Throwable failure) { return new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR, \"delegated.plugin\", \"failed\", failure); } }"); //$NON-NLS-1$
	}
}
