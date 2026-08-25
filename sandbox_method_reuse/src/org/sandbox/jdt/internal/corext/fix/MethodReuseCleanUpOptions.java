/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

/** String-valued options owned by the Method Reuse cleanup. */
public final class MethodReuseCleanUpOptions {

	/** Minimum number of contiguous statements required for extraction. */
	public static final String MINIMUM_STATEMENTS= "cleanup.method_reuse.minimum_statements"; //$NON-NLS-1$

	/** Default profile value for {@link #MINIMUM_STATEMENTS}. */
	public static final String DEFAULT_MINIMUM_STATEMENTS= "3"; //$NON-NLS-1$

	private MethodReuseCleanUpOptions() {
	}
}
