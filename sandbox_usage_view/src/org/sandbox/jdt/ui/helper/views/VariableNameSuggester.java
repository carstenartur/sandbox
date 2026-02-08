/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

/**
 * Helper class for generating suggested variable names based on existing name and type.
 * This is useful for renaming variables to include type information in their names.
 */
public class VariableNameSuggester {

	private VariableNameSuggester() {
		// Utility class
	}

	/**
	 * Generates a suggested name for a variable based on its current name and type.
	 * The suggestion follows common Java naming conventions.
	 * 
	 * Examples:
	 * - "value" + String -> "stringValue"
	 * - "result" + Integer -> "integerResult"
	 * - "item" + List<String> -> "stringListItem"
	 * - "data" + byte[] -> "byteArrayData"
	 * 
	 * @param variableBinding the variable binding to generate a name for
	 * @return a suggested name incorporating the type
	 */
	public static String suggestName(IVariableBinding variableBinding) {
		String currentName = variableBinding.getName();
		ITypeBinding typeBinding = variableBinding.getType();
		
		if (typeBinding == null) {
			return currentName;
		}
		
		String typePrefix = getTypePrefix(typeBinding);
		
		if (typePrefix.isEmpty()) {
			return currentName;
		}
		
		// Combine type prefix with existing name using camelCase
		return typePrefix + capitalizeFirst(currentName);
	}

	/**
	 * Gets a prefix string based on the type binding.
	 * 
	 * @param typeBinding the type binding
	 * @return a lowercase prefix string representing the type
	 */
	private static String getTypePrefix(ITypeBinding typeBinding) {
		if (typeBinding.isArray()) {
			// Handle array types: int[] -> "intArray", String[] -> "stringArray"
			ITypeBinding elementType = typeBinding.getElementType();
			String elementName = getSimpleTypeName(elementType);
			return lowercaseFirst(elementName) + "Array"; //$NON-NLS-1$
		}
		
		if (typeBinding.isParameterizedType()) {
			// Handle generic types: List<String> -> "stringList"
			ITypeBinding[] typeArguments = typeBinding.getTypeArguments();
			String baseName = getSimpleTypeName(typeBinding);
			
			if (typeArguments != null && typeArguments.length > 0) {
				String argName = getSimpleTypeName(typeArguments[0]);
				return lowercaseFirst(argName) + baseName;
			}
			return lowercaseFirst(baseName);
		}
		
		// Simple type
		String typeName = getSimpleTypeName(typeBinding);
		return lowercaseFirst(typeName);
	}

	/**
	 * Gets the simple name of a type (without package).
	 * 
	 * @param typeBinding the type binding
	 * @return the simple type name
	 */
	private static String getSimpleTypeName(ITypeBinding typeBinding) {
		if (typeBinding.isPrimitive()) {
			// Capitalize primitives for better readability
			return capitalizeFirst(typeBinding.getName());
		}
		
		// Use simple name for reference types
		String name = typeBinding.getName();
		
		// Remove generic parameters if present (e.g., "List<E>" -> "List")
		int genericStart = name.indexOf('<');
		if (genericStart > 0) {
			name = name.substring(0, genericStart);
		}
		
		return name;
	}

	/**
	 * Capitalizes the first character of a string.
	 * 
	 * @param str the string to capitalize
	 * @return the string with first character capitalized
	 */
	private static String capitalizeFirst(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}

	/**
	 * Lowercases the first character of a string.
	 * 
	 * @param str the string to lowercase
	 * @return the string with first character lowercased
	 */
	private static String lowercaseFirst(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return Character.toLowerCase(str.charAt(0)) + str.substring(1);
	}
}
