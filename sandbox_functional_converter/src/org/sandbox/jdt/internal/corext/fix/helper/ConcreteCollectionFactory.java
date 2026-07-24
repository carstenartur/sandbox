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

import java.util.Map;

import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.functional.core.terminal.CollectTerminal;

/** Resolves behavior-preserving suppliers for fresh collection accumulators. */
final class ConcreteCollectionFactory {

	private static final Map<String, CollectTerminal.CollectorType> SUPPORTED= Map.of(
			"java.util.ArrayList", CollectTerminal.CollectorType.TO_LIST, //$NON-NLS-1$
			"java.util.LinkedList", CollectTerminal.CollectorType.TO_LIST, //$NON-NLS-1$
			"java.util.HashSet", CollectTerminal.CollectorType.TO_SET, //$NON-NLS-1$
			"java.util.LinkedHashSet", CollectTerminal.CollectorType.TO_SET, //$NON-NLS-1$
			"java.util.TreeSet", CollectTerminal.CollectorType.TO_SET); //$NON-NLS-1$

	private ConcreteCollectionFactory() {
	}

	/**
	 * Adds the proven constructor supplier to a collect terminal. Unsupported or
	 * construction-sensitive declarations fail closed by returning {@code null}.
	 */
	static CollectTerminal preserveFactory(VariableDeclarationStatement declaration, CollectTerminal terminal) {
		String supplier= supplier(declaration, terminal);
		if (supplier == null) {
			return null;
		}
		return new CollectTerminal(terminal.collectorType(), terminal.targetVariable(), supplier);
	}

	/**
	 * Returns a qualified constructor reference for one exactly modeled initializer.
	 * Constructor arguments, anonymous classes, custom implementations and unresolved
	 * bindings fail closed.
	 */
	static String supplier(VariableDeclarationStatement declaration, CollectTerminal terminal) {
		if (declaration == null || terminal == null || declaration.fragments().size() != 1) {
			return null;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) declaration.fragments().get(0);
		Expression initializer= fragment.getInitializer();
		if (!(initializer instanceof ClassInstanceCreation creation)
				|| !creation.arguments().isEmpty()
				|| hasAnonymousClass(creation)) {
			return null;
		}
		ITypeBinding createdType= creation.resolveTypeBinding();
		ITypeBinding declaredType= declaration.getType().resolveBinding();
		if (createdType == null || declaredType == null) {
			return null;
		}
		ITypeBinding createdErasure= createdType.getErasure();
		if (createdErasure == null || declaredType.getErasure() == null) {
			return null;
		}
		String qualifiedName= createdErasure.getQualifiedName();
		CollectTerminal.CollectorType supportedType= SUPPORTED.get(qualifiedName);
		if (supportedType != terminal.collectorType()
				|| !createdType.isAssignmentCompatible(declaredType)) {
			return null;
		}
		return qualifiedName + "::new"; //$NON-NLS-1$
	}

	private static boolean hasAnonymousClass(ClassInstanceCreation creation) {
		AnonymousClassDeclaration anonymous= creation.getAnonymousClassDeclaration();
		return anonymous != null;
	}
}
