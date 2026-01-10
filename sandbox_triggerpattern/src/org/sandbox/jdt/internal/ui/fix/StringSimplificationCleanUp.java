/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
 * Wrapper for string simplification cleanup.
 * 
 * <p>Simplifies string concatenation patterns using TriggerPattern-based hints.</p>
 * 
 * @since 1.2.2
 */
public class StringSimplificationCleanUp extends AbstractCleanUpCoreWrapper<StringSimplificationCleanUpCore> {
	
	public StringSimplificationCleanUp(final Map<String, String> options) {
		super(options, new StringSimplificationCleanUpCore());
	}
	
	public StringSimplificationCleanUp() {
		this(Collections.emptyMap());
	}
}
