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
package org.sandbox.jdt.internal.corext.fix;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNWITH;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.internal.corext.fix.helper.ParameterizedTestJUnitPlugin;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Restricts the local Parameterized rewrite to the provider contract it can
 * transform atomically.
 */
@CleanupPattern(value = "@RunWith($runner)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_RUNWITH,
		cleanupId = "cleanup.junit.parameterized",
		description = "Migrate @RunWith(Parameterized.class) to @ParameterizedTest",
		displayName = "JUnit 4 @RunWith(Parameterized) → JUnit 5 @ParameterizedTest")
final class ClosedParameterizedTestJUnitPlugin extends ParameterizedTestJUnitPlugin {

	private static final String PARAMETERS_ANNOTATION= "org.junit.runners.Parameterized.Parameters"; //$NON-NLS-1$

	@Override
	protected JunitHolder createHolder(Match match) {
		JunitHolder holder= super.createHolder(match);
		if (holder == null || !(holder.getAdditionalInfo() instanceof TypeDeclaration type)) {
			return holder;
		}
		return hasLocalParametersProvider(type) ? holder : null;
	}

	private static boolean hasLocalParametersProvider(TypeDeclaration type) {
		for (MethodDeclaration method : type.getMethods()) {
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation && isParametersAnnotation(annotation)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isParametersAnnotation(Annotation annotation) {
		ITypeBinding binding= annotation.resolveTypeBinding();
		if (binding != null) {
			return PARAMETERS_ANNOTATION.equals(binding.getQualifiedName());
		}
		String name= annotation.getTypeName().getFullyQualifiedName();
		return "Parameters".equals(name) || PARAMETERS_ANNOTATION.equals(name); //$NON-NLS-1$
	}
}
