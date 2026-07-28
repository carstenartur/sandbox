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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;

/** Planning-time accessibility policy for generated package-private nested enums. */
public final class IntEnumPackageVisibilityPolicy {

	private IntEnumPackageVisibilityPolicy() {
	}

	/**
	 * Removes candidates with a caller outside the owner package and replaces the
	 * transformed diagnostic with a deterministic rejection.
	 */
	public static MultiFileCleanUpPlanResult<IntEnumMigrationPlan> enforce(
			MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result, ICompilationUnit[] compilationUnits) {
		if (result.plan() == null || result.plan().candidates().isEmpty()) {
			return result;
		}
		Map<String, ICompilationUnit> unitsByHandle= new LinkedHashMap<>();
		Arrays.stream(compilationUnits).map(ICompilationUnit::getPrimary)
				.forEach(unit -> unitsByHandle.put(unit.getHandleIdentifier(), unit));
		List<IntEnumCandidate> accepted= new ArrayList<>();
		List<MultiFileCandidateDiagnostic> diagnostics= new ArrayList<>(result.diagnostics().candidates());
		for (IntEnumCandidate candidate : result.plan().candidates()) {
			ICompilationUnit owner= unitsByHandle.get(candidate.ownerCompilationUnitHandle());
			String ownerPackage= owner == null ? null : packageName(owner);
			Set<String> crossPackageCallers= new LinkedHashSet<>();
			for (String handle : candidate.expectedCallCountsByUnit().keySet()) {
				if (handle.equals(candidate.ownerCompilationUnitHandle())) {
					continue;
				}
				ICompilationUnit caller= unitsByHandle.get(handle);
				if (ownerPackage == null || caller == null || !ownerPackage.equals(packageName(caller))) {
					crossPackageCallers.add(handle);
				}
			}
			if (ownerPackage != null && crossPackageCallers.isEmpty()) {
				accepted.add(candidate);
				continue;
			}

			Set<String> expectedRelatedHandles= new LinkedHashSet<>();
			expectedRelatedHandles.add(candidate.ownerCompilationUnitHandle());
			expectedRelatedHandles.addAll(candidate.expectedCallCountsByUnit().keySet());
			MultiFileCandidateDiagnostic transformedDiagnostic= diagnostics.stream()
					.filter(diagnostic -> diagnostic.outcome() == MultiFileCandidateOutcome.TRANSFORMED)
					.filter(diagnostic -> diagnostic.ownerCompilationUnitHandle()
							.equals(candidate.ownerCompilationUnitHandle()))
					.filter(diagnostic -> Set.copyOf(diagnostic.relatedCompilationUnitHandles())
							.equals(Set.copyOf(expectedRelatedHandles)))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"Missing transformed diagnostic for planned int-to-enum candidate")); //$NON-NLS-1$
			diagnostics.removeIf(diagnostic -> diagnostic.outcome() == MultiFileCandidateOutcome.TRANSFORMED
					&& diagnostic.candidateId().equals(transformedDiagnostic.candidateId()));

			List<String> related= new ArrayList<>();
			related.add(candidate.ownerCompilationUnitHandle());
			related.addAll(crossPackageCallers);
			diagnostics.add(MultiFileCandidateDiagnostic.rejected(transformedDiagnostic.candidateId(),
					candidate.ownerCompilationUnitHandle(), "CROSS_PACKAGE_CALLER", //$NON-NLS-1$
					ownerPackage == null
							? "The owner compilation unit could not be resolved for package visibility validation." //$NON-NLS-1$
							: "The generated nested enum is package-private, but at least one caller is outside package " //$NON-NLS-1$
									+ printablePackage(ownerPackage)
									+ ". Select a compatibility-managed migration instead.", //$NON-NLS-1$
					related));
		}
		IntEnumMigrationPlan filteredPlan= new IntEnumMigrationPlan(result.plan().selectedScope(), accepted);
		MultiFileCleanUpDiagnostics filteredDiagnostics= new MultiFileCleanUpDiagnostics(
				result.diagnostics().cleanupId(), result.diagnostics().scope(), diagnostics);
		return MultiFileCleanUpPlanResult.success(filteredPlan, result.status(), result.metrics(), filteredDiagnostics);
	}

	private static String packageName(ICompilationUnit unit) {
		return unit.getParent() instanceof IPackageFragment fragment ? fragment.getElementName() : ""; //$NON-NLS-1$
	}

	private static String printablePackage(String packageName) {
		return packageName.isEmpty() ? "<default>" : packageName; //$NON-NLS-1$
	}
}
