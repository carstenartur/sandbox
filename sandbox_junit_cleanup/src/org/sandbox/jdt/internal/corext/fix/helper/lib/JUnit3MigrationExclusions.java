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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Base types whose JUnit 3 execution contract must not be migrated to Jupiter.
 *
 * <p>Some frameworks derive from {@code junit.framework.TestCase} only to reuse
 * the JUnit 3 runner while adding an execution contract that has no Jupiter
 * equivalent. {@code org.eclipse.test.performance.PerformanceTestCase} is the
 * dominant example in the Eclipse code base: it measures and asserts performance
 * through the JUnit 3 lifecycle and is executed by a dedicated harness.
 *
 * <p>The default list can be extended or replaced through the system property
 * {@value #EXCLUDED_SUPERTYPES_PROPERTY} using a comma-separated list of fully
 * qualified type names. A leading {@code +} keeps the built-in defaults and adds
 * the configured names; without it the configured names replace the defaults.
 */
public final class JUnit3MigrationExclusions {

	/** System property that configures additional or replacing excluded base types. */
	public static final String EXCLUDED_SUPERTYPES_PROPERTY= "sandbox.junit.migration.excludedSuperTypes"; //$NON-NLS-1$

	/** Stable reason code reported when an excluded base type blocks a migration. */
	public static final String EXCLUDED_BASE_TYPE_REASON= "EXCLUDED_JUNIT3_BASE_TYPE"; //$NON-NLS-1$

	private static final Set<String> DEFAULT_EXCLUDED_SUPERTYPES= Set.of(
			"org.eclipse.test.performance.PerformanceTestCase", //$NON-NLS-1$
			"junit.extensions.TestSetup", //$NON-NLS-1$
			"junit.extensions.TestDecorator", //$NON-NLS-1$
			"junit.framework.TestSuite"); //$NON-NLS-1$

	private JUnit3MigrationExclusions() {
	}

	/**
	 * Returns the configured excluded base types.
	 *
	 * @return fully qualified names of base types that block a JUnit 3 migration
	 */
	public static Set<String> excludedSuperTypes() {
		String configured= System.getProperty(EXCLUDED_SUPERTYPES_PROPERTY);
		if (configured == null || configured.isBlank()) {
			return DEFAULT_EXCLUDED_SUPERTYPES;
		}
		boolean additive= configured.startsWith("+"); //$NON-NLS-1$
		String names= additive ? configured.substring(1) : configured;
		Set<String> result= new LinkedHashSet<>();
		if (additive) {
			result.addAll(DEFAULT_EXCLUDED_SUPERTYPES);
		}
		Arrays.stream(names.split(",")) //$NON-NLS-1$
				.map(String::trim)
				.filter(name -> !name.isEmpty())
				.forEach(result::add);
		return Set.copyOf(result);
	}

	/**
	 * Returns whether the type itself or any of its superclasses is excluded.
	 *
	 * @param binding type binding to inspect, may be {@code null}
	 * @return {@code true} if the type must not be migrated
	 */
	public static boolean isExcluded(ITypeBinding binding) {
		return excludedSuperType(binding) != null;
	}

	/**
	 * Returns the first excluded type found in the superclass chain.
	 *
	 * @param binding type binding to inspect, may be {@code null}
	 * @return the excluded fully qualified type name, or {@code null} if none applies
	 */
	public static String excludedSuperType(ITypeBinding binding) {
		Set<String> excluded= excludedSuperTypes();
		ITypeBinding current= binding;
		while (current != null) {
			ITypeBinding erasure= current.getErasure();
			String name= erasure == null ? null : erasure.getQualifiedName();
			if (name != null && excluded.contains(name)) {
				return name;
			}
			current= current.getSuperclass();
		}
		return null;
	}
}
