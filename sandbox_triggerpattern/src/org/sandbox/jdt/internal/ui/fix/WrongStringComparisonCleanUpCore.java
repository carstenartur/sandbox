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

import static org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants.WRONG_STRING_COMPARISON_CLEANUP;

import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.WrongStringComparisonFixCore;
import org.sandbox.jdt.triggerpattern.eclipse.CleanUpResult;

/**
 * CleanUp for wrong string comparison detection using TriggerPattern hints.
 *
 * <p>This cleanup detects string comparisons using == or != and replaces
 * them with proper .equals() calls.</p>
 *
 */
public class WrongStringComparisonCleanUpCore extends AbstractSandboxCleanUpCore {

	public WrongStringComparisonCleanUpCore(final Map<String, String> options) {
		super(options);
	}

	public WrongStringComparisonCleanUpCore() {
	}

	@Override
	protected String getCleanUpKey() {
		return WRONG_STRING_COMPARISON_CLEANUP;
	}

	@Override
	protected String getFixLabel() {
		return MultiFixMessages.WrongStringComparisonCleanUpFix_refactor;
	}

	@Override
	protected String getDescription() {
		return MultiFixMessages.WrongStringComparisonCleanUp_description;
	}

	@Override
	protected void detect(CompilationUnit cu, CleanUpResult result) {
		WrongStringComparisonFixCore.findOperations(cu, result.getOperations());
	}

	@Override
	public String getPreview() {
		if (isEnabled(WRONG_STRING_COMPARISON_CLEANUP)) {
			return "\"literal\".equals(str)\n"; //$NON-NLS-1$
		}
		return "str == \"literal\"\n"; //$NON-NLS-1$
	}
}
