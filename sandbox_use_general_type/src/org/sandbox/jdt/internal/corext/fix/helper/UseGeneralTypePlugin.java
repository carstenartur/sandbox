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
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
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
		/** All method names used on this variable */
		public Set<String> usedMethods = new HashSet<>();
		/** All field names accessed on this variable */
		public Set<String> usedFields = new HashSet<>();
		/** Whether the variable is cast to a specific type */
		public boolean hasCast;
		/** Whether the variable is used in instanceof check */
		public boolean hasInstanceof;
		/** Nodes that have been processed */
		public Set<ASTNode> nodesprocessed;
	}

	public void find(UseGeneralTypeFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForIfVarNotUsed) {
		
		ReferenceHolder<Integer, TypeWidenHolder> holder = new ReferenceHolder<>();
		
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				// Skip if already processed
				if (nodesprocessed.contains(node)) {
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
				
				// Process each fragment
				for (Object fragObj : node.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragObj;
					IVariableBinding varBinding = fragment.resolveBinding();
					if (varBinding == null || varBinding.isField() || varBinding.isParameter()) {
						continue;
					}
					
					// Analyze variable usage
					TypeWidenHolder typeHolder = analyzeVariableUsage(compilationUnit, varBinding, node, fragment, typeBinding);
					if (typeHolder != null && typeHolder.widenedType != null && !typeHolder.widenedType.equals(typeHolder.currentType)) {
						typeHolder.nodesprocessed = nodesprocessed;
						holder.put(holder.size(), typeHolder);
					}
				}
				
				return true;
			}
		});
		
		if (!holder.isEmpty()) {
			operations.add(fixcore.rewrite(holder));
		}
	}

	/**
	 * Analyzes all usages of a variable to determine if its type can be widened.
	 */
	private TypeWidenHolder analyzeVariableUsage(CompilationUnit cu, IVariableBinding varBinding,
			VariableDeclarationStatement statement, VariableDeclarationFragment fragment, ITypeBinding currentType) {
		
		TypeWidenHolder holder = new TypeWidenHolder();
		holder.variableDeclarationStatement = statement;
		holder.fragment = fragment;
		holder.currentType = currentType;
		
		// Find all references to this variable
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				IBinding binding = node.resolveBinding();
				if (binding != null && binding.equals(varBinding)) {
					// Check if used in cast or instanceof
					ASTNode parent = node.getParent();
					if (parent instanceof CastExpression) {
						holder.hasCast = true;
					} else if (parent instanceof InstanceofExpression) {
						holder.hasInstanceof = true;
					} else if (parent instanceof MethodInvocation) {
						MethodInvocation mi = (MethodInvocation) parent;
						if (mi.getExpression() == node) {
							// This variable is the receiver of a method call
							IMethodBinding methodBinding = mi.resolveMethodBinding();
							if (methodBinding != null) {
								holder.usedMethods.add(methodBinding.getName());
							}
						}
					} else if (parent instanceof FieldAccess) {
						FieldAccess fa = (FieldAccess) parent;
						if (fa.getExpression() == node) {
							holder.usedFields.add(fa.getName().getIdentifier());
						}
					}
				}
				return true;
			}
		});
		
		// If has cast or instanceof, don't widen
		if (holder.hasCast || holder.hasInstanceof) {
			return null;
		}
		
		// Find the most general type that declares all used methods and fields
		holder.widenedType = findMostGeneralType(currentType, holder.usedMethods, holder.usedFields);
		
		return holder;
	}

	/**
	 * Walks the type hierarchy to find the most general type that declares
	 * all the required methods and fields.
	 */
	private ITypeBinding findMostGeneralType(ITypeBinding currentType, Set<String> usedMethods, Set<String> usedFields) {
		if (currentType == null) {
			return null;
		}
		
		ITypeBinding mostGeneral = currentType;
		
		// Check superclass
		ITypeBinding superclass = currentType.getSuperclass();
		if (superclass != null && declaresAllMembers(superclass, usedMethods, usedFields)) {
			ITypeBinding candidate = findMostGeneralType(superclass, usedMethods, usedFields);
			if (candidate != null) {
				mostGeneral = candidate;
			}
		}
		
		// Check interfaces
		for (ITypeBinding iface : currentType.getInterfaces()) {
			if (declaresAllMembers(iface, usedMethods, usedFields)) {
				// Prefer interfaces over classes
				mostGeneral = iface;
			}
		}
		
		return mostGeneral;
	}

	/**
	 * Checks if a type declares all the required methods and fields.
	 */
	private boolean declaresAllMembers(ITypeBinding type, Set<String> usedMethods, Set<String> usedFields) {
		if (type == null) {
			return false;
		}
		
		// Check methods
		Set<String> declaredMethods = new HashSet<>();
		for (IMethodBinding method : type.getDeclaredMethods()) {
			declaredMethods.add(method.getName());
		}
		
		// Also check inherited methods
		ITypeBinding superclass = type.getSuperclass();
		if (superclass != null) {
			for (IMethodBinding method : superclass.getDeclaredMethods()) {
				declaredMethods.add(method.getName());
			}
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			for (IMethodBinding method : iface.getDeclaredMethods()) {
				declaredMethods.add(method.getName());
			}
		}
		
		if (!declaredMethods.containsAll(usedMethods)) {
			return false;
		}
		
		// Check fields (usually fields are not in interfaces, so this is mainly for concrete classes)
		Set<String> declaredFields = new HashSet<>();
		for (IVariableBinding field : type.getDeclaredFields()) {
			declaredFields.add(field.getName());
		}
		
		return declaredFields.containsAll(usedFields);
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
