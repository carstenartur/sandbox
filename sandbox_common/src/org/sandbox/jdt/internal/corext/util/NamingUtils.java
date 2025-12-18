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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Utility class for naming and string operations.
 * Provides methods for class name generation, type name extraction,
 * and string transformations needed during JUnit migration.
 */
public final class NamingUtils {

	/** Length in hexadecimal characters of the checksum used for generated nested class names to ensure uniqueness */
	private static final int GENERATED_CLASS_NAME_CHECKSUM_LENGTH = 5;

	private NamingUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Capitalizes the first letter of a string.
	 * 
	 * @param input the string to capitalize
	 * @return the string with first letter capitalized, or the original string if null or empty
	 */
	public static String capitalizeFirstLetter(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		return Character.toUpperCase(input.charAt(0)) + input.substring(1);
	}

	/**
	 * Generates a unique nested class name based on the anonymous class content and field name.
	 * Uses a checksum of the class code to ensure uniqueness.
	 * 
	 * @param anonymousClass the anonymous class declaration
	 * @param baseName the base name from the field
	 * @return a unique class name combining capitalized base name and checksum
	 */
	public static String generateUniqueNestedClassName(AnonymousClassDeclaration anonymousClass, String baseName) {
		// Convert anonymous class to string for checksum generation to ensure unique naming
		String anonymousCode = anonymousClass.toString();
		String checksum = generateChecksum(anonymousCode);

		// Capitalize field name for class naming convention
		String capitalizedBaseName = capitalizeFirstLetter(baseName);

		return capitalizedBaseName + "_" + checksum; //$NON-NLS-1$
	}

	/**
	 * Generates a short SHA-256 checksum for the given input.
	 * 
	 * @param input the string to hash
	 * @return a 5-character hexadecimal checksum
	 * @throws RuntimeException if SHA-256 algorithm is not available (should never happen in standard JVM environments)
	 */
	public static String generateChecksum(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256"); //$NON-NLS-1$
			byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hashBytes) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString().substring(0, GENERATED_CLASS_NAME_CHECKSUM_LENGTH);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 algorithm not found", e); //$NON-NLS-1$
		}
	}

	/**
	 * Extracts the class name from a field declaration's initializer.
	 * 
	 * @param field the field declaration to extract from
	 * @return the class name, or null if not found
	 */
	public static String extractClassNameFromField(FieldDeclaration field) {
		for (Object fragmentObj : field.fragments()) {
			if (fragmentObj instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
				if (fragment.getInitializer() instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation) {
					return extractTypeName(
							((org.eclipse.jdt.core.dom.ClassInstanceCreation) fragment.getInitializer()).getType());
				}
			}
		}
		return null;
	}

	/**
	 * Extracts the field name from a field declaration.
	 * 
	 * @param fieldDeclaration the field declaration
	 * @return the field name, or "UnnamedField" if not found
	 */
	public static String extractFieldName(FieldDeclaration fieldDeclaration) {
		return (String) fieldDeclaration.fragments().stream()
				.filter(VariableDeclarationFragment.class::isInstance)
				.map(fragment -> ((VariableDeclarationFragment) fragment).getName().getIdentifier())
				.findFirst()
				.orElse("UnnamedField"); //$NON-NLS-1$
	}

	/**
	 * Extracts the fully qualified type name from a QualifiedType AST node.
	 * 
	 * @param qualifiedType the qualified type to extract from
	 * @return the fully qualified class name
	 */
	public static String extractQualifiedTypeName(QualifiedType qualifiedType) {
		StringBuilder fullClassName = new StringBuilder();
		Type currentType = qualifiedType;

		while (currentType instanceof QualifiedType) {
			QualifiedType currentQualified = (QualifiedType) currentType;
			if (fullClassName.length() > 0) {
				fullClassName.insert(0, "."); //$NON-NLS-1$
			}
			fullClassName.insert(0, currentQualified.getName().getFullyQualifiedName());
			currentType = currentQualified.getQualifier();
		}
		return fullClassName.toString();
	}

	/**
	 * General method to extract a type's fully qualified name.
	 * 
	 * @param type the type to extract from
	 * @return the type name, or null if not a recognized type
	 */
	public static String extractTypeName(Type type) {
		if (type instanceof QualifiedType) {
			return extractQualifiedTypeName((QualifiedType) type);
		} else if (type instanceof SimpleType) {
			return ((SimpleType) type).getName().getFullyQualifiedName();
		}
		return null;
	}

	/**
	 * Converts a string to UpperCamelCase (PascalCase).
	 * Handles various input formats including snake_case, kebab-case, and space-separated words.
	 * 
	 * @param input the string to convert
	 * @return the string in UpperCamelCase format, or the original string if null or empty
	 */
	public static String toUpperCamelCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		
		// Split by common separators: underscore, hyphen, space
		String[] words = input.split("[_\\-\\s]+"); //$NON-NLS-1$
		StringBuilder result = new StringBuilder();
		
		for (String word : words) {
			if (!word.isEmpty()) {
				result.append(Character.toUpperCase(word.charAt(0)));
				result.append(word.substring(1).toLowerCase());
			}
		}
		
		return result.toString();
	}

	/**
	 * Converts a string to lowerCamelCase.
	 * Handles various input formats including snake_case, kebab-case, and space-separated words.
	 * 
	 * @param input the string to convert
	 * @return the string in lowerCamelCase format, or the original string if null or empty
	 */
	public static String toLowerCamelCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		
		String upperCamel = toUpperCamelCase(input);
		if (upperCamel.isEmpty()) {
			return upperCamel;
		}
		
		return Character.toLowerCase(upperCamel.charAt(0)) + upperCamel.substring(1);
	}

	/**
	 * Converts a string to snake_case.
	 * Handles CamelCase, PascalCase, and other input formats.
	 * 
	 * @param input the string to convert
	 * @return the string in snake_case format, or the original string if null or empty
	 */
	public static String toSnakeCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		
		// Replace common separators with underscores first
		String normalized = input.replaceAll("[\\-\\s]+", "_"); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Insert underscores before uppercase letters (for CamelCase)
		String result = normalized.replaceAll("([a-z])([A-Z])", "$1_$2"); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Convert to lowercase
		return result.toLowerCase();
	}

	/**
	 * Checks if a string is a valid Java identifier.
	 * A valid identifier starts with a letter, underscore, or dollar sign,
	 * and can contain letters, digits, underscores, or dollar signs.
	 * Reserved keywords are considered invalid identifiers.
	 * 
	 * @param name the string to check
	 * @return true if the string is a valid Java identifier, false otherwise
	 */
	public static boolean isValidJavaIdentifier(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		
		// Check if it's a reserved keyword
		if (isJavaKeyword(name)) {
			return false;
		}
		
		// Check first character
		if (!Character.isJavaIdentifierStart(name.charAt(0))) {
			return false;
		}
		
		// Check remaining characters
		for (int i = 1; i < name.length(); i++) {
			if (!Character.isJavaIdentifierPart(name.charAt(i))) {
				return false;
			}
		}
		
		return true;
	}

	/**
	 * Checks if a string is a Java reserved keyword.
	 * 
	 * @param word the string to check
	 * @return true if the string is a Java keyword, false otherwise
	 */
	private static boolean isJavaKeyword(String word) {
		// Java keywords and reserved words (including literal values)
		return switch (word) {
			case "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", 
			     "class", "const", "continue", "default", "do", "double", "else", "enum", 
			     "extends", "final", "finally", "float", "for", "goto", "if", "implements", 
			     "import", "instanceof", "int", "interface", "long", "native", "new", "package", 
			     "private", "protected", "public", "return", "short", "static", "strictfp", 
			     "super", "switch", "synchronized", "this", "throw", "throws", "transient", 
			     "try", "void", "volatile", "while", "true", "false", "null", 
			     "var", "yield", "record", "sealed", "permits", "non-sealed" -> true;
			default -> false;
		};
	}
}
