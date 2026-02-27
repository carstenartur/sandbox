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
 * Wrapper for printStackTrace() cleanup.
 *
 * <p>Detects calls to printStackTrace() using TriggerPattern-based hints.</p>
 *
 */
public class PrintStackTraceCleanUp extends AbstractCleanUpCoreWrapper<PrintStackTraceCleanUpCore> {

	public PrintStackTraceCleanUp(final Map<String, String> options) {
		super(options, new PrintStackTraceCleanUpCore());
	}

	public PrintStackTraceCleanUp() {
		this(Collections.emptyMap());
	}
}
