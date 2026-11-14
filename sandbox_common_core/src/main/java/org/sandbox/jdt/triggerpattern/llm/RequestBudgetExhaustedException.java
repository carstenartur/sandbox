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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.llm;

import java.io.IOException;

/** The client declined to send another HTTP request because its local budget is spent. */
public final class RequestBudgetExhaustedException extends IOException {
	private static final long serialVersionUID = 1L;

	public RequestBudgetExhaustedException(int attempts) {
		super("Client request budget exhausted after " + attempts + " HTTP attempts"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
