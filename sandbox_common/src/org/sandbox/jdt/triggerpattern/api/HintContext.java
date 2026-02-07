/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;

/**
 * Context information provided to hint implementations.
 * 
 * <p>This class provides access to the compilation unit, the matched pattern result,
 * and tools for making code changes (ASTRewrite, ImportRewrite).</p>
 * 
 * @since 1.2.2
 */
public final class HintContext {
	private final CompilationUnit cu;
	private final ICompilationUnit icu;
	private final Match match;
	private final ASTRewrite rewrite;
	private ImportRewrite importRewrite;
	private final AtomicBoolean cancel;
	
	/**
	 * Creates a new hint context.
	 * 
	 * @param cu the parsed compilation unit
	 * @param icu the ICompilationUnit (workspace model)
	 * @param match the pattern match result
	 * @param rewrite the AST rewrite for making changes
	 */
	public HintContext(CompilationUnit cu, ICompilationUnit icu, Match match, ASTRewrite rewrite) {
		this(cu, icu, match, rewrite, new AtomicBoolean(false));
	}
	
	/**
	 * Creates a new hint context with cancellation support.
	 * 
	 * @param cu the parsed compilation unit
	 * @param icu the ICompilationUnit (workspace model)
	 * @param match the pattern match result
	 * @param rewrite the AST rewrite for making changes
	 * @param cancel atomic boolean for cancellation signaling
	 */
	public HintContext(CompilationUnit cu, ICompilationUnit icu, Match match, ASTRewrite rewrite, AtomicBoolean cancel) {
		this.cu = cu;
		this.icu = icu;
		this.match = match;
		this.rewrite = rewrite;
		this.cancel = cancel != null ? cancel : new AtomicBoolean(false);
	}
	
	/**
	 * Returns the parsed compilation unit.
	 * 
	 * @return the compilation unit
	 */
	public CompilationUnit getCompilationUnit() {
		return cu;
	}
	
	/**
	 * Returns the ICompilationUnit (workspace model).
	 * 
	 * @return the ICompilationUnit
	 */
	public ICompilationUnit getICompilationUnit() {
		return icu;
	}
	
	/**
	 * Returns the pattern match.
	 * 
	 * @return the match
	 */
	public Match getMatch() {
		return match;
	}
	
	/**
	 * Returns the AST rewrite for making code changes.
	 * 
	 * @return the AST rewrite
	 */
	public ASTRewrite getASTRewrite() {
		return rewrite;
	}
	
	/**
	 * Returns the import rewrite for managing imports.
	 * Creates one lazily if needed.
	 * 
	 * @return the import rewrite
	 */
	public ImportRewrite getImportRewrite() {
		if (importRewrite == null) {
			try {
				importRewrite = ImportRewrite.create(cu, true);
			} catch (Exception e) {
				// Return null if creation fails
				return null;
			}
		}
		return importRewrite;
	}
	
	/**
	 * Sets the import rewrite.
	 * 
	 * @param importRewrite the import rewrite to use
	 */
	public void setImportRewrite(ImportRewrite importRewrite) {
		this.importRewrite = importRewrite;
	}
	
	/**
	 * Checks if the operation has been canceled.
	 * 
	 * @return true if canceled
	 */
	public boolean isCanceled() {
		return cancel.get();
	}
	
	/**
	 * Returns single-placeholder bindings only.
	 * 
	 * <p>Filters out multi-placeholder bindings (those with values that are lists).</p>
	 * 
	 * @return map of placeholder names to their bound AST nodes
	 */
	public Map<String, ASTNode> getVariables() {
		Map<String, ASTNode> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : match.getBindings().entrySet()) {
			if (entry.getValue() instanceof ASTNode) {
				result.put(entry.getKey(), (ASTNode) entry.getValue());
			}
		}
		return result;
	}
	
	/**
	 * Returns multi-placeholder bindings only.
	 * 
	 * <p>Filters out single-placeholder bindings (those with ASTNode values).</p>
	 * 
	 * @return map of placeholder names to their lists of bound AST nodes
	 */
	@SuppressWarnings("unchecked")
	public Map<String, List<ASTNode>> getMultiVariables() {
		Map<String, List<ASTNode>> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : match.getBindings().entrySet()) {
			if (entry.getValue() instanceof List<?>) {
				result.put(entry.getKey(), (List<ASTNode>) entry.getValue());
			}
		}
		return result;
	}
	
	/**
	 * Returns a map of placeholder names to their source text representations.
	 * 
	 * <p>For single placeholders, returns the toString() of the bound node.
	 * For multi-placeholders, returns a comma-separated string of all nodes.</p>
	 * 
	 * @return map of placeholder names to source text
	 */
	public Map<String, String> getVariableNames() {
		Map<String, String> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : match.getBindings().entrySet()) {
			String name = entry.getKey();
			Object value = entry.getValue();
			
			if (value instanceof ASTNode) {
				result.put(name, value.toString());
			} else if (value instanceof List<?>) {
				@SuppressWarnings("unchecked")
				List<ASTNode> nodes = (List<ASTNode>) value;
				String text = nodes.stream()
					.map(Object::toString)
					.collect(Collectors.joining(", ")); //$NON-NLS-1$
				result.put(name, text);
			}
		}
		return result;
	}
}
