/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/** Compatibility facade over the shared source-preserving NLS rewrite. */
final class EncodingSourceRewrite {

	private EncodingSourceRewrite() {
	}

	static void clear(CompilationUnit root) {
		org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite.clear(root);
	}

	static void record(CompilationUnitRewrite cuRewrite, ASTNode argument) {
		org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite.record(cuRewrite, argument);
	}

	static void complete(CompilationUnit root, CompilationUnitChange change) throws JavaModelException {
		org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite.complete(root, change);
	}
}
