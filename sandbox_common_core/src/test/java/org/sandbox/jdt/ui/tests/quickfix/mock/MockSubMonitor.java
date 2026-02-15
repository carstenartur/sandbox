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
 * Mimics the SubMonitor pattern (replacement for SubProgressMonitor) without JFace dependencies.
 */
public class MockSubMonitor implements MockProgressMonitor {
	
	/**
	 * Converts a monitor to a SubMonitor.
	 * 
	 * @param monitor the monitor to convert
	 * @param work the total work units
	 * @return a new MockSubMonitor instance
	 */
	public static MockSubMonitor convert(MockProgressMonitor monitor, int work) {
		return new MockSubMonitor();
	}
	
	/**
	 * Converts a monitor to a SubMonitor with a task name.
	 * 
	 * @param monitor the monitor to convert
	 * @param taskName the task name
	 * @param work the total work units
	 * @return a new MockSubMonitor instance
	 */
	public static MockSubMonitor convert(MockProgressMonitor monitor, String taskName, int work) {
		return new MockSubMonitor();
	}
	
	/**
	 * Splits off a portion of work.
	 * 
	 * @param work the amount of work to split
	 * @return a new MockSubMonitor for the split work
	 */
	public MockSubMonitor split(int work) {
		return new MockSubMonitor();
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
