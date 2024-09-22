/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
 */
public class UseIteratorToForLoopCleanUp extends AbstractCleanUpCoreWrapper<UseIteratorToForLoopCleanUpCore> {
	public UseIteratorToForLoopCleanUp(final Map<String, String> options) {
		super(options, new UseIteratorToForLoopCleanUpCore());
	}

	public UseIteratorToForLoopCleanUp() {
		this(Collections.emptyMap());
	}
}
