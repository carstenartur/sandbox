/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

/** Eclipse UI wrapper for semantic container-contract cleanups. */
public final class ContainerCleanUp
		extends AbstractCleanUpCoreWrapper<ContainerCleanUpCore> {

	public ContainerCleanUp(Map<String, String> options) {
		super(options, new ContainerCleanUpCore());
	}

	public ContainerCleanUp() {
		this(Collections.emptyMap());
	}
}
