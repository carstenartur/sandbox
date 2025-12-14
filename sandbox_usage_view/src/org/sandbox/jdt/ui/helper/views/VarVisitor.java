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
import org.eclipse.jdt.core.dom.SimpleName;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Visitor class that collects all variable bindings from SimpleName nodes
 * using the AstProcessorBuilder API for cleaner and more maintainable code.
 */
final class VarVisitor {
	/**
	 *
	 */
	private final JHViewContentProvider varvisitor;
	Set<IVariableBinding> methods= new HashSet<>();

	/**
	 * @param jhViewContentProvider
	 */
	VarVisitor(JHViewContentProvider jhViewContentProvider) {
		varvisitor= jhViewContentProvider;
	}

	/**
	 * Process the AST node and collect all variable bindings.
	 * Uses AstProcessorBuilder API for processing SimpleName nodes.
	 * 
	 * @param node the AST node to process
	 */
	public void process(ASTNode node) {
		AstProcessorBuilder.with(new ReferenceHolder<String, Object>())
			.processor()
			.callSimpleNameVisitor((simpleName, dataHolder) -> {
				IBinding binding = simpleName.resolveBinding();
				if (binding instanceof IVariableBinding varBinding) {
					methods.add(varBinding);
				}
				return true;
			})
			.build(node);
	}

	public Set<IVariableBinding> getVars() {
		return methods;
	}
}