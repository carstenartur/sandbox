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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;

/**
 * Until JEP 400 is active, platform encoding might be different than UTF-8.
 * Explicit encoding also enables migration to the type-safe modern APIs.
 */
public class UseExplicitEncodingCleanUp extends AbstractCleanUpCoreWrapper<UseExplicitEncodingCleanUpCore>
		implements IMultiFileCleanUpScopeProvider {
	public UseExplicitEncodingCleanUp(final Map<String, String> options) {
		super(options, new UseExplicitEncodingCleanUpCore());
	}

	public UseExplicitEncodingCleanUp() {
		this(Collections.emptyMap());
	}

	@Override
	public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
			Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) throws CoreException {
		return cleanUpCore.expandCleanUpScope(project, currentScope, monitor);
	}

	public Collection<Map<String, Object>> getCoordinatedCleanUpPreview(IJavaProject project) throws CoreException {
		return cleanUpCore.getCoordinatedCleanUpPreview(project);
	}
}
