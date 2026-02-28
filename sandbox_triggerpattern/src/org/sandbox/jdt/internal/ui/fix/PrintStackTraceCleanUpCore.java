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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.PRINT_STACKTRACE_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.PrintStackTraceFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for printStackTrace() detection.
 *
 * <p>This is a hint-only cleanup that detects calls to {@code printStackTrace()}
 * and reports them as problem markers. It does not modify code because the
 * appropriate logger varies per project.</p>
 */
public class PrintStackTraceCleanUpCore extends AbstractSandboxCleanUpCore {

	public PrintStackTraceCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public PrintStackTraceCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return PRINT_STACKTRACE_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return null; // hint-only: no operations
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.PrintStackTraceCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		PrintStackTraceFixCore.findFindings(cu, result.getFindings());
	}

	@Override
	public String getPreview() {
		if (isEnabled(PRINT_STACKTRACE_CLEANUP)) {
			return "logger.log(Level.SEVERE, \"Error\", ex);\n"; //$NON-NLS-1$
		}
		return "ex.printStackTrace();\n"; //$NON-NLS-1$
	}
}
