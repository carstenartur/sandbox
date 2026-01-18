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

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
	
	/**
	 * Creates a new hint context.
	 * 
	 * @param cu the parsed compilation unit
	 * @param icu the ICompilationUnit (workspace model)
	 * @param match the pattern match result
	 * @param rewrite the AST rewrite for making changes
	 */
	public HintContext(CompilationUnit cu, ICompilationUnit icu, Match match, ASTRewrite rewrite) {
		this.cu = cu;
		this.icu = icu;
		this.match = match;
		this.rewrite = rewrite;
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
}
