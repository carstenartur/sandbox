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
 * Runs the project-wide cleanup and disposes the SWT display created internally
 * by JDT's cleanup change implementation before the dedicated headless process
 * returns to Equinox.
 */
public final class HeadlessProjectWideCodeCleanupApplication implements IApplication {

	private final ProjectWideCodeCleanupApplication delegate= new ProjectWideCodeCleanupApplication();

	@Override
	public Object start(IApplicationContext context) {
		try {
			return delegate.start(context);
		} finally {
			disposeDisplay();
		}
	}

	@Override
	public void stop() {
		delegate.stop();
		disposeDisplay();
	}

	private static void disposeDisplay() {
		Display display= Display.getDefault();
		if (display.isDisposed()) {
			return;
		}
		if (Display.getCurrent() == display) {
			display.dispose();
			return;
		}
		display.syncExec(() -> {
			if (!display.isDisposed()) {
				display.dispose();
			}
		});
	}
}
