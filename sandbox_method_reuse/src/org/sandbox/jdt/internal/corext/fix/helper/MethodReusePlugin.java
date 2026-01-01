/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodReuse;

/**
 * Placeholder plugin for general method reuse detection
 * 
 * This is a placeholder implementation for the METHOD_REUSE_CLEANUP constant.
 * It corresponds to general method reuse detection functionality that is planned
 * for future implementation.
 * 
 * The METHOD_REUSE_CLEANUP constant is already used in the UI preferences but
 * does not yet trigger any cleanup operations. This placeholder allows the enum
 * structure to match the constant without implementing the full functionality.
 * 
 * Compare with InlineSequencesPlugin which provides the complete implementation
 * for METHOD_REUSE_INLINE_SEQUENCES constant.
 * 
 * Future implementation should detect similar methods across the codebase and
 * suggest refactoring opportunities. See MethodReuseFinder for planned functionality.
 */
public class MethodReusePlugin extends AbstractMethodReuse<ASTNode> {

	@Override
	public void find(Object fixcore, CompilationUnit compilationUnit,
			Set<?> operations, Set<ASTNode> nodesprocessed) throws CoreException {
		// Placeholder implementation - does nothing
		// This will be implemented in the future
	}

	@Override
	public void rewrite(Object fixcore, ReferenceHolder<?, ?> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		// Placeholder implementation - does nothing
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				// After: Placeholder for method reuse
				// This feature is not yet implemented
				""";
		} else {
			return """
				// Before: Placeholder for method reuse
				// This feature is not yet implemented
				""";
		}
	}
}
