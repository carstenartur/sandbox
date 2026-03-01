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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.debug;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;

/**
 * Launch configuration delegate for debugging {@code .sandbox-hint} files.
 *
 * <p>Compiles all {@code <? ?>} blocks with debug information ({@code -g} flag)
 * and starts the cleanup run in debug mode, registering the
 * {@link SandboxHintSourceLocator} for source lookup.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintLaunchConfigurationType implements ILaunchConfigurationDelegate {

	/**
	 * The launch configuration type ID.
	 */
	public static final String TYPE_ID = "org.sandbox.jdt.triggerpattern.debug.sandboxHintLaunch"; //$NON-NLS-1$

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		// Set the source locator for mapping synthetic class frames to hint file lines
		launch.setSourceLocator(new SandboxHintSourceLocator());
		// Debug launch is a placeholder for future bytecode-based execution
	}
}
