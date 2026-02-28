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

import java.io.IOException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Console for streaming embedded Java execution logs during cleanup/quickfix runs.
 *
 * <p>Provides color-coded output:</p>
 * <ul>
 *   <li>Green: successful guard evaluation</li>
 *   <li>Yellow: skipped guard (returned false)</li>
 *   <li>Red: execution failure/exception</li>
 *   <li>Default: general info messages</li>
 * </ul>
 *
 * @since 1.5.0
 */
public final class SandboxHintConsole {

	private static final String CONSOLE_NAME = "Sandbox Hint Execution"; //$NON-NLS-1$

	private static SandboxHintConsole instance;

	private final MessageConsole console;

	private SandboxHintConsole() {
		console = new MessageConsole(CONSOLE_NAME, null);
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		if (plugin != null) {
			IConsoleManager manager = plugin.getConsoleManager();
			manager.addConsoles(new IConsole[] { console });
		}
	}

	/**
	 * Returns the singleton console instance.
	 *
	 * @return the console instance
	 */
	public static synchronized SandboxHintConsole getInstance() {
		if (instance == null) {
			instance = new SandboxHintConsole();
		}
		return instance;
	}

	/**
	 * Logs a successful guard execution (green text).
	 *
	 * @param message the message to log
	 */
	public void logSuccess(String message) {
		writeColored(message, Display.getDefault().getSystemColor(SWT.COLOR_DARK_GREEN));
	}

	/**
	 * Logs a skipped guard execution (yellow/dark yellow text).
	 *
	 * @param message the message to log
	 */
	public void logSkipped(String message) {
		writeColored(message, Display.getDefault().getSystemColor(SWT.COLOR_DARK_YELLOW));
	}

	/**
	 * Logs a failed execution (red text).
	 *
	 * @param message the message to log
	 */
	public void logError(String message) {
		writeColored(message, Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED));
	}

	/**
	 * Logs a general info message (default color).
	 *
	 * @param message the message to log
	 */
	public void logInfo(String message) {
		try (MessageConsoleStream stream = console.newMessageStream()) {
			stream.println(message);
		} catch (IOException e) {
			ILog log = Platform.getLog(SandboxHintConsole.class);
			log.warn("Failed to write to console", e); //$NON-NLS-1$
		}
	}

	/**
	 * Reveals the console in the Console view.
	 */
	public void reveal() {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		if (plugin != null) {
			IConsoleManager manager = plugin.getConsoleManager();
			manager.showConsoleView(console);
		}
	}

	/**
	 * Clears the console output.
	 */
	public void clear() {
		console.clearConsole();
	}

	private void writeColored(String message, Color color) {
		MessageConsoleStream stream = console.newMessageStream();
		stream.setColor(color);
		try {
			stream.println(message);
			stream.close();
		} catch (IOException e) {
			ILog log = Platform.getLog(SandboxHintConsole.class);
			log.warn("Failed to write to console", e); //$NON-NLS-1$
		}
	}
}
