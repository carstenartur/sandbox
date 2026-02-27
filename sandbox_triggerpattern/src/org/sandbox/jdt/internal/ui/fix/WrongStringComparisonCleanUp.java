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
package org.sandbox.jdt.internal.ui.fix;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

/**
 * Wrapper for wrong string comparison cleanup.
 *
 * <p>Detects string comparisons using == or != using TriggerPattern-based hints.</p>
 *
 * @since 1.3.9
 */
public class WrongStringComparisonCleanUp extends AbstractCleanUpCoreWrapper<WrongStringComparisonCleanUpCore> {

	public WrongStringComparisonCleanUp(final Map<String, String> options) {
		super(options, new WrongStringComparisonCleanUpCore());
	}

	public WrongStringComparisonCleanUp() {
		this(Collections.emptyMap());
	}
}
