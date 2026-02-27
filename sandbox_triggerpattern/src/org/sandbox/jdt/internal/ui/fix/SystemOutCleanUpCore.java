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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.SYSTEM_OUT_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.SystemOutFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for System.out/System.err detection.
 *
 * <p>This is a hint-only cleanup that detects usage of {@code System.out}
 * and {@code System.err} and reports them as problem markers.</p>
 */
public class SystemOutCleanUpCore extends AbstractSandboxCleanUpCore {

	public SystemOutCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public SystemOutCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return SYSTEM_OUT_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return null; // hint-only: no operations
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.SystemOutCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		SystemOutFixCore.findFindings(cu, result.getFindings());
	}

	@Override
	public String getPreview() {
		if (isEnabled(SYSTEM_OUT_CLEANUP)) {
			return "logger.info(message);\n"; //$NON-NLS-1$
		}
		return "System.out.println(message);\n"; //$NON-NLS-1$
	}
}
