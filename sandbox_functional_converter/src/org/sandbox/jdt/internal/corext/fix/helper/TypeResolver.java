/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.internal.corext.util.VariableResolver;

/**
 * Utility class for resolving variable types in AST nodes.
 * 
 * <p>
 * This class delegates to {@link VariableResolver} in sandbox_common for
 * the actual implementation. It is maintained for backward compatibility
 * within the functional converter.
 * </p>
 * 
 * @see VariableResolver
 * @deprecated Use {@link VariableResolver} directly from sandbox_common instead.
 */
@Deprecated
public final class TypeResolver {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private TypeResolver() {
		// Utility class - no instances allowed
	}

	/**
	 * @see VariableResolver#getVariableType(ASTNode, String)
	 */
	public static String getVariableType(ASTNode startNode, String varName) {
		return VariableResolver.getVariableType(startNode, varName);
	}

	/**
	 * @see VariableResolver#getTypeBinding(ASTNode, String)
	 */
	public static ITypeBinding getTypeBinding(ASTNode startNode, String varName) {
		return VariableResolver.getTypeBinding(startNode, varName);
	}

	/**
	 * @see VariableResolver#findVariableDeclaration(ASTNode, String)
	 */
	public static VariableDeclarationFragment findVariableDeclaration(ASTNode startNode, String varName) {
		return VariableResolver.findVariableDeclaration(startNode, varName);
	}

	/**
	 * @see VariableResolver#hasNotNullAnnotation(ASTNode, String)
	 */
	public static boolean hasNotNullAnnotation(ASTNode startNode, String varName) {
		return VariableResolver.hasNotNullAnnotation(startNode, varName);
	}

	/**
	 * @see VariableResolver#hasNotNullAnnotationOnBinding(IVariableBinding)
	 */
	public static boolean hasNotNullAnnotationOnBinding(IVariableBinding binding) {
		return VariableResolver.hasNotNullAnnotationOnBinding(binding);
	}
}