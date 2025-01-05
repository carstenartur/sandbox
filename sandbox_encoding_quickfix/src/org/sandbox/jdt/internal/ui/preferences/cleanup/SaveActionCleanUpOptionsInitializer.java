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
		options.setOption(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR, CleanUpOptions.TRUE);
		options.setOption(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8, CleanUpOptions.FALSE);
		options.setOption(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8, CleanUpOptions.FALSE);
	}
}
