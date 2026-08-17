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

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

import org.junit.jupiter.api.Test;

/** Regression contract for cancellable lifecycle hierarchy discovery. */
public class JUnitLifecycleScopeDetectorTest {

	@Test
	public void passesTheCallerMonitorToJdtHierarchyComputation() throws JavaModelException {
		IProgressMonitor monitor= new NullProgressMonitor();
		ITypeHierarchy expectedHierarchy= proxy(ITypeHierarchy.class);
		AtomicReference<IProgressMonitor> receivedMonitor= new AtomicReference<>();
		IType rootType= (IType) Proxy.newProxyInstance(
				IType.class.getClassLoader(),
				new Class<?>[] { IType.class },
				(proxy, method, arguments) -> {
					if ("newTypeHierarchy".equals(method.getName()) //$NON-NLS-1$
							&& method.getParameterCount() == 1) {
						receivedMonitor.set((IProgressMonitor) arguments[0]);
						return expectedHierarchy;
					}
					return objectMethod(proxy, method, arguments);
				});

		ITypeHierarchy actualHierarchy= JUnitLifecycleScopeDetector.newTypeHierarchy(rootType, monitor);

		assertSame(expectedHierarchy, actualHierarchy);
		assertSame(monitor, receivedMonitor.get(),
				"Lifecycle hierarchy discovery must remain cancellable through the caller monitor"); //$NON-NLS-1$
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				JUnitLifecycleScopeDetectorTest::objectMethod);
	}

	private static Object objectMethod(Object proxy, Method method, Object[] arguments) {
		return switch (method.getName()) {
			case "equals" -> proxy == arguments[0]; //$NON-NLS-1$
			case "hashCode" -> System.identityHashCode(proxy); //$NON-NLS-1$
			case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + " proxy"; //$NON-NLS-1$ //$NON-NLS-2$
			default -> throw new AssertionError("Unexpected proxy method: " + method); //$NON-NLS-1$
		};
	}
}
