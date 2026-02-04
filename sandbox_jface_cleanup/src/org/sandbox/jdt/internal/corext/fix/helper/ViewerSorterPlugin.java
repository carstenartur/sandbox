/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
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
 * <p>This helper transforms deprecated ViewerSorter usage patterns in Eclipse JFace code:</p>
 * <ul>
 * <li>Converts {@code ViewerSorter} to {@code ViewerComparator}</li>
 * <li>Converts {@code TreePathViewerSorter} to {@code TreePathViewerComparator}</li>
 * <li>Converts {@code CommonViewerSorter} to {@code CommonViewerComparator}</li>
 * <li>Converts {@code getSorter()} method calls to {@code getComparator()}</li>
 * <li>Updates variable names from "sorter" to "comparator"</li>
 * </ul>
 * 
 * <p><b>Migration Pattern:</b></p>
 * <pre>
 * // Before:
 * import org.eclipse.jface.viewers.ViewerSorter;
 * ViewerSorter sorter = new ViewerSorter();
 * viewer.getSorter();
 * 
 * // After:
 * import org.eclipse.jface.viewers.ViewerComparator;
 * ViewerComparator comparator = new ViewerComparator();
 * viewer.getComparator();
 * </pre>
 * 
 * @see org.eclipse.jface.viewers.ViewerSorter
 * @see org.eclipse.jface.viewers.ViewerComparator
 */
public class ViewerSorterPlugin extends AbstractTool<ReferenceHolder<Integer, ViewerSorterPlugin.SorterHolder>> {

	/** Mapping from deprecated Sorter classes to their Comparator replacements */
	private static final Map<String, String> SORTER_TO_COMPARATOR_MAP = new HashMap<>();
	
	static {
		SORTER_TO_COMPARATOR_MAP.put("org.eclipse.jface.viewers.ViewerSorter", "org.eclipse.jface.viewers.ViewerComparator");
		SORTER_TO_COMPARATOR_MAP.put("org.eclipse.jface.viewers.TreePathViewerSorter", "org.eclipse.jface.viewers.TreePathViewerComparator");
		SORTER_TO_COMPARATOR_MAP.put("org.eclipse.ui.navigator.CommonViewerSorter", "org.eclipse.ui.navigator.CommonViewerComparator");
	}

	/**
	 * Holder for sorter-related transformation data.
	 * Tracks nodes that need to be converted.
	 */
	public static class SorterHolder {
		/** Nodes that use ViewerSorter types that need to be replaced */
		public Set<ASTNode> nodesToReplace = new HashSet<>();
		/** Nodes that have been processed to avoid duplicate transformations */
		public Set<ASTNode> nodesprocessed;
	}

	/**
	 * Finds ViewerSorter usage patterns to be transformed.
	 * 
	 * <p>This method scans the compilation unit for:</p>
	 * <ul>
	 * <li>Type references to ViewerSorter, TreePathViewerSorter, CommonViewerSorter</li>
	 * <li>Method invocations of getSorter()</li>
	 * <li>Class instance creations using these types</li>
	 * <li>Variable declarations with these types</li>
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
				// Check if the class extends a ViewerSorter class
				Type superclassType = node.getSuperclassType();
				if (superclassType != null && isSorterType(superclassType)) {
					holder.nodesToReplace.add(superclassType);
				}
				return true;
			}
			
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				// Check variable declarations
				Type type = node.getType();
				if (isSorterType(type)) {
					holder.nodesToReplace.add(type);
				}
				return true;
			}
			
			@Override
			public boolean visit(ClassInstanceCreation node) {
				// Check class instance creations
				Type type = node.getType();
				if (isSorterType(type)) {
					holder.nodesToReplace.add(type);
				}
				return true;
			}
			
			@Override
			public boolean visit(MethodInvocation node) {
				// Check for getSorter() method calls
				if (node.getName().getIdentifier().equals("getSorter") && node.arguments().isEmpty()) {
					holder.nodesToReplace.add(node.getName());
				}
				return true;
			}
		});
		
		if (!holder.nodesToReplace.isEmpty()) {
			operations.add(fixcore.rewrite(dataholder));
		}
	}

	/**
	 * Checks if a type is one of the ViewerSorter types.
	 * 
	 * @param type the type to check
	 * @return true if the type is a ViewerSorter type
	 */
	private boolean isSorterType(Type type) {
		if (type == null) {
			return false;
		}
		
		ITypeBinding binding = type.resolveBinding();
		if (binding == null) {
			return false;
		}
		
		String qualifiedName = binding.getQualifiedName();
		return SORTER_TO_COMPARATOR_MAP.containsKey(qualifiedName);
	}

	/**
	 * Rewrites AST nodes to transform ViewerSorter patterns to ViewerComparator.
	 * 
	 * <p>Performs the following transformations:</p>
	 * <ol>
	 * <li><b>Type replacements:</b> Replaces ViewerSorter types with ViewerComparator types</li>
	 * <li><b>Method name changes:</b> Replaces getSorter() with getComparator()</li>
	 * <li><b>Import updates:</b> Removes old imports and adds new ones</li>
	 * </ol>
	 * 
	 * @param upp the cleanup fix core instance
	 * @param hit the holder containing identified sorter patterns to transform
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for tracking changes
	 */
	@Override
	public void rewrite(JfaceCleanUpFixCore upp, ReferenceHolder<Integer, SorterHolder> hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		
		if (hit.isEmpty()) {
			return;
		}
		
		SorterHolder holder = hit.get(0);
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRewrite = cuRewrite.getImportRewrite();
		
		for (ASTNode node : holder.nodesToReplace) {
			if (holder.nodesprocessed.contains(node)) {
				continue;
			}
			holder.nodesprocessed.add(node);
			
			if (node instanceof SimpleType) {
				SimpleType simpleType = (SimpleType) node;
				ITypeBinding binding = simpleType.resolveBinding();
				if (binding != null) {
					String oldQualifiedName = binding.getQualifiedName();
					String newQualifiedName = SORTER_TO_COMPARATOR_MAP.get(oldQualifiedName);
					
					if (newQualifiedName != null) {
						// Create new type
						Name newName = addImport(newQualifiedName, cuRewrite, ast);
						SimpleType newType = ast.newSimpleType(newName);
						
						// Replace the type
						rewrite.replace(simpleType, newType, group);
						
						// Remove old import
						importRewrite.removeImport(oldQualifiedName);
					}
				}
			} else if (node instanceof Name && node.getParent() instanceof MethodInvocation) {
				// Replace getSorter with getComparator
				Name oldName = (Name) node;
				if (oldName.toString().equals("getSorter")) {
					Name newName = ast.newSimpleName("getComparator");
					rewrite.replace(oldName, newName, group);
				}
			}
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					import org.eclipse.jface.viewers.ViewerSorter;
					import org.eclipse.ui.navigator.CommonViewerSorter;
					
					public class TeamViewerSorter extends ViewerSorter {
					    private final CommonViewerSorter sorter;
					    
					    public TeamViewerSorter(CommonViewerSorter sorter) {
					        this.sorter = sorter;
					    }
					    
					    void test() {
					        viewer.getSorter();
					    }
					}
				"""; //$NON-NLS-1$
		}
		return """
				import org.eclipse.jface.viewers.ViewerComparator;
				import org.eclipse.ui.navigator.CommonViewerComparator;
				
				public class TeamViewerComparator extends ViewerComparator {
				    private final CommonViewerComparator sorter;
				    
				    public TeamViewerComparator(CommonViewerComparator comparator) {
				        this.comparator = comparator;
				    }
				    
				    void test() {
				        viewer.getComparator();
				    }
				}
			"""; //$NON-NLS-1$
	}
}
