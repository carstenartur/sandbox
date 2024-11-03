/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpOptionsInitializer;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

public class SaveActionCleanUpOptionsInitializer implements ICleanUpOptionsInitializer {

	@Override
	public void setDefaultOptions(CleanUpOptions options) {
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, CleanUpOptions.FALSE);
	}
}
