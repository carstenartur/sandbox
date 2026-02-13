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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
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
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseGeneralTypeFixCore;

/**
 * Plugin that widens variable declaration types to more general supertypes/interfaces
 * based on actual usage of the variable.
 * 
 * <p>Example transformations:</p>
 * <pre>
 * // Before:
 * ArrayList&lt;String&gt; list = new ArrayList&lt;&gt;();
 * list.add("a");
 * list.size();
 * 
 * // After:
 * List&lt;String&gt; list = new ArrayList&lt;&gt;();
 * list.add("a");
 * list.size();
 * </pre>
 */
public class UseGeneralTypePlugin {

	/**
	 * Holder for variable type widening transformation data.
	 */
	public static class TypeWidenHolder {
		/** The variable declaration statement to transform */
		public VariableDeclarationStatement variableDeclarationStatement;
		/** The variable declaration fragment */
		public VariableDeclarationFragment fragment;
		/** The current declared type binding */
		public ITypeBinding currentType;
		/** The widened type binding (most general type that still supports all usages) */
		public ITypeBinding widenedType;
		/** All method signatures used on this variable (methodName + parameter types) */
		public Set<String> usedMethodSignatures = new HashSet<>();
		/** All field names accessed on this variable */
		public Set<String> usedFields = new HashSet<>();
		/** Whether the variable is cast to a specific type */
		public boolean hasCast;
		/** Whether the variable is used in instanceof check */
		public boolean hasInstanceof;
		/** Whether the variable is passed as method argument or returned (unsafe) */
		public boolean hasUnsafeUsage;
		/** Nodes that have been processed */
		public Set<ASTNode> nodesprocessed;
	}

	public void find(UseGeneralTypeFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForIfVarNotUsed) {
		
		// Single-pass visitor for better performance: collect all variable usage info in one traversal
		Map<IVariableBinding, VariableInfo> variableUsages = new HashMap<>();
		Map<IVariableBinding, VariableDeclarationInfo> variableDeclarations = new HashMap<>();
		
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				// Skip if already processed
				if (nodesprocessed.contains(node)) {
					return true;
				}
				
				// Skip multi-fragment declarations to avoid breaking compilation
				if (node.fragments().size() > 1) {
					return true;
				}
				
				// Only process local variables with explicit concrete types
				Type type = node.getType();
				if (type == null || type.isVar()) {
					return true;
				}
				
				ITypeBinding typeBinding = type.resolveBinding();
				if (typeBinding == null || typeBinding.isPrimitive() || typeBinding.isArray()) {
					return true;
				}
				
				// Process the single fragment
				for (Object fragObj : node.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragObj;
					IVariableBinding varBinding = fragment.resolveBinding();
					if (varBinding == null || varBinding.isField() || varBinding.isParameter()) {
						continue;
					}
					
					// Store declaration info
					variableDeclarations.put(varBinding, new VariableDeclarationInfo(node, fragment, typeBinding));
					variableUsages.put(varBinding, new VariableInfo());
				}
				
				return true;
			}
			
			@Override
			public boolean visit(SimpleName node) {
				IBinding binding = node.resolveBinding();
				if (!(binding instanceof IVariableBinding)) {
					return true;
				}
				
				IVariableBinding varBinding = (IVariableBinding) binding;
				VariableInfo info = variableUsages.get(varBinding);
				if (info == null) {
					return true;
				}
				
				ASTNode parent = node.getParent();
				
				// Check for casts and instanceof
				if (parent instanceof CastExpression) {
					info.hasCast = true;
				} else if (parent instanceof InstanceofExpression) {
					info.hasInstanceof = true;
				} else if (parent instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) parent;
					if (mi.getExpression() == node) {
						// Variable is the receiver of a method call
						IMethodBinding methodBinding = mi.resolveMethodBinding();
						if (methodBinding != null) {
							info.usedMethodSignatures.add(createMethodSignature(methodBinding));
						}
					} else {
						// Variable is passed as an argument - this is unsafe
						info.hasUnsafeUsage = true;
					}
				} else if (parent instanceof FieldAccess) {
					FieldAccess fa = (FieldAccess) parent;
					if (fa.getExpression() == node) {
						info.usedFields.add(fa.getName().getIdentifier());
					}
				} else if (parent instanceof QualifiedName) {
					// Handle qualified field access: obj.field
					QualifiedName qn = (QualifiedName) parent;
					if (qn.getQualifier() == node) {
						info.usedFields.add(qn.getName().getIdentifier());
					}
				} else if (parent instanceof SuperFieldAccess) {
					// Handle super.field access
					SuperFieldAccess sfa = (SuperFieldAccess) parent;
					info.usedFields.add(sfa.getName().getIdentifier());
				} else if (parent instanceof Assignment) {
					// Check if assigned to a variable with narrower type
					Assignment assignment = (Assignment) parent;
					if (assignment.getRightHandSide() == node) {
						info.hasUnsafeUsage = true;
					}
				} else if (parent instanceof ReturnStatement) {
					// Variable is returned - unsafe
					info.hasUnsafeUsage = true;
				}
				
				return true;
			}
		});
		
		// Now analyze each variable and determine if it can be widened
		ReferenceHolder<Integer, TypeWidenHolder> holder = new ReferenceHolder<>();
		
		for (Map.Entry<IVariableBinding, VariableDeclarationInfo> entry : variableDeclarations.entrySet()) {
			IVariableBinding varBinding = entry.getKey();
			VariableDeclarationInfo declInfo = entry.getValue();
			VariableInfo usageInfo = variableUsages.get(varBinding);
			
			if (usageInfo == null) {
				continue;
			}
			
			// Skip if has unsafe usage patterns
			if (usageInfo.hasCast || usageInfo.hasInstanceof || usageInfo.hasUnsafeUsage) {
				continue;
			}
			
			// Don't widen if the variable has no actual usage (no method calls, no field access)
			if (usageInfo.usedMethodSignatures.isEmpty() && usageInfo.usedFields.isEmpty()) {
				continue;
			}
			
			// Find the most general type
			ITypeBinding widenedType = findMostGeneralType(declInfo.typeBinding, usageInfo.usedMethodSignatures, usageInfo.usedFields);
			
			if (widenedType != null && !widenedType.equals(declInfo.typeBinding)) {
				TypeWidenHolder typeHolder = new TypeWidenHolder();
				typeHolder.variableDeclarationStatement = declInfo.statement;
				typeHolder.fragment = declInfo.fragment;
				typeHolder.currentType = declInfo.typeBinding;
				typeHolder.widenedType = widenedType;
				typeHolder.usedMethodSignatures = usageInfo.usedMethodSignatures;
				typeHolder.usedFields = usageInfo.usedFields;
				typeHolder.nodesprocessed = nodesprocessed;
				
				holder.put(holder.size(), typeHolder);
			}
		}
		
		if (!holder.isEmpty()) {
			operations.add(fixcore.rewrite(holder));
		}
	}
	
	/**
	 * Helper class to store variable declaration information.
	 */
	private static class VariableDeclarationInfo {
		final VariableDeclarationStatement statement;
		final VariableDeclarationFragment fragment;
		final ITypeBinding typeBinding;
		
		VariableDeclarationInfo(VariableDeclarationStatement statement, VariableDeclarationFragment fragment, ITypeBinding typeBinding) {
			this.statement = statement;
			this.fragment = fragment;
			this.typeBinding = typeBinding;
		}
	}
	
	/**
	 * Helper class to store variable usage information.
	 */
	private static class VariableInfo {
		Set<String> usedMethodSignatures = new HashSet<>();
		Set<String> usedFields = new HashSet<>();
		boolean hasCast;
		boolean hasInstanceof;
		boolean hasUnsafeUsage;
	}
	
	/**
	 * Creates a method signature string from a method binding.
	 * Format: methodName(param1Type,param2Type):returnType
	 */
	private String createMethodSignature(IMethodBinding methodBinding) {
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
	 * Walks the type hierarchy to find the most general type that declares
	 * all the required method signatures and fields.
	 */
	private ITypeBinding findMostGeneralType(ITypeBinding currentType, Set<String> usedMethodSignatures, Set<String> usedFields) {
		if (currentType == null) {
			return null;
		}
		
		ITypeBinding mostGeneral = currentType;
		
		// Check superclass
		ITypeBinding superclass = currentType.getSuperclass();
		if (superclass != null && !isJavaLangObject(superclass) && declaresAllMembers(currentType, superclass, usedMethodSignatures, usedFields)) {
			ITypeBinding candidate = findMostGeneralType(superclass, usedMethodSignatures, usedFields);
			if (candidate != null) {
				mostGeneral = candidate;
			}
		}
		
		// Check interfaces - prefer interfaces over classes
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
	private boolean isTaggingInterface(ITypeBinding type) {
		if (type == null || !type.isInterface()) {
			return false;
		}
		// A tagging/marker interface has no methods declared directly on the interface itself
		// (excluding inherited Object methods)
		return type.getDeclaredMethods().length == 0;
	}

	/**
	 * Checks if a type is java.lang.Object.
	 */
	private boolean isJavaLangObject(ITypeBinding type) {
		return type != null && "java.lang.Object".equals(type.getQualifiedName()); //$NON-NLS-1$
	}

	/**
	 * Checks if a candidate type declares all the required method signatures and fields
	 * that are available on the original type.
	 */
	private boolean declaresAllMembers(ITypeBinding originalType, ITypeBinding candidateType, Set<String> usedMethodSignatures, Set<String> usedFields) {
		if (originalType == null || candidateType == null) {
			return false;
		}
		
		// Collect method signatures from the original type hierarchy
		Map<String, Set<String>> originalMethodSignatures = new HashMap<>();
		collectMethodSignatures(originalType, new HashSet<>(), originalMethodSignatures);
		
		// Collect method signatures from the candidate type hierarchy
		Map<String, Set<String>> candidateMethodSignatures = new HashMap<>();
		collectMethodSignatures(candidateType, new HashSet<>(), candidateMethodSignatures);
		
		// Check if all used method signatures exist in the candidate type
		for (String usedSignature : usedMethodSignatures) {
			// Extract method name from signature (before the '(' character)
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
		
		// Check fields
		Set<String> declaredFields = new HashSet<>();
		collectFields(candidateType, new HashSet<>(), declaredFields);
		
		return declaredFields.containsAll(usedFields);
	}

	/**
	 * Recursively collects method signatures for a type and its supertypes/interfaces.
	 */
	private void collectMethodSignatures(ITypeBinding type, Set<ITypeBinding> visited, Map<String, Set<String>> signaturesByName) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IMethodBinding method : type.getDeclaredMethods()) {
			String signature = createMethodSignature(method);
			String name = method.getName();
			signaturesByName.computeIfAbsent(name, k -> new HashSet<>()).add(signature);
		}

		// Recurse into superclass and interfaces
		collectMethodSignatures(type.getSuperclass(), visited, signaturesByName);
		for (ITypeBinding iface : type.getInterfaces()) {
			collectMethodSignatures(iface, visited, signaturesByName);
		}
	}

	/**
	 * Recursively collects field names for a type and its supertypes.
	 */
	private void collectFields(ITypeBinding type, Set<ITypeBinding> visited, Set<String> fieldNames) {
		if (type == null || !visited.add(type)) {
			return;
		}

		for (IVariableBinding field : type.getDeclaredFields()) {
			fieldNames.add(field.getName());
		}

		// Recurse into superclass (interfaces don't have fields typically)
		collectFields(type.getSuperclass(), visited, fieldNames);
	}

	public void rewrite(UseGeneralTypeFixCore fixcore, ReferenceHolder<Integer, TypeWidenHolder> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getAST();
		
		for (TypeWidenHolder typeHolder : holder.values()) {
			if (typeHolder.nodesprocessed.contains(typeHolder.variableDeclarationStatement)) {
				continue;
			}
			
			// Create new type
			Type newType = cuRewrite.getImportRewrite().addImport(typeHolder.widenedType, ast);
			
			// Replace the type in the variable declaration statement
			rewrite.replace(typeHolder.variableDeclarationStatement.getType(), newType, group);
			
			// Mark as processed
			typeHolder.nodesprocessed.add(typeHolder.variableDeclarationStatement);
		}
	}

	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				Map<String, Integer> map = new LinkedHashMap<>();
				map.put("a", 1);
				""";
		}
		return """
			LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
			map.put("a", 1);
			""";
	}
}
