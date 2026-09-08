/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;

/** Immutable value and declared type of a supported closed state constant. */
public record EnumConstantValue(String typeName, Object value) {

	private static final Set<String> INTEGRAL_TYPES= Set.of("byte", "short", "char", "int", "long"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	private static final String STRING= "java.lang.String"; //$NON-NLS-1$

	public EnumConstantValue {
		if (!(INTEGRAL_TYPES.contains(typeName) && value instanceof Long
				|| STRING.equals(typeName) && value instanceof String)) {
			throw new IllegalArgumentException("Unsupported enum constant value"); //$NON-NLS-1$
		}
	}

	/** Resolves a compile-time constant without narrowing a long value to int. */
	public static EnumConstantValue from(IVariableBinding binding) {
		if (binding == null || binding.isRecovered() || !binding.isField()
				|| !Modifier.isStatic(binding.getModifiers()) || !Modifier.isFinal(binding.getModifiers())) {
			return null;
		}
		ITypeBinding type= binding.getType();
		if (!supports(type)) {
			return null;
		}
		Object constant= binding.getConstantValue();
		String name= type.getQualifiedName();
		if (STRING.equals(name)) {
			return constant instanceof String ? new EnumConstantValue(name, constant) : null;
		}
		if (constant instanceof Character character) {
			return new EnumConstantValue(name, Long.valueOf(character.charValue()));
		}
		return constant instanceof Byte || constant instanceof Short || constant instanceof Integer || constant instanceof Long
				? new EnumConstantValue(name, Long.valueOf(((Number) constant).longValue())) : null;
	}

	public static boolean supports(Type type) {
		return type != null && supports(type.resolveBinding());
	}

	/** Replacing a constant must not discard an evaluated receiver or its exceptions. */
	public static boolean isDirectReference(Expression expression) {
		while (expression instanceof ParenthesizedExpression parenthesized) {
			expression= parenthesized.getExpression();
		}
		return expression instanceof SimpleName
				|| expression instanceof QualifiedName name && name.getQualifier().resolveBinding() instanceof ITypeBinding
				|| expression instanceof FieldAccess access && access.getExpression() instanceof ThisExpression;
	}

	private static boolean supports(ITypeBinding type) {
		return type != null && !type.isRecovered()
				&& (type.isPrimitive() && INTEGRAL_TYPES.contains(type.getQualifiedName())
						|| STRING.equals(type.getQualifiedName()));
	}

	/** A domain never mixes declared types or silently changes numeric promotion. */
	public boolean matches(ITypeBinding type) {
		return supports(type) && typeName.equals(type.getQualifiedName());
	}
}
