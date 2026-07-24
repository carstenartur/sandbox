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
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;

class MultiFilePlanningBudgetTest {

	@AfterEach
	void clearProperties() {
		System.clearProperty(MultiFilePlanningLimits.WARNING_UNITS_PROPERTY);
		System.clearProperty(MultiFilePlanningLimits.HARD_UNITS_PROPERTY);
		System.clearProperty(MultiFilePlanningLimits.WARNING_BYTES_PROPERTY);
		System.clearProperty(MultiFilePlanningLimits.HARD_BYTES_PROPERTY);
	}

	@Test
	void measuresDistinctWorkingCopyUtf8Bytes() throws Exception {
		ICompilationUnit first= unit("first", "ascii ä 😀"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit duplicate= unit("first", "ignored"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit second= unit("second", "\uD800"); //$NON-NLS-1$ //$NON-NLS-2$

		MultiFilePlanningBudget.Assessment assessment= MultiFilePlanningBudget.assess(
				new ICompilationUnit[] { first, duplicate, second },
				new MultiFilePlanningLimits(10, 20, 100, 200), null);

		assertTrue(assessment.mayProceed());
		assertFalse(assessment.status().hasWarning());
		assertEquals(2, assessment.metrics().compilationUnitCount());
		assertEquals(14, assessment.metrics().sourceBytes());
	}

	@Test
	void emitsWarningBeforeHardLimit() throws Exception {
		MultiFilePlanningBudget.Assessment assessment= MultiFilePlanningBudget.assess(
				new ICompilationUnit[] { unit("one", "12345"), unit("two", "67890") }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				new MultiFilePlanningLimits(1, 3, 8, 20), null);

		assertTrue(assessment.mayProceed());
		assertTrue(assessment.status().hasWarning());
		assertEquals(2, assessment.metrics().compilationUnitCount());
		assertEquals(10, assessment.metrics().sourceBytes());
	}

	@Test
	void abortsImmediatelyAtHardUnitLimit() throws Exception {
		ICompilationUnit unread= unit("three", null); //$NON-NLS-1$
		MultiFilePlanningBudget.Assessment assessment= MultiFilePlanningBudget.assess(
				new ICompilationUnit[] { unit("one", "a"), unit("two", "b"), unread }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				new MultiFilePlanningLimits(1, 2, 10, 20), null);

		assertFalse(assessment.mayProceed());
		assertTrue(assessment.status().hasFatalError());
		assertEquals(3, assessment.metrics().compilationUnitCount());
		assertEquals(2, assessment.metrics().sourceBytes());
	}

	@Test
	void abortsAtHardByteLimit() throws Exception {
		MultiFilePlanningBudget.Assessment assessment= MultiFilePlanningBudget.assess(
				new ICompilationUnit[] { unit("one", "123456") }, //$NON-NLS-1$ //$NON-NLS-2$
				new MultiFilePlanningLimits(2, 3, 3, 5), null);

		assertFalse(assessment.mayProceed());
		assertEquals(6, assessment.metrics().sourceBytes());
	}

	@Test
	void checksCancellationBetweenUnits() {
		IProgressMonitor canceled= proxy(IProgressMonitor.class, (proxy, method, arguments) -> switch (method.getName()) {
			case "isCanceled" -> true; //$NON-NLS-1$
			default -> defaultValue(proxy, method, arguments);
		});

		assertThrows(OperationCanceledException.class, () -> MultiFilePlanningBudget.assess(
				new ICompilationUnit[] { unit("one", "source") }, //$NON-NLS-1$ //$NON-NLS-2$
				MultiFilePlanningLimits.defaults(), canceled));
	}

	@Test
	void invalidSystemOverridesCannotDisableLimits() {
		System.setProperty(MultiFilePlanningLimits.WARNING_UNITS_PROPERTY, "invalid"); //$NON-NLS-1$
		System.setProperty(MultiFilePlanningLimits.HARD_UNITS_PROPERTY, "4"); //$NON-NLS-1$
		System.setProperty(MultiFilePlanningLimits.WARNING_BYTES_PROPERTY, "99"); //$NON-NLS-1$
		System.setProperty(MultiFilePlanningLimits.HARD_BYTES_PROPERTY, "7"); //$NON-NLS-1$

		MultiFilePlanningLimits limits= MultiFilePlanningLimits.fromSystemProperties();

		assertEquals(4, limits.hardCompilationUnits());
		assertTrue(limits.warningCompilationUnits() <= limits.hardCompilationUnits());
		assertEquals(7, limits.hardSourceBytes());
		assertTrue(limits.warningSourceBytes() <= limits.hardSourceBytes());
	}

	@Test
	void validatesExplicitLimitOrdering() {
		assertThrows(IllegalArgumentException.class,
				() -> new MultiFilePlanningLimits(3, 2, 10, 20));
		assertThrows(IllegalArgumentException.class,
				() -> new MultiFilePlanningLimits(1, 2, 30, 20));
	}

	private static ICompilationUnit unit(String handle, String source) {
		ICompilationUnit[] holder= new ICompilationUnit[1];
		ICompilationUnit unit= proxy(ICompilationUnit.class, (proxy, method, arguments) -> switch (method.getName()) {
			case "exists" -> true; //$NON-NLS-1$
			case "getPrimary" -> holder[0]; //$NON-NLS-1$
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getElementName" -> handle + ".java"; //$NON-NLS-1$ //$NON-NLS-2$
			case "getSource" -> source; //$NON-NLS-1$
			default -> defaultValue(proxy, method, arguments);
		});
		holder[0]= unit;
		return unit;
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Invocation invocation) {
		return (T) Proxy.newProxyInstance(MultiFilePlanningBudgetTest.class.getClassLoader(),
				new Class<?>[] { type }, invocation::invoke);
	}

	private static Object defaultValue(Object proxy, Method method, Object[] arguments) {
		return switch (method.getName()) {
			case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
			case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
			case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName(); //$NON-NLS-1$
			default -> primitiveDefault(method.getReturnType());
		};
	}

	private static Object primitiveDefault(Class<?> type) {
		if (type == boolean.class) {
			return false;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0F;
		}
		if (type == double.class) {
			return 0D;
		}
		if (type == char.class) {
			return '\0';
		}
		return null;
	}
}
