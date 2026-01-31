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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * @param <T> Type found in Visitor
 */
public abstract class AbstractFunctionalCall<T extends ASTNode> {

	public abstract void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed);

	public abstract void rewrite(UseFunctionalCallFixCore useExplicitEncodingFixCore, T visited, CompilationUnitRewrite cuRewrite,
			TextEditGroup group, org.sandbox.jdt.internal.common.ReferenceHolder<ASTNode, Object> data) throws CoreException;

	public abstract String getPreview(boolean afterRefactoring);
}
