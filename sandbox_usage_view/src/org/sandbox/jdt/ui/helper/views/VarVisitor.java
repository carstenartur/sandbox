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

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;

final class VarVisitor extends ASTVisitor {
	/**
	 *
	 */
	private final JHViewContentProvider varvisitor;

	/**
	 * @param jhViewContentProvider
	 */
	VarVisitor(JHViewContentProvider jhViewContentProvider) {
		varvisitor= jhViewContentProvider;
	}

	Set<IVariableBinding> methods= new HashSet<>();

	@Override
	public boolean visit(SimpleName node) {
		IBinding binding= node.resolveBinding();
		if (binding instanceof IVariableBinding) {
			IVariableBinding varBinding= (IVariableBinding) binding;
			methods.add(varBinding);
		}
		return true;
	}

	public Set<IVariableBinding> getVars() {
		return methods;
	}
}