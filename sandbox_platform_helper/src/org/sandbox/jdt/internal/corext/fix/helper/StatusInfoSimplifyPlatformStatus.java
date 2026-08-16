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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;

/**
 * Removes a redundant {@code IStatus.OK} code from an informational status while
 * preserving its explicit plug-in identifier, message and throwable.
 */
public class StatusInfoSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus<ClassInstanceCreation> {

	public StatusInfoSimplifyPlatformStatus() {
		super(IStatus.INFO);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "IStatus status = new Status(IStatus.INFO, UIPlugin.PLUGIN_ID, message, e);\n"; //$NON-NLS-1$
		}
		return "IStatus status = new Status(IStatus.INFO, UIPlugin.PLUGIN_ID, IStatus.OK, message, e);\n"; //$NON-NLS-1$
	}
}
