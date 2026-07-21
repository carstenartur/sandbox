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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.SourceVersion;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
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
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import org.sandbox.jdt.cleanup.multifile.JavaProjectCompilationUnits;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;

/** Builds conservative source-wide integer-state migration plans. */
public final class IntEnumMultiFilePlanner {

	private record ConstantDecl(String bindingKey, String name, int value) {
	}

	private static final class CandidateBuilder {
		String ownerUnitHandle;
		String ownerTypeKey;
		String ownerTypeQualifiedName;
		String methodKey;
		int parameterIndex;
		String prefix;
		String enumName;
		IVariableBinding stateBinding;
		final List<ConstantDecl> constants= new ArrayList<>();
		final Map<Expression, String> recognisedReferences= new IdentityHashMap<>();
		final Set<Expression> stateReferences= java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		final Map<String, Integer> callCountsByUnit= new LinkedHashMap<>();
		boolean valid= true;
		boolean hasExternalCaller;
	}

	private IntEnumMultiFilePlanner() {
	}

	/** Creates a plan for complete-project source selections. */
	public static MultiFileCleanUpPlanResult<IntEnumMigrationPlan> create(IJavaProject project,
			ICompilationUnit[] selectedUnits, IProgressMonitor monitor) throws CoreException {
		SelectedCompilationUnitPlan scope= SelectedCompilationUnitPlan.of(project, selectedUnits);
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		Set<String> allHandles= new LinkedHashSet<>();
		for (ICompilationUnit unit : JavaProjectCompilationUnits.collect(project)) {
			allHandles.add(unit.getHandleIdentifier());
		}
		if (!scope.compilationUnitHandles().equals(allHandles)) {
			return MultiFileCleanUpPlanResult.success(new IntEnumMigrationPlan(scope, List.of()));
		}

		Map<String, CompilationUnit> roots= parse(project, selectedUnits, monitor);
		List<CandidateBuilder> builders= discoverCandidates(roots);
		validateReferences(roots, builders);
		List<IntEnumCandidate> candidates= freeze(builders);
		return MultiFileCleanUpPlanResult.success(new IntEnumMigrationPlan(scope, candidates));
	}

	private static Map<String, CompilationUnit> parse(IJavaProject project, ICompilationUnit[] units,
			IProgressMonitor monitor) {
		Map<String, CompilationUnit> roots= new LinkedHashMap<>();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots.put(source.getPrimary().getHandleIdentifier(), ast);
			}
		}, monitor);
		return roots;
	}

	private static List<CandidateBuilder> discoverCandidates(Map<String, CompilationUnit> roots) {
		List<CandidateBuilder> result= new ArrayList<>();
		Set<String> claimedConstants= new HashSet<>();
		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			String unitHandle= entry.getKey();
			entry.getValue().accept(new ASTVisitor() {
				@Override
				public boolean visit(TypeDeclaration type) {
					if (!(type.getParent() instanceof CompilationUnit) || type.isInterface()
							|| type.getSuperclassType() != null || !type.superInterfaceTypes().isEmpty()) {
						return false;
					}
					ITypeBinding typeBinding= type.resolveBinding();
					String typeKey= typeKey(typeBinding);
					if (typeKey == null) {
						return false;
					}
					Map<String, ConstantDecl> constants= collectPackageConstants(type);
					if (constants.size() < 2) {
						return false;
					}
					for (MethodDeclaration method : type.getMethods()) {
						if (method.isConstructor() || !isPackagePrivate(method.getModifiers()) || method.getBody() == null) {
							continue;
						}
						for (int parameterIndex= 0; parameterIndex < method.parameters().size(); parameterIndex++) {
							SingleVariableDeclaration parameter= (SingleVariableDeclaration) method.parameters().get(parameterIndex);
							if (!isPlainInt(parameter)) {
								continue;
							}
							CandidateBuilder candidate= discoverMethodCandidate(unitHandle, type, typeBinding, typeKey, method,
									parameter, parameterIndex, constants);
							if (candidate != null && candidate.constants.stream()
									.noneMatch(constant -> claimedConstants.contains(constant.bindingKey()))) {
								candidate.constants.forEach(constant -> claimedConstants.add(constant.bindingKey()));
								result.add(candidate);
							}
						}
					}
					return false;
				}
			});
		}
		return result;
	}

	private static CandidateBuilder discoverMethodCandidate(String unitHandle, TypeDeclaration type,
			ITypeBinding typeBinding, String typeKey, MethodDeclaration method, SingleVariableDeclaration parameter,
			int parameterIndex, Map<String, ConstantDecl> constants) {
		IMethodBinding methodBinding= method.resolveBinding();
		IVariableBinding parameterBinding= parameter.resolveBinding();
		if (methodBinding == null || parameterBinding == null) {
			return null;
		}
		List<IfStatement> roots= new ArrayList<>();
		method.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(IfStatement node) {
				if (!(node.getParent() instanceof IfStatement parent && parent.getElseStatement() == node)) {
					roots.add(node);
				}
				return true;
			}
		});
		for (IfStatement root : roots) {
			CandidateBuilder candidate= parseChain(root, parameterBinding, constants);
			if (candidate == null) {
				continue;
			}
			candidate.ownerUnitHandle= unitHandle;
			candidate.ownerTypeKey= typeKey;
			candidate.ownerTypeQualifiedName= typeBinding.getQualifiedName();
			candidate.methodKey= methodBinding.getMethodDeclaration().getKey();
			candidate.parameterIndex= parameterIndex;
			if (candidate.methodKey == null || candidate.ownerTypeQualifiedName.isEmpty()
					|| hasNestedTypeNamed(type, candidate.enumName)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static CandidateBuilder parseChain(IfStatement root, IVariableBinding parameter,
			Map<String, ConstantDecl> constants) {
		CandidateBuilder candidate= new CandidateBuilder();
		candidate.stateBinding= parameter.getVariableDeclaration();
		Set<String> usedKeys= new LinkedHashSet<>();
		IfStatement current= root;
		while (current != null) {
			Comparison comparison= parseComparison(current.getExpression(), candidate.stateBinding, constants);
			if (comparison == null || !usedKeys.add(comparison.constant().bindingKey())) {
				return null;
			}
			candidate.recognisedReferences.put(comparison.constantExpression(), comparison.constant().bindingKey());
			candidate.stateReferences.add(comparison.stateExpression());
			current= current.getElseStatement() instanceof IfStatement next ? next : null;
		}
		if (usedKeys.size() < 2) {
			return null;
		}
		List<ConstantDecl> used= constants.values().stream().filter(constant -> usedKeys.contains(constant.bindingKey())).toList();
		String prefix= commonPrefix(used.stream().map(ConstantDecl::name).toList());
		if (prefix == null) {
			return null;
		}
		List<ConstantDecl> group= constants.values().stream().filter(constant -> constant.name().startsWith(prefix)).toList();
		if (group.size() != used.size() || !distinctValues(group)) {
			return null;
		}
		String enumName= enumTypeName(prefix);
		if (!validIdentifier(enumName)) {
			return null;
		}
		for (ConstantDecl constant : group) {
			if (!validIdentifier(constant.name().substring(prefix.length()))) {
				return null;
			}
		}
		candidate.prefix= prefix;
		candidate.enumName= enumName;
		candidate.constants.addAll(group);
		return candidate;
	}

	private record Comparison(Expression stateExpression, ConstantDecl constant, Expression constantExpression) {
	}

	private static Comparison parseComparison(Expression expression, IVariableBinding parameter,
			Map<String, ConstantDecl> constants) {
		Expression unwrapped= unwrap(expression);
		if (!(unwrapped instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.EQUALS || !infix.extendedOperands().isEmpty()) {
			return null;
		}
		Expression left= unwrap(infix.getLeftOperand());
		Expression right= unwrap(infix.getRightOperand());
		IVariableBinding leftBinding= resolveVariable(left);
		IVariableBinding rightBinding= resolveVariable(right);
		ConstantDecl leftConstant= leftBinding == null ? null : constants.get(leftBinding.getKey());
		ConstantDecl rightConstant= rightBinding == null ? null : constants.get(rightBinding.getKey());
		if (leftConstant != null && sameVariable(parameter, rightBinding) && rightConstant == null) {
			return new Comparison(right, leftConstant, left);
		}
		if (rightConstant != null && sameVariable(parameter, leftBinding) && leftConstant == null) {
			return new Comparison(left, rightConstant, right);
		}
		return null;
	}

	private static void validateReferences(Map<String, CompilationUnit> roots, List<CandidateBuilder> candidates) {
		Map<String, CandidateBuilder> byMethod= new HashMap<>();
		Map<String, CandidateBuilder> byConstant= new HashMap<>();
		for (CandidateBuilder candidate : candidates) {
			byMethod.put(candidate.methodKey, candidate);
			for (ConstantDecl constant : candidate.constants) {
				byConstant.put(constant.bindingKey(), candidate);
			}
		}
		for (Map.Entry<String, CompilationUnit> entry : roots.entrySet()) {
			String unitHandle= entry.getKey();
			CompilationUnit root= entry.getValue();
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					IMethodBinding methodBinding= node.resolveMethodBinding();
					String methodKey= methodBinding == null ? null : methodBinding.getMethodDeclaration().getKey();
					CandidateBuilder candidate= byMethod.get(methodKey);
					if (candidate == null) {
						return true;
					}
					if (candidate.parameterIndex >= node.arguments().size()) {
						candidate.valid= false;
						return false;
					}
					Expression argument= unwrap((Expression) node.arguments().get(candidate.parameterIndex));
					IVariableBinding argumentBinding= resolveVariable(argument);
					IntEnumConstant constant= frozenConstant(candidate, argumentBinding);
					if (constant == null) {
						candidate.valid= false;
						return false;
					}
					candidate.recognisedReferences.put(argument, constant.bindingKey());
					candidate.callCountsByUnit.merge(unitHandle, Integer.valueOf(1), Integer::sum);
					if (!unitHandle.equals(candidate.ownerUnitHandle)) {
						candidate.hasExternalCaller= true;
					}
					return true;
				}

				@Override
				public boolean visit(SimpleName node) {
					IBinding binding= node.resolveBinding();
					if (binding instanceof IVariableBinding variable) {
						String key= variable.getVariableDeclaration().getKey();
						CandidateBuilder candidate= byConstant.get(key);
						if (candidate != null && !isVariableDeclarationName(node)
								&& !candidate.recognisedReferences.containsKey(containingExpression(node))) {
							candidate.valid= false;
						}
						for (CandidateBuilder stateCandidate : candidates) {
							if (sameVariable(stateCandidate.stateBinding, variable) && !isParameterDeclarationName(node)
									&& !stateCandidate.stateReferences.contains(containingExpression(node))) {
								stateCandidate.valid= false;
							}
						}
					} else if (binding instanceof IMethodBinding methodBinding) {
						CandidateBuilder candidate= byMethod.get(methodBinding.getMethodDeclaration().getKey());
						if (candidate != null && !isMethodDeclarationName(node)
								&& !(node.getParent() instanceof MethodInvocation invocation && invocation.getName() == node)) {
							candidate.valid= false;
						}
					}
					return true;
				}
			});
		}
	}

	private static List<IntEnumCandidate> freeze(List<CandidateBuilder> builders) {
		List<IntEnumCandidate> result= new ArrayList<>();
		for (CandidateBuilder builder : builders) {
			if (!builder.valid || !builder.hasExternalCaller) {
				continue;
			}
			Map<String, Map<String, Integer>> referenceCounts= new LinkedHashMap<>();
			for (Map.Entry<Expression, String> reference : builder.recognisedReferences.entrySet()) {
				CompilationUnit root= root(reference.getKey());
				if (root == null || !(root.getJavaElement() instanceof ICompilationUnit unit)) {
					builder.valid= false;
					break;
				}
				referenceCounts.computeIfAbsent(unit.getPrimary().getHandleIdentifier(), key -> new LinkedHashMap<>())
						.merge(reference.getValue(), Integer.valueOf(1), Integer::sum);
			}
			if (!builder.valid) {
				continue;
			}
			List<IntEnumConstant> constants= builder.constants.stream()
					.map(constant -> new IntEnumConstant(constant.bindingKey(), constant.name(),
							constant.name().substring(builder.prefix.length()), constant.value()))
					.toList();
			result.add(new IntEnumCandidate(builder.ownerUnitHandle, builder.ownerTypeKey,
					builder.ownerTypeQualifiedName, builder.methodKey, builder.parameterIndex, builder.prefix,
					builder.enumName, constants, referenceCounts, builder.callCountsByUnit));
		}
		return List.copyOf(result);
	}

	private static Map<String, ConstantDecl> collectPackageConstants(TypeDeclaration type) {
		Map<String, ConstantDecl> result= new LinkedHashMap<>();
		for (FieldDeclaration field : type.getFields()) {
			if (!isPackagePrivate(field.getModifiers()) || !Modifier.isStatic(field.getModifiers())
					|| !Modifier.isFinal(field.getModifiers()) || !isInt(field.getType())) {
				continue;
			}
			for (Object fragmentObject : field.fragments()) {
				VariableDeclarationFragment fragment= (VariableDeclarationFragment) fragmentObject;
				IVariableBinding binding= fragment.resolveBinding();
				Object value= binding == null ? null : binding.getConstantValue();
				String key= binding == null ? null : binding.getVariableDeclaration().getKey();
				if (key != null && value instanceof Integer integer) {
					result.put(key, new ConstantDecl(key, fragment.getName().getIdentifier(), integer.intValue()));
				}
			}
		}
		return result;
	}

	private static IntEnumConstant frozenConstant(CandidateBuilder candidate, IVariableBinding binding) {
		if (binding == null) {
			return null;
		}
		String key= binding.getVariableDeclaration().getKey();
		for (ConstantDecl constant : candidate.constants) {
			if (constant.bindingKey().equals(key)) {
				return new IntEnumConstant(key, constant.name(), constant.name().substring(candidate.prefix.length()),
						constant.value());
			}
		}
		return null;
	}

	private static IVariableBinding resolveVariable(Expression expression) {
		Expression unwrapped= unwrap(expression);
		IBinding binding= null;
		if (unwrapped instanceof Name name) {
			binding= name.resolveBinding();
		} else if (unwrapped instanceof FieldAccess fieldAccess) {
			binding= fieldAccess.resolveFieldBinding();
		}
		return binding instanceof IVariableBinding variable ? variable.getVariableDeclaration() : null;
	}

	private static Expression unwrap(Expression expression) {
		Expression current= expression;
		while (current instanceof ParenthesizedExpression parenthesized) {
			current= parenthesized.getExpression();
		}
		return current;
	}

	private static Expression containingExpression(SimpleName name) {
		ASTNode parent= name.getParent();
		if (parent instanceof QualifiedName qualified && qualified.getName() == name) {
			return qualified;
		}
		if (parent instanceof FieldAccess access && access.getName() == name) {
			return access;
		}
		return name;
	}

	private static CompilationUnit root(ASTNode node) {
		ASTNode current= node;
		while (current != null && !(current instanceof CompilationUnit)) {
			current= current.getParent();
		}
		return (CompilationUnit) current;
	}

	private static boolean isVariableDeclarationName(SimpleName name) {
		return name.getParent() instanceof VariableDeclarationFragment fragment && fragment.getName() == name;
	}

	private static boolean isParameterDeclarationName(SimpleName name) {
		return name.getParent() instanceof SingleVariableDeclaration parameter && parameter.getName() == name;
	}

	private static boolean isMethodDeclarationName(SimpleName name) {
		return name.getParent() instanceof MethodDeclaration method && method.getName() == name;
	}

	private static boolean isPackagePrivate(int modifiers) {
		return !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
	}

	private static boolean isPlainInt(SingleVariableDeclaration parameter) {
		return !parameter.isVarargs() && parameter.extraDimensions().isEmpty() && isInt(parameter.getType());
	}

	private static boolean isInt(Type type) {
		return type.isPrimitiveType()
				&& ((PrimitiveType) type).getPrimitiveTypeCode() == PrimitiveType.INT;
	}

	private static boolean sameVariable(IVariableBinding first, IVariableBinding second) {
		return first != null && second != null
				&& first.getVariableDeclaration().isEqualTo(second.getVariableDeclaration());
	}

	private static String typeKey(ITypeBinding binding) {
		return binding == null ? null : binding.getTypeDeclaration().getKey();
	}

	private static boolean distinctValues(Collection<ConstantDecl> constants) {
		Set<Integer> values= new HashSet<>();
		return constants.stream().allMatch(constant -> values.add(Integer.valueOf(constant.value())));
	}

	private static String commonPrefix(List<String> names) {
		if (names.isEmpty()) {
			return null;
		}
		String first= names.get(0);
		int end= first.length();
		for (String name : names) {
			end= Math.min(end, name.length());
			int index= 0;
			while (index < end && first.charAt(index) == name.charAt(index)) {
				index++;
			}
			end= index;
		}
		int underscore= first.substring(0, end).lastIndexOf('_');
		return underscore > 0 ? first.substring(0, underscore + 1) : null;
	}

	private static String enumTypeName(String prefix) {
		String raw= prefix.endsWith("_") ? prefix.substring(0, prefix.length() - 1) : prefix; //$NON-NLS-1$ //$NON-NLS-2$
		StringBuilder result= new StringBuilder();
		boolean capitalize= true;
		for (char character : raw.toLowerCase().toCharArray()) {
			if (character == '_') {
				capitalize= true;
			} else {
				result.append(capitalize ? Character.toUpperCase(character) : character);
				capitalize= false;
			}
		}
		return result.toString();
	}

	private static boolean validIdentifier(String name) {
		return SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name);
	}

	private static boolean hasNestedTypeNamed(TypeDeclaration type, String name) {
		for (Object declaration : type.bodyDeclarations()) {
			if (declaration instanceof AbstractTypeDeclaration nested
					&& name.equals(nested.getName().getIdentifier())) {
				return true;
			}
		}
		return false;
	}
}
