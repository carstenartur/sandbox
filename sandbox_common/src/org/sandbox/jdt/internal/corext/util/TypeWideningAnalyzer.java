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
package org.sandbox.jdt.internal.corext.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Analyzes variable declarations in a compilation unit to determine the widest
 * (most general) type each variable can be downgraded to, based on its actual usage.
 *
 * <p>This utility walks the type hierarchy (superclass + interfaces) to find
 * the highest type that declares all required method signatures and fields
 * used on each variable.</p>
 *
 * <p>Example: If an {@code ArrayList<String>} variable only uses {@code add()} and
 * {@code size()}, it can be widened to {@code Collection<String>}.</p>
 *
 * <p>Safety: Only proven receiver uses, null comparisons, simple reassignments,
 * and iteration are accepted. Other roles can constrain the static type and
 * prevent widening. Candidate members retain accessibility, field identity and
 * the exception contract resolved at each use; widening must not make a
 * declaration or assignment cast redundant.</p>
 */
public final class TypeWideningAnalyzer {

	private TypeWideningAnalyzer() {
		// Utility class - prevent instantiation
	}

	/**
	 * Result of type widening analysis for a single variable.
	 */
	public static class TypeWideningResult {
		private final IVariableBinding variableBinding;
		private final ITypeBinding currentType;
		private final ITypeBinding widestType;
		private final List<ITypeBinding> intermediateTypes;

		TypeWideningResult(IVariableBinding variableBinding, ITypeBinding currentType, ITypeBinding widestType,
				List<ITypeBinding> intermediateTypes) {
			this.variableBinding = variableBinding;
			this.currentType = currentType;
			this.widestType = widestType;
			this.intermediateTypes = intermediateTypes;
		}

		/** The variable binding that was analyzed */
		public IVariableBinding getVariableBinding() {
			return variableBinding;
		}

		/** The current declared type of the variable */
		public ITypeBinding getCurrentType() {
			return currentType;
		}

		/** The widest type the variable can be downgraded to, or null if no widening is possible */
		public ITypeBinding getWidestType() {
			return widestType;
		}

		/**
		 * Returns all types in the hierarchy from currentType upward that declare all
		 * required members, ordered from most specific (just above currentType) to most
		 * general (widestType). The widestType is included as the last element.
		 */
		public List<ITypeBinding> getIntermediateTypes() {
			return Collections.unmodifiableList(intermediateTypes);
		}

		/** Whether the variable can be widened to a more general type */
		public boolean canWiden() {
			return widestType != null && !widestType.getQualifiedName().equals(currentType.getQualifiedName());
		}
	}

	/**
	 * Helper class to store variable usage information during AST traversal.
	 */
	private static class VariableInfo {
		Set<IMethodBinding> usedMethods = new HashSet<>();
		Set<IVariableBinding> usedFields = new HashSet<>();
		List<Expression> assignedValues = new ArrayList<>();
		boolean needsIterable;
		boolean hasUnsafeUsage;
	}

	/**
	 * Helper class to store variable declaration information.
	 */
	private static class VariableDeclarationInfo {
		final ITypeBinding typeBinding;
		final Expression initializer;

		VariableDeclarationInfo(ITypeBinding typeBinding, Expression initializer) {
			this.typeBinding = typeBinding;
			this.initializer = initializer;
		}
	}

	/**
	 * Analyzes all local variable declarations in the given compilation unit and
	 * returns type widening results for variables that can be widened.
	 *
	 * @param compilationUnit the compilation unit to analyze
	 * @return a map from variable binding key to type widening result
	 */
	public static Map<String, TypeWideningResult> analyzeCompilationUnit(CompilationUnit compilationUnit) {
		Map<IVariableBinding, VariableInfo> variableUsages = new HashMap<>();
		Map<IVariableBinding, VariableDeclarationInfo> variableDeclarations = new HashMap<>();
		String sourcePackage = compilationUnit.getPackage() == null ? "" //$NON-NLS-1$
				: compilationUnit.getPackage().getName().getFullyQualifiedName();

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				if (node.fragments().size() > 1) {
					return true;
				}

				Type type = node.getType();
				if (type == null || type.isVar()) {
					return true;
				}

				ITypeBinding typeBinding = type.resolveBinding();
				if (typeBinding == null || typeBinding.isRecovered() || typeBinding.isPrimitive() || typeBinding.isArray()) {
					return true;
				}

				for (Object fragObj : node.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragObj;
					IVariableBinding varBinding = fragment.resolveBinding();
					if (varBinding == null || varBinding.isRecovered() || varBinding.isField() || varBinding.isParameter()) {
						continue;
					}

					variableDeclarations.put(varBinding, new VariableDeclarationInfo(typeBinding, fragment.getInitializer()));
					variableUsages.put(varBinding, new VariableInfo());
				}

				return true;
			}

			@Override
			public boolean visit(SimpleName node) {
				IBinding binding = node.resolveBinding();
				if (!(binding instanceof IVariableBinding varBinding)) {
					return true;
				}

				VariableInfo info = variableUsages.get(varBinding);
				if (info == null) {
					return true;
				}

				collectUsageInfo(node, info);
				return true;
			}
		});

		Map<String, TypeWideningResult> results = new HashMap<>();

		for (Map.Entry<IVariableBinding, VariableDeclarationInfo> entry : variableDeclarations.entrySet()) {
			IVariableBinding varBinding = entry.getKey();
			VariableDeclarationInfo declInfo = entry.getValue();
			VariableInfo usageInfo = variableUsages.get(varBinding);

			if (usageInfo == null) {
				continue;
			}

			if (usageInfo.hasUnsafeUsage) {
				continue;
			}

			// Don't widen if the variable has no actual usage (no method calls, no field access)
			if (usageInfo.usedMethods.isEmpty() && usageInfo.usedFields.isEmpty()) {
				continue;
			}

			ITypeBinding widenedType = findMostGeneralType(declInfo.typeBinding,
					usageInfo.usedMethods, usageInfo.usedFields, sourcePackage);

			if (widenedType != null && !widenedType.getQualifiedName().equals(declInfo.typeBinding.getQualifiedName())) {
				if ((usageInfo.needsIterable && !isSubtypeOf(widenedType, "java.lang.Iterable")) //$NON-NLS-1$
						|| makesCastRedundant(declInfo.initializer, widenedType)
						|| usageInfo.assignedValues.stream().anyMatch(value -> makesCastRedundant(value, widenedType))) {
					continue;
				}
				List<ITypeBinding> intermediateTypes = collectIntermediateTypes(declInfo.typeBinding, widenedType,
						usageInfo.usedMethods, usageInfo.usedFields, sourcePackage);
				results.put(varBinding.getKey(),
						new TypeWideningResult(varBinding, declInfo.typeBinding, widenedType, intermediateTypes));
			}
		}

		return results;
	}

	/**
	 * Collects usage information from a SimpleName node into the given VariableInfo.
	 */
	private static void collectUsageInfo(SimpleName node, VariableInfo info) {
		if (node.getParent() instanceof VariableDeclarationFragment fragment && fragment.getName() == node) {
			return;
		}
		Expression expression = node;
		while (expression.getParent() instanceof ParenthesizedExpression parentheses) {
			expression = parentheses;
		}
		var parent = expression.getParent();
		if (parent instanceof MethodInvocation invocation && invocation.getExpression() == expression) {
			IMethodBinding method = invocation.resolveMethodBinding();
			if (method != null && !method.isRecovered()) {
				info.usedMethods.add(method);
				return;
			}
		} else if (parent instanceof FieldAccess access && access.getExpression() == expression
				&& access.resolveFieldBinding() != null && !access.resolveFieldBinding().isRecovered()) {
			info.usedFields.add(access.resolveFieldBinding());
			return;
		} else if (parent instanceof QualifiedName name && name.getQualifier() == expression
				&& name.resolveBinding() instanceof IVariableBinding field && field.isField() && !field.isRecovered()) {
			info.usedFields.add(field);
			return;
		} else if (parent instanceof Assignment assignment && assignment.getLeftHandSide() == expression
				&& assignment.getOperator() == Assignment.Operator.ASSIGN
				&& assignment.getParent() instanceof ExpressionStatement) {
			info.assignedValues.add(assignment.getRightHandSide());
			return;
		} else if (parent instanceof EnhancedForStatement loop && loop.getExpression() == expression) {
			info.needsIterable = true;
			return;
		} else if (parent instanceof InfixExpression comparison && comparison.extendedOperands().isEmpty()
				&& (comparison.getOperator() == InfixExpression.Operator.EQUALS
						|| comparison.getOperator() == InfixExpression.Operator.NOT_EQUALS)
				&& (unparenthesize(comparison.getLeftOperand()) instanceof NullLiteral
						|| unparenthesize(comparison.getRightOperand()) instanceof NullLiteral)) {
			return;
		}
		// Every other role can constrain the static type: constructor arguments,
		// method references, patterns, switch selectors, throws, aliases and results.
		info.hasUnsafeUsage = true;
	}

	private static Expression unparenthesize(Expression expression) {
		while (expression instanceof ParenthesizedExpression parentheses) {
			expression = parentheses.getExpression();
		}
		return expression;
	}

	private static boolean makesCastRedundant(Expression value, ITypeBinding widenedType) {
		if (unparenthesize(value) instanceof CastExpression cast) {
			ITypeBinding operand = cast.getExpression().resolveTypeBinding();
			return operand == null || operand.isAssignmentCompatible(widenedType);
		}
		return false;
	}

	private static boolean isSubtypeOf(ITypeBinding type, String name) {
		if (type == null) {
			return false;
		}
		if (name.equals(type.getErasure().getQualifiedName()) || isSubtypeOf(type.getSuperclass(), name)) {
			return true;
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			if (isSubtypeOf(iface, name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a method signature string from a method binding.
	 * Format: methodName(param1Type,param2Type):returnType
	 */
	static String createMethodSignature(IMethodBinding methodBinding) {
		StringBuilder signature = new StringBuilder();
		signature.append(methodBinding.getName()).append('(');
		ITypeBinding[] parameterTypes = methodBinding.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) {
			if (i > 0) {
				signature.append(',');
			}
			ITypeBinding paramType = parameterTypes[i];
			if (paramType != null) {
				ITypeBinding erasure = paramType.getErasure();
				signature.append(erasure != null ? erasure.getQualifiedName() : "java.lang.Object"); //$NON-NLS-1$
			}
		}
		signature.append(')');
		ITypeBinding returnType = methodBinding.getReturnType();
		if (returnType != null) {
			signature.append(':').append(returnType.getQualifiedName());
		}
		return signature.toString();
	}

	/**
	 * Collects all types in the hierarchy from {@code currentType} upward that
	 * still declare all used members and are supertypes of (or equal to)
	 * {@code widestType}. The {@code widestType} is guaranteed to be the last
	 * element when it passes the member check.
	 *
	 * @param currentType           the current declared type of the variable
	 * @param widestType            the widest (most general) type computed by analysis
	 * @param usedMethodSignatures  the set of method signatures used on the variable
	 * @param usedFields            the set of field names accessed on the variable
	 * @return ordered list of valid target types (exclusive of {@code currentType},
	 *         inclusive of {@code widestType} as the last element)
	 */
	public static List<ITypeBinding> collectIntermediateTypes(ITypeBinding currentType, ITypeBinding widestType,
			Set<String> usedMethodSignatures, Set<String> usedFields) {
		VariableInfo members = resolveMembers(currentType, usedMethodSignatures, usedFields);
		return collectIntermediateTypes(currentType, widestType, members.usedMethods, members.usedFields, null);
	}

	private static List<ITypeBinding> collectIntermediateTypes(ITypeBinding currentType, ITypeBinding widestType,
			Set<IMethodBinding> usedMethods, Set<IVariableBinding> usedFields, String sourcePackage) {
		if (widestType == null) {
			return new ArrayList<>();
		}
		String widestErasureName = widestType.getErasure().getQualifiedName();

		List<ITypeBinding> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		seen.add(currentType.getErasure().getQualifiedName());

		Queue<ITypeBinding> queue = new LinkedList<>();
		enqueueSupertypes(currentType, queue);

		while (!queue.isEmpty()) {
			ITypeBinding type = queue.poll();
			if (type == null || isJavaLangObject(type)) {
				continue;
			}
			String erasureName = type.getErasure().getQualifiedName();
			if (!seen.add(erasureName)) {
				continue;
			}
			// Only include types that are supertypes of currentType and subtypes
			// of (or equal to) widestType, and declare all used members.
			if (!isTaggingInterface(type)
					&& declaresAllMembers(currentType, type, usedMethods, usedFields, sourcePackage)
					&& widestType.isSubTypeCompatible(type)) {
				// Don't add widestType yet — it will be appended last
				if (!erasureName.equals(widestErasureName)) {
					result.add(type);
				}
				enqueueSupertypes(type, queue);
			}
		}

		// Always append widestType as the last element if it passed the member check
		if (declaresAllMembers(currentType, widestType, usedMethods, usedFields, sourcePackage)) {
			result.add(widestType);
		}
		return result;
	}

	/**
	 * Enqueues all direct supertypes (superclass and interfaces) of the given type.
	 */
	private static void enqueueSupertypes(ITypeBinding type, Queue<ITypeBinding> queue) {
		ITypeBinding superclass = type.getSuperclass();
		if (superclass != null && !isJavaLangObject(superclass)) {
			queue.add(superclass);
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			queue.add(iface);
		}
	}

	/**
	 * Walks the type hierarchy to find the most general type that declares
	 * all the required method signatures and fields.
	 */
	static ITypeBinding findMostGeneralType(ITypeBinding currentType, Set<String> usedMethodSignatures,
			Set<String> usedFields) {
		VariableInfo members = resolveMembers(currentType, usedMethodSignatures, usedFields);
		return findMostGeneralType(currentType, members.usedMethods, members.usedFields, null);
	}

	private static ITypeBinding findMostGeneralType(ITypeBinding currentType, Set<IMethodBinding> usedMethods,
			Set<IVariableBinding> usedFields, String sourcePackage) {
		if (currentType == null) {
			return null;
		}

		ITypeBinding mostGeneral = currentType;

		ITypeBinding superclass = currentType.getSuperclass();
		if (superclass != null && !isJavaLangObject(superclass)
				&& declaresAllMembers(currentType, superclass, usedMethods, usedFields, sourcePackage)) {
			ITypeBinding candidate = findMostGeneralType(superclass, usedMethods, usedFields, sourcePackage);
			if (candidate != null) {
				mostGeneral = candidate;
			}
		}

		for (ITypeBinding iface : currentType.getInterfaces()) {
			// Skip tagging/marker interfaces (e.g., Serializable, Cloneable, RandomAccess)
			if (isTaggingInterface(iface)) {
				continue;
			}
			if (declaresAllMembers(currentType, iface, usedMethods, usedFields, sourcePackage)) {
				mostGeneral = iface;
			}
		}

		return mostGeneral;
	}

	/**
	 * Checks if a type is a tagging/marker interface (has no declared methods).
	 */
	private static boolean isTaggingInterface(ITypeBinding type) {
		if (type == null || !type.isInterface()) {
			return false;
		}
		return type.getDeclaredMethods().length == 0;
	}

	/**
	 * Checks if a type is java.lang.Object.
	 */
	private static boolean isJavaLangObject(ITypeBinding type) {
		return type != null && "java.lang.Object".equals(type.getQualifiedName()); //$NON-NLS-1$
	}

	/**
	 * Checks if a candidate type declares all the required method signatures and fields.
	 */
	private static boolean declaresAllMembers(ITypeBinding originalType, ITypeBinding candidateType,
			Set<IMethodBinding> usedMethods, Set<IVariableBinding> usedFields, String sourcePackage) {
		if (originalType == null || candidateType == null || candidateType.isRecovered()
				|| !isTypeAccessible(candidateType, sourcePackage)
				|| (candidateType.isDeprecated() && !originalType.isDeprecated())) {
			return false;
		}

		Map<String, IMethodBinding> candidateMethods = new HashMap<>();
		collectMethods(candidateType, new HashSet<>(), candidateMethods);

		for (IMethodBinding original : usedMethods) {
			if (original == null) {
				return false;
			}
			IMethodBinding candidate = candidateMethods.get(createMethodSignature(original));
			if (candidate == null || candidate.isRecovered()
					|| !isAccessible(candidate.getModifiers(), candidate.getDeclaringClass(), sourcePackage)
					|| (candidate.isDeprecated() && !original.isDeprecated())
					|| (Modifier.isStatic(candidate.getModifiers())
							&& !candidate.getMethodDeclaration().isEqualTo(original.getMethodDeclaration()))
					|| introducesCheckedException(original, candidate)) {
				return false;
			}
		}

		Map<String, IVariableBinding> candidateFields = new HashMap<>();
		collectFields(candidateType, new HashSet<>(), candidateFields);
		for (IVariableBinding original : usedFields) {
			if (original == null) {
				return false;
			}
			IVariableBinding candidate = candidateFields.get(original.getName());
			if (candidate == null || candidate.isRecovered()
					|| !original.getVariableDeclaration().isEqualTo(candidate.getVariableDeclaration())
					|| !original.getType().isEqualTo(candidate.getType())
					|| !isAccessible(candidate.getModifiers(), candidate.getDeclaringClass(), sourcePackage)) {
				return false;
			}
		}
		return true;
	}

	/** Compatibility adapter for clients supplying member names rather than use-site bindings. */
	private static VariableInfo resolveMembers(ITypeBinding type, Set<String> signatures, Set<String> fieldNames) {
		var result = new VariableInfo();
		Map<String, IMethodBinding> methods = new HashMap<>();
		Map<String, IVariableBinding> fields = new HashMap<>();
		collectMethods(type, new HashSet<>(), methods);
		collectFields(type, new HashSet<>(), fields);
		for (String signature : signatures) {
			result.usedMethods.add(methods.get(signature));
		}
		for (String field : fieldNames) {
			result.usedFields.add(fields.get(field));
		}
		return result;
	}

	private static boolean isTypeAccessible(ITypeBinding type, String sourcePackage) {
		return isAccessible(type.getModifiers(), type, sourcePackage)
				&& (type.getDeclaringClass() == null || isTypeAccessible(type.getDeclaringClass(), sourcePackage));
	}

	private static boolean isAccessible(int modifiers, ITypeBinding declaringType, String sourcePackage) {
		return Modifier.isPublic(modifiers) || (!Modifier.isPrivate(modifiers) && declaringType.getPackage() != null
				&& declaringType.getPackage().getName().equals(sourcePackage));
	}

	private static boolean introducesCheckedException(IMethodBinding original, IMethodBinding candidate) {
		for (ITypeBinding exception : candidate.getExceptionTypes()) {
			if (isSubtypeOf(exception, "java.lang.RuntimeException") || isSubtypeOf(exception, "java.lang.Error")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			boolean covered = false;
			for (ITypeBinding originalException : original.getExceptionTypes()) {
				covered |= exception.isAssignmentCompatible(originalException);
			}
			if (!covered) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Recursively collects method signatures for a type and its supertypes/interfaces.
	 */
	private static void collectMethods(ITypeBinding type, Set<ITypeBinding> visited,
			Map<String, IMethodBinding> methods) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IMethodBinding method : type.getDeclaredMethods()) {
			methods.putIfAbsent(createMethodSignature(method), method);
		}

		collectMethods(type.getSuperclass(), visited, methods);
		for (ITypeBinding iface : type.getInterfaces()) {
			collectMethods(iface, visited, methods);
		}
	}

	/**
	 * Recursively collects field names for a type and its supertypes.
	 */
	private static void collectFields(ITypeBinding type, Set<ITypeBinding> visited, Map<String, IVariableBinding> fields) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IVariableBinding field : type.getDeclaredFields()) {
			fields.putIfAbsent(field.getName(), field);
		}

		collectFields(type.getSuperclass(), visited, fields);
		for (ITypeBinding iface : type.getInterfaces()) {
			collectFields(iface, visited, fields);
		}
	}
}
