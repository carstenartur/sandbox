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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.internal.corext.dom.AbortSearchException;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

/**
 * Utility class for AST manipulation and rewriting operations.
 * Provides common factory methods and helpers for working with AST nodes.
 */
public final class AstRewriteUtils {

	private AstRewriteUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Checks if a type binding matches a specific type name.
	 * Handles array types by unwrapping to the element type.
	 * Throws AbortSearchException if the binding is null.
	 * 
	 * @param typeBinding the type binding to check
	 * @param typename the fully qualified type name to match
	 * @return true if the type matches
	 * @throws AbortSearchException if typeBinding is null
	 */
	public static boolean isOfType(ITypeBinding typeBinding, String typename) {
		if (typeBinding == null) {
			throw new AbortSearchException();
		}
		if (typeBinding.isArray()) {
			typeBinding = typeBinding.getElementType();
		}
		return typeBinding.getQualifiedName().equals(typename);
	}

	/**
	 * Adds an import to the compilation unit and returns the appropriate name.
	 * This method should be used for every class reference added to generated code.
	 * 
	 * @param typeName  fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite instance
	 * @param ast       AST instance
	 * @return simple name if the import was added, fully qualified name if there was a conflict
	 */
	public static Name ensureImport(String typeName, CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName = cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	/**
	 * Extracts the fully qualified type name from a QualifiedType AST node.
	 * Delegates to {@link org.sandbox.jdt.internal.corext.util.NamingUtils#extractQualifiedTypeName(QualifiedType)}.
	 * 
	 * @param qualifiedType the qualified type to extract from
	 * @return the fully qualified class name
	 */
	public static String extractQualifiedTypeName(QualifiedType qualifiedType) {
		return org.sandbox.jdt.internal.corext.util.NamingUtils.extractQualifiedTypeName(qualifiedType);
	}

	/**
	 * Gets all variable names used in the scope of the given AST node.
	 * Delegates to {@link org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer}.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names used in the node's scope
	 */
	public static java.util.Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root = (CompilationUnit) node.getRoot();
		return new org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer(root)
				.getUsedVariableNames(node.getStartPosition(), node.getLength());
	}
}
