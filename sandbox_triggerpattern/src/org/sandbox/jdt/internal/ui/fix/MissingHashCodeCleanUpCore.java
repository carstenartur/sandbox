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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.MISSING_HASHCODE_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.MissingHashCodeFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for missing hashCode() detection.
 *
 * <p>This is a hint-only cleanup that detects classes overriding
 * {@code equals()} without {@code hashCode()} and reports them as
 * problem markers.</p>
 */
public class MissingHashCodeCleanUpCore extends AbstractSandboxCleanUpCore {

	public MissingHashCodeCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public MissingHashCodeCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return MISSING_HASHCODE_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return null; // hint-only: no operations
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.MissingHashCodeCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		MissingHashCodeFixCore.findFindings(cu, result.getFindings());
	}

	@Override
	public String getPreview() {
		if (isEnabled(MISSING_HASHCODE_CLEANUP)) {
			return "// equals() and hashCode() both overridden\n"; //$NON-NLS-1$
		}
		return "// equals() overridden, hashCode() missing\n"; //$NON-NLS-1$
	}
}
