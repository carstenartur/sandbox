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
package org.sandbox.jdt.internal.corext.fix.helper.base;

import java.util.Collection;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Abstract base class for cleanup tools.
 * Provides common functionality for AST manipulation and transformation operations.
 * 
 * @param <T> Type found in Visitor (holder type for passing data between find and rewrite phases)
 */
public abstract class AbstractToolBase<T> {

	/**
	 * Checks if a type binding matches a specific type name.
	 * 
	 * @param typeBinding the type binding to check
	 * @param typename the fully qualified type name to match
	 * @return true if the type matches
	 */
	protected static boolean isOfType(ITypeBinding typeBinding, String typename) {
		return AstRewriteUtils.isOfType(typeBinding, typename);
	}

	/**
	 * Adds an import to the class and returns the appropriate name to use.
	 * This method should be used for every class reference added to the generated code.
	 *
	 * @param typeName  fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	protected Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		return AstRewriteUtils.ensureImport(typeName, cuRewrite, ast);
	}

	/**
	 * Gets all variable names used in the scope of the given AST node.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names used in the node's scope
	 */
	public static Collection<String> getUsedVariableNames(ASTNode node) {
		return AstRewriteUtils.getUsedVariableNames(node);
	}

	/**
	 * Extracts the fully qualified type name from a QualifiedType AST node.
	 * 
	 * @param qualifiedType the qualified type to extract from
	 * @return the fully qualified class name
	 */
	protected String extractQualifiedTypeName(QualifiedType qualifiedType) {
		return AstRewriteUtils.extractQualifiedTypeName(qualifiedType);
	}

	/**
	 * Gets a preview of the code before or after refactoring.
	 * Used to display examples in the Eclipse cleanup preferences UI.
	 * 
	 * @param afterRefactoring if true, returns the "after" preview; if false, returns the "before" preview
	 * @return a code snippet showing the transformation (formatted as Java source code)
	 */
	public abstract String getPreview(boolean afterRefactoring);
}
