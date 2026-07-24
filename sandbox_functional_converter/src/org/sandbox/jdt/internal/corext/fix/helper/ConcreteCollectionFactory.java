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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.functional.core.terminal.CollectTerminal;

/** Resolves behavior-preserving suppliers for fresh collection accumulators. */
final class ConcreteCollectionFactory {

	private static final String JAVA_UTIL_LIST= "java.util.List"; //$NON-NLS-1$
	private static final String JAVA_UTIL_SET= "java.util.Set"; //$NON-NLS-1$
	private static final String JAVA_UTIL_TREE_SET_FACTORY= "java.util.TreeSet::new"; //$NON-NLS-1$

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
	 *
	 * <p>For an exact {@link java.util.List} declaration, or an exact
	 * {@link java.util.Set} declaration backed by a modeled hash-based set, whose
	 * value is never referenced after the accumulating loop, the concrete
	 * implementation is not observable. In that case the existing interface
	 * collector remains sufficient and avoids changing established output for a
	 * dead local. A later read, return or subsequent loop keeps the explicit
	 * constructor supplier. {@link java.util.TreeSet} always keeps its supplier:
	 * comparison and null-handling failures remain observable even when the final
	 * set value is not read.</p>
	 */
	static CollectTerminal preserveFactory(VariableDeclarationStatement declaration, CollectTerminal terminal) {
		String supplier= supplier(declaration, terminal);
		if (supplier == null) {
			return null;
		}
		if (canUseInterfaceCollector(declaration, terminal, supplier)) {
			return terminal;
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

	private static boolean canUseInterfaceCollector(VariableDeclarationStatement declaration,
			CollectTerminal terminal, String supplier) {
		if (JAVA_UTIL_TREE_SET_FACTORY.equals(supplier)) {
			return false;
		}
		ITypeBinding declaredType= declaration.getType().resolveBinding();
		ITypeBinding erasure= declaredType == null ? null : declaredType.getErasure();
		if (erasure == null) {
			return false;
		}
		String qualifiedName= erasure.getQualifiedName();
		boolean exactInterface= terminal.collectorType() == CollectTerminal.CollectorType.TO_LIST
				? JAVA_UTIL_LIST.equals(qualifiedName)
				: terminal.collectorType() == CollectTerminal.CollectorType.TO_SET
						&& JAVA_UTIL_SET.equals(qualifiedName);
		return exactInterface && !isReferencedAfterAccumulation(declaration);
	}

	private static boolean isReferencedAfterAccumulation(VariableDeclarationStatement declaration) {
		if (!(declaration.getParent() instanceof Block block) || declaration.fragments().size() != 1) {
			return true;
		}
		VariableDeclarationFragment fragment= (VariableDeclarationFragment) declaration.fragments().get(0);
		IVariableBinding binding= fragment.resolveBinding();
		if (binding == null) {
			return true;
		}
		@SuppressWarnings("unchecked") //$NON-NLS-1$
		List<Statement> statements= block.statements();
		int declarationIndex= statements.indexOf(declaration);
		if (declarationIndex < 0) {
			return true;
		}
		boolean accumulationFound= false;
		for (int index= declarationIndex + 1; index < statements.size(); index++) {
			boolean referencesAccumulator= references(statements.get(index), binding);
			if (!accumulationFound) {
				accumulationFound= referencesAccumulator;
			} else if (referencesAccumulator) {
				return true;
			}
		}
		return !accumulationFound;
	}

	private static boolean references(Statement statement, IVariableBinding target) {
		boolean[] found= { false };
		statement.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding resolved= node.resolveBinding();
				if (resolved instanceof IVariableBinding variable
						&& target.getVariableDeclaration().isEqualTo(variable.getVariableDeclaration())) {
					found[0]= true;
					return false;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static boolean hasAnonymousClass(ClassInstanceCreation creation) {
		AnonymousClassDeclaration anonymous= creation.getAnonymousClassDeclaration();
		return anonymous != null;
	}
}
