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
 * Mock interface for testing ClassInstanceCreation visitor patterns.
 * Mimics the IProgressMonitor pattern without JFace dependencies.
 */
public interface MockProgressMonitor {
	/**
	 * Begins a task with the specified name and total work units.
	 * 
	 * @param name the task name
	 * @param totalWork the total work units
	 */
	void beginTask(String name, int totalWork);
	
	/**
	 * Marks the task as complete.
	 */
	void done();
	
	/**
	 * Reports work progress.
	 * 
	 * @param work the amount of work done
	 */
	void worked(int work);
}
