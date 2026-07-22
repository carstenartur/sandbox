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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.core.resources.IResource;

import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

class JavaProjectCompilationUnitsTest {

	@Test
	void classifiesProductionTestGeneratedDerivedOutputAndExcludedRoots() throws JavaModelException {
		ProjectFixture fixture= new ProjectFixture("/project/bin", //$NON-NLS-1$
				new RootSpec("main", "/project/src/main/java", false, false, IPackageFragmentRoot.K_SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
				new RootSpec("test", "/project/src/test/java", true, false, IPackageFragmentRoot.K_SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
				new RootSpec("fixtures", "/project/src/testFixtures/java", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE),
				new RootSpec("generated", "/project/target/generated-sources/annotations", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE),
				new RootSpec("derived", "/project/build/generated/sources", false, true, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE),
				new RootSpec("projectOutput", "/project/bin/generated", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE),
				new RootSpec("entryOutput", "/project/target/test-classes/generated", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE, "/project/target/test-classes"), //$NON-NLS-1$
				new RootSpec("missingMetadata", "/project/src/broken/java", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE, null, false),
				new RootSpec("binary", "/project/lib", false, false, IPackageFragmentRoot.K_BINARY)); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(SourceRootKind.PRODUCTION, JavaProjectCompilationUnits.classify(fixture.root("main"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.TEST, JavaProjectCompilationUnits.classify(fixture.root("test"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.TEST, JavaProjectCompilationUnits.classify(fixture.root("fixtures"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.GENERATED, JavaProjectCompilationUnits.classify(fixture.root("generated"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.DERIVED, JavaProjectCompilationUnits.classify(fixture.root("derived"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.OUTPUT, JavaProjectCompilationUnits.classify(fixture.root("projectOutput"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.OUTPUT, JavaProjectCompilationUnits.classify(fixture.root("entryOutput"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.EXCLUDED,
				JavaProjectCompilationUnits.classify(fixture.root("missingMetadata"))); //$NON-NLS-1$
		assertEquals(SourceRootKind.EXCLUDED, JavaProjectCompilationUnits.classify(fixture.root("binary"))); //$NON-NLS-1$
	}

	@Test
	void appliesCleanupSpecificExpansionPoliciesWithoutEditingGeneratedRoots() throws JavaModelException {
		ProjectFixture fixture= new ProjectFixture("/project/bin", //$NON-NLS-1$
				new RootSpec("main", "/project/src/main/java", false, false, IPackageFragmentRoot.K_SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
				new RootSpec("support", "/project/src/support/java", false, false, IPackageFragmentRoot.K_SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
				new RootSpec("test", "/project/src/test/java", true, false, IPackageFragmentRoot.K_SOURCE), //$NON-NLS-1$ //$NON-NLS-2$
				new RootSpec("fixtures", "/project/src/testFixtures/java", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE),
				new RootSpec("generated", "/project/target/generated-test-sources/test-annotations", false, false, //$NON-NLS-1$ //$NON-NLS-2$
						IPackageFragmentRoot.K_SOURCE));

		assertEquals(List.of("test/Unit.java"), names(JavaProjectCompilationUnits.collect(fixture.project(), //$NON-NLS-1$
				List.of(fixture.unit("test")), SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS))); //$NON-NLS-1$
		assertEquals(List.of("fixtures/Unit.java", "main/Unit.java", "support/Unit.java", "test/Unit.java"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				names(JavaProjectCompilationUnits.collect(fixture.project(), List.of(fixture.unit("main")), //$NON-NLS-1$
						SourceRootPolicy.PRODUCTION_WITH_DEPENDENT_TESTS)));
		assertEquals(List.of("fixtures/Unit.java", "support/Unit.java", "test/Unit.java"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				names(JavaProjectCompilationUnits.collect(fixture.project(), List.of(fixture.unit("support")), //$NON-NLS-1$
						SourceRootPolicy.TEST_ROOTS_AND_SELECTED_SUPPORT)));
		assertEquals(List.of("main/Unit.java", "test/Unit.java"), //$NON-NLS-1$ //$NON-NLS-2$
				names(JavaProjectCompilationUnits.collect(fixture.project(),
						List.of(fixture.unit("test"), fixture.unit("main")), //$NON-NLS-1$ //$NON-NLS-2$
						SourceRootPolicy.EXPLICIT_SELECTED_ROOTS)));
		assertEquals(List.of("fixtures/Unit.java", "main/Unit.java", "support/Unit.java", "test/Unit.java"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				names(JavaProjectCompilationUnits.collect(fixture.project(), List.of(),
						SourceRootPolicy.COMPLETE_PROJECT)));
	}

	private static List<String> names(List<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).toList();
	}

	private record RootSpec(String handle, String path, boolean test, boolean derived, int kind,
			String entryOutputPath, boolean classpathMetadataPresent) {
		RootSpec(String handle, String path, boolean test, boolean derived, int kind) {
			this(handle, path, test, derived, kind, null, true);
		}

		RootSpec(String handle, String path, boolean test, boolean derived, int kind, String entryOutputPath) {
			this(handle, path, test, derived, kind, entryOutputPath, true);
		}
	}

	private static final class ProjectFixture {
		private final IJavaProject[] projectHolder= new IJavaProject[1];
		private final RootFixture[] roots;
		private final IJavaProject project;

		ProjectFixture(String outputPath, RootSpec... specs) {
			roots= Arrays.stream(specs).map(spec -> new RootFixture(projectHolder, spec)).toArray(RootFixture[]::new);
			project= proxy(IJavaProject.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "getPackageFragmentRoots" -> Arrays.stream(roots).map(RootFixture::root) //$NON-NLS-1$
						.toArray(IPackageFragmentRoot[]::new);
				case "getOutputLocation" -> new Path(outputPath); //$NON-NLS-1$
				default -> defaultValue(proxy, method, arguments);
			});
			projectHolder[0]= project;
		}

		IJavaProject project() {
			return project;
		}

		IPackageFragmentRoot root(String handle) {
			return Arrays.stream(roots)
					.filter(root -> root.spec.handle().equals(handle))
					.findFirst()
					.orElseThrow()
					.root();
		}

		ICompilationUnit unit(String handle) {
			return Arrays.stream(roots)
					.filter(root -> root.spec.handle().equals(handle))
					.findFirst()
					.orElseThrow()
					.unit();
		}
	}

	private static final class RootFixture {
		private final RootSpec spec;
		private final IPackageFragmentRoot root;
		private final ICompilationUnit unit;

		RootFixture(IJavaProject[] projectHolder, RootSpec spec) {
			this.spec= spec;
			IPackageFragmentRoot[] rootHolder= new IPackageFragmentRoot[1];
			IResource resource= proxy(IResource.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "isDerived" -> spec.derived(); //$NON-NLS-1$
				default -> defaultValue(proxy, method, arguments);
			});
			IClasspathEntry entry= proxy(IClasspathEntry.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "isTest" -> spec.test(); //$NON-NLS-1$
				case "getOutputLocation" -> spec.entryOutputPath() == null //$NON-NLS-1$
						? null
						: new Path(spec.entryOutputPath());
				default -> defaultValue(proxy, method, arguments);
			});
			unit= proxy(ICompilationUnit.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "exists" -> true; //$NON-NLS-1$
				case "getHandleIdentifier" -> spec.handle() + "/Unit.java"; //$NON-NLS-1$
				case "getPrimary" -> proxy; //$NON-NLS-1$
				case "getAncestor" -> arguments != null && arguments.length == 1 //$NON-NLS-1$
						&& Integer.valueOf(IJavaElement.PACKAGE_FRAGMENT_ROOT).equals(arguments[0]) ? rootHolder[0] : null;
				default -> defaultValue(proxy, method, arguments);
			});
			IPackageFragment fragment= proxy(IPackageFragment.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "exists" -> true; //$NON-NLS-1$
				case "getCompilationUnits" -> new ICompilationUnit[] { unit }; //$NON-NLS-1$
				default -> defaultValue(proxy, method, arguments);
			});
			root= proxy(IPackageFragmentRoot.class, (proxy, method, arguments) -> switch (method.getName()) {
				case "exists" -> true; //$NON-NLS-1$
				case "getKind" -> spec.kind(); //$NON-NLS-1$
				case "getHandleIdentifier" -> spec.handle(); //$NON-NLS-1$
				case "getPath" -> new Path(spec.path()); //$NON-NLS-1$
				case "getResource" -> resource; //$NON-NLS-1$
				case "getResolvedClasspathEntry", "getRawClasspathEntry" -> spec.classpathMetadataPresent() //$NON-NLS-1$ //$NON-NLS-2$
						? entry
						: null;
				case "getJavaProject" -> projectHolder[0]; //$NON-NLS-1$
				case "getChildren" -> new IJavaElement[] { fragment }; //$NON-NLS-1$
				default -> defaultValue(proxy, method, arguments);
			});
			rootHolder[0]= root;
		}

		IPackageFragmentRoot root() {
			return root;
		}

		ICompilationUnit unit() {
			return unit;
		}
	}

	@FunctionalInterface
	private interface Invocation {
		Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable;
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Invocation invocation) {
		return (T) Proxy.newProxyInstance(JavaProjectCompilationUnitsTest.class.getClassLoader(),
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
