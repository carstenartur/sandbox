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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

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
			VariableDeclarationFragment fragment = ASTNodes.as(fragmentObj, VariableDeclarationFragment.class);
			if (fragment != null) {
				ClassInstanceCreation creation = ASTNodes.as(fragment.getInitializer(), ClassInstanceCreation.class);
				if (creation != null) {
					return extractTypeName(creation.getType());
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

		while (currentType != null) {
			QualifiedType currentQualified = ASTNodes.as(currentType, QualifiedType.class);
			if (currentQualified == null) {
				break;
			}
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
		QualifiedType qualifiedType = ASTNodes.as(type, QualifiedType.class);
		if (qualifiedType != null) {
			return extractQualifiedTypeName(qualifiedType);
		}
		SimpleType simpleType = ASTNodes.as(type, SimpleType.class);
		if (simpleType != null) {
			return simpleType.getName().getFullyQualifiedName();
		}
		return null;
	}
}
