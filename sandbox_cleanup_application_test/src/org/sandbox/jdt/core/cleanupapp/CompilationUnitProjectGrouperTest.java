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
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;

class CompilationUnitProjectGrouperTest {

	@Test
	void groupsPrimaryUnitsByProjectInStableOrder() {
		IJavaProject alpha= project("=Alpha", "Alpha", true); //$NON-NLS-1$ //$NON-NLS-2$
		IJavaProject beta= project("=Beta", "Beta", true); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit alphaA= unit("=Alpha/src<p{A.java", alpha, true, null); //$NON-NLS-1$
		ICompilationUnit alphaB= unit("=Alpha/src<p{B.java", alpha, true, null); //$NON-NLS-1$
		ICompilationUnit betaC= unit("=Beta/src<p{C.java", beta, true, null); //$NON-NLS-1$
		ICompilationUnit workingCopyOfAlphaA= unit("working-copy-A", alpha, true, alphaA); //$NON-NLS-1$

		File betaFile= new File("z/C.java"); //$NON-NLS-1$
		File alphaBFile= new File("a/B.java"); //$NON-NLS-1$
		File alphaAFile= new File("a/A.java"); //$NON-NLS-1$
		File duplicateAlphaA= new File("a/A-copy.java"); //$NON-NLS-1$
		Map<String, ICompilationUnit> units= Map.of(
				betaFile.getPath(), betaC,
				alphaBFile.getPath(), alphaB,
				alphaAFile.getPath(), workingCopyOfAlphaA,
				duplicateAlphaA.getPath(), alphaA);

		CompilationUnitProjectGrouper.Result result= new CompilationUnitProjectGrouper(
				file -> units.get(file.getPath())).group(
						List.of(betaFile, duplicateAlphaA, alphaBFile, alphaAFile));

		assertTrue(result.diagnostics().isEmpty());
		assertEquals(2, result.groups().size());
		assertSame(alpha, result.groups().get(0).project());
		assertEquals(List.of("=Alpha/src<p{A.java", "=Alpha/src<p{B.java"), //$NON-NLS-1$ //$NON-NLS-2$
				handles(result.groups().get(0)));
		assertSame(alphaA, result.groups().get(0).inputs().get(0).compilationUnit(),
				"Working copies must be normalized to their primary compilation unit");
		assertSame(beta, result.groups().get(1).project());
		assertEquals(List.of("=Beta/src<p{C.java"), handles(result.groups().get(1))); //$NON-NLS-1$
	}

	@Test
	void resolutionFailuresAreStructuredAndDoNotBlockOtherProjects() {
		IJavaProject project= project("=Project", "Project", true); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit valid= unit("=Project/src<p{Valid.java", project, true, null); //$NON-NLS-1$
		File missing= new File("a/Missing.java"); //$NON-NLS-1$
		File broken= new File("b/Broken.java"); //$NON-NLS-1$
		File validFile= new File("c/Valid.java"); //$NON-NLS-1$

		CompilationUnitProjectGrouper.Result result= new CompilationUnitProjectGrouper(file -> {
			if (file.equals(missing)) {
				return null;
			}
			if (file.equals(broken)) {
				throw new IllegalStateException("injected resolution failure"); //$NON-NLS-1$
			}
			return valid;
		}).group(List.of(validFile, broken, missing));

		assertEquals(1, result.groups().size());
		assertEquals(List.of("=Project/src<p{Valid.java"), handles(result.groups().get(0))); //$NON-NLS-1$
		assertEquals(List.of("NOT_COMPILATION_UNIT", "RESOLUTION_FAILED"), //$NON-NLS-1$ //$NON-NLS-2$
				result.diagnostics().stream().map(CompilationUnitProjectGrouper.Diagnostic::reasonCode).toList());
		assertEquals(List.of("injected resolution failure"), result.diagnostics().stream() //$NON-NLS-1$
				.map(CompilationUnitProjectGrouper.Diagnostic::message)
				.filter(message -> !message.startsWith("No existing")) //$NON-NLS-1$
				.toList());
	}

	@Test
	void missingJavaProjectIsRejectedBeforeTransactionCreation() {
		IJavaProject missingProject= project("=Missing", "Missing", false); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= unit("=Missing/src<p{A.java", missingProject, true, null); //$NON-NLS-1$

		CompilationUnitProjectGrouper.Result result= new CompilationUnitProjectGrouper(file -> unit)
				.group(List.of(new File("A.java"))); //$NON-NLS-1$

		assertTrue(result.groups().isEmpty());
		assertEquals(List.of("NO_JAVA_PROJECT"), result.diagnostics().stream() //$NON-NLS-1$
				.map(CompilationUnitProjectGrouper.Diagnostic::reasonCode).toList());
	}

	@Test
	void nullCollectionProducesOneDeterministicDiagnostic() {
		CompilationUnitProjectGrouper.Result result= new CompilationUnitProjectGrouper(file -> null).group(null);

		assertTrue(result.groups().isEmpty());
		assertEquals(1, result.diagnostics().size());
		assertEquals("RESOLUTION_FAILED", result.diagnostics().get(0).reasonCode()); //$NON-NLS-1$
	}

	private static List<String> handles(CompilationUnitProjectGrouper.ProjectGroup group) {
		return group.compilationUnits().stream().map(ICompilationUnit::getHandleIdentifier).toList();
	}

	private static IJavaProject project(String handle, String name, boolean exists) {
		return proxy(IJavaProject.class, (proxy, method, arguments) -> switch (method.getName()) {
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getElementName" -> name; //$NON-NLS-1$
			case "exists" -> Boolean.valueOf(exists); //$NON-NLS-1$
			default -> objectOrDefault(proxy, method, arguments, handle);
		});
	}

	private static ICompilationUnit unit(String handle, IJavaProject project, boolean exists,
			ICompilationUnit explicitPrimary) {
		AtomicReference<ICompilationUnit> self= new AtomicReference<>();
		ICompilationUnit result= proxy(ICompilationUnit.class, (proxy, method, arguments) -> switch (method.getName()) {
			case "getHandleIdentifier" -> handle; //$NON-NLS-1$
			case "getJavaProject" -> project; //$NON-NLS-1$
			case "getPrimary" -> explicitPrimary == null ? self.get() : explicitPrimary; //$NON-NLS-1$
			case "exists" -> Boolean.valueOf(exists); //$NON-NLS-1$
			default -> objectOrDefault(proxy, method, arguments, handle);
		});
		self.set(result);
		return result;
	}

	private static Object objectOrDefault(Object proxy, Method method, Object[] arguments, String display) {
		return switch (method.getName()) {
			case "toString" -> display; //$NON-NLS-1$
			case "hashCode" -> Integer.valueOf(System.identityHashCode(proxy)); //$NON-NLS-1$
			case "equals" -> Boolean.valueOf(arguments != null && arguments.length == 1 && proxy == arguments[0]); //$NON-NLS-1$
			default -> defaultValue(method.getReturnType());
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return Boolean.FALSE;
		}
		if (type == char.class) {
			return Character.valueOf('\0');
		}
		if (type == byte.class) {
			return Byte.valueOf((byte) 0);
		}
		if (type == short.class) {
			return Short.valueOf((short) 0);
		}
		if (type == int.class) {
			return Integer.valueOf(0);
		}
		if (type == long.class) {
			return Long.valueOf(0L);
		}
		if (type == float.class) {
			return Float.valueOf(0F);
		}
		if (type == double.class) {
			return Double.valueOf(0D);
		}
		throw new AssertionError(type);
	}
}
