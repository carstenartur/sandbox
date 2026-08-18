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

/** Simplifies informational statuses without changing their observable identity. */
public class StatusInfoSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus {

	public StatusInfoSimplifyPlatformStatus() {
		super(IStatus.INFO, "info"); //$NON-NLS-1$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "IStatus status = Status.info(message);\n"; //$NON-NLS-1$
		}
		return "IStatus status = new Status(IStatus.INFO, UIPlugin.class, IStatus.OK, message, null);\n"; //$NON-NLS-1$
	}
}
