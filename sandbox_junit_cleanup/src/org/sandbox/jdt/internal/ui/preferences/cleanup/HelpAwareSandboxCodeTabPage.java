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
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

/**
 * Adds the stable Eclipse Help context to the existing JUnit cleanup tab while
 * leaving its option construction and behavior in {@link SandboxCodeTabPage}.
 */
public class HelpAwareSandboxCodeTabPage extends SandboxCodeTabPage {

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite,
				"sandbox_junit_cleanup.cleanup_configuration"); //$NON-NLS-1$
		super.doCreatePreferences(composite, numColumns);
	}
}
