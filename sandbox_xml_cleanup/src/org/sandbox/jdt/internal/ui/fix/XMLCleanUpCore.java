/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.fix;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

/**
 * Inert compatibility entry point for previously stored Java cleanup settings.
 * XML resources are edited only by the explicit PDE commands. JDT cleanup fixes
 * describe Java compilation-unit edits and cannot carry XML resource changes.
 */
public class XMLCleanUpCore extends AbstractCleanUp {

	public XMLCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public XMLCleanUpCore() {
	}

	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(false, false, false, null);
	}

	public boolean requireAST() {
		return false;
	}

	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		// Do not discover or prepare XML edits during Java change calculation.
		return null;
	}

	@Override
	public String[] getStepDescriptions() {
		return new String[0];
	}

	@Override
	public String getPreview() {
		return ""; //$NON-NLS-1$
	}
}
