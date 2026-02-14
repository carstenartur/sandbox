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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Context for guard evaluation, providing access to match bindings and the
 * compilation unit.
 * 
 * <p>A {@code GuardContext} is created from a {@link Match} and a
 * {@link CompilationUnit}, and optionally a set of compiler options. It
 * provides methods to resolve placeholder bindings and query the Java source
 * version.</p>
 * 
 * @since 1.3.2
 */
public final class GuardContext {
	private final Match match;
	private final CompilationUnit cu;
	private final Map<String, String> compilerOptions;
	
	/**
	 * Creates a new guard context.
	 * 
	 * @param match the match providing placeholder bindings
	 * @param cu the compilation unit
	 * @param compilerOptions compiler options for source version queries
	 */
	private GuardContext(Match match, CompilationUnit cu, Map<String, String> compilerOptions) {
		this.match = Objects.requireNonNull(match, "Match cannot be null"); //$NON-NLS-1$
		this.cu = cu;
		this.compilerOptions = compilerOptions != null
				? Collections.unmodifiableMap(compilerOptions)
				: Collections.emptyMap();
	}
	
	/**
	 * Creates a guard context from a match and compilation unit using default
	 * compiler options.
	 * 
	 * @param match the match providing placeholder bindings
	 * @param cu the compilation unit
	 * @return a new guard context
	 */
	public static GuardContext fromMatch(Match match, CompilationUnit cu) {
		return new GuardContext(match, cu, JavaCore.getOptions());
	}
	
	/**
	 * Creates a guard context from a match, compilation unit, and explicit
	 * compiler options.
	 * 
	 * @param match the match providing placeholder bindings
	 * @param cu the compilation unit
	 * @param compilerOptions compiler options for source version queries
	 * @return a new guard context
	 */
	public static GuardContext fromMatch(Match match, CompilationUnit cu, Map<String, String> compilerOptions) {
		return new GuardContext(match, cu, compilerOptions);
	}
	
	/**
	 * Returns the match.
	 * 
	 * @return the match
	 */
	public Match getMatch() {
		return match;
	}
	
	/**
	 * Returns the compilation unit.
	 * 
	 * @return the compilation unit, or {@code null} if not available
	 */
	public CompilationUnit getCompilationUnit() {
		return cu;
	}
	
	/**
	 * Returns the compiler options.
	 * 
	 * @return an unmodifiable map of compiler options
	 */
	public Map<String, String> getCompilerOptions() {
		return compilerOptions;
	}
	
	/**
	 * Resolves a placeholder to a single AST node.
	 * 
	 * @param name the placeholder name including {@code $} marker (e.g., {@code "$x"})
	 * @return the bound AST node, or {@code null} if not found
	 */
	public ASTNode getBinding(String name) {
		return match.getBinding(name);
	}
	
	/**
	 * Resolves a placeholder to a list of AST nodes.
	 * 
	 * @param name the placeholder name including {@code $} markers (e.g., {@code "$args$"})
	 * @return the list of matched nodes, or an empty list if not found
	 */
	public List<ASTNode> getListBinding(String name) {
		return match.getListBinding(name);
	}
	
	/**
	 * Returns the Java source version from compiler options.
	 * 
	 * <p>The source version is determined from the {@code org.eclipse.jdt.core.compiler.source}
	 * compiler option. If the option is not set, returns {@code "1.8"} as default.</p>
	 * 
	 * @return the source version string (e.g., {@code "11"}, {@code "17"}, {@code "21"})
	 */
	public String getSourceVersion() {
		String version = compilerOptions.get(JavaCore.COMPILER_SOURCE);
		return version != null ? version : "1.8"; //$NON-NLS-1$
	}
}
