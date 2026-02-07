/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
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
package org.sandbox.jdt.ui.helper.views;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Visitor class that collects all variable bindings from SimpleName nodes
 * using the AstProcessorBuilder API for cleaner and more maintainable code.
 */
public final class VariableBindingVisitor {
	private Set<IVariableBinding> collectedVariableBindings = new HashSet<>();

	/**
	 * Process the AST node and collect all variable bindings.
	 * Uses AstProcessorBuilder API for processing SimpleName nodes.
	 * 
	 * @param astNode the AST node to process
	 */
	public void process(ASTNode astNode) {
		AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
			.onSimpleName((simpleName, dataHolder) -> {
				IBinding binding = simpleName.resolveBinding();
				if (binding instanceof IVariableBinding variableBinding) {
					collectedVariableBindings.add(variableBinding);
				}
				return true;
			})
			.build(astNode);
	}

	/**
	 * Returns the set of collected variable bindings.
	 * 
	 * @return set of IVariableBinding instances found during processing
	 */
	public Set<IVariableBinding> getVariableBindings() {
		return collectedVariableBindings;
	}
	
	/**
	 * Returns the set of collected variable bindings.
	 * Alias for {@link #getVariableBindings()} for convenience.
	 * 
	 * @return set of IVariableBinding instances found during processing
	 */
	public Set<IVariableBinding> getVars() {
		return collectedVariableBindings;
	}
}
