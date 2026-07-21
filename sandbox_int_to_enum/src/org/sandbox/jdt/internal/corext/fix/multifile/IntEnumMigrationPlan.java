/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;

/** Immutable plan containing all conservative package-scoped enum candidates. */
public record IntEnumMigrationPlan(SelectedCompilationUnitPlan selectedScope, List<IntEnumCandidate> candidates) {

	/** Defensively copies plan data. */
	public IntEnumMigrationPlan {
		candidates= List.copyOf(candidates);
	}

	/** Returns whether the unit participates in the cleanup run. */
	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	/** Adds the local rewrite operation after resolving all expected plan targets. */
	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		String handle= unit.getPrimary().getHandleIdentifier();
		List<IntEnumCandidate> relevant= candidates.stream()
				.filter(candidate -> handle.equals(candidate.ownerCompilationUnitHandle())
						|| candidate.expectedReferenceCountsByUnit().containsKey(handle)
						|| candidate.expectedCallCountsByUnit().containsKey(handle))
				.toList();
		if (relevant.isEmpty()) {
			return;
		}
		IntEnumMultiFileRewriteOperation.ResolvedPlan resolved= IntEnumMultiFileRewriteOperation.resolve(
				unit.getPrimary(), root, relevant);
		nodesProcessed.addAll(resolved.processedNodes());
		operations.add(new IntEnumMultiFileRewriteOperation(resolved));
	}
}
