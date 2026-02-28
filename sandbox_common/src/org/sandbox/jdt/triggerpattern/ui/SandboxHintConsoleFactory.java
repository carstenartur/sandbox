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
package org.sandbox.jdt.triggerpattern.ui;

import org.eclipse.ui.console.IConsoleFactory;

/**
 * Factory for creating and revealing the {@link SandboxHintConsole}.
 *
 * <p>Registered via the {@code org.eclipse.ui.console.consoleFactories}
 * extension point to appear in the Console view's "Open Console" dropdown.</p>
 *
 * @since 1.5.0
 */
public class SandboxHintConsoleFactory implements IConsoleFactory {

	@Override
	public void openConsole() {
		SandboxHintConsole hintConsole = SandboxHintConsole.getInstance();
		hintConsole.reveal();
	}
}
