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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
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
		if (!(compilationUnit.getJavaElement() instanceof ICompilationUnit unit)
				|| !(unit.getJavaProject() instanceof JavaProject project)) {
			return;
		}
		try {
			var access = new ProjectTypeAccess(project, unit);
			try {
				// Keep restricted references: removing the only one can make an
				// existing @SuppressWarnings("restriction") unnecessary.
				analysisResults.values().removeIf(result -> !access.permits(result.getCurrentType(), new HashSet<>())
						|| !access.permits(result.getWidestType(), new HashSet<>()));
			} finally {
				access.cleanup();
			}
		} catch (JavaModelException exception) {
			// Keep the declaration when its replacement's project access cannot be established.
			return;
		}
		if (analysisResults.isEmpty()) {
			return;
		}

		// Collect VariableDeclarationStatement info needed for rewriting
		ReferenceHolder<String, StatementInfo> statementsByKey = new ReferenceHolder<>();
		AstProcessorBuilder.with(statementsByKey, nodesprocessed)
			.onVariableDeclarationStatement((node, holder) -> {
				if (node.fragments().size() > 1) {
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
						holder.put(varBinding.getKey(), new StatementInfo(node, fragment));
					}
				}
				return true;
			})
			.build(compilationUnit);

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

	/** Queries the same combined classpath access rules that JDT applies to a new source reference. */
	private static final class ProjectTypeAccess extends SearchableEnvironment {
		private final Map<String, Boolean> unrestricted = new HashMap<>();

		ProjectTypeAccess(JavaProject project, ICompilationUnit unit) throws JavaModelException {
			super(project, unit.getOwner(), false, JavaProject.NO_RELEASE);
			// Check both discouraged and forbidden references without changing compiler preferences.
			checkAccessRestrictions = true;
		}

		boolean permits(ITypeBinding binding, Set<ITypeBinding> visited) {
			if (binding == null || binding.isRecovered()) {
				return false;
			}
			if (!visited.add(binding) || binding.isPrimitive() || binding.isTypeVariable()) {
				return true;
			}
			if (binding.isArray()) {
				return permits(binding.getComponentType(), visited);
			}
			if (binding.isWildcardType()) {
				return binding.getBound() == null || permits(binding.getBound(), visited);
			}
			if (binding.isCapture()) {
				return permits(binding.getWildcard(), visited);
			}
			if (!(binding.getTypeDeclaration().getJavaElement() instanceof IType type)
					|| !unrestricted.computeIfAbsent(type.getHandleIdentifier(), key -> {
						var answer = find(type.getTypeQualifiedName('.'), type.getPackageFragment().getElementName(), null);
						return answer != null && answer.getAccessRestriction() == null;
					})) {
				return false;
			}
			for (ITypeBinding argument : binding.getTypeArguments()) {
				if (!permits(argument, visited)) {
					return false;
				}
			}
			return binding.getDeclaringClass() == null || permits(binding.getDeclaringClass(), visited);
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
			
			Type oldType = typeHolder.variableDeclarationStatement.getType();
			var imports = cuRewrite.getImportRewrite();
			var lexicalContext = new ContextSensitiveImportRewriteContext(oldType, imports);
			ImportRewriteContext context = lexicalContext;
			Type oldBase = oldType instanceof ParameterizedType parameterized ? parameterized.getType() : oldType;
			boolean qualified = oldBase.toString().indexOf('.') >= 0
					&& oldBase.toString().equals(typeHolder.currentType.getErasure().getQualifiedName());
			if (qualified) {
				// Preserve intentional qualification. Importing a new package on demand
				// can make existing wildcard-imported names ambiguous (for example SWT/AWT).
				context = new ImportRewriteContext() {
					@Override
					public int findInContext(String qualifier, String name, int kind) {
						return kind == KIND_TYPE && (qualifier + '.' + name).equals(typeHolder.widenedType.getErasure().getQualifiedName())
								? RES_NAME_CONFLICT : lexicalContext.findInContext(qualifier, name, kind);
					}
				};
			}
			Type newType = imports.addImport(typeHolder.widenedType, ast, context);
			Type newBase = newType instanceof ParameterizedType parameterized ? parameterized.getType() : newType;
			// ImportRemover protects simple names even for qualified nodes. A
			// qualification forced by a name clash must not retain the old import.
			boolean qualifiedReplacement = newBase.toString().equals(typeHolder.widenedType.getErasure().getQualifiedName());
			if (!qualifiedReplacement) {
				cuRewrite.getImportRemover().registerAddedImports(newType);
			} else if (newType instanceof ParameterizedType parameterized) {
				for (Object argument : parameterized.typeArguments()) {
					cuRewrite.getImportRemover().registerAddedImports((Type) argument);
				}
			}

			// Let JDT remove the old import only when no surviving reference
			// needs it. Replacing the declaration alone can create an unused import.
			cuRewrite.getImportRemover().registerRemovedNode(oldType);
			rewrite.replace(oldType, newType, group);
			
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
