/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/** Shared constructor eligibility check for flow planning and stale-plan validation. */
public final class ContainerConstructorTarget {
	private ContainerConstructorTarget() {
	}

	/** Whether this constructor can be considered for a coordinated parameter rewrite. */
	public static boolean isClosed(ClassInstanceCreation creation) {
		IMethodBinding target= creation.resolveConstructorBinding();
		if (target == null || target.isRecovered() || target.isVarargs() || target.isGenericMethod()
				|| creation.getAnonymousClassDeclaration() != null || !Modifier.isPrivate(target.getModifiers())) {
			return false;
		}
		ITypeBinding type= target.getDeclaringClass();
		if (type == null || type.isRecovered() || !type.isFromSource() || type.isParameterizedType()
				|| type.isGenericType() || type.isRawType() || type.isLocal() || type.isAnonymous()) {
			return false;
		}
		for (IMethodBinding other : type.getDeclaredMethods()) {
			if (other.isConstructor() && !other.getMethodDeclaration().isEqualTo(target.getMethodDeclaration())) {
				return false;
			}
		}
		return true;
	}
}
