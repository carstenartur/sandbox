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
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

class MultiFilePlanningStressTest {

	@Test
	void largeScopeStopsBeforeReadingSourceBeyondHardUnitLimit() throws Exception {
		AtomicInteger sourceReads= new AtomicInteger();
		ICompilationUnit[] units= IntStream.range(0, 5_000)
				.mapToObj(index -> unit("unit-" + index, sourceReads)) //$NON-NLS-1$
				.toArray(ICompilationUnit[]::new);

		MultiFilePlanningBudget.Assessment assessment= MultiFilePlanningBudget.assess(units,
				new MultiFilePlanningLimits(500, 1_000, 10_000, 20_000), null);

		assertFalse(assessment.mayProceed());
		assertEquals(1_001, assessment.metrics().compilationUnitCount());
		assertEquals(1_000, sourceReads.get(),
				"The first unit beyond the hard count must be rejected before its source is read"); //$NON-NLS-1$
	}

	@Test
	void measuredPlanResultPreservesImmutableMetrics() {
		Object plan= new Object();
		RefactoringStatus status= new RefactoringStatus();
		status.addWarning("large but permitted"); //$NON-NLS-1$
		MultiFilePlanningMetrics metrics= MultiFilePlanningMetrics.scope(12, 34_567)
				.withDurations(89, 144)
				.withRetainedPlanEntries(5);

		MultiFileCleanUpPlanResult<Object> result=
				MultiFileCleanUpPlanResult.success(plan, status, metrics);

		assertSame(plan, result.plan());
		assertSame(status, result.status());
		assertSame(metrics, result.metrics());
		assertEquals(5, result.metrics().retainedPlanEntries());
	}

	private static ICompilationUnit unit(String handle, AtomicInteger sourceReads) {
		ICompilationUnit[] holder= new ICompilationUnit[1];
		ICompilationUnit unit= proxy(ICompilationUnit.class, (proxy, method, arguments) -> switch (method.getName()) {
			case "exists" -> true; //$NON-NLS-1$
			case "getPrimary" -> holder[0]; //$NON-NLS-1$
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getElementName" -> handle + ".java"; //$NON-NLS-1$ //$NON-NLS-2$
			case "getSource" -> { //$NON-NLS-1$
				sourceReads.incrementAndGet();
				yield "class A {}"; //$NON-NLS-1$
			}
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
		return (T) Proxy.newProxyInstance(MultiFilePlanningStressTest.class.getClassLoader(),
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
