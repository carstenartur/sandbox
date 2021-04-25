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
import org.sandbox.jdt.internal.corext.fix.MYCleanUpConstants;

public class DefaultCleanUpOptionsInitializer implements ICleanUpOptionsInitializer {

	@Override
	public void setDefaultOptions(CleanUpOptions options) {
//		for (String elem: options.getKeys()) {
//			if(elem.startsWith("cleanup.ex")) { //$NON-NLS-1$
//				System.out.println("found:"+elem); //$NON-NLS-1$
//			}
//		}
		options.setOption(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, CleanUpOptions.FALSE);
	}
}
