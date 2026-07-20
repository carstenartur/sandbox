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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.lang.model.SourceVersion;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.IntToEnumFixCore;

/**
 * Converts an enum-like group of private integer constants used by a private
 * method parameter in an if/else chain to a nested enum.
 *
 * <p>The detector deliberately accepts only candidates whose complete data flow
 * can be proven inside the current compilation unit. In particular, constants
 * and the migrated method must be private, every use of the state parameter must
 * be one of the recognised equality comparisons, and every call site must pass
 * one of the recognised constants. This keeps the ordinary single-file cleanup
 * safe while leaving project-wide API migrations to a future multi-file
 * refactoring.</p>
 */
public class IntToEnumHelper extends AbstractTool<ReferenceHolder<Integer, IntToEnumHelper.IntConstantHolder>> {

	/** Data required to perform one int-to-enum migration. */
	public static class IntConstantHolder {
		/** Root if-statement of the recognised chain. */
		public IfStatement ifStatement;
		/** Switch statement used by the switch-specific helper. */
		public SwitchStatement switchStatement;
		/** Map of constant names to their field declarations. */
		public Map<String, FieldDeclaration> constantFields = new LinkedHashMap<>();
		/** Constant names in declaration order. */
		public List<String> constantNames = new ArrayList<>();
		/** Name of the migrated state parameter. */
		public String comparedVariable;
		/** Set of nodes already processed by the cleanup. */
		public Set<ASTNode> nodesProcessed;
		/** Private method containing the state parameter. */
		public MethodDeclaration method;
		/** Parameter whose type is changed from int to the generated enum. */
		public SingleVariableDeclaration parameter;
		/** Type into which the nested enum is inserted. */
		public TypeDeclaration enclosingType;
		/** Constant expressions in conditions and call sites to replace. */
		public Map<Expression, String> constantReferences = new LinkedHashMap<>();
	}

	private static final class ConstantInfo {
		private final String name;
		private final FieldDeclaration field;
		private final IVariableBinding binding;
		private final int value;

		private ConstantInfo(String name, FieldDeclaration field, IVariableBinding binding, int value) {
			this.name = name;
			this.field = field;
			this.binding = binding;
			this.value = value;
		}
	}

	private static final class Comparison {
		private final IVariableBinding stateBinding;
		private final Expression stateExpression;
		private final ConstantInfo constant;
		private final Expression constantExpression;

		private Comparison(IVariableBinding stateBinding, Expression stateExpression, ConstantInfo constant,
				Expression constantExpression) {
			this.stateBinding = stateBinding;
			this.stateExpression = stateExpression;
			this.constant = constant;
			this.constantExpression = constantExpression;
		}
	}

	private static final class Candidate {
		private final IntConstantHolder holder = new IntConstantHolder();
		private final List<IfStatement> chain = new ArrayList<>();
		private final Set<Expression> stateReferences = new LinkedHashSet<>();
		private IVariableBinding stateBinding;
		private IMethodBinding methodBinding;
		private int parameterIndex;
	}

	@Override
	public void find(IntToEnumFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(IfStatement node) {
				if (nodesprocessed.contains(node) || isElseIf(node)) {
					return true;
				}

				Candidate candidate = createCandidate(compilationUnit, node);
				if (candidate == null) {
					return true;
				}

				candidate.holder.nodesProcessed = nodesprocessed;
				ReferenceHolder<Integer, IntConstantHolder> dataholder = new ReferenceHolder<>();
				dataholder.put(0, candidate.holder);
				operations.add(fixcore.rewrite(dataholder));
				nodesprocessed.addAll(candidate.chain);
				return false;
			}
		});
	}

	private static Candidate createCandidate(CompilationUnit compilationUnit, IfStatement root) {
		MethodDeclaration method = findEnclosingMethod(root);
		TypeDeclaration enclosingType = findEnclosingType(root);
		if (method == null || enclosingType == null || !Modifier.isPrivate(method.getModifiers())) {
			return null;
		}

		Map<String, ConstantInfo> constantsByBinding = collectPrivateIntConstants(enclosingType);
		if (constantsByBinding.size() < 2) {
			return null;
		}

		Candidate candidate = new Candidate();
		Set<String> usedNames = new LinkedHashSet<>();
		IfStatement current = root;
		while (current != null) {
			Comparison comparison = parseComparison(current.getExpression(), constantsByBinding);
			if (comparison == null) {
				return null;
			}
			if (candidate.stateBinding == null) {
				candidate.stateBinding = comparison.stateBinding;
			} else if (!sameVariable(candidate.stateBinding, comparison.stateBinding)) {
				return null;
			}
			if (!usedNames.add(comparison.constant.name)) {
				return null;
			}

			candidate.chain.add(current);
			candidate.stateReferences.add(comparison.stateExpression);
			candidate.holder.constantReferences.put(comparison.constantExpression, comparison.constant.name);
			current = current.getElseStatement() instanceof IfStatement next ? next : null;
		}

		if (usedNames.size() < 2) {
			return null;
		}

		SingleVariableDeclaration parameter = findParameter(method, candidate.stateBinding);
		if (parameter == null || !isPlainInt(parameter)) {
			return null;
		}

		IMethodBinding methodBinding = method.resolveBinding();
		if (methodBinding == null) {
			return null;
		}
		candidate.methodBinding = methodBinding.getMethodDeclaration();
		candidate.parameterIndex = findParameterIndex(method, parameter);
		if (candidate.parameterIndex < 0) {
			return null;
		}

		List<String> usedNameList = new ArrayList<>(usedNames);
		String prefix = SwitchIntToEnumHelper.findCommonPrefix(usedNameList);
		if (prefix == null) {
			return null;
		}
		String enumName = SwitchIntToEnumHelper.prefixToEnumName(prefix);
		if (!isValidIdentifier(enumName) || hasNestedTypeNamed(enclosingType, enumName)) {
			return null;
		}

		Map<String, ConstantInfo> enumConstants = constantsForPrefix(constantsByBinding, prefix);
		if (enumConstants.size() < 2 || !enumConstants.keySet().containsAll(usedNames)
				|| !hasDistinctValues(enumConstants.values())) {
			return null;
		}
		for (String constantName : enumConstants.keySet()) {
			String enumConstantName = constantName.substring(prefix.length());
			if (!isValidIdentifier(enumConstantName)) {
				return null;
			}
		}

		candidate.holder.ifStatement = root;
		candidate.holder.method = method;
		candidate.holder.parameter = parameter;
		candidate.holder.enclosingType = enclosingType;
		candidate.holder.comparedVariable = parameter.getName().getIdentifier();
		for (ConstantInfo info : enumConstants.values()) {
			candidate.holder.constantNames.add(info.name);
			candidate.holder.constantFields.put(info.name, info.field);
		}

		if (!collectCallSiteReplacements(compilationUnit, candidate, enumConstants)
				|| !validateAllReferences(compilationUnit, candidate, enumConstants)) {
			return null;
		}
		return candidate;
	}

	private static boolean collectCallSiteReplacements(CompilationUnit compilationUnit, Candidate candidate,
			Map<String, ConstantInfo> enumConstants) {
		AtomicBoolean valid = new AtomicBoolean(true);
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				if (!valid.get()) {
					return false;
				}
				IMethodBinding binding = node.resolveMethodBinding();
				if (binding == null || !candidate.methodBinding.isEqualTo(binding.getMethodDeclaration())) {
					return true;
				}
				if (candidate.parameterIndex >= node.arguments().size()) {
					valid.set(false);
					return false;
				}
				Expression argument = (Expression) node.arguments().get(candidate.parameterIndex);
				ConstantInfo constant = resolveConstant(argument, enumConstants);
				if (constant == null) {
					valid.set(false);
					return false;
				}
				candidate.holder.constantReferences.put(unparenthesize(argument), constant.name);
				return true;
			}
		});
		return valid.get();
	}

	private static boolean validateAllReferences(CompilationUnit compilationUnit, Candidate candidate,
			Map<String, ConstantInfo> enumConstants) {
		AtomicBoolean valid = new AtomicBoolean(true);
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (!valid.get()) {
					return false;
				}
				IBinding binding = node.resolveBinding();
				if (binding instanceof IVariableBinding variableBinding) {
					ConstantInfo constant = enumConstants.get(bindingKey(variableBinding));
					if (constant != null) {
						if (isDeclarationName(node)) {
							return true;
						}
						Expression reference = containingExpression(node);
						if (!candidate.holder.constantReferences.containsKey(reference)) {
							valid.set(false);
							return false;
						}
					} else if (sameVariable(candidate.stateBinding, variableBinding)
							&& node != candidate.holder.parameter.getName()
							&& !candidate.stateReferences.contains(containingExpression(node))) {
						valid.set(false);
						return false;
					}
				} else if (binding instanceof IMethodBinding methodBinding
						&& candidate.methodBinding.isEqualTo(methodBinding.getMethodDeclaration())
						&& node != candidate.holder.method.getName()
						&& !(node.getParent() instanceof MethodInvocation invocation && invocation.getName() == node)) {
					valid.set(false);
					return false;
				}
				return true;
			}
		});
		return valid.get();
	}

	private static Map<String, ConstantInfo> collectPrivateIntConstants(TypeDeclaration typeDeclaration) {
		Map<String, ConstantInfo> result = new LinkedHashMap<>();
		for (FieldDeclaration field : typeDeclaration.getFields()) {
			int modifiers = field.getModifiers();
			if (!Modifier.isPrivate(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)
					|| !isInt(field.getType())) {
				continue;
			}
			for (Object fragmentObject : field.fragments()) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
				IVariableBinding binding = fragment.resolveBinding();
				Object constantValue = binding == null ? null : binding.getConstantValue();
				String key = bindingKey(binding);
				if (key != null && constantValue instanceof Integer value) {
					result.put(key, new ConstantInfo(fragment.getName().getIdentifier(), field,
							binding.getVariableDeclaration(), value.intValue()));
				}
			}
		}
		return result;
	}

	private static Map<String, ConstantInfo> constantsForPrefix(Map<String, ConstantInfo> constantsByBinding,
			String prefix) {
		Map<String, ConstantInfo> result = new LinkedHashMap<>();
		for (ConstantInfo info : constantsByBinding.values()) {
			if (info.name.startsWith(prefix)) {
				result.put(info.name, info);
			}
		}
		return result;
	}

	private static Comparison parseComparison(Expression expression, Map<String, ConstantInfo> constantsByBinding) {
		Expression unwrapped = unparenthesize(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.EQUALS
				|| !infix.extendedOperands().isEmpty()) {
			return null;
		}

		Expression left = unparenthesize(infix.getLeftOperand());
		Expression right = unparenthesize(infix.getRightOperand());
		ConstantInfo leftConstant = resolveConstant(left, constantsByBinding);
		ConstantInfo rightConstant = resolveConstant(right, constantsByBinding);
		IVariableBinding leftVariable = resolveVariable(left);
		IVariableBinding rightVariable = resolveVariable(right);

		if (leftConstant != null && rightVariable != null && rightConstant == null) {
			return new Comparison(rightVariable, right, leftConstant, left);
		}
		if (rightConstant != null && leftVariable != null && leftConstant == null) {
			return new Comparison(leftVariable, left, rightConstant, right);
		}
		return null;
	}

	private static ConstantInfo resolveConstant(Expression expression, Map<String, ConstantInfo> constantsByBinding) {
		IVariableBinding binding = resolveVariable(unparenthesize(expression));
		return binding == null ? null : constantsByBinding.get(bindingKey(binding));
	}

	private static IVariableBinding resolveVariable(Expression expression) {
		Expression unwrapped = unparenthesize(expression);
		IBinding binding = null;
		if (unwrapped instanceof Name name) {
			binding = name.resolveBinding();
		} else if (unwrapped instanceof FieldAccess fieldAccess) {
			binding = fieldAccess.resolveFieldBinding();
		}
		return binding instanceof IVariableBinding variableBinding ? variableBinding.getVariableDeclaration() : null;
	}

	private static Expression unparenthesize(Expression expression) {
		Expression current = expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current = parenthesized.getExpression();
		}
		return current;
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

	private static boolean isDeclarationName(SimpleName name) {
		return name.getParent() instanceof VariableDeclarationFragment fragment && fragment.getName() == name;
	}

	private static SingleVariableDeclaration findParameter(MethodDeclaration method, IVariableBinding binding) {
		for (Object parameterObject : method.parameters()) {
			SingleVariableDeclaration parameter = (SingleVariableDeclaration) parameterObject;
			IVariableBinding parameterBinding = parameter.resolveBinding();
			if (sameVariable(binding, parameterBinding)) {
				return parameter;
			}
		}
		return null;
	}

	private static int findParameterIndex(MethodDeclaration method, SingleVariableDeclaration parameter) {
		for (int i = 0; i < method.parameters().size(); i++) {
			if (method.parameters().get(i) == parameter) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isPlainInt(SingleVariableDeclaration parameter) {
		return !parameter.isVarargs() && parameter.extraDimensions().isEmpty() && isInt(parameter.getType());
	}

	private static boolean isInt(Type type) {
		return type.isPrimitiveType()
				&& ((PrimitiveType) type).getPrimitiveTypeCode() == PrimitiveType.INT;
	}

	private static boolean hasDistinctValues(Iterable<ConstantInfo> constants) {
		Set<Integer> values = new HashSet<>();
		for (ConstantInfo constant : constants) {
			if (!values.add(Integer.valueOf(constant.value))) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasNestedTypeNamed(TypeDeclaration enclosingType, String name) {
		if (enclosingType.getName().getIdentifier().equals(name)) {
			return true;
		}
		for (Object declaration : enclosingType.bodyDeclarations()) {
			if (declaration instanceof AbstractTypeDeclaration typeDeclaration
					&& typeDeclaration.getName().getIdentifier().equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isValidIdentifier(String name) {
		return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
	}

	private static boolean sameVariable(IVariableBinding first, IVariableBinding second) {
		return first != null && second != null
				&& first.getVariableDeclaration().isEqualTo(second.getVariableDeclaration());
	}

	private static String bindingKey(IVariableBinding binding) {
		return binding == null ? null : binding.getVariableDeclaration().getKey();
	}

	private static boolean isElseIf(IfStatement statement) {
		return statement.getParent() instanceof IfStatement parent && parent.getElseStatement() == statement;
	}

	private static MethodDeclaration findEnclosingMethod(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration methodDeclaration) {
				return methodDeclaration;
			}
			current = current.getParent();
		}
		return null;
	}

	private static TypeDeclaration findEnclosingType(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof TypeDeclaration typeDeclaration) {
				return typeDeclaration;
			}
			current = current.getParent();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void rewrite(IntToEnumFixCore fixCore, ReferenceHolder<Integer, IntConstantHolder> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		for (IntConstantHolder data : holder.values()) {
			if (data.ifStatement == null || data.parameter == null || data.enclosingType == null
					|| data.constantNames.size() < 2) {
				continue;
			}

			String prefix = SwitchIntToEnumHelper.findCommonPrefix(data.constantNames);
			if (prefix == null) {
				continue;
			}
			String enumName = SwitchIntToEnumHelper.prefixToEnumName(prefix);
			AST ast = cuRewrite.getRoot().getAST();
			ASTRewrite rewrite = cuRewrite.getASTRewrite();

			EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
			enumDeclaration.setName(ast.newSimpleName(enumName));
			enumDeclaration.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));
			for (String constantName : data.constantNames) {
				EnumConstantDeclaration enumConstant = ast.newEnumConstantDeclaration();
				enumConstant.setName(ast.newSimpleName(constantName.substring(prefix.length())));
				enumDeclaration.enumConstants().add(enumConstant);
			}

			ListRewrite bodyRewrite = rewrite.getListRewrite(data.enclosingType,
					TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			FieldDeclaration firstField = data.constantFields.values().iterator().next();
			bodyRewrite.insertBefore(enumDeclaration, firstField, group);
			removeConstantFields(rewrite, bodyRewrite, data, group);

			Type enumType = ast.newSimpleType(ast.newSimpleName(enumName));
			rewrite.replace(data.parameter.getType(), enumType, group);
			for (Map.Entry<Expression, String> reference : data.constantReferences.entrySet()) {
				String enumConstantName = reference.getValue().substring(prefix.length());
				Name replacement = ast.newName(enumName + "." + enumConstantName); //$NON-NLS-1$
				rewrite.replace(reference.getKey(), replacement, group);
			}
		}
	}

	private static void removeConstantFields(ASTRewrite rewrite, ListRewrite bodyRewrite, IntConstantHolder data,
			TextEditGroup group) {
		Set<FieldDeclaration> fieldsToRemove = new LinkedHashSet<>(data.constantFields.values());
		for (FieldDeclaration field : fieldsToRemove) {
			boolean removeWholeField = true;
			for (Object fragmentObject : field.fragments()) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
				if (!data.constantFields.containsKey(fragment.getName().getIdentifier())) {
					removeWholeField = false;
					break;
				}
			}
			if (removeWholeField) {
				bodyRewrite.remove(field, group);
			} else {
				ListRewrite fragmentRewrite = rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
				for (Object fragmentObject : field.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
					if (data.constantFields.containsKey(fragment.getName().getIdentifier())) {
						fragmentRewrite.remove(fragment, group);
					}
				}
			}
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					private static final int STATUS_PENDING = 0;
					private static final int STATUS_APPROVED = 1;

					private void process(int status) {
					    if (status == STATUS_PENDING) {
					        handlePending();
					    } else if (status == STATUS_APPROVED) {
					        handleApproved();
					    }
					}
					"""; //$NON-NLS-1$
		}
		return """
				private enum Status {
				    PENDING, APPROVED
				}

				private void process(Status status) {
				    if (status == Status.PENDING) {
				        handlePending();
				    } else if (status == Status.APPROVED) {
				        handleApproved();
				    }
				}
				"""; //$NON-NLS-1$
	}
}
