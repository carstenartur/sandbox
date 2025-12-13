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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/**
 * Utility class for annotation-related operations.
 * Provides methods for checking, finding, and validating annotations
 * commonly needed during JUnit migration.
 */
public final class AnnotationUtils {

	private AnnotationUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Checks if a field has the specified annotation.
	 * 
	 * @param field the field declaration to check
	 * @param annotationClass the fully qualified annotation class name
	 * @return true if the field has the annotation
	 */
	public static boolean isFieldAnnotatedWith(FieldDeclaration field, String annotationClass) {
		return hasAnnotation(field.modifiers(), annotationClass);
	}

	/**
	 * Checks if a body declaration has the specified annotation.
	 * 
	 * @param declaration the body declaration to check
	 * @param annotationClass the fully qualified annotation class name
	 * @return true if the declaration has the annotation
	 */
	public static boolean isAnnotatedWith(BodyDeclaration declaration, String annotationClass) {
		return hasAnnotation(declaration.modifiers(), annotationClass);
	}

	/**
	 * Checks if a list of modifiers contains an annotation with the specified qualified name.
	 * 
	 * @param modifiers the list of modifiers to check
	 * @param annotationClass the fully qualified annotation class name
	 * @return true if the annotation is present
	 */
	static boolean hasAnnotation(List<?> modifiers, String annotationClass) {
		return modifiers.stream()
				.filter(Annotation.class::isInstance)
				.map(Annotation.class::cast)
				.map(Annotation::resolveTypeBinding)
				.anyMatch(binding -> binding != null && annotationClass.equals(binding.getQualifiedName()));
	}

	/**
	 * Checks if the given modifiers list contains an annotation with the specified simple name.
	 * 
	 * @param modifiers the list of modifiers
	 * @param annotationSimpleName the simple name of the annotation
	 * @return true if the annotation is present
	 */
	public static boolean hasAnnotationBySimpleName(List<?> modifiers, String annotationSimpleName) {
		return modifiers.stream()
				.anyMatch(modifier -> modifier instanceof Annotation && ((Annotation) modifier).getTypeName()
						.getFullyQualifiedName().equals(annotationSimpleName));
	}

	/**
	 * Checks if a field has the static modifier.
	 * 
	 * @param field the field declaration to check
	 * @return true if the field is static
	 */
	public static boolean isFieldStatic(FieldDeclaration field) {
		return hasModifier(field.modifiers(), Modifier.ModifierKeyword.STATIC_KEYWORD);
	}

	/**
	 * Checks if a list of modifiers contains the specified modifier keyword.
	 * 
	 * @param modifiers the list of modifiers
	 * @param keyword the modifier keyword to check for
	 * @return true if the modifier is present
	 */
	static boolean hasModifier(List<?> modifiers, Modifier.ModifierKeyword keyword) {
		return modifiers.stream()
				.filter(Modifier.class::isInstance)
				.map(Modifier.class::cast)
				.anyMatch(modifier -> modifier.getKeyword().equals(keyword));
	}
}
