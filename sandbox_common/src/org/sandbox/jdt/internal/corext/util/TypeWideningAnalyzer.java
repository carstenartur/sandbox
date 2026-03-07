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
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
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
 * <p>Safety: Variables are skipped when they are cast, used in instanceof, passed
 * as method arguments, returned, or assigned to other variables.</p>
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
		Set<String> usedMethodSignatures = new HashSet<>();
		Set<String> usedFields = new HashSet<>();
		boolean hasCast;
		boolean hasInstanceof;
		boolean hasUnsafeUsage;
	}

	/**
	 * Helper class to store variable declaration information.
	 */
	private static class VariableDeclarationInfo {
		final ITypeBinding typeBinding;

		VariableDeclarationInfo(ITypeBinding typeBinding) {
			this.typeBinding = typeBinding;
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
				if (typeBinding == null || typeBinding.isPrimitive() || typeBinding.isArray()) {
					return true;
				}

				for (Object fragObj : node.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragObj;
					IVariableBinding varBinding = fragment.resolveBinding();
					if (varBinding == null || varBinding.isField() || varBinding.isParameter()) {
						continue;
					}

					variableDeclarations.put(varBinding, new VariableDeclarationInfo(typeBinding));
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

			if (usageInfo.hasCast || usageInfo.hasInstanceof || usageInfo.hasUnsafeUsage) {
				continue;
			}

			// Don't widen if the variable has no actual usage (no method calls, no field access)
			if (usageInfo.usedMethodSignatures.isEmpty() && usageInfo.usedFields.isEmpty()) {
				continue;
			}

			ITypeBinding widenedType = findMostGeneralType(declInfo.typeBinding,
					usageInfo.usedMethodSignatures, usageInfo.usedFields);

			if (widenedType != null && !widenedType.getQualifiedName().equals(declInfo.typeBinding.getQualifiedName())) {
				List<ITypeBinding> intermediateTypes = collectIntermediateTypes(declInfo.typeBinding, widenedType,
						usageInfo.usedMethodSignatures, usageInfo.usedFields);
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
		var parent = node.getParent();

		if (parent instanceof CastExpression) {
			info.hasCast = true;
		} else if (parent instanceof InstanceofExpression) {
			info.hasInstanceof = true;
		} else if (parent instanceof MethodInvocation mi) {
			if (mi.getExpression() == node) {
				IMethodBinding methodBinding = mi.resolveMethodBinding();
				if (methodBinding != null) {
					info.usedMethodSignatures.add(createMethodSignature(methodBinding));
				}
			} else {
				info.hasUnsafeUsage = true;
			}
		} else if (parent instanceof FieldAccess fa) {
			if (fa.getExpression() == node) {
				info.usedFields.add(fa.getName().getIdentifier());
			}
		} else if (parent instanceof QualifiedName qn) {
			if (qn.getQualifier() == node) {
				info.usedFields.add(qn.getName().getIdentifier());
			}
		} else if (parent instanceof SuperFieldAccess sfa) {
			info.usedFields.add(sfa.getName().getIdentifier());
		} else if (parent instanceof Assignment assignment) {
			if (assignment.getRightHandSide() == node) {
				info.hasUnsafeUsage = true;
			}
		} else if (parent instanceof ReturnStatement) {
			info.hasUnsafeUsage = true;
		}
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
	 * still declare all used members. Returns them ordered from most specific
	 * (just above {@code currentType}) to most general ({@code widestType}).
	 * The {@code widestType} itself is included as the last element (if valid).
	 *
	 * @param currentType           the current declared type of the variable
	 * @param widestType            the widest (most general) type computed by analysis
	 * @param usedMethodSignatures  the set of method signatures used on the variable
	 * @param usedFields            the set of field names accessed on the variable
	 * @return ordered list of valid target types (exclusive of {@code currentType})
	 */
	public static List<ITypeBinding> collectIntermediateTypes(ITypeBinding currentType, ITypeBinding widestType,
			Set<String> usedMethodSignatures, Set<String> usedFields) {
		List<ITypeBinding> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		seen.add(currentType.getQualifiedName());

		Queue<ITypeBinding> queue = new LinkedList<>();
		enqueueSupertypes(currentType, queue);

		while (!queue.isEmpty()) {
			ITypeBinding type = queue.poll();
			if (type == null || isJavaLangObject(type)) {
				continue;
			}
			String qualName = type.getQualifiedName();
			if (!seen.add(qualName)) {
				continue;
			}
			if (!isTaggingInterface(type)
					&& declaresAllMembers(currentType, type, usedMethodSignatures, usedFields)) {
				result.add(type);
				enqueueSupertypes(type, queue);
			}
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
		if (currentType == null) {
			return null;
		}

		ITypeBinding mostGeneral = currentType;

		ITypeBinding superclass = currentType.getSuperclass();
		if (superclass != null && !isJavaLangObject(superclass) && declaresAllMembers(currentType, superclass, usedMethodSignatures, usedFields)) {
			ITypeBinding candidate = findMostGeneralType(superclass, usedMethodSignatures, usedFields);
			if (candidate != null) {
				mostGeneral = candidate;
			}
		}

		for (ITypeBinding iface : currentType.getInterfaces()) {
			// Skip tagging/marker interfaces (e.g., Serializable, Cloneable, RandomAccess)
			if (isTaggingInterface(iface)) {
				continue;
			}
			if (declaresAllMembers(currentType, iface, usedMethodSignatures, usedFields)) {
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
			Set<String> usedMethodSignatures, Set<String> usedFields) {
		if (originalType == null || candidateType == null) {
			return false;
		}

		Map<String, Set<String>> candidateMethodSignatures = new HashMap<>();
		collectMethodSignatures(candidateType, new HashSet<>(), candidateMethodSignatures);

		for (String usedSignature : usedMethodSignatures) {
			int parenIndex = usedSignature.indexOf('(');
			if (parenIndex < 0) {
				continue;
			}
			String methodName = usedSignature.substring(0, parenIndex);

			Set<String> candidateSignatures = candidateMethodSignatures.get(methodName);
			if (candidateSignatures == null || !candidateSignatures.contains(usedSignature)) {
				return false;
			}
		}

		Set<String> declaredFields = new HashSet<>();
		collectFields(candidateType, new HashSet<>(), declaredFields);

		return declaredFields.containsAll(usedFields);
	}

	/**
	 * Recursively collects method signatures for a type and its supertypes/interfaces.
	 */
	private static void collectMethodSignatures(ITypeBinding type, Set<ITypeBinding> visited,
			Map<String, Set<String>> signaturesByName) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IMethodBinding method : type.getDeclaredMethods()) {
			String signature = createMethodSignature(method);
			String name = method.getName();
			signaturesByName.computeIfAbsent(name, k -> new HashSet<>()).add(signature);
		}

		collectMethodSignatures(type.getSuperclass(), visited, signaturesByName);
		for (ITypeBinding iface : type.getInterfaces()) {
			collectMethodSignatures(iface, visited, signaturesByName);
		}
	}

	/**
	 * Recursively collects field names for a type and its supertypes.
	 */
	private static void collectFields(ITypeBinding type, Set<ITypeBinding> visited, Set<String> fieldNames) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IVariableBinding field : type.getDeclaredFields()) {
			fieldNames.add(field.getName());
		}

		collectFields(type.getSuperclass(), visited, fieldNames);
	}
}
