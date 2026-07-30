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

import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.InvocationMigration;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodMigration;

/** Exact immutable rewrite targets for one direct custom JDT Core TestCase family. */
public record JdtCoreDirectFamilyMigration(String harnessCompilationUnitHandle,
		String harnessTypeBindingKey, String familyCompilationUnitHandle,
		String familyTypeBindingKey, String constructorBindingKey,
		String localSuiteMethodBindingKey, List<MethodMigration> testMethods,
		List<InvocationMigration> assertionInvocations) {

	public JdtCoreDirectFamilyMigration {
		harnessCompilationUnitHandle= Objects.requireNonNull(harnessCompilationUnitHandle);
		harnessTypeBindingKey= Objects.requireNonNull(harnessTypeBindingKey);
		familyCompilationUnitHandle= Objects.requireNonNull(familyCompilationUnitHandle);
		familyTypeBindingKey= Objects.requireNonNull(familyTypeBindingKey);
		constructorBindingKey= Objects.requireNonNull(constructorBindingKey);
		testMethods= List.copyOf(testMethods);
		assertionInvocations= List.copyOf(assertionInvocations);
	}
}
