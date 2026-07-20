/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

import org.sandbox.jdt.cleanup.multifile.IMultiFileCleanUpScopeProvider;

/** Cleanup that converts integer state domains to enums. */
public class IntToEnumCleanUp extends AbstractCleanUpCoreWrapper<IntToEnumCleanUpCore>
		implements IMultiFileCleanUpScopeProvider {
	public IntToEnumCleanUp(final Map<String, String> options) {
		super(options, new IntToEnumCleanUpCore());
	}

	public IntToEnumCleanUp() {
		this(Collections.emptyMap());
	}

	@Override
	public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		return cleanUpCore.expandCleanUpScope(project, currentScope, monitor);
	}
}
