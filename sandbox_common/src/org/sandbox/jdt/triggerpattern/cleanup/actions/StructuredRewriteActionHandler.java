/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import org.eclipse.core.runtime.CoreException;

import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/** Runtime SPI for one schema-validated structured rewrite action. */
@FunctionalInterface
public interface StructuredRewriteActionHandler {

	/** Applies one action to the shared compilation-unit rewrite transaction. */
	void apply(StructuredRewriteAction action, StructuredRewriteActionContext context)
			throws CoreException;
}
