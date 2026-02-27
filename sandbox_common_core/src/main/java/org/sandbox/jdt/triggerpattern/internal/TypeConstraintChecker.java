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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Checks type constraints on placeholder bindings.
 * 
 * <p>Type constraints map placeholder variable names to required fully qualified
 * Java types. When bindings are available from a pattern match, this checker
 * validates that each constrained placeholder's bound expression has the
 * expected type.</p>
 * 
 * <p>Type checking is performed using {@link ITypeBinding} when available.
 * The checker supports both exact type matching and subtype matching
 * (i.e., the bound expression's type may be a subtype of the required type).
 * When bindings are not available (e.g., parsing from source strings without
 * a project context), the constraint check is skipped (returns {@code true}).</p>
 * 
 * @since 1.3.3
 */
public final class TypeConstraintChecker {

	private TypeConstraintChecker() {
		// utility class
	}

	/**
	 * Checks whether all type constraints are satisfied by the given bindings.
	 * 
	 * <p>For each entry in {@code typeConstraints}, the corresponding binding is
	 * looked up. If the binding is an {@link Expression} with a resolved
	 * {@link ITypeBinding}, the type is checked against the constraint. If
	 * bindings are not available, the constraint is considered satisfied.</p>
	 * 
	 * @param bindings the placeholder bindings from pattern matching
	 * @param typeConstraints map of placeholder names to required fully qualified type names
	 * @return {@code true} if all constraints are satisfied or cannot be checked
	 */
	public static boolean satisfiesConstraints(Map<String, Object> bindings,
			Map<String, String> typeConstraints) {
		if (typeConstraints == null || typeConstraints.isEmpty()) {
			return true;
		}

		for (Map.Entry<String, String> constraint : typeConstraints.entrySet()) {
			String variable = constraint.getKey();
			String requiredType = constraint.getValue();

			Object binding = bindings.get(variable);
			if (binding == null) {
				// Placeholder not bound — constraint cannot be checked, treat as unsatisfied
				return false;
			}

			if (binding instanceof Expression expr) {
				if (!checkExpressionType(expr, requiredType)) {
					return false;
				}
			} else if (binding instanceof ASTNode) {
				// Non-expression AST nodes (e.g., statements) cannot be type-checked
				// with ITypeBinding — skip this constraint
				continue;
			}
		}

		return true;
	}

	/**
	 * Checks if the expression's resolved type matches the required type.
	 * 
	 * <p>Returns {@code true} if:</p>
	 * <ul>
	 *   <li>The expression has no resolved type binding (bindings not available)</li>
	 *   <li>The expression's type exactly matches the required type</li>
	 *   <li>The expression's type is assignment-compatible with the required type
	 *       (i.e., is a subtype of the required type)</li>
	 * </ul>
	 * 
	 * @param expr the expression to check
	 * @param requiredType the required fully qualified type name
	 * @return {@code true} if the type matches or cannot be determined
	 */
	private static boolean checkExpressionType(Expression expr, String requiredType) {
		ITypeBinding typeBinding = expr.resolveTypeBinding();
		if (typeBinding == null) {
			// No binding available — cannot check, treat as satisfied
			return true;
		}

		// Check exact match first
		String qualifiedName = typeBinding.getQualifiedName();
		if (requiredType.equals(qualifiedName)) {
			return true;
		}

		// Check subtype relationship (assignability)
		return isAssignableTo(typeBinding, requiredType);
	}

	/**
	 * Checks if a type is assignable to the required type by walking the type hierarchy.
	 * 
	 * @param typeBinding the type to check
	 * @param requiredType the required fully qualified type name
	 * @return {@code true} if the type is assignable to the required type
	 */
	private static boolean isAssignableTo(ITypeBinding typeBinding, String requiredType) {
		// Check superclass chain
		ITypeBinding superClass = typeBinding.getSuperclass();
		while (superClass != null) {
			if (requiredType.equals(superClass.getQualifiedName())) {
				return true;
			}
			superClass = superClass.getSuperclass();
		}

		// Check interfaces
		return implementsInterface(typeBinding, requiredType);
	}

	/**
	 * Recursively checks if a type implements the specified interface.
	 * 
	 * @param typeBinding the type to check
	 * @param requiredType the required interface type
	 * @return {@code true} if the type implements the required interface
	 */
	private static boolean implementsInterface(ITypeBinding typeBinding, String requiredType) {
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (requiredType.equals(iface.getQualifiedName())) {
				return true;
			}
			if (implementsInterface(iface, requiredType)) {
				return true;
			}
		}
		// Also check superclass interfaces
		ITypeBinding superClass = typeBinding.getSuperclass();
		if (superClass != null) {
			return implementsInterface(superClass, requiredType);
		}
		return false;
	}
}
