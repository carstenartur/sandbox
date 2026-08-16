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

/** Simplifies warning statuses without changing their observable identity. */
public class StatusWarningSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus {

	public StatusWarningSimplifyPlatformStatus() {
		super(IStatus.WARNING, "warning"); //$NON-NLS-1$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "IStatus status = Status.warning(message, e);\n"; //$NON-NLS-1$
		}
		return "IStatus status = new Status(IStatus.WARNING, UIPlugin.class, IStatus.OK, message, e);\n"; //$NON-NLS-1$
	}
}
