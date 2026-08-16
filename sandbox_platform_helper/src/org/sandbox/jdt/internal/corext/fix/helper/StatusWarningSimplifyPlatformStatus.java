/*******************************************************************************
 * Copyright (c) 2021, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.core.runtime.IStatus;

/**
 * Removes a redundant {@code IStatus.OK} code from a warning status while
 * preserving its explicit plug-in identifier, message and throwable.
 */
public class StatusWarningSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus {

	public StatusWarningSimplifyPlatformStatus() {
		super(IStatus.WARNING);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "IStatus status = new Status(IStatus.WARNING, UIPlugin.PLUGIN_ID, message, e);\n"; //$NON-NLS-1$
		}
		return "IStatus status = new Status(IStatus.WARNING, UIPlugin.PLUGIN_ID, IStatus.OK, message, e);\n"; //$NON-NLS-1$
	}
}
