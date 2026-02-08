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
 * Cleanup that detects shift operations with out-of-range shift amounts
 * and replaces them with the effective masked value.
 */
public class ShiftOutOfRangeCleanUp extends AbstractCleanUpCoreWrapper<ShiftOutOfRangeCleanUpCore> {
	public ShiftOutOfRangeCleanUp(final Map<String, String> options) {
		super(options, new ShiftOutOfRangeCleanUpCore());
	}

	public ShiftOutOfRangeCleanUp() {
		this(Collections.emptyMap());
	}
}
