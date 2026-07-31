/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;

import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/**
 * Fail-closed identity check for qualified annotation removals.
 *
 * <p>The standard action accepts simple annotation names for deliberately
 * source-oriented rules. A qualified action argument, however, is an exact
 * semantic contract and must never remove another annotation that merely has
 * the same simple name.</p>
 */
final class StructuredRewriteAnnotationGuard {

	enum AnnotationMatch {
		EXACT,
		COLLISION,
		DIFFERENT
	}

	private StructuredRewriteAnnotationGuard() {
	}

	static void validate(StructuredRewriteAction action,
			StructuredRewriteActionContext context) throws CoreException {
		if (!"removeAnnotation".equals(action.name())) { //$NON-NLS-1$
			return;
		}
		String expected= context.resolveString(action.requiredArgument("annotation")); //$NON-NLS-1$
		if (expected.indexOf('.') < 0) {
			return;
		}
		BodyDeclaration declaration= declarationTarget(context, action);
		for (Object modifier : declaration.modifiers()) {
			if (modifier instanceof Annotation annotation) {
				AnnotationMatch match= classifyAnnotation(annotation, expected);
				if (match == AnnotationMatch.EXACT) {
					return;
				}
				if (match == AnnotationMatch.COLLISION) {
					throw context.failure("Refusing to remove annotation " + expected //$NON-NLS-1$
							+ " because a different annotation with the same simple name is selected"); //$NON-NLS-1$
				}
			}
		}
		throw context.failure("Structured action cannot find annotation " + expected); //$NON-NLS-1$
	}

	private static BodyDeclaration declarationTarget(StructuredRewriteActionContext context,
			StructuredRewriteAction action) throws CoreException {
		ASTNode current= context.resolveAuthorizedNode(action.requiredArgument("target")); //$NON-NLS-1$
		while (current != null) {
			if (current instanceof BodyDeclaration declaration) {
				if (declaration instanceof FieldDeclaration field && field.fragments().size() != 1) {
					throw context.failure(
							"Declaration-wide action is ambiguous for a multi-fragment field"); //$NON-NLS-1$
				}
				return declaration;
			}
			current= current.getParent();
		}
		throw context.failure("Structured action target is not a Java declaration"); //$NON-NLS-1$
	}

	private static AnnotationMatch classifyAnnotation(Annotation annotation, String expected) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		String resolvedName= binding == null ? null : binding.getQualifiedName();
		return classifyAnnotationName(expected, annotation.getTypeName().getFullyQualifiedName(),
				resolvedName);
	}

	static AnnotationMatch classifyAnnotationName(String expected, String sourceName,
			String resolvedName) {
		if (expected == null || expected.isBlank() || sourceName == null || sourceName.isBlank()) {
			return AnnotationMatch.DIFFERENT;
		}
		String actual= resolvedName == null || resolvedName.isBlank() ? sourceName : resolvedName;
		if (expected.equals(actual)) {
			return AnnotationMatch.EXACT;
		}
		return simpleName(expected).equals(simpleName(actual))
				? AnnotationMatch.COLLISION : AnnotationMatch.DIFFERENT;
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}
}
