/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix;

/** Additional options that control the scope of the Int-to-Enum cleanup. */
public final class IntToEnumCleanUpOptions {

	/**
	 * Enables coordinated planning across every source compilation unit in the
	 * selected Java project. The option is deliberately separate from the main
	 * cleanup switch because project-wide analysis can change additional files and
	 * is substantially more expensive than a local cleanup.
	 */
	public static final String PROJECT_WIDE= "cleanup.int_to_enum.project_wide"; //$NON-NLS-1$

	private IntToEnumCleanUpOptions() {
		// constants only
	}
}
