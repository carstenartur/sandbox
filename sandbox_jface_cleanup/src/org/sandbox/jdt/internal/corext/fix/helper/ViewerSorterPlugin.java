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

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 * Cleanup transformation for migrating from deprecated ViewerSorter to ViewerComparator.
 * 
 * <p>This helper transforms ViewerSorter usage patterns in Eclipse JFace code:</p>
 * <ul>
 * <li>Converts {@code ViewerSorter} to {@code ViewerComparator}</li>
 * <li>Converts {@code TreePathViewerSorter} to {@code TreePathViewerComparator}</li>
 * <li>Converts {@code CommonViewerSorter} to {@code CommonViewerComparator}</li>
 * <li>Converts {@code getSorter()} and {@code setSorter()} method calls to {@code getComparator()} and {@code setComparator()} respectively (for JFace viewers only)</li>
 * </ul>
 * 
 * <p><b>Migration Pattern:</b></p>
 * <pre>
 * // Before:
 * public class MyViewer extends ViewerSorter {
 *     viewer.setSorter(new ViewerSorter());
 *     ViewerSorter sorter = viewer.getSorter();
 * }
 * 
 * // After:
 * public class MyViewer extends ViewerComparator {
 *     viewer.setComparator(new ViewerComparator());
 *     ViewerComparator comparator = viewer.getComparator();
 * }
 * </pre>
 * 
 * @see org.eclipse.jface.viewers.ViewerSorter
 * @see org.eclipse.jface.viewers.ViewerComparator
 */
public class ViewerSorterPlugin extends
AbstractTool<ReferenceHolder<Integer, ViewerSorterPlugin.SorterHolder>> {

	/** Deprecated ViewerSorter class */
	private static final String VIEWER_SORTER = "org.eclipse.jface.viewers.ViewerSorter"; //$NON-NLS-1$
	
	/** Replacement ViewerComparator class */
	private static final String VIEWER_COMPARATOR = "org.eclipse.jface.viewers.ViewerComparator"; //$NON-NLS-1$
	
	/** Deprecated TreePathViewerSorter class */
	private static final String TREEPATH_VIEWER_SORTER = "org.eclipse.ui.navigator.TreePathViewerSorter"; //$NON-NLS-1$
	
	/** Replacement TreePathViewerComparator class */
	private static final String TREEPATH_VIEWER_COMPARATOR = "org.eclipse.jface.viewers.TreePathViewerComparator"; //$NON-NLS-1$
	
	/** Deprecated CommonViewerSorter class */
	private static final String COMMON_VIEWER_SORTER = "org.eclipse.ui.navigator.CommonViewerSorter"; //$NON-NLS-1$
	
	/** Replacement CommonViewerComparator class */
	private static final String COMMON_VIEWER_COMPARATOR = "org.eclipse.ui.navigator.CommonViewerComparator"; //$NON-NLS-1$

	/**
	 * Holder for ViewerSorter-related transformation data.
	 * Tracks types and method names that need to be replaced.
	 */
	public static class SorterHolder {
		/** Types that need to be replaced */
		public Set<Type> typesToReplace = new HashSet<>();
		/** Method names that need to be replaced (e.g. getSorter -> getComparator, setSorter -> setComparator) */
		public Set<Name> methodNamesToReplace = new HashSet<>();
		/** Nodes that have been processed to avoid duplicate transformations */
		public Set<ASTNode> nodesprocessed;
	}

	/**
	 * Checks if a type is one of the deprecated ViewerSorter types.
	 * 
	 * @param typeBinding the type binding to check
	 * @return {@code true} if it's a ViewerSorter type, {@code false} otherwise
	 */
	private static boolean isViewerSorterType(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return false;
		}
		String qualifiedName = typeBinding.getQualifiedName();
		return VIEWER_SORTER.equals(qualifiedName) 
				|| TREEPATH_VIEWER_SORTER.equals(qualifiedName)
				|| COMMON_VIEWER_SORTER.equals(qualifiedName);
	}

	/**
	 * Checks if a type is one of the deprecated ViewerSorter types based on the Type node.
	 * This method handles both cases: when type binding is available and when it's not.
	 * 
	 * @param type the Type AST node to check
	 * @return {@code true} if it's a ViewerSorter type, {@code false} otherwise
	 */
	private static boolean isViewerSorterType(Type type) {
		if (type == null) {
			return false;
		}
		
		// First try to resolve the binding (most reliable)
		ITypeBinding typeBinding = type.resolveBinding();
		if (typeBinding != null && !typeBinding.isRecovered()) {
			// Only trust the binding if it's fully resolved
			return isViewerSorterType(typeBinding);
		}
		
		// Fallback: check by type name string (for when bindings aren't available or are recovered/incomplete)
		String typeName = getTypeName(type);
		return isViewerSorterTypeName(typeName);
	}

	/**
	 * Extracts the type name from a Type AST node.
	 * 
	 * @param type the Type AST node
	 * @return the simple or qualified type name, or null if not determinable
	 */
	private static String getTypeName(Type type) {
		if (type == null) {
			return null;
		}
		if (type.isSimpleType()) {
			return ((org.eclipse.jdt.core.dom.SimpleType) type).getName().getFullyQualifiedName();
		}
		if (type.isQualifiedType()) {
			return ((org.eclipse.jdt.core.dom.QualifiedType) type).getName().getFullyQualifiedName();
		}
		if (type.isNameQualifiedType()) {
			return ((org.eclipse.jdt.core.dom.NameQualifiedType) type).getName().getFullyQualifiedName();
		}
		return type.toString();
	}

	/**
	 * Checks if a type name (simple or qualified) matches a ViewerSorter type.
	 * 
	 * @param typeName the type name to check
	 * @return {@code true} if it matches a ViewerSorter type, {@code false} otherwise
	 */
	private static boolean isViewerSorterTypeName(String typeName) {
		if (typeName == null) {
			return false;
		}
		// Check both simple and qualified names
		return "ViewerSorter".equals(typeName) //$NON-NLS-1$
				|| VIEWER_SORTER.equals(typeName)
				|| "TreePathViewerSorter".equals(typeName) //$NON-NLS-1$
				|| TREEPATH_VIEWER_SORTER.equals(typeName)
				|| "CommonViewerSorter".equals(typeName) //$NON-NLS-1$
				|| COMMON_VIEWER_SORTER.equals(typeName);
	}

	/**
	 * Gets the replacement type name for a given ViewerSorter type.
	 * Works with both resolved bindings and type name strings.
	 * 
	 * @param type the Type AST node
	 * @return the replacement qualified type name, or null if not a ViewerSorter type
	 */
	private static String getReplacementTypeName(Type type) {
		if (type == null) {
			return null;
		}
		
		// First try to resolve the binding (only if fully resolved)
		ITypeBinding typeBinding = type.resolveBinding();
		if (typeBinding != null && !typeBinding.isRecovered()) {
			return getReplacementQualifiedTypeName(typeBinding);
		}
		
		// Fallback: determine replacement based on type name string
		String typeName = getTypeName(type);
		if (typeName == null) {
			return null;
		}
		
		if ("ViewerSorter".equals(typeName) || VIEWER_SORTER.equals(typeName)) { //$NON-NLS-1$
			return VIEWER_COMPARATOR;
		} else if ("TreePathViewerSorter".equals(typeName) || TREEPATH_VIEWER_SORTER.equals(typeName)) { //$NON-NLS-1$
			return TREEPATH_VIEWER_COMPARATOR;
		} else if ("CommonViewerSorter".equals(typeName) || COMMON_VIEWER_SORTER.equals(typeName)) { //$NON-NLS-1$
			return COMMON_VIEWER_COMPARATOR;
		}
		return null;
	}

	/**
	 * Gets the fully qualified replacement type name for a deprecated ViewerSorter type.
	 * 
	 * @param typeBinding the type binding of the deprecated type
	 * @return the fully qualified replacement type name
	 */
	private static String getReplacementQualifiedTypeName(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return null;
		}
		String qualifiedName = typeBinding.getQualifiedName();
		if (VIEWER_SORTER.equals(qualifiedName)) {
			return VIEWER_COMPARATOR;
		} else if (TREEPATH_VIEWER_SORTER.equals(qualifiedName)) {
			return TREEPATH_VIEWER_COMPARATOR;
		} else if (COMMON_VIEWER_SORTER.equals(qualifiedName)) {
			return COMMON_VIEWER_COMPARATOR;
		}
		return null;
	}

	/**
	 * Checks if a method is a JFace viewer getSorter() or setSorter() method.
	 * 
	 * @param methodBinding the method binding to check
	 * @return {@code true} if it's a JFace viewer getSorter() or setSorter() method, {@code false} otherwise
	 */
	private static boolean isJFaceViewerSorterMethod(IMethodBinding methodBinding) {
		if (methodBinding == null) {
			return false;
		}
		
		String methodName = methodBinding.getName();
		
		// Check method name is getSorter or setSorter
		if ("getSorter".equals(methodName)) { //$NON-NLS-1$
			// getSorter should have no parameters
			if (methodBinding.getParameterTypes().length != 0) {
				return false;
			}
		} else if ("setSorter".equals(methodName)) { //$NON-NLS-1$
			// setSorter should have exactly one parameter
			if (methodBinding.getParameterTypes().length != 1) {
				return false;
			}
		} else {
			return false;
		}
		
		// Check declaring class is a JFace viewer
		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}
		
		// Check if declaring class or any of its supertypes is a JFace viewer
		return isJFaceViewer(declaringClass);
	}

	/**
	 * Checks if a method invocation is a JFace viewer getSorter() or setSorter() method
	 * based on the expression type name (fallback when bindings are not available).
	 * 
	 * @param node the method invocation to check
	 * @return {@code true} if it appears to be a JFace viewer sorter method, {@code false} otherwise
	 */
	private static boolean isJFaceViewerSorterMethodByName(MethodInvocation node) {
		if (node == null || node.getExpression() == null) {
			return false;
		}
		
		// Try to get the type of the expression
		org.eclipse.jdt.core.dom.Expression expr = node.getExpression();
		ITypeBinding exprType = expr.resolveTypeBinding();
		
		// Only trust fully resolved bindings
		if (exprType != null && !exprType.isRecovered()) {
			return isJFaceViewer(exprType);
		}
		
		// Fallback: check by variable name or type name
		if (expr instanceof SimpleName simpleName) {
			String name = simpleName.getIdentifier();
			// Heuristic: if the variable name contains "viewer" (case-insensitive),
			// it's likely a viewer
			if (name.toLowerCase().contains("viewer")) { //$NON-NLS-1$
				return true;
			}
		}
		
		// Additional fallback: check if the type name (from recovered binding) contains "Viewer"
		if (exprType != null && exprType.isRecovered()) {
			String typeName = exprType.getName();
			if (typeName != null && typeName.contains("Viewer")) { //$NON-NLS-1$
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Checks if a type is a JFace viewer (or subtype).
	 * 
	 * @param typeBinding the type binding to check
	 * @return {@code true} if it's a JFace viewer type, {@code false} otherwise
	 */
	private static boolean isJFaceViewer(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return false;
		}
		
		String qualifiedName = typeBinding.getQualifiedName();
		
		// Check common JFace viewer types
		if (qualifiedName.startsWith("org.eclipse.jface.viewers.")) { //$NON-NLS-1$
			if (qualifiedName.equals("org.eclipse.jface.viewers.StructuredViewer") //$NON-NLS-1$
					|| qualifiedName.equals("org.eclipse.jface.viewers.ContentViewer") //$NON-NLS-1$
					|| qualifiedName.equals("org.eclipse.jface.viewers.TableViewer") //$NON-NLS-1$
					|| qualifiedName.equals("org.eclipse.jface.viewers.TreeViewer") //$NON-NLS-1$
					|| qualifiedName.equals("org.eclipse.jface.viewers.ListViewer") //$NON-NLS-1$
					|| qualifiedName.equals("org.eclipse.jface.viewers.ComboViewer")) { //$NON-NLS-1$
				return true;
			}
		}
		
		// Check superclass
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null && isJFaceViewer(superclass)) {
			return true;
		}
		
		return false;
	}

	/**
	 * Finds and identifies ViewerSorter usage patterns to be transformed.
	 * 
	 * <p>This method scans the compilation unit for:</p>
	 * <ul>
	 * <li>Type declarations extending ViewerSorter classes</li>
	 * <li>Field declarations using ViewerSorter types</li>
	 * <li>Variable declarations using ViewerSorter types</li>
	 * <li>Method return types and parameters using ViewerSorter types</li>
	 * <li>ClassInstanceCreation of ViewerSorter types</li>
	 * <li>Cast expressions to ViewerSorter types</li>
	 * <li>getSorter() and setSorter() method invocations on JFace viewers</li>
	 * </ul>
	 * 
	 * @param fixcore the cleanup fix core instance
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect identified cleanup operations
	 * @param nodesprocessed set of nodes already processed to avoid duplicates
	 * @param createForOnlyIfVarUsed flag to control when operations are created (unused in this implementation)
	 */
	@Override
	public void find(JfaceCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		
		ReferenceHolder<Integer, SorterHolder> dataholder = new ReferenceHolder<>();
		SorterHolder holder = new SorterHolder();
		holder.nodesprocessed = nodesprocessed;
		dataholder.put(0, holder);
		
		compilationUnit.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(TypeDeclaration node) {
				// Check extends clause
				Type superclassType = node.getSuperclassType();
				if (superclassType != null && isViewerSorterType(superclassType)) {
					holder.typesToReplace.add(superclassType);
				}
				return true;
			}
			
			@Override
			public boolean visit(FieldDeclaration node) {
				Type fieldType = node.getType();
				if (fieldType != null && isViewerSorterType(fieldType)) {
					holder.typesToReplace.add(fieldType);
				}
				return true;
			}
			
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				Type variableType = node.getType();
				if (variableType != null && isViewerSorterType(variableType)) {
					holder.typesToReplace.add(variableType);
				}
				return true;
			}
			
			@Override
			public boolean visit(MethodDeclaration node) {
				// Check return type
				Type returnType = node.getReturnType2();
				if (returnType != null && isViewerSorterType(returnType)) {
					holder.typesToReplace.add(returnType);
				}
				return true;
			}
			
			@Override
			public boolean visit(SingleVariableDeclaration node) {
				// Check parameter type
				Type paramType = node.getType();
				if (paramType != null && isViewerSorterType(paramType)) {
					holder.typesToReplace.add(paramType);
				}
				return true;
			}
			
			@Override
			public boolean visit(ClassInstanceCreation node) {
				Type instanceType = node.getType();
				if (instanceType != null && isViewerSorterType(instanceType)) {
					holder.typesToReplace.add(instanceType);
				}
				return true;
			}
			
			@Override
			public boolean visit(CastExpression node) {
				Type castType = node.getType();
				if (castType != null && isViewerSorterType(castType)) {
					holder.typesToReplace.add(castType);
				}
				return true;
			}
			
			@Override
			public boolean visit(MethodInvocation node) {
				SimpleName methodName = node.getName();
				if (methodName != null) {
					String name = methodName.getIdentifier();
					if ("getSorter".equals(name) || "setSorter".equals(name)) { //$NON-NLS-1$ //$NON-NLS-2$
						// Check if this is a JFace viewer method - first try binding, then fallback to name-based check
						IMethodBinding methodBinding = node.resolveMethodBinding();
						if (isJFaceViewerSorterMethod(methodBinding) || isJFaceViewerSorterMethodByName(node)) {
							holder.methodNamesToReplace.add(methodName);
						}
					}
				}
				return true;
			}
		});
		
		// If we found anything to replace, register the operation
		if (!holder.typesToReplace.isEmpty() || !holder.methodNamesToReplace.isEmpty()) {
			operations.add(fixcore.rewrite(dataholder));
		}
	}

	/**
	 * Rewrites AST nodes to transform ViewerSorter patterns to ViewerComparator.
	 * 
	 * <p>Performs transformations on:</p>
	 * <ol>
	 * <li>All Type nodes that reference ViewerSorter classes</li>
	 * <li>All method names that are getSorter() or setSorter() calls on JFace viewers</li>
	 * </ol>
	 * 
	 * <p>The transformation ensures:</p>
	 * <ul>
	 * <li>Correct type replacement based on the original type</li>
	 * <li>Removal of old imports</li>
	 * <li>Addition of new imports</li>
	 * <li>Method name changes (getSorter → getComparator, setSorter → setComparator)</li>
	 * </ul>
	 * 
	 * @param upp the cleanup fix core instance
	 * @param hit the holder containing identified ViewerSorter patterns to transform
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for tracking changes
	 */
	@Override
	public void rewrite(JfaceCleanUpFixCore upp, final ReferenceHolder<Integer, SorterHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRewrite = cuRewrite.getImportRewrite();
		
		if (hit.isEmpty()) {
			return;
		}
		
		SorterHolder holder = hit.get(0);
		
		// Replace all types
		for (Type typeToReplace : holder.typesToReplace) {
			if (!holder.nodesprocessed.contains(typeToReplace)) {
				holder.nodesprocessed.add(typeToReplace);
				
				// Use the new helper that handles null bindings
				String replacementQualifiedName = getReplacementTypeName(typeToReplace);
				
				if (replacementQualifiedName != null) {
					// Create new type with proper import
					Name newTypeName = addImport(replacementQualifiedName, cuRewrite, ast);
					Type newType = ast.newSimpleType(newTypeName);
					
					// Replace the type
					rewrite.replace(typeToReplace, newType, group);
					
					// Remove old import (if binding is available and fully resolved)
					ITypeBinding typeBinding = typeToReplace.resolveBinding();
					if (typeBinding != null && !typeBinding.isRecovered()) {
						String oldQualifiedName = typeBinding.getQualifiedName();
						importRewrite.removeImport(oldQualifiedName);
					} else {
						// Fallback: remove import based on type name
						String typeName = getTypeName(typeToReplace);
						if (typeName != null) {
							// Try to remove both simple and qualified versions
							if ("ViewerSorter".equals(typeName)) { //$NON-NLS-1$
								importRewrite.removeImport(VIEWER_SORTER);
							} else if ("TreePathViewerSorter".equals(typeName)) { //$NON-NLS-1$
								importRewrite.removeImport(TREEPATH_VIEWER_SORTER);
							} else if ("CommonViewerSorter".equals(typeName)) { //$NON-NLS-1$
								importRewrite.removeImport(COMMON_VIEWER_SORTER);
							} else {
								// Already qualified name
								importRewrite.removeImport(typeName);
							}
						}
					}
				}
			}
		}
		
		// Replace all method names
		for (Name methodNameToReplace : holder.methodNamesToReplace) {
			if (!holder.nodesprocessed.contains(methodNameToReplace)) {
				holder.nodesprocessed.add(methodNameToReplace);
				
				// Determine the replacement method name based on the original
				String originalName = methodNameToReplace.toString();
				String replacementName;
				if ("getSorter".equals(originalName)) { //$NON-NLS-1$
					replacementName = "getComparator"; //$NON-NLS-1$
				} else if ("setSorter".equals(originalName)) { //$NON-NLS-1$
					replacementName = "setComparator"; //$NON-NLS-1$
				} else {
					// Should not happen, but keep original name as fallback
					replacementName = originalName;
				}
				
				// Create new method name
				SimpleName newMethodName = ast.newSimpleName(replacementName);
				
				// Replace the method name
				rewrite.replace(methodNameToReplace, newMethodName, group);
			}
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					import org.eclipse.jface.viewers.ViewerSorter;
					import org.eclipse.jface.viewers.TableViewer;
					public class MyClass extends ViewerSorter {
						private ViewerSorter sorter;
						public void configure(TableViewer viewer) {
							viewer.setSorter(new ViewerSorter());
							ViewerSorter s = viewer.getSorter();
						}
					}
				"""; //$NON-NLS-1$
		}
		return """
				import org.eclipse.jface.viewers.ViewerComparator;
				import org.eclipse.jface.viewers.TableViewer;
				public class MyClass extends ViewerComparator {
					private ViewerComparator sorter;
					public void configure(TableViewer viewer) {
						viewer.setComparator(new ViewerComparator());
						ViewerComparator s = viewer.getComparator();
					}
				}
			"""; //$NON-NLS-1$
	}
}
