/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;

/** Canonical registration entry point for built-in guard functions. */
public final class BuiltInGuardRegistration {

	private BuiltInGuardRegistration() {
	}

	/** Registers built-in guards with strict plan-aware migration guards. */
	public static void registerAll(Map<String, GuardFunction> guards) {
		BuiltInGuards.registerAll(guards);
		guards.put("instanceof", BuiltInGuardRegistration::evaluateInstanceOf); //$NON-NLS-1$
		guards.put("genericTypeIs", BuiltInGuardRegistration::evaluateGenericTypeIs); //$NON-NLS-1$
		guards.put("plannedRole", BuiltInGuardRegistration::evaluatePlannedRole); //$NON-NLS-1$
		guards.put("enclosingPlannedRole", BuiltInGuardRegistration::evaluateEnclosingPlannedRole); //$NON-NLS-1$
	}

	private static boolean evaluatePlannedRole(GuardContext context, Object... args) {
		ASTNode node;
		String role;
		if (args.length == 1) {
			node= context.getMatchedNode();
			role= stripQuotes(argument(args, 0));
		} else if (args.length >= 2) {
			node= context.getBinding(argument(args, 0));
			role= stripQuotes(argument(args, 1));
		} else {
			return false;
		}
		return node != null && context.getSemanticPlan().hasRole(node, role);
	}

	private static boolean evaluateEnclosingPlannedRole(GuardContext context, Object... args) {
		ASTNode node;
		String role;
		if (args.length == 1) {
			node= context.getMatchedNode();
			role= stripQuotes(argument(args, 0));
		} else if (args.length >= 2) {
			node= context.getBinding(argument(args, 0));
			role= stripQuotes(argument(args, 1));
		} else {
			return false;
		}
		return node != null && context.getSemanticPlan().hasEnclosingRole(node, role);
	}

	private static boolean evaluateInstanceOf(GuardContext context, Object... args) {
		if (args.length < 2) {
			return false;
		}
		ASTNode node= context.getBinding(argument(args, 0));
		if (node == null) {
			return false;
		}
		ITypeBinding binding= resolveTypeBinding(node);
		if (binding == null) {
			return true;
		}
		String expectedType= stripQuotes(argument(args, 1));
		if (expectedType.endsWith("[]") && expectedType.length() > 2) { //$NON-NLS-1$
			return binding.isArray() && matchesTypeName(binding.getElementType(),
					expectedType.substring(0, expectedType.length() - 2));
		}
		return matchesTypeName(binding, expectedType);
	}

	private static boolean evaluateGenericTypeIs(GuardContext context, Object... args) {
		if (args.length < 3) {
			return false;
		}
		ASTNode node= context.getBinding(argument(args, 0));
		if (node == null) {
			return false;
		}
		int index;
		try {
			index= Integer.parseInt(argument(args, 1));
		} catch (NumberFormatException e) {
			return false;
		}
		ITypeBinding binding= resolveTypeBinding(node);
		if (binding == null) {
			return true;
		}
		ITypeBinding[] arguments= binding.getTypeArguments();
		return index >= 0 && index < arguments.length
				&& matchesTypeName(arguments[index], stripQuotes(argument(args, 2)));
	}

	private static String argument(Object[] args, int index) {
		if (args == null || index < 0 || index >= args.length || args[index] == null) {
			return ""; //$NON-NLS-1$
		}
		return args[index].toString();
	}

	private static ITypeBinding resolveTypeBinding(ASTNode node) {
		if (node instanceof Expression expression) {
			return expression.resolveTypeBinding();
		}
		if (node instanceof Type type) {
			return type.resolveBinding();
		}
		if (node instanceof SingleVariableDeclaration declaration) {
			IVariableBinding binding= declaration.resolveBinding();
			return binding == null ? declaration.getType().resolveBinding() : binding.getType();
		}
		if (node instanceof VariableDeclarationFragment fragment) {
			IVariableBinding binding= fragment.resolveBinding();
			return binding == null ? null : binding.getType();
		}
		return null;
	}

	private static boolean matchesTypeName(ITypeBinding binding, String expectedType) {
		if (binding == null || expectedType == null || expectedType.isBlank()) {
			return false;
		}
		ITypeBinding declaration= binding.getTypeDeclaration();
		String qualifiedName= declaration.getQualifiedName();
		return expectedType.equals(qualifiedName) || expectedType.equals(declaration.getName());
	}

	private static String stripQuotes(String value) {
		String stripped= value == null ? "" : value.trim(); //$NON-NLS-1$
		if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
			return stripped.substring(1, stripped.length() - 1);
		}
		return stripped;
	}
}
