package org.sandbox.jdt.ui.tests.quickfix.rules;

/*-
 * #%L
 * Sandbox test commons
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


import org.eclipse.jdt.core.JavaCore;

public class EclipseJava17 extends AbstractEclipseJava {
	private static final String TESTRESOURCES_RTSTUBS_17_JAR= "testresources/rtstubs_17.jar"; //$NON-NLS-1$

	public EclipseJava17() {
		super(TESTRESOURCES_RTSTUBS_17_JAR, JavaCore.VERSION_17);
	}
}
