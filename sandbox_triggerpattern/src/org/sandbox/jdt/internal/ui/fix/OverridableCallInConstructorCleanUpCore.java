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
package org.sandbox.jdt.internal.ui.fix;

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.OverridableCallInConstructorFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for overridable method call in constructor detection.
 *
 * <p>This is a hint-only cleanup that detects constructors calling
 * overridable (non-private, non-final) methods and reports them as
 * problem markers.</p>
 */
public class OverridableCallInConstructorCleanUpCore extends AbstractSandboxCleanUpCore {

	public OverridableCallInConstructorCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public OverridableCallInConstructorCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return null; // hint-only: no operations
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.OverridableCallInConstructorCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		OverridableCallInConstructorFixCore.findFindings(cu, result.getFindings());
	}

	@Override
	public String getPreview() {
		if (isEnabled(OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP)) {
			return "// Constructor calls only private/final methods\n"; //$NON-NLS-1$
		}
		return "// Constructor calls overridable method\n"; //$NON-NLS-1$
	}
}
