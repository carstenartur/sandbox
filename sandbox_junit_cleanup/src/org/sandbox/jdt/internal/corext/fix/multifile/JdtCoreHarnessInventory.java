/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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
import java.util.Objects;

/** Immutable inventory of source families using the custom Eclipse JDT Core JUnit 3 harness. */
public record JdtCoreHarnessInventory(List<Family> families) {

	/** The framework layer whose semantics control a test family. */
	public enum FamilyKind {
		/** Direct family using the custom named-test {@code TestCase} base only. */
		DIRECT_TEST_CASE,
		/** Family using {@code SuiteOfTestCases} once-per-suite state transfer. */
		SUITE_STATE,
		/** Family multiplied through {@code AbstractCompilerTest} compliance suites. */
		COMPILER_COMPLIANCE
	}

	/** One binding-stable family classification and its migration boundary. */
	public record Family(String compilationUnitHandle, String typeBindingKey, String typeName,
			FamilyKind kind, boolean directSliceApplicable, String reasonCode, String message,
			List<String> relatedCompilationUnitHandles) {
		public Family {
			compilationUnitHandle= Objects.requireNonNull(compilationUnitHandle);
			typeBindingKey= Objects.requireNonNull(typeBindingKey);
			typeName= Objects.requireNonNull(typeName);
			kind= Objects.requireNonNull(kind);
			reasonCode= Objects.requireNonNull(reasonCode);
			message= Objects.requireNonNull(message);
			relatedCompilationUnitHandles= relatedCompilationUnitHandles == null
					? List.of()
					: relatedCompilationUnitHandles.stream().filter(Objects::nonNull).distinct().sorted().toList();
		}
	}

	public JdtCoreHarnessInventory {
		families= families == null ? List.of() : List.copyOf(families);
	}

	/** Empty inventory for projects that do not contain the JDT Core harness. */
	public static JdtCoreHarnessInventory empty() {
		return new JdtCoreHarnessInventory(List.of());
	}

	/** Number of direct families admitted by the first explicit harness slice. */
	public long applicableDirectFamilyCount() {
		return families.stream().filter(Family::directSliceApplicable).count();
	}
}
