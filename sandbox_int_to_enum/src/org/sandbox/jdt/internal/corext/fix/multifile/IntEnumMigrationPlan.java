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
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator.Allocation;
import org.sandbox.jdt.cleanup.multifile.GeneratedNameAllocator.NestedTypeRequest;
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
		validateGeneratedNames(unit.getPrimary(), root, relevant.stream()
				.filter(candidate -> handle.equals(candidate.ownerCompilationUnitHandle())).toList());
		IntEnumMultiFileRewriteOperation.ResolvedPlan resolved= IntEnumMultiFileRewriteOperation.resolve(
				unit.getPrimary(), root, relevant);
		nodesProcessed.addAll(resolved.processedNodes());
		operations.add(new IntEnumMultiFileRewriteOperation(resolved));
	}

	private static void validateGeneratedNames(ICompilationUnit unit, CompilationUnit root,
			List<IntEnumCandidate> ownerCandidates) throws CoreException {
		if (ownerCandidates.isEmpty()) {
			return;
		}
		List<NestedTypeRequest> requests= ownerCandidates.stream()
				.map(candidate -> new NestedTypeRequest(requestId(candidate), candidate.ownerCompilationUnitHandle(),
						candidate.ownerTypeBindingKey(), candidate.ownerTypeQualifiedName(), candidate.enumTypeName()))
				.toList();
		Map<String, Allocation> allocations= GeneratedNameAllocator.allocateNestedTypes(List.of(root), requests);
		for (NestedTypeRequest request : requests) {
			Allocation allocation= allocations.get(request.requestId());
			if (allocation == null || allocation.available()) {
				continue;
			}
			String message= "The project-wide int-to-enum plan is stale for " + unit.getElementName() //$NON-NLS-1$
					+ ": generated name " + request.requestedName() + " is not available: " //$NON-NLS-1$ //$NON-NLS-2$
					+ allocation.diagnosticMessage();
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_int_to_enum", message)); //$NON-NLS-1$
		}
	}

	private static String requestId(IntEnumCandidate candidate) {
		return candidate.ownerTypeBindingKey() + '#' + candidate.methodBindingKey() + ':'
				+ candidate.parameterIndex() + "->" + candidate.enumTypeName(); //$NON-NLS-1$
	}
}