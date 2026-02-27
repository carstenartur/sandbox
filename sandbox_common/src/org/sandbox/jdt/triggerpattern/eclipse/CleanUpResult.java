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
package org.sandbox.jdt.triggerpattern.eclipse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;

/**
 * Holds the combined output of a cleanup's detection phase.
 *
 * <p>A cleanup may produce rewrite operations (code changes), hint findings
 * (problem markers), or both. This unified result type enables a cleanup
 * core template method to handle both paths in a single pipeline.</p>
 *
 * <p>The DSL {@code HintFileCleanUp} can produce <em>both</em> rewrite
 * operations (for rules with {@code =>}) and hint findings (for hint-only
 * rules) in the same pass.</p>
 *
 * @since 1.3.7
 */
public final class CleanUpResult {

	private final Set<CompilationUnitRewriteOperation> operations = new LinkedHashSet<>();
	private final List<HintFinding> findings = new ArrayList<>();

	/**
	 * Adds a rewrite operation to this result.
	 *
	 * @param op the operation to add
	 */
	public void addOperation(CompilationUnitRewriteOperation op) {
		operations.add(op);
	}

	/**
	 * Adds a hint-only finding to this result.
	 *
	 * @param finding the finding to add
	 */
	public void addFinding(HintFinding finding) {
		findings.add(finding);
	}

	/**
	 * Returns whether this result contains any rewrite operations.
	 *
	 * @return {@code true} if there are operations
	 */
	public boolean hasOperations() {
		return !operations.isEmpty();
	}

	/**
	 * Returns whether this result contains any hint-only findings.
	 *
	 * @return {@code true} if there are findings
	 */
	public boolean hasFindings() {
		return !findings.isEmpty();
	}

	/**
	 * Returns the rewrite operations.
	 *
	 * @return modifiable set of operations
	 */
	public Set<CompilationUnitRewriteOperation> getOperations() {
		return operations;
	}

	/**
	 * Returns the hint-only findings.
	 *
	 * @return modifiable list of findings
	 */
	public List<HintFinding> getFindings() {
		return findings;
	}
}
