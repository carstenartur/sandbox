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
package org.sandbox.jdt.ui.tests.quickfix.mock;

/**
 * Mock class for testing ClassInstanceCreation visitor patterns.
 * Mimics the deprecated SubProgressMonitor pattern without JFace dependencies.
 */
public class MockSubProgressMonitor implements MockProgressMonitor {
	
	private final MockProgressMonitor monitor;
	private final int ticks;
	private final int style;
	
	/**
	 * Creates a new MockSubProgressMonitor with 2 arguments.
	 * 
	 * @param monitor the parent monitor
	 * @param ticks the number of ticks
	 */
	public MockSubProgressMonitor(MockProgressMonitor monitor, int ticks) {
		this(monitor, ticks, 0);
	}
	
	/**
	 * Creates a new MockSubProgressMonitor with 3 arguments.
	 * 
	 * @param monitor the parent monitor
	 * @param ticks the number of ticks
	 * @param style the style flags
	 */
	public MockSubProgressMonitor(MockProgressMonitor monitor, int ticks, int style) {
		this.monitor = monitor;
		this.ticks = ticks;
		this.style = style;
	}
	
	@Override
	public void beginTask(String name, int totalWork) {
		// Mock implementation
	}
	
	@Override
	public void done() {
		// Mock implementation
	}
	
	@Override
	public void worked(int work) {
		// Mock implementation
	}
}
