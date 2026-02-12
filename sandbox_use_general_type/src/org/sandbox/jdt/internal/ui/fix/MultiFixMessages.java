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

import org.eclipse.osgi.util.NLS;

public class MultiFixMessages extends NLS {
	private static final String BUNDLE_NAME= "org.sandbox.jdt.internal.ui.fix.MultiFixMessages"; //$NON-NLS-1$

	public static String UseGeneralTypeCleanUp_description;
	public static String UseGeneralTypeCleanUpFix_refactor;

	static {
		NLS.initializeMessages(BUNDLE_NAME, MultiFixMessages.class);
	}

	private MultiFixMessages() {
	}
}
