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
package org.sandbox.jdt.internal.corext.util;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.corext.dom.AbortSearchException;

/**
 * Utility class for type-related checks and validations.
 * Provides methods for checking type hierarchies, interfaces, and type relationships
 * commonly needed during JUnit migration.
 */
public final class TypeCheckingUtils {

	private TypeCheckingUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Checks if a type binding matches a given fully qualified type name.
	 * If the type binding is an array type, the element type is checked.
	 *
	 * @param typeBinding the type binding to check
	 * @param typename    the fully qualified type name to compare against
	 * @return true if the type binding matches the given type name
	 * @throws AbortSearchException if typeBinding is null
	 */
	public static boolean isOfType(ITypeBinding typeBinding, String typename) {
		if (typeBinding == null) {
			throw new AbortSearchException();
		}
		if (typeBinding.isArray()) {
			typeBinding= typeBinding.getElementType();
		}
		return typeBinding.getQualifiedName().equals(typename);
	}

	/**
	 * Checks if the given type binding matches or is a subtype of the specified qualified name.
	 * Traverses the superclass hierarchy to find a match.
	 * 
	 * @param typeBinding the type binding to check
	 * @param qualifiedName the fully qualified type name to match
	 * @return true if the type or any of its supertypes matches the qualified name
	 */
	public static boolean isTypeOrSubtype(ITypeBinding typeBinding, String qualifiedName) {
		while (typeBinding != null) {
			if (qualifiedName.equals(typeBinding.getQualifiedName())) {
				return true;
			}
			typeBinding = typeBinding.getSuperclass();
		}
		return false;
	}

	/**
	 * Checks if subtype is a subtype of or implements supertype.
	 * 
	 * @param subtype the potential subtype binding
	 * @param supertype the supertype binding
	 * @return true if subtype is a subtype of or implements supertype
	 */
	public static boolean isSubtypeOf(ITypeBinding subtype, ITypeBinding supertype) {
		return subtype != null && supertype != null
				&& (isTypeOrSubtype(subtype, supertype.getQualifiedName()) || implementsInterface(subtype, supertype));
	}

	/**
	 * Checks if a type binding implements a specific interface (directly or indirectly).
	 * 
	 * @param subtype the type binding to check
	 * @param supertype the interface type binding
	 * @return true if the type implements the interface
	 */
	public static boolean implementsInterface(ITypeBinding subtype, ITypeBinding supertype) {
		for (ITypeBinding iface : subtype.getInterfaces()) {
			if (iface.getQualifiedName().equals(supertype.getQualifiedName())
					|| implementsInterface(iface, supertype)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if an expression has a String type.
	 * 
	 * @param expression the expression to check
	 * @param classType the class type (String.class)
	 * @return true if the expression resolves to String type
	 */
	public static boolean isStringType(Expression expression, Class<String> classType) {
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		return typeBinding != null && classType.getCanonicalName().equals(typeBinding.getQualifiedName());
	}
}
