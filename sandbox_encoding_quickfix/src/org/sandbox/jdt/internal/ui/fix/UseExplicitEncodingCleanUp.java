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
 * Until
 * https://openjdk.java.net/jeps/400
 * is active platform encoding might be different than UTF-8.
 * So it can be dangerous not to use explicit encoding.
 */
public class UseExplicitEncodingCleanUp extends AbstractCleanUpCoreWrapper<UseExplicitEncodingCleanUpCore> {
	public UseExplicitEncodingCleanUp(final Map<String, String> options) {
		super(options, new UseExplicitEncodingCleanUpCore());
	}

	public UseExplicitEncodingCleanUp() {
		this(Collections.emptyMap());
	}
}