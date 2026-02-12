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
 * Use General Type cleanup - widens variable declarations to more general types
 */
public class UseGeneralTypeCleanUp extends AbstractCleanUpCoreWrapper<UseGeneralTypeCleanUpCore> {
	public UseGeneralTypeCleanUp(final Map<String, String> options) {
		super(options, new UseGeneralTypeCleanUpCore());
	}

	public UseGeneralTypeCleanUp() {
		this(Collections.emptyMap());
	}
}
