/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

/** Local AST rewrite generated from a project-wide integer-state plan. */
final class IntEnumMultiFileRewriteOperation extends CompilationUnitRewriteOperationWithSourceRange {

	record ResolvedOwner(IntEnumCandidate candidate, TypeDeclaration type, MethodDeclaration method,
			SingleVariableDeclaration parameter, Map<FieldDeclaration, Set<String>> constantFragments) {
	}

	record ResolvedPlan(List<ResolvedOwner> owners, Map<Expression, String> replacements,
			Set<ASTNode> processedNodes) {
	}

	private final ResolvedPlan plan;

	IntEnumMultiFileRewriteOperation(ResolvedPlan plan) {
		this.plan= plan;
	}

	static ResolvedPlan resolve(ICompilationUnit unit, org.eclipse.jdt.core.dom.CompilationUnit root,
			List<IntEnumCandidate> candidates) throws CoreException {
		String unitHandle= unit.getHandleIdentifier();
		Map<String, IntEnumCandidate> byMethod= new HashMap<>();
		Map<String, IntEnumCandidate> byConstant= new HashMap<>();
		for (IntEnumCandidate candidate : candidates) {
			byMethod.put(candidate.methodBindingKey(), candidate);
			candidate.constants().forEach(constant -> byConstant.put(constant.bindingKey(), candidate));
		}

		Map<IntEnumCandidate, TypeDeclaration> ownerTypes= new LinkedHashMap<>();
		Map<IntEnumCandidate, MethodDeclaration> ownerMethods= new LinkedHashMap<>();
		Map<IntEnumCandidate, Map<FieldDeclaration, Set<String>>> ownerFields= new LinkedHashMap<>();
		Map<Expression, String> replacements= new IdentityHashMap<>();
		Map<IntEnumCandidate, Map<String, Integer>> referenceCounts= new HashMap<>();
		Map<IntEnumCandidate, Integer> callCounts= new HashMap<>();
		Set<ASTNode> processed= java.util.Collections.newSetFromMap(new IdentityHashMap<>());

		try {
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration node) {
					String key= typeKey(node.resolveBinding());
					for (IntEnumCandidate candidate : candidates) {
						if (unitHandle.equals(candidate.ownerCompilationUnitHandle())
								&& candidate.ownerTypeBindingKey().equals(key)) {
							ownerTypes.put(candidate, node);
							processed.add(node);
						}
					}
					return true;
				}

				@Override
				public boolean visit(MethodDeclaration node) {
					IMethodBinding binding= node.resolveBinding();
					String key= binding == null ? null : binding.getMethodDeclaration().getKey();
					IntEnumCandidate candidate= byMethod.get(key);
					if (candidate != null && unitHandle.equals(candidate.ownerCompilationUnitHandle())) {
						ownerMethods.put(candidate, node);
						processed.add(node);
					}
					return true;
				}

				@Override
				public boolean visit(FieldDeclaration node) {
					for (Object fragmentObject : node.fragments()) {
						VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
						IVariableBinding binding= fragment.resolveBinding();
						String key= binding == null ? null : binding.getVariableDeclaration().getKey();
						IntEnumCandidate candidate= byConstant.get(key);
						if (candidate != null && unitHandle.equals(candidate.ownerCompilationUnitHandle())) {
							ownerFields.computeIfAbsent(candidate, ignored -> new LinkedHashMap<>())
									.computeIfAbsent(node, ignored -> new LinkedHashSet<>()).add(key);
							processed.add(node);
						}
					}
					return true;
				}

				@Override
				public boolean visit(MethodInvocation node) {
					IMethodBinding binding= node.resolveMethodBinding();
					String key= binding == null ? null : binding.getMethodDeclaration().getKey();
					IntEnumCandidate candidate= byMethod.get(key);
					if (candidate != null) {
						callCounts.merge(candidate, Integer.valueOf(1), Integer::sum);
					}
					return true;
				}

				@Override
				public boolean visit(SimpleName node) {
					IBinding binding= node.resolveBinding();
					if (!(binding instanceof IVariableBinding variable)) {
						return true;
					}
					String constantKey= variable.getVariableDeclaration().getKey();
					IntEnumCandidate candidate= byConstant.get(constantKey);
					if (candidate == null || isDeclarationName(node)) {
						return true;
					}
					Expression expression= containingExpression(node);
					if (!isRecognisedReference(expression, candidate)) {
						throw new StalePlanRuntimeException(
								stale(unit, "unexpected use of " + node.getIdentifier())); //$NON-NLS-1$
					}
					IntEnumConstant constant= candidate.constant(constantKey);
					String qualifier= unitHandle.equals(candidate.ownerCompilationUnitHandle())
							? candidate.enumTypeName()
							: candidate.ownerTypeQualifiedName() + "." + candidate.enumTypeName(); //$NON-NLS-1$
					replacements.put(expression, qualifier + "." + constant.enumName()); //$NON-NLS-1$
					referenceCounts.computeIfAbsent(candidate, ignored -> new LinkedHashMap<>())
							.merge(constantKey, Integer.valueOf(1), Integer::sum);
					processed.add(expression);
					return true;
				}
			});
		} catch (StalePlanRuntimeException exception) {
			throw exception.coreException;
		}

		List<ResolvedOwner> owners= new ArrayList<>();
		for (IntEnumCandidate candidate : candidates) {
			Map<String, Integer> expectedReferences= candidate.expectedReferenceCountsByUnit()
					.getOrDefault(unitHandle, Map.of());
			Map<String, Integer> actualReferences= referenceCounts.getOrDefault(candidate, Map.of());
			if (!expectedReferences.equals(actualReferences)) {
				throw stale(unit, "constant reference count changed for " + candidate.enumTypeName()); //$NON-NLS-1$
			}
			int expectedCalls= candidate.expectedCallCountsByUnit().getOrDefault(unitHandle, Integer.valueOf(0))
					.intValue();
			if (expectedCalls != callCounts.getOrDefault(candidate, Integer.valueOf(0)).intValue()) {
				throw stale(unit, "method call count changed for " + candidate.enumTypeName()); //$NON-NLS-1$
			}
			if (unitHandle.equals(candidate.ownerCompilationUnitHandle())) {
				TypeDeclaration type= ownerTypes.get(candidate);
				MethodDeclaration method= ownerMethods.get(candidate);
				Map<FieldDeclaration, Set<String>> fields= ownerFields.getOrDefault(candidate, Map.of());
				if (type == null || method == null || candidate.parameterIndex() >= method.parameters().size()
						|| fields.values().stream().mapToInt(Set::size).sum() != candidate.constants().size()) {
					throw stale(unit, "owner declaration changed for " + candidate.enumTypeName()); //$NON-NLS-1$
				}
				SingleVariableDeclaration parameter= (SingleVariableDeclaration) method.parameters()
						.get(candidate.parameterIndex());
				if (!isInt(parameter.getType())) {
					throw stale(unit, "state parameter is no longer int"); //$NON-NLS-1$
				}
				owners.add(new ResolvedOwner(candidate, type, method, parameter, fields));
			}
		}
		return new ResolvedPlan(List.copyOf(owners), Map.copyOf(replacements), Set.copyOf(processed));
	}

	private static final class StalePlanRuntimeException extends RuntimeException {
		private static final long serialVersionUID= 1L;
		private final CoreException coreException;

		StalePlanRuntimeException(CoreException coreException) {
			this.coreException= coreException;
		}
	}

	@Override
	public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		TextEditGroup group= createTextEditGroup("Convert package-scoped integer state domain to enum", cuRewrite); //$NON-NLS-1$
		AST ast= cuRewrite.getRoot().getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();

		for (ResolvedOwner owner : plan.owners()) {
			insertEnum(owner, ast, rewrite, group);
			rewrite.replace(owner.parameter().getType(),
					ast.newSimpleType(ast.newSimpleName(owner.candidate().enumTypeName())), group);
			removeConstants(owner, rewrite, group);
		}
		for (Map.Entry<Expression, String> replacement : plan.replacements().entrySet()) {
			Name name= ast.newName(replacement.getValue());
			rewrite.replace(replacement.getKey(), name, group);
		}
	}

	private static void insertEnum(ResolvedOwner owner, AST ast, ASTRewrite rewrite, TextEditGroup group) {
		EnumDeclaration enumDeclaration= ast.newEnumDeclaration();
		enumDeclaration.setName(ast.newSimpleName(owner.candidate().enumTypeName()));
		for (IntEnumConstant constant : owner.candidate().constants()) {
			EnumConstantDeclaration enumConstant= ast.newEnumConstantDeclaration();
			enumConstant.setName(ast.newSimpleName(constant.enumName()));
			enumDeclaration.enumConstants().add(enumConstant);
		}
		FieldDeclaration firstField= owner.constantFragments().keySet().stream()
				.min(java.util.Comparator.comparingInt(ASTNode::getStartPosition)).orElseThrow();
		ListRewrite body= rewrite.getListRewrite(owner.type(), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		body.insertBefore(enumDeclaration, firstField, group);
	}

	private static void removeConstants(ResolvedOwner owner, ASTRewrite rewrite, TextEditGroup group) {
		ListRewrite body= rewrite.getListRewrite(owner.type(), TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		for (Map.Entry<FieldDeclaration, Set<String>> entry : owner.constantFragments().entrySet()) {
			FieldDeclaration field= entry.getKey();
			Set<String> selected= entry.getValue();
			boolean all= true;
			for (Object fragmentObject : field.fragments()) {
				VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
				IVariableBinding binding= fragment.resolveBinding();
				String key= binding == null ? null : binding.getVariableDeclaration().getKey();
				if (!selected.contains(key)) {
					all= false;
					break;
				}
			}
			if (all) {
				body.remove(field, group);
			} else {
				ListRewrite fragments= rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
				for (Object fragmentObject : field.fragments()) {
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
					IVariableBinding binding= fragment.resolveBinding();
					String key= binding == null ? null : binding.getVariableDeclaration().getKey();
					if (selected.contains(key)) {
						fragments.remove(fragment, group);
					}
				}
			}
		}
	}

	private static boolean isRecognisedReference(Expression expression, IntEnumCandidate candidate) {
		Expression outer= outerExpression(expression);
		ASTNode parent= outer.getParent();
		if (parent instanceof MethodInvocation invocation) {
			IMethodBinding binding= invocation.resolveMethodBinding();
			String key= binding == null ? null : binding.getMethodDeclaration().getKey();
			return candidate.methodBindingKey().equals(key) && candidate.parameterIndex() < invocation.arguments().size()
					&& outerExpression((Expression) invocation.arguments().get(candidate.parameterIndex())) == outer;
		}
		ASTNode current= outer.getParent();
		while (current != null && !(current instanceof MethodDeclaration)) {
			if (current instanceof InfixExpression infix && infix.getOperator() == InfixExpression.Operator.EQUALS) {
				Expression left= outerExpression(infix.getLeftOperand());
				Expression right= outerExpression(infix.getRightOperand());
				Expression other= left == outer ? right : right == outer ? left : null;
				IVariableBinding otherBinding= other == null ? null : resolveVariable(other);
				if (otherBinding != null && candidate.methodBindingKey().equals(enclosingMethodKey(infix))
						&& candidate.parameterIndex() >= 0) {
					MethodDeclaration method= enclosingMethod(infix);
					if (method != null && candidate.parameterIndex() < method.parameters().size()) {
						SingleVariableDeclaration parameter= (SingleVariableDeclaration) method.parameters()
								.get(candidate.parameterIndex());
						IVariableBinding parameterBinding= parameter.resolveBinding();
						return parameterBinding != null
								&& parameterBinding.getVariableDeclaration().isEqualTo(otherBinding.getVariableDeclaration());
					}
				}
			}
			current= current.getParent();
		}
		return false;
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		ASTNode current= node;
		while (current != null && !(current instanceof MethodDeclaration)) {
			current= current.getParent();
		}
		return (MethodDeclaration) current;
	}

	private static String enclosingMethodKey(ASTNode node) {
		MethodDeclaration method= enclosingMethod(node);
		IMethodBinding binding= method == null ? null : method.resolveBinding();
		return binding == null ? null : binding.getMethodDeclaration().getKey();
	}

	private static Expression containingExpression(SimpleName name) {
		ASTNode parent= name.getParent();
		Expression expression;
		if (parent instanceof QualifiedName qualified && qualified.getName() == name) {
			expression= qualified;
		} else if (parent instanceof FieldAccess access && access.getName() == name) {
			expression= access;
		} else {
			expression= name;
		}
		return outerExpression(expression);
	}

	private static Expression outerExpression(Expression expression) {
		Expression current= expression;
		while (current.getParent() instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized;
		}
		return current;
	}

	private static IVariableBinding resolveVariable(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		IBinding binding= null;
		if (current instanceof Name name) {
			binding= name.resolveBinding();
		} else if (current instanceof FieldAccess access) {
			binding= access.resolveFieldBinding();
		}
		return binding instanceof IVariableBinding variable ? variable.getVariableDeclaration() : null;
	}

	private static boolean isDeclarationName(SimpleName name) {
		return name.getParent() instanceof VariableDeclarationFragment fragment && fragment.getName() == name;
	}

	private static String typeKey(ITypeBinding binding) {
		return binding == null ? null : binding.getTypeDeclaration().getKey();
	}

	private static boolean isInt(Type type) {
		return type.isPrimitiveType()
				&& ((PrimitiveType) type).getPrimitiveTypeCode() == PrimitiveType.INT;
	}

	private static CoreException stale(ICompilationUnit unit, String detail) {
		String message= "The project-wide int-to-enum plan is stale for " + unit.getElementName() + ": " + detail; //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_int_to_enum", message)); //$NON-NLS-1$
	}
}
