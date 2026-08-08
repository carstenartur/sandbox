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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
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
				if (IntToEnumFixCore.this == IF_ELSE_TO_SWITCH && !prepareCompleteLocalRewrite(cuRewrite.getRoot(), hit)) {
					return;
				}

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
	 * Performs a final binding-independent safety pass immediately before the
	 * local int-to-enum rewrite is emitted. Save actions may occasionally provide
	 * an AST in which a call-site binding is unavailable even though declaration
	 * and condition bindings were resolved. The detector must never interpret
	 * such an unresolved reference as proof that no reference exists: doing so can
	 * remove the integer constants and change the method signature while leaving
	 * an integer call site behind.
	 *
	 * <p>The safety pass therefore proves complete source coverage again. A simple,
	 * unqualified call to the unique local private method can be recovered without
	 * a binding when its argument is an unshadowed candidate constant. Every other
	 * unresolved or untracked use causes the whole operation to be skipped. This
	 * makes the transformation atomic and fail-closed.</p>
	 */
	private static boolean prepareCompleteLocalRewrite(CompilationUnit root,
			ReferenceHolder<Integer, IntConstantHolder> hit) {
		for (IntConstantHolder holder : hit.values()) {
			if (!prepareCompleteLocalRewrite(root, holder)) {
				return false;
			}
		}
		return true;
	}

	private static boolean prepareCompleteLocalRewrite(CompilationUnit root, IntConstantHolder holder) {
		if (holder == null || holder.method == null || holder.parameter == null || holder.enclosingType == null
				|| holder.constantNames.size() < 2) {
			return false;
		}

		int parameterIndex = holder.method.parameters().indexOf(holder.parameter);
		if (parameterIndex < 0) {
			return false;
		}

		AtomicBoolean valid = new AtomicBoolean(true);
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (!valid.get()) {
					return false;
				}
				if (!isCandidateInvocation(node, holder)) {
					return true;
				}
				if (parameterIndex >= node.arguments().size()) {
					valid.set(false);
					return false;
				}

				Expression argument = unparenthesize((Expression) node.arguments().get(parameterIndex));
				if (holder.constantReferences.containsKey(argument)) {
					return true;
				}

				String constantName = recoverUnresolvedConstantName(argument, holder);
				if (constantName == null) {
					valid.set(false);
					return false;
				}
				holder.constantReferences.put(argument, constantName);
				return true;
			}
		});
		if (!valid.get()) {
			return false;
		}

		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (!valid.get()) {
					return false;
				}

				String identifier = node.getIdentifier();
				if (holder.constantNames.contains(identifier)) {
					if (isCandidateFieldDeclaration(node, holder)) {
						return true;
					}
					Expression reference = containingExpression(node);
					if (!holder.constantReferences.containsKey(reference)) {
						valid.set(false);
						return false;
					}
				}

				if (identifier.equals(holder.parameter.getName().getIdentifier()) && node != holder.parameter.getName()
						&& !isSupportedStateReference(node, holder)) {
					valid.set(false);
					return false;
				}
				return true;
			}
		});
		return valid.get();
	}

	private static boolean isCandidateInvocation(MethodInvocation invocation, IntConstantHolder holder) {
		IMethodBinding declaredBinding = holder.method.resolveBinding();
		IMethodBinding invocationBinding = invocation.resolveMethodBinding();
		if (declaredBinding != null && invocationBinding != null) {
			return declaredBinding.getMethodDeclaration().isEqualTo(invocationBinding.getMethodDeclaration());
		}

		if (invocation.getExpression() != null
				|| !invocation.getName().getIdentifier().equals(holder.method.getName().getIdentifier())
				|| invocation.arguments().size() != holder.method.parameters().size()
				|| findEnclosingType(invocation) != holder.enclosingType) {
			return false;
		}

		if (hasCompetingLocalMethod(holder.enclosingType, holder.method)) {
			return false;
		}
		return true;
	}

	private static boolean hasCompetingLocalMethod(TypeDeclaration type, MethodDeclaration candidate) {
		String name = candidate.getName().getIdentifier();
		int parameterCount = candidate.parameters().size();
		for (MethodDeclaration method : type.getMethods()) {
			if (method != candidate && method.getName().getIdentifier().equals(name)
					&& method.parameters().size() == parameterCount) {
				return true;
			}
		}
		return false;
	}

	private static String recoverUnresolvedConstantName(Expression expression, IntConstantHolder holder) {
		if (!(expression instanceof SimpleName name)) {
			return null;
		}
		String identifier = name.getIdentifier();
		if (!holder.constantNames.contains(identifier)) {
			return null;
		}
		MethodDeclaration enclosingMethod = findEnclosingMethod(name);
		if (enclosingMethod != null && hasLocalDeclarationNamed(enclosingMethod, identifier)) {
			return null;
		}
		return identifier;
	}

	private static boolean hasLocalDeclarationNamed(MethodDeclaration method, String identifier) {
		AtomicBoolean found = new AtomicBoolean(false);
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SingleVariableDeclaration node) {
				if (node.getName().getIdentifier().equals(identifier)) {
					found.set(true);
					return false;
				}
				return !found.get();
			}

			@Override
			public boolean visit(VariableDeclarationFragment node) {
				if (node.getName().getIdentifier().equals(identifier)) {
					found.set(true);
					return false;
				}
				return !found.get();
			}
		});
		return found.get();
	}

	private static boolean isCandidateFieldDeclaration(SimpleName name, IntConstantHolder holder) {
		if (!(name.getParent() instanceof VariableDeclarationFragment fragment) || fragment.getName() != name
				|| !(fragment.getParent() instanceof FieldDeclaration field)) {
			return false;
		}
		return holder.constantFields.get(name.getIdentifier()) == field;
	}

	private static boolean isSupportedStateReference(SimpleName name, IntConstantHolder holder) {
		Expression stateExpression = outerParenthesized(containingExpression(name));
		if (!(stateExpression.getParent() instanceof InfixExpression comparison)
				|| comparison.getOperator() != InfixExpression.Operator.EQUALS
				|| !comparison.extendedOperands().isEmpty()) {
			return false;
		}

		Expression other;
		if (outerParenthesized(comparison.getLeftOperand()) == stateExpression) {
			other = unparenthesize(comparison.getRightOperand());
		} else if (outerParenthesized(comparison.getRightOperand()) == stateExpression) {
			other = unparenthesize(comparison.getLeftOperand());
		} else {
			return false;
		}
		return holder.constantReferences.containsKey(other);
	}

	private static Expression containingExpression(SimpleName name) {
		ASTNode parent = name.getParent();
		if (parent instanceof QualifiedName qualifiedName && qualifiedName.getName() == name) {
			return qualifiedName;
		}
		if (parent instanceof FieldAccess fieldAccess && fieldAccess.getName() == name) {
			return fieldAccess;
		}
		return name;
	}

	private static Expression unparenthesize(Expression expression) {
		Expression current = expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current = parenthesized.getExpression();
		}
		return current;
	}

	private static Expression outerParenthesized(Expression expression) {
		Expression current = expression;
		while (current.getParent() instanceof ParenthesizedExpression parenthesized) {
			current = parenthesized;
		}
		return current;
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
