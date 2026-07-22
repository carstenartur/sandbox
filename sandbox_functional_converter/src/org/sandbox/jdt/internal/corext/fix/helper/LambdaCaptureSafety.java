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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Validates local-variable capture rules before a loop is rewritten as a lambda.
 *
 * <p>Variables declared inside the original body, the generated lambda parameter,
 * fields, and accumulators removed from the lambda are not external captures. Every
 * remaining local must be explicitly {@code final} or effectively final according
 * to its resolved JDT binding. Incomplete binding identity is rejected rather than
 * allowing a potentially uncompilable cleanup proposal.</p>
 */
final class LambdaCaptureSafety {

	private LambdaCaptureSafety() {
		// utility class
	}

	/**
	 * Returns whether a generated lambda would capture an unsafe local variable.
	 *
	 * @param body original loop body with resolved bindings
	 * @param liftedVariableNames external accumulator names removed from the lambda
	 * @param additionalLocalBindings declarations outside {@code body} that become
	 *            lambda-local, such as an enhanced-for parameter
	 * @return {@code true} when binding identity is incomplete or a captured local is
	 *         neither explicitly final nor effectively final
	 */
	static boolean hasUnsafeCapture(Statement body, Set<String> liftedVariableNames,
			IVariableBinding... additionalLocalBindings) {
		if (body == null) {
			return true;
		}
		Set<String> localBindingKeys= new HashSet<>();
		for (IVariableBinding binding : additionalLocalBindings) {
			if (!addBindingKey(localBindingKeys, binding)) {
				return true;
			}
		}

		AtomicBoolean incompleteBindings= new AtomicBoolean();
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment node) {
				if (!addBindingKey(localBindingKeys, node.resolveBinding())) {
					incompleteBindings.set(true);
				}
				return true;
			}

			@Override
			public boolean visit(SingleVariableDeclaration node) {
				if (!addBindingKey(localBindingKeys, node.resolveBinding())) {
					incompleteBindings.set(true);
				}
				return true;
			}
		});
		if (incompleteBindings.get()) {
			return true;
		}

		Set<String> liftedBindingKeys= resolveLiftedBindingKeys(body, liftedVariableNames);
		AtomicBoolean unsafe= new AtomicBoolean();
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding binding= node.resolveBinding();
				if (!(binding instanceof IVariableBinding variableBinding)) {
					return true;
				}
				IVariableBinding declaration= variableBinding.getVariableDeclaration();
				String key= declaration.getKey();
				if (key == null) {
					unsafe.set(true);
					return false;
				}
				boolean finalLocal= Modifier.isFinal(declaration.getModifiers()) || declaration.isEffectivelyFinal();
				if (!declaration.isField() && !localBindingKeys.contains(key)
						&& !liftedBindingKeys.contains(key) && !finalLocal) {
					unsafe.set(true);
					return false;
				}
				return true;
			}
		});
		return unsafe.get();
	}

	private static Set<String> resolveLiftedBindingKeys(Statement body, Set<String> liftedVariableNames) {
		if (liftedVariableNames.isEmpty()) {
			return Set.of();
		}
		Set<String> result= new HashSet<>();
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (liftedVariableNames.contains(node.getIdentifier())
						&& node.resolveBinding() instanceof IVariableBinding variableBinding) {
					String key= variableBinding.getVariableDeclaration().getKey();
					if (key != null) {
						result.add(key);
					}
				}
				return true;
			}
		});
		return result;
	}

	private static boolean addBindingKey(Set<String> keys, IVariableBinding binding) {
		if (binding == null) {
			return false;
		}
		String key= binding.getVariableDeclaration().getKey();
		if (key == null) {
			return false;
		}
		keys.add(key);
		return true;
	}
}
