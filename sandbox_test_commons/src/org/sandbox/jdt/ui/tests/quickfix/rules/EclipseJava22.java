/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
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
package org.sandbox.jdt.ui.tests.quickfix.rules;

import org.eclipse.jdt.core.JavaCore;

/**
 * JUnit 5 extension that configures tests to run with Java 22 runtime stubs.
 * <p>
 * This class provides the necessary test environment setup for testing code
 * transformations and cleanups that target Java 22 language features and APIs.
 * </p>
 */
public final class EclipseJava22 extends AbstractEclipseJava {
	
	/** Path to the Java 22 runtime stubs JAR file used for testing. */
	private static final String RT_STUBS_JAR_PATH = "testresources/rtstubs_22.jar"; //$NON-NLS-1$

	/**
	 * Constructs a new test configuration for Java 22.
	 */
	public EclipseJava22() {
		super(RT_STUBS_JAR_PATH, JavaCore.VERSION_22);
	}
}
