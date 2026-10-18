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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.sandbox.jdt.triggerpattern.api.GuardContext.UnknownSemanticRequirement;

/** Shared source-node identity, automatic bindings and pattern constraints. */
final class PatternMatchSupport {

	private PatternMatchSupport() {
	}

	static Match createMatch(ASTNode node, Map<String, Object> bindings, int offset, int length,
			List<UnknownSemanticRequirement> unknownRequirements) {
		Map<String, Object> completeBindings = new HashMap<>(bindings);
		completeBindings.put("$_", node); //$NON-NLS-1$
		for (ASTNode current = node; current != null; current = current.getParent()) {
			if (current instanceof AbstractTypeDeclaration) {
				completeBindings.put("$this", current); //$NON-NLS-1$
				break;
			}
		}
		return new Match(node, completeBindings, offset, length, unknownRequirements);
	}

	static boolean satisfiesConstraints(Pattern pattern, Match match) {
		String overridesType = pattern.getOverridesType();
		if (overridesType != null && !overridesType.isEmpty()
				&& (!(match.getMatchedNode() instanceof MethodDeclaration method)
						|| !checkOverrides(method, overridesType))) {
			return false;
		}
		ConstraintVariableType[] constraints = pattern.getConstraints();
		return constraints == null || checkTypeConstraints(match, constraints);
	}

	/**
	 * Checks whether a match satisfies all type constraints.
	 *
	 * <p>For each constraint, looks up the bound AST node for the placeholder
	 * variable and resolves its {@link ITypeBinding}. The constraint is satisfied
	 * if the resolved type matches the expected type (by simple name or qualified
	 * name). Missing placeholders or unresolved necessary bindings reject the
	 * match, because an explicit semantic constraint must be proven.</p>
	 *
	 * @param match the match to check
	 * @param constraints the type constraints
	 * @return {@code true} if all constraints are satisfied
	 * @since 1.4.0
	 */
	static boolean checkTypeConstraints(Match match, ConstraintVariableType[] constraints) {
		for (ConstraintVariableType constraint : constraints) {
			String variable = constraint.variable();
			String expectedType = constraint.type();

			ASTNode node = match.getBinding(variable);
			if (node == null) {
				return false;
			}

			ITypeBinding typeBinding = resolveTypeBinding(node);
			if (typeBinding == null) {
				return false;
			}

			if (!matchesTypeOrSupertype(typeBinding, expectedType)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Resolves the type binding for an AST node.
	 *
	 * @param node the AST node
	 * @return the type binding, or {@code null} if not resolvable
	 */
	private static ITypeBinding resolveTypeBinding(ASTNode node) {
		if (node instanceof Name name) {
			IBinding binding = name.resolveBinding();
			if (binding instanceof IVariableBinding varBinding) {
				return varBinding.getType();
			}
			if (binding instanceof ITypeBinding typeBinding) {
				return typeBinding;
			}
		}
		if (node instanceof MethodInvocation methodInv) {
			IMethodBinding methodBinding = methodInv.resolveMethodBinding();
			if (methodBinding != null) {
				return methodBinding.getReturnType();
			}
		}
		if (node instanceof Expression expr) {
			return expr.resolveTypeBinding();
		}
		return null;
	}

	/**
	 * Checks if a type binding matches an expected type name (including supertypes).
	 *
	 * @param typeBinding the resolved type binding
	 * @param expectedType the expected type name (simple or qualified)
	 * @return {@code true} if the type matches
	 */
	private static boolean matchesTypeOrSupertype(ITypeBinding typeBinding, String expectedType) {
		return matchesTypeOrSupertype(typeBinding, expectedType, new java.util.HashSet<>());
	}

	/**
	 * Recursively checks if a type binding matches an expected type name,
	 * using a visited set to prevent infinite recursion.
	 */
	private static boolean matchesTypeOrSupertype(ITypeBinding typeBinding, String expectedType,
			java.util.Set<String> visited) {
		if (typeBinding == null || typeBinding.isRecovered()) {
			return false;
		}
		String qualifiedName = typeBinding.getQualifiedName();
		if (!visited.add(qualifiedName)) {
			return false; // Already visited — break potential cycle
		}
		ITypeBinding erasure = typeBinding.getErasure();
		if (typeBinding.getName().equals(expectedType)
				|| qualifiedName.equals(expectedType)
				|| erasure != null && (erasure.getName().equals(expectedType)
						|| erasure.getQualifiedName().equals(expectedType))) {
			return true;
		}
		// Check superclass chain
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (matchesTypeOrSupertype(superclass, expectedType, visited)) {
			return true;
		}
		// Check interfaces
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (matchesTypeOrSupertype(iface, expectedType, visited)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a method declaration overrides a method from the specified type.
	 *
	 * <p>Uses binding resolution to traverse the type hierarchy and find overridden methods.
	 * Returns {@code false} if bindings are not available (e.g., when parsing from raw source).</p>
	 *
	 * @param method the method declaration to check
	 * @param overridesType the fully qualified type name that must declare the overridden method
	 * @return {@code true} if the method overrides a method from the specified type
	 */
	static boolean checkOverrides(MethodDeclaration method, String overridesType) {
		IMethodBinding methodBinding = method.resolveBinding();
		if (methodBinding == null) {
			return false;
		}

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}

		// Walk the superclass chain looking for the overridden method
		ITypeBinding superClass = declaringClass.getSuperclass();
		while (superClass != null) {
			if (overridesType.equals(superClass.getQualifiedName())) {
				// Found the target type - check if it declares a method that is overridden
				for (IMethodBinding superMethod : superClass.getDeclaredMethods()) {
					if (methodBinding.overrides(superMethod)) {
						return true;
					}
				}
				// Target type found but method not declared there - stop searching
				return false;
			}
			superClass = superClass.getSuperclass();
		}

		// Also check interfaces
		return checkOverridesInInterfaces(methodBinding, declaringClass, overridesType);
	}

	/**
	 * Checks if a method overrides a method declared in an interface matching the specified type.
	 *
	 * @param methodBinding the method binding to check
	 * @param typeBinding the declaring type
	 * @param overridesType the target interface type
	 * @return {@code true} if the method overrides a method from a matching interface
	 */
	private static boolean checkOverridesInInterfaces(IMethodBinding methodBinding, ITypeBinding typeBinding,
			String overridesType) {
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (overridesType.equals(iface.getQualifiedName())) {
				// Found the target interface - check if it declares the overridden method
				for (IMethodBinding ifaceMethod : iface.getDeclaredMethods()) {
					if (methodBinding.overrides(ifaceMethod)) {
						return true;
					}
				}
				// Target interface found but method not declared there - stop searching
				return false;
			}
			// Recursively check super-interfaces
			if (checkOverridesInInterfaces(methodBinding, iface, overridesType)) {
				return true;
			}
		}
		return false;
	}

}
