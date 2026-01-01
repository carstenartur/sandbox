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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;

/**
 * Abstract base class for method reuse detection and refactoring.
 * Provides common functionality for finding code patterns that can be
 * refactored to reuse existing methods.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #find} - to locate code patterns that need to be fixed</li>
 *   <li>{@link #rewrite} - to apply the method reuse transformation</li>
 *   <li>{@link #getPreview} - to generate preview text for the fix</li>
 * </ul>
 *
 * @param <T> The type of AST node that this handler processes
 */
public abstract class AbstractMethodReuse<T extends ASTNode> {

	/**
	 * Finds all occurrences of the pattern that this handler processes
	 * and adds corresponding rewrite operations.
	 *
	 * @param fixcore the fix core instance, must not be null
	 * @param compilationUnit the compilation unit to search in, must not be null
	 * @param operations the set to add rewrite operations to, must not be null
	 * @param nodesprocessed the set of already processed nodes (to avoid duplicates), must not be null
	 */
	public abstract void find(MethodReuseCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed);

	/**
	 * Rewrites the visited AST node to use method reuse.
	 *
	 * @param fixcore the fix core instance, must not be null
	 * @param visited the AST node to rewrite, must not be null
	 * @param cuRewrite the compilation unit rewrite context, must not be null
	 * @param group the text edit group for grouping changes, must not be null
	 * @param data the reference holder containing node-specific data, must not be null
	 */
	public abstract void rewrite(MethodReuseCleanUpFixCore fixcore, T visited, CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ReferenceHolder<ASTNode, Object> data);

	/**
	 * Generates a preview string showing the code before or after the refactoring.
	 *
	 * @param afterRefactoring true to show the code after refactoring, false for before
	 * @return the preview string, never null
	 */
	public abstract String getPreview(boolean afterRefactoring);
}
