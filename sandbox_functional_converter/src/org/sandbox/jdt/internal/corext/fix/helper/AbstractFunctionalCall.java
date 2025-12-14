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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Name;
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
			TextEditGroup group);

	/**
	 * Adds an import to the class. This method should be used for every class reference added to
	 * the generated code.
	 *
	 * @param typeName a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast AST
	 * @return simple name of a class if the import was added and fully qualified name if there was
	 *         a conflict
	 */
	protected Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName= cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}


	public abstract String getPreview(boolean afterRefactoring);
}
