/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

class XMLPluginSynchronizationTest {

	@Test
	void cleanupEntryPointsDoNotExposePublicInstanceMonitor() throws NoSuchMethodException {
		Method find= XMLPlugin.class.getDeclaredMethod("find", //$NON-NLS-1$
				XMLCleanUpFixCore.class, CompilationUnit.class, Set.class, Set.class, boolean.class);
		Method setEnableIndent= XMLPlugin.class.getDeclaredMethod("setEnableIndent", boolean.class); //$NON-NLS-1$

		assertFalse(Modifier.isSynchronized(find.getModifiers()));
		assertFalse(Modifier.isSynchronized(setEnableIndent.getModifiers()));
	}
}
