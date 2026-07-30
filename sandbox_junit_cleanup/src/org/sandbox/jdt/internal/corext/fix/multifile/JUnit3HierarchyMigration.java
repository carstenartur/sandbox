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

/** Immutable semantic plan for one supported JUnit 3 source hierarchy. */
public record JUnit3HierarchyMigration(String rootTypeName, List<TypeMigration> types,
		List<String> baselineTestTypeHandles) {

	public static final String ROLE_HIERARCHY_TYPE= "JUNIT3_HIERARCHY_TYPE"; //$NON-NLS-1$
	public static final String ROLE_TEST_METHOD= "JUNIT3_TEST_METHOD"; //$NON-NLS-1$
	public static final String ROLE_BEFORE_EACH= "JUNIT3_BEFORE_EACH"; //$NON-NLS-1$
	public static final String ROLE_AFTER_EACH= "JUNIT3_AFTER_EACH"; //$NON-NLS-1$
	public static final String ROLE_ASSERTION_QUALIFY= "JUNIT3_ASSERTION_QUALIFY"; //$NON-NLS-1$
	public static final String ROLE_ASSERTION_MESSAGE_FIRST= "JUNIT3_ASSERTION_MESSAGE_FIRST"; //$NON-NLS-1$

	public JUnit3HierarchyMigration {
		types= List.copyOf(types);
		baselineTestTypeHandles= List.copyOf(baselineTestTypeHandles);
	}

	/** Local semantic targets belonging to one type declaration. */
	public record TypeMigration(String compilationUnitHandle, String typeBindingKey,
			boolean removeTestCaseSuperclass, List<MethodMigration> methods,
			List<InvocationMigration> invocations) {
		public TypeMigration {
			methods= List.copyOf(methods);
			invocations= List.copyOf(invocations);
		}
	}

	/**
	 * One method migration. {@code executionOrder} is the one-based order required
	 * to reproduce JUnit 3 {@code TestSuite} discovery; lifecycle methods use 0.
	 */
	public record MethodMigration(String methodBindingKey, MethodKind kind, int executionOrder) {
		public MethodMigration(String methodBindingKey, MethodKind kind) {
			this(methodBindingKey, kind, 0);
		}
	}

	/** Exact source invocation authorized for a declarative assertion rewrite. */
	public record InvocationMigration(String methodBindingKey, int sourceStart, int sourceLength,
			InvocationKind kind) {
	}

	public enum MethodKind {
		TEST,
		BEFORE_EACH,
		AFTER_EACH
	}

	public enum InvocationKind {
		QUALIFY,
		MESSAGE_FIRST
	}
}
