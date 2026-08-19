/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup;

import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;

import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule;

/** Keeps every semantic container migration opt-in. */
public final class DefaultCleanUpOptionsInitializer
		implements ICleanUpOptionsInitializer {

	@Override
	public void setDefaultOptions(CleanUpOptions options) {
		options.setOption(CLEANUP, CleanUpOptions.FALSE);
		for (ContainerCleanUpRule rule : ContainerCleanUpRule.values()) {
			options.setOption(rule.optionId(), CleanUpOptions.FALSE);
		}
	}
}
