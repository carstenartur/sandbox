/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;

/** Completes recorded NLS changes without replacing enclosing statements as raw text. */
public class NlsAwareCleanUpFix extends CompilationUnitRewriteOperationsFixCore {

	private final CompilationUnit root;

	public NlsAwareCleanUpFix(String name, CompilationUnit root, CompilationUnitRewriteOperation[] operations) {
		super(name, root, operations);
		this.root= root;
	}

	@Override
	public CompilationUnitChange createChange(IProgressMonitor monitor) throws CoreException {
		EncodingSourceRewrite.clear(root);
		CompilationUnitChange change= null;
		try {
			change= super.createChange(monitor);
			EncodingSourceRewrite.complete(root, change);
			return change;
		} catch (CoreException | RuntimeException exception) {
			if (change != null) {
				change.dispose();
			}
			throw exception;
		} finally {
			EncodingSourceRewrite.clear(root);
		}
	}
}
