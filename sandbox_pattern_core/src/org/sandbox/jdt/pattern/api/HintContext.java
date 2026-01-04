/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.api;

import java.util.Objects;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * Context information provided to hint methods when a pattern match is found.
 * <p>
 * This class provides access to:
 * <ul>
 * <li>The matched pattern and binding information</li>
 * <li>The compilation unit being processed</li>
 * <li>An AST rewrite for making changes</li>
 * </ul>
 * </p>
 * 
 * @since 1.0
 */
public final class HintContext {
	private final CompilationUnit cu;
	private final ICompilationUnit icu;
	private final Match match;
	private final ASTRewrite rewrite;

	/**
	 * Creates a new hint context.
	 * 
	 * @param cu the compilation unit AST
	 * @param icu the compilation unit
	 * @param match the pattern match
	 * @param rewrite the AST rewrite for making changes
	 */
	public HintContext(CompilationUnit cu, ICompilationUnit icu, Match match, ASTRewrite rewrite) {
		this.cu= Objects.requireNonNull(cu, "CompilationUnit cannot be null");
		this.icu= Objects.requireNonNull(icu, "ICompilationUnit cannot be null");
		this.match= Objects.requireNonNull(match, "Match cannot be null");
		this.rewrite= Objects.requireNonNull(rewrite, "ASTRewrite cannot be null");
	}

	/**
	 * Returns the compilation unit AST.
	 * 
	 * @return the compilation unit
	 */
	public CompilationUnit getCompilationUnit() {
		return cu;
	}

	/**
	 * Returns the ICompilationUnit.
	 * 
	 * @return the compilation unit
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
	 * Returns the AST rewrite for making changes.
	 * 
	 * @return the rewrite
	 */
	public ASTRewrite getRewrite() {
		return rewrite;
	}
}
