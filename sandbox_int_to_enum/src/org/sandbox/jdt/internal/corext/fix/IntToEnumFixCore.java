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
package org.sandbox.jdt.internal.corext.fix;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.helper.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper.IntConstantHolder;
import org.sandbox.jdt.internal.corext.fix.helper.SwitchIntToEnumHelper;
import org.sandbox.jdt.internal.ui.fix.MultiFixMessages;

/**
 * Enum containing different types of int to enum transformations.
 */
public enum IntToEnumFixCore {
	/**
	 * Convert if-else chains using int constants to switch with enum.
	 */
	IF_ELSE_TO_SWITCH(new IntToEnumHelper()),

	/**
	 * Convert switch statements using int constants to switch with enum.
	 */
	SWITCH_INT_TO_ENUM(new SwitchIntToEnumHelper());

	AbstractTool<ReferenceHolder<Integer, IntConstantHolder>> intToEnumHelper;

	@SuppressWarnings("unchecked")
	IntToEnumFixCore(AbstractTool<? extends ReferenceHolder<Integer, IntConstantHolder>> helper) {
		this.intToEnumHelper = (AbstractTool<ReferenceHolder<Integer, IntConstantHolder>>) helper;
	}

	public String getPreview(boolean enabled) {
		return intToEnumHelper.getPreview(enabled);
	}

	/**
	 * Find operations for this transformation type.
	 *
	 * @param compilationUnit The compilation unit to search
	 * @param operations Set to add operations to
	 * @param nodesProcessed Set of already processed nodes
	 */
	public void findOperations(final CompilationUnit compilationUnit,
			final Set<CompilationUnitRewriteOperationWithSourceRange> operations,
			final Set<ASTNode> nodesProcessed) {
		intToEnumHelper.find(this, compilationUnit, operations, nodesProcessed);
	}

	public CompilationUnitRewriteOperationWithSourceRange rewrite(final ReferenceHolder<Integer, IntConstantHolder> hit) {
		return new CompilationUnitRewriteOperationWithSourceRange() {
			@Override
			public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel)
					throws CoreException {
				TextEditGroup group = createTextEditGroup(MultiFixMessages.IntToEnumCleanUp_description, cuRewrite);
				TightSourceRangeComputer rangeComputer;
				ASTRewrite rewrite = cuRewrite.getASTRewrite();
				if (rewrite.getExtendedSourceRangeComputer() instanceof TightSourceRangeComputer) {
					rangeComputer = (TightSourceRangeComputer) rewrite.getExtendedSourceRangeComputer();
				} else {
					rangeComputer = new TightSourceRangeComputer();
				}

				// Get the first IntConstantHolder from the hit map
				IntConstantHolder holder = hit.values().stream().findFirst().orElse(null);
				if (holder != null) {
					recoverLocalCallSites(holder, cuRewrite.getRoot());
					if (holder.switchStatement != null) {
						rangeComputer.addTightSourceNode(holder.switchStatement);
					}
					if (holder.ifStatement != null) {
						rangeComputer.addTightSourceNode(holder.ifStatement);
					}
				}

				rewrite.setTargetSourceRangeComputer(rangeComputer);
				intToEnumHelper.rewrite(IntToEnumFixCore.this, hit, cuRewrite, group);
			}
		};
	}

	/**
	 * Recovers an unqualified, unambiguous local call site when a save-action AST
	 * carries a temporarily stale method or constant binding. Candidate discovery
	 * has already proven the private method and enum-like constants; this final
	 * structural pass only fills a missing replacement within the same top-level
	 * type. Qualified calls, overloads, nested types and shadowed constants remain
	 * fail-closed.
	 */
	private static void recoverLocalCallSites(IntConstantHolder holder, CompilationUnit compilationUnit) {
		if (holder.method == null || holder.enclosingType == null || holder.method.parameters().size() != 1) {
			return;
		}
		String methodName = holder.method.getName().getIdentifier();
		for (MethodDeclaration method : holder.enclosingType.getMethods()) {
			if (method != holder.method && method.getName().getIdentifier().equals(methodName)
					&& method.parameters().size() == 1) {
				return;
			}
		}

		Set<String> constantNames = new HashSet<>(holder.constantNames);
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (invocation.getExpression() != null
						|| !invocation.getName().getIdentifier().equals(methodName)
						|| invocation.arguments().size() != 1
						|| findEnclosingType(invocation) != holder.enclosingType) {
					return true;
				}
				MethodDeclaration caller = findEnclosingMethod(invocation);
				if (caller == null || caller.getParent() != holder.enclosingType) {
					return true;
				}
				Expression argument = unparenthesize((Expression) invocation.arguments().get(0));
				if (!(argument instanceof SimpleName simpleName)
						|| !constantNames.contains(simpleName.getIdentifier())
						|| hasLocalDeclarationNamed(caller, simpleName.getIdentifier())) {
					return true;
				}
				holder.constantReferences.putIfAbsent(argument, simpleName.getIdentifier());
				return true;
			}
		});
	}

	private static Expression unparenthesize(Expression expression) {
		Expression current = expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current = parenthesized.getExpression();
		}
		return current;
	}

	private static boolean hasLocalDeclarationNamed(MethodDeclaration method, String identifier) {
		boolean[] found = { false };
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SingleVariableDeclaration declaration) {
				if (declaration.getName().getIdentifier().equals(identifier)) {
					found[0] = true;
					return false;
				}
				return !found[0];
			}

			@Override
			public boolean visit(VariableDeclarationFragment declaration) {
				if (declaration.getName().getIdentifier().equals(identifier)) {
					found[0] = true;
					return false;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	private static MethodDeclaration findEnclosingMethod(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
			current = current.getParent();
		}
		return null;
	}

	private static TypeDeclaration findEnclosingType(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof TypeDeclaration type) {
				return type;
			}
			current = current.getParent();
		}
		return null;
	}

	@Override
	public String toString() {
		return name();
	}
}
