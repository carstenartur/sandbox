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
package org.sandbox.jdt.internal.common.util;

import org.eclipse.jdt.core.dom.*;

/**
 * OSGi-free replacements for org.eclipse.jdt.internal.corext.dom.ASTNodes utilities.
 * 
 * <p>This class provides common AST traversal utilities without dependencies on
 * Eclipse OSGi internal classes, enabling fast unit testing without Tycho.</p>
 */
public final class ASTNodeUtils {
	private ASTNodeUtils() {}
	
	/**
	 * Returns the first ancestor of the given node that is an instance of the specified class.
	 * 
	 * @param <T> the type of parent to find
	 * @param node the starting node
	 * @param parentClass the class of the parent to find
	 * @return the first ancestor of the specified type, or null if not found
	 */
	@SuppressWarnings("unchecked")
	public static <T extends ASTNode> T getParent(ASTNode node, Class<T> parentClass) {
		if (node == null) return null;
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parentClass.isInstance(parent)) {
				return (T) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}
	
	/**
	 * Returns the first ancestor of the given node that is an instance of the specified class.
	 * Alias for {@link #getParent(ASTNode, Class)}.
	 * 
	 * @param <T> the type of ancestor to find
	 * @param node the starting node
	 * @param ancestorClass the class of the ancestor to find
	 * @return the first ancestor of the specified type, or null if not found
	 */
	public static <T extends ASTNode> T getFirstAncestorOrNull(ASTNode node, Class<T> ancestorClass) {
		return getParent(node, ancestorClass);
	}
	
	/**
	 * Checks if the given node has an ancestor of the specified type.
	 * 
	 * @param node the starting node
	 * @param ancestorClass the class of the ancestor to check for
	 * @return true if an ancestor of the specified type exists, false otherwise
	 */
	public static boolean hasAncestor(ASTNode node, Class<? extends ASTNode> ancestorClass) {
		return getParent(node, ancestorClass) != null;
	}
	
	/**
	 * Returns the method declaration that encloses the given node.
	 * 
	 * @param node the starting node
	 * @return the enclosing method declaration, or null if not found
	 */
	public static MethodDeclaration getEnclosingMethod(ASTNode node) {
		return getParent(node, MethodDeclaration.class);
	}
	
	/**
	 * Returns the type declaration that encloses the given node.
	 * 
	 * @param node the starting node
	 * @return the enclosing type declaration, or null if not found
	 */
	public static TypeDeclaration getEnclosingType(ASTNode node) {
		return getParent(node, TypeDeclaration.class);
	}
}
