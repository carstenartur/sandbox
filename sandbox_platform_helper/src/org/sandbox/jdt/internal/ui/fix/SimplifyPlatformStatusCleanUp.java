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
package org.sandbox.jdt.internal.ui.fix;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

/**
 * New static methods to ease Status creation
 *
 * New API methods in Status (org.eclipse.core.runtime.Status) makes it easier
 * and less verbose to make Status object for error handling. There are methods
 * called info, warning and error for creating status objects of those
 * severities. These methods simplify the API by using StackWalker API
 * (introdcued in Java 9) to automatically determine the Plug-in ID. The
 * existing constructors for more fine grained control still continue to exist
 * and may be the most suitable when using Status objects in non-error handling
 * cases as explicitly passing the plug-in id in by String can be faster than
 * automatically determining it.
 *
 * A couple of examples of before and after with the new API:
 *
 * Creating a warning Status Existing API:
 *
 * IStatus status = new Status(IStatus.WARNING, UIPlugin.PLUGIN_ID, IStatus.OK,
 * message, null));
 *
 * New static helper methods:
 *
 * IStatus status = Status.warning(message);
 *
 * Throwing a CoreException: Existing API:
 *
 * throw new CoreException(new Status(IStatus.ERROR, UIPlugin.PLUGIN_ID,
 * message, e));
 *
 * New static helper methods:
 *
 * throw new CoreException(Status.error(message, e));
 *
 *
 */
public class SimplifyPlatformStatusCleanUp extends AbstractCleanUpCoreWrapper<SimplifyPlatformStatusCleanUpCore> {
	public SimplifyPlatformStatusCleanUp(final Map<String, String> options) {
		super(options, new SimplifyPlatformStatusCleanUpCore());
	}

	public SimplifyPlatformStatusCleanUp() {
		this(Collections.emptyMap());
	}
}
