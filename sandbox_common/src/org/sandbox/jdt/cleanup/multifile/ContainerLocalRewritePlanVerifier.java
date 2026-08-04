/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.Objects;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;

/** Revalidates allowed non-editing occurrences retained by a local rewrite plan. */
final class ContainerLocalRewritePlanVerifier {

	private static final String PLUGIN_ID= "sandbox_common"; //$NON-NLS-1$

	private ContainerLocalRewritePlanVerifier() {
	}

	static void verifyEncounterIterations(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		int expected= Math.toIntExact(plan.edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ENCOUNTER_ITERATION)
				.count());
		int[] actual= { 0 };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(EnhancedForStatement node) {
				IVariableBinding binding= variableBinding(node.getExpression());
				if (binding != null
						&& plan.bindingKey().equals(
								binding.getVariableDeclaration().getKey())) {
					actual[0]++;
				}
				return true;
			}
		});
		if (actual[0] != expected) {
			throw new CoreException(new Status(
					IStatus.ERROR,
					PLUGIN_ID,
					"Container rewrite plan is stale for " + unit.getElementName() //$NON-NLS-1$
							+ ": encounter iteration occurrence count changed")); //$NON-NLS-1$
		}
	}

	private static IVariableBinding variableBinding(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		if (current instanceof SimpleName name
				&& name.resolveBinding() instanceof IVariableBinding variable) {
			return variable;
		}
		return null;
	}
}
