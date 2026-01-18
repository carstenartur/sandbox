package org.sandbox.jdt.internal.common.util;

/*******************************************************************************
 * Copyright (c) 2026 Sandbox Contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox Contributors - initial API and implementation
 *******************************************************************************/

import org.eclipse.jdt.core.dom.*;

/**
 * OSGi-free equivalents of JDT utility methods.
 * These provide the same functionality as org.eclipse.jdt.internal.corext.dom.ASTNodes
 * but without OSGi dependencies, allowing use in regular Maven projects.
 * 
 * @since 1.2.2
 */
public final class ASTNodeUtils {

	private ASTNodeUtils() {
		// Utility class - no instantiation
	}

	/**
	 * Checks if a method invocation uses a given method signature.
	 * 
	 * @param invocation the method invocation to check
	 * @param qualifiedTypeName the fully qualified name of the declaring type
	 * @param methodName the name of the method
	 * @return true if the invocation matches the signature
	 */
	public static boolean usesGivenSignature(MethodInvocation invocation, String qualifiedTypeName, String methodName) {
		if (invocation == null || methodName == null) {
			return false;
		}
		
		if (!methodName.equals(invocation.getName().getIdentifier())) {
			return false;
		}
		
		return isMethodFromType(invocation, qualifiedTypeName);
	}

	/**
	 * Checks if a method invocation uses a given method signature with parameter types.
	 * 
	 * @param invocation the method invocation to check
	 * @param qualifiedTypeName the fully qualified name of the declaring type
	 * @param methodName the name of the method
	 * @param parameterTypes array of fully qualified parameter type names
	 * @return true if the invocation matches the signature including parameter types
	 */
	public static boolean usesGivenSignature(MethodInvocation invocation, String qualifiedTypeName, 
			String methodName, String[] parameterTypes) {
		if (!usesGivenSignature(invocation, qualifiedTypeName, methodName)) {
			return false;
		}
		
		if (parameterTypes == null || parameterTypes.length == 0) {
			return invocation.arguments().isEmpty();
		}
		
		if (invocation.arguments().size() != parameterTypes.length) {
			return false;
		}
		
		IMethodBinding methodBinding = invocation.resolveMethodBinding();
		if (methodBinding == null) {
			return false;
		}
		
		ITypeBinding[] methodParamTypes = methodBinding.getParameterTypes();
		if (methodParamTypes.length != parameterTypes.length) {
			return false;
		}
		
		for (int i = 0; i < parameterTypes.length; i++) {
			String expectedType = parameterTypes[i];
			String actualType = methodParamTypes[i].getQualifiedName();
			if (!expectedType.equals(actualType)) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Checks if a method invocation is from a given type.
	 * 
	 * @param invocation the method invocation to check
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if the method is declared in the given type or its supertypes
	 */
	private static boolean isMethodFromType(MethodInvocation invocation, String qualifiedTypeName) {
		if (qualifiedTypeName == null) {
			return true; // No type check requested
		}
		
		IMethodBinding methodBinding = invocation.resolveMethodBinding();
		if (methodBinding == null) {
			return false;
		}
		
		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}
		
		return isTypeCompatible(declaringClass, qualifiedTypeName);
	}

	/**
	 * Checks if a type binding is compatible with a given qualified type name.
	 * 
	 * @param typeBinding the type binding to check
	 * @param qualifiedTypeName the fully qualified type name to match
	 * @return true if the type binding matches or is a subtype of the qualified type
	 */
	private static boolean isTypeCompatible(ITypeBinding typeBinding, String qualifiedTypeName) {
		if (typeBinding == null || qualifiedTypeName == null) {
			return false;
		}
		
		// Check exact match
		if (qualifiedTypeName.equals(typeBinding.getQualifiedName())) {
			return true;
		}
		
		// Check erasure for generic types
		ITypeBinding erasure = typeBinding.getErasure();
		if (erasure != null && qualifiedTypeName.equals(erasure.getQualifiedName())) {
			return true;
		}
		
		// Check superclass
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null && isTypeCompatible(superclass, qualifiedTypeName)) {
			return true;
		}
		
		// Check interfaces
		for (ITypeBinding interfaceBinding : typeBinding.getInterfaces()) {
			if (isTypeCompatible(interfaceBinding, qualifiedTypeName)) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Gets the parent of a given type from an AST node.
	 * 
	 * @param node the starting AST node
	 * @param parentClass the class type of the parent to find
	 * @param <T> the type of parent node
	 * @return the parent node of the specified type, or null if not found
	 */
	public static <T extends ASTNode> T getParent(ASTNode node, Class<T> parentClass) {
		if (node == null || parentClass == null) {
			return null;
		}
		
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parentClass.isInstance(parent)) {
				return parentClass.cast(parent);
			}
			parent = parent.getParent();
		}
		
		return null;
	}

	/**
	 * Checks if an AST node is a parent of another node.
	 * 
	 * @param node the potential child node
	 * @param parent the potential parent node
	 * @return true if parent is an ancestor of node
	 */
	public static boolean isParent(ASTNode node, ASTNode parent) {
		if (node == null || parent == null) {
			return false;
		}
		
		ASTNode current = node.getParent();
		while (current != null) {
			if (current == parent) {
				return true;
			}
			current = current.getParent();
		}
		
		return false;
	}

	/**
	 * Gets the statement that contains the given node.
	 * 
	 * @param node the AST node
	 * @return the containing statement, or null if not found
	 */
	public static Statement getContainingStatement(ASTNode node) {
		return getParent(node, Statement.class);
	}

	/**
	 * Gets the method declaration that contains the given node.
	 * 
	 * @param node the AST node
	 * @return the containing method declaration, or null if not found
	 */
	public static MethodDeclaration getContainingMethod(ASTNode node) {
		return getParent(node, MethodDeclaration.class);
	}

	/**
	 * Gets the type declaration that contains the given node.
	 * 
	 * @param node the AST node
	 * @return the containing type declaration, or null if not found
	 */
	public static AbstractTypeDeclaration getContainingType(ASTNode node) {
		return getParent(node, AbstractTypeDeclaration.class);
	}
}
