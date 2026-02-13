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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseGeneralTypeFixCore;
import org.sandbox.jdt.internal.corext.util.TypeWideningAnalyzer;

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

		// Use shared TypeWideningAnalyzer from sandbox_common
		Map<String, TypeWideningAnalyzer.TypeWideningResult> analysisResults =
				TypeWideningAnalyzer.analyzeCompilationUnit(compilationUnit);

		if (analysisResults.isEmpty()) {
			return;
		}

		// Collect VariableDeclarationStatement info needed for rewriting
		Map<String, StatementInfo> statementsByKey = new HashMap<>();
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				if (nodesprocessed.contains(node) || node.fragments().size() > 1) {
					return true;
				}
				Type type = node.getType();
				if (type == null || type.isVar()) {
					return true;
				}
				for (Object fragObj : node.fragments()) {
					VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragObj;
					IVariableBinding varBinding = fragment.resolveBinding();
					if (varBinding != null) {
						statementsByKey.put(varBinding.getKey(), new StatementInfo(node, fragment));
					}
				}
				return true;
			}
		});

		// Build TypeWidenHolder entries from analysis results
		ReferenceHolder<Integer, TypeWidenHolder> holder = new ReferenceHolder<>();

		for (TypeWideningAnalyzer.TypeWideningResult result : analysisResults.values()) {
			StatementInfo stmtInfo = statementsByKey.get(result.getVariableBinding().getKey());
			if (stmtInfo == null) {
				continue;
			}

			TypeWidenHolder typeHolder = new TypeWidenHolder();
			typeHolder.variableDeclarationStatement = stmtInfo.statement;
			typeHolder.fragment = stmtInfo.fragment;
			typeHolder.currentType = result.getCurrentType();
			typeHolder.widenedType = result.getWidestType();
			typeHolder.usedMethodSignatures = new HashSet<>();
			typeHolder.usedFields = new HashSet<>();
			typeHolder.nodesprocessed = nodesprocessed;

			holder.put(holder.size(), typeHolder);
		}

		if (!holder.isEmpty()) {
			operations.add(fixcore.rewrite(holder));
		}
	}

	/**
	 * Helper class to store variable declaration statement information needed for rewriting.
	 */
	private static class StatementInfo {
		final VariableDeclarationStatement statement;
		final VariableDeclarationFragment fragment;

		StatementInfo(VariableDeclarationStatement statement, VariableDeclarationFragment fragment) {
			this.statement = statement;
			this.fragment = fragment;
		}
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
