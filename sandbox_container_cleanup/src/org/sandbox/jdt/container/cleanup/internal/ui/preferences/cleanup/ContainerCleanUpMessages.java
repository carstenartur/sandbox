/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup;

import org.eclipse.osgi.util.NLS;

/** Messages for the semantic container cleanup preference page. */
public final class ContainerCleanUpMessages extends NLS {

	private static final String BUNDLE_NAME=
			"org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup.ContainerCleanUpMessages"; //$NON-NLS-1$

	public static String ContainerTabPage_GroupName;
	public static String ContainerTabPage_Master;
	public static String ContainerTabPage_AppendArrayToList;
	public static String ContainerTabPage_UniqueSequenceToSet;

	static {
		NLS.initializeMessages(BUNDLE_NAME, ContainerCleanUpMessages.class);
	}

	private ContainerCleanUpMessages() {
	}
}
