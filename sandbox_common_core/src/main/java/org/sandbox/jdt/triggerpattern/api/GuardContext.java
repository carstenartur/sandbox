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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

/** Context for guard evaluation. */
public final class GuardContext {
	private final Match match;
	private final CompilationUnit cu;
	private final Map<String, String> compilerOptions;
	private final SemanticRewritePlan semanticPlan;

	private GuardContext(Match match, CompilationUnit cu, Map<String, String> compilerOptions,
			SemanticRewritePlan semanticPlan) {
		this.match= Objects.requireNonNull(match, "Match cannot be null"); //$NON-NLS-1$
		this.cu= cu;
		this.compilerOptions= compilerOptions != null
				? Collections.unmodifiableMap(compilerOptions)
				: Collections.emptyMap();
		this.semanticPlan= semanticPlan == null ? SemanticRewritePlan.empty() : semanticPlan;
	}

	/** Creates a context without compiler options or semantic plan. */
	public static GuardContext fromMatch(Match match, CompilationUnit cu) {
		return new GuardContext(match, cu, Collections.emptyMap(), SemanticRewritePlan.empty());
	}

	/** Creates a context with compiler options and no semantic plan. */
	public static GuardContext fromMatch(Match match, CompilationUnit cu, Map<String, String> compilerOptions) {
		return new GuardContext(match, cu, compilerOptions, SemanticRewritePlan.empty());
	}

	/** Creates a context with explicit compiler options and semantic authorization plan. */
	public static GuardContext fromMatch(Match match, CompilationUnit cu, Map<String, String> compilerOptions,
			SemanticRewritePlan semanticPlan) {
		return new GuardContext(match, cu, compilerOptions, semanticPlan);
	}

	public Match getMatch() {
		return match;
	}

	public CompilationUnit getCompilationUnit() {
		return cu;
	}

	public Map<String, String> getCompilerOptions() {
		return compilerOptions;
	}

	/** Returns the immutable semantic plan; empty for ordinary hint execution. */
	public SemanticRewritePlan getSemanticPlan() {
		return semanticPlan;
	}

	public ASTNode getBinding(String name) {
		return match.getBinding(name);
	}

	public List<ASTNode> getListBinding(String name) {
		return match.getListBinding(name);
	}

	public String getSourceVersion() {
		String version= compilerOptions.get("org.eclipse.jdt.core.compiler.source"); //$NON-NLS-1$
		return version != null ? version : "1.8"; //$NON-NLS-1$
	}

	public ASTNode getMatchedNode() {
		return match.getMatchedNode();
	}
}
