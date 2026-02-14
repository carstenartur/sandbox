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
package org.sandbox.jdt.internal.ui.fix;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

/**
 * Wrapper for hint-file-based cleanup.
 *
 * <p>Applies transformation rules from registered {@code .sandbox-hint} files
 * as Eclipse CleanUp operations.</p>
 *
 * @since 1.3.5
 */
public class HintFileCleanUp extends AbstractCleanUpCoreWrapper<HintFileCleanUpCore> {

	public HintFileCleanUp(final Map<String, String> options) {
		super(options, new HintFileCleanUpCore());
	}

	public HintFileCleanUp() {
		this(Collections.emptyMap());
	}
}
