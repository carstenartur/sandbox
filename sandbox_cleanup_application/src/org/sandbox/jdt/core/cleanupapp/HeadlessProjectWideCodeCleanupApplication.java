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
package org.sandbox.jdt.core.cleanupapp;

import org.eclipse.swt.widgets.Display;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

/**
 * Runs the project-wide cleanup with an SWT display owned by the application
 * thread and disposes that display before the dedicated headless process
 * returns to Equinox.
 */
public final class HeadlessProjectWideCodeCleanupApplication implements IApplication {

	private final ProjectWideCodeCleanupApplication delegate= new ProjectWideCodeCleanupApplication();

	@Override
	public Object start(IApplicationContext context) {
		Display current= Display.getCurrent();
		if (current != null) {
			return delegate.start(context);
		}

		Display display= new Display();
		try {
			return delegate.start(context);
		} finally {
			if (!display.isDisposed()) {
				display.dispose();
			}
		}
	}

	@Override
	public void stop() {
		delegate.stop();
	}
}
