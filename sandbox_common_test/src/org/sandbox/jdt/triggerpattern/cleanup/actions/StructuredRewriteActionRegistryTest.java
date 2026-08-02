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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.triggerpattern.api.RewriteActionCatalog;

/** Keeps the parser-visible action catalog aligned with executable handlers. */
class StructuredRewriteActionRegistryTest {

	@Test
	void everyStandardActionHasExactlyOneRuntimeHandler() {
		assertEquals(RewriteActionCatalog.standard().names(),
				StructuredRewriteActionRegistry.getInstance().registeredNames());
	}
}
