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
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the real project metadata transaction independently of the cleanup producer. */
class PdeJupiterDependenciesTest {

	private static final String ORIGINAL= "class Example {}\n"; //$NON-NLS-1$
	private static final String MANIFEST= """
			Manifest-Version: 1.0
			Bundle-SymbolicName: example.tests
			Bundle-Version: 1.0.0
			Require-Bundle: org.junit
			Import-Package: existing.package

			""";
	private IProject project;

	@BeforeEach
	void setup() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject("jupiter-metadata-" + UUID.randomUUID()); //$NON-NLS-1$
		project.create(null);
		project.open(null);
		project.setDefaultCharset(StandardCharsets.UTF_8.name(), null);
		IFolder folder= project.getFolder("META-INF"); //$NON-NLS-1$
		folder.create(false, true, null);
	}

	@AfterEach
	void dispose() throws Exception {
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	static Stream<Arguments> introducedReferences() {
		return Stream.of(
				Arguments.of("import org.sandbox.junit.jupiter.ExternalResourceExtension; class Example { Object factory = ExternalResourceExtension.perTest(null,null); }", //$NON-NLS-1$
						Set.of("org.sandbox.junit.jupiter")), //$NON-NLS-1$
				Arguments.of("class Example { Object factory = org.sandbox.junit.jupiter.ExternalResourceExtension.perClass(() -> this, r -> r.start(), r -> r.stop()); }", //$NON-NLS-1$
						Set.of("org.sandbox.junit.jupiter", "org.junit.jupiter.api.function")), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("import org.junit.jupiter.api.Test; @Test class Example {}", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api")), //$NON-NLS-1$
				Arguments.of("import static org.junit.jupiter.api.Assertions.assertTrue; class Example { void run() { assertTrue(true); } }", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api")), //$NON-NLS-1$
				Arguments.of("class Example { void run() { org.junit.jupiter.api.Assertions.assertTrue(true); } }", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api")), //$NON-NLS-1$
				Arguments.of("import org.junit.jupiter.api.MethodOrderer.OrderAnnotation; class Example { OrderAnnotation order; }", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api")), //$NON-NLS-1$
				Arguments.of("import org.junit.jupiter.params.ParameterizedTest; import org.junit.jupiter.params.provider.ValueSource; class Example { @ParameterizedTest @ValueSource(ints=1) void run(int n) {} }", //$NON-NLS-1$
						Set.of("org.junit.jupiter.params", "org.junit.jupiter.params.provider")), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("import org.junit.jupiter.api.extension.ExtendWith; @ExtendWith(Object.class) class Example {}", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api.extension")), //$NON-NLS-1$
				Arguments.of("@org.junit.jupiter.api.parallel.Isolated class Example {}", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api.parallel")), //$NON-NLS-1$
				Arguments.of("import org.junit.jupiter.api.*; @Test class Example {}", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api")), //$NON-NLS-1$
				Arguments.of("import static org.junit.jupiter.api.Assertions.*; class Example { void run() { assertTrue(true); } }", //$NON-NLS-1$
						Set.of("org.junit.jupiter.api"))); //$NON-NLS-1$
	}

	@ParameterizedTest
	@MethodSource("introducedReferences") //$NON-NLS-1$
	void addsThePackagesNeededByChangedJavaSources(String after, Set<String> expected) throws Exception {
		IFile descriptor= create("META-INF/MANIFEST.MF", MANIFEST); //$NON-NLS-1$
		IFile source= create("Example.java", ORIGINAL); //$NON-NLS-1$
		Files.writeString(path(source), after, StandardCharsets.UTF_8);
		byte[] before= bytes(descriptor);
		assertEquals(1, update(source, ORIGINAL, descriptor));
		byte[] updated= bytes(descriptor);
		var attributes= new Manifest(new ByteArrayInputStream(updated)).getMainAttributes();
		Set<String> packages= Arrays.stream(attributes.getValue("Import-Package").split(",")) //$NON-NLS-1$ //$NON-NLS-2$
				.map(String::strip).collect(Collectors.toSet());
		packages.remove("existing.package"); //$NON-NLS-1$
		assertEquals(expected, packages);
		assertEquals("org.junit", attributes.getValue("Require-Bundle")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(0, update(source, ORIGINAL, descriptor), "Existing imports must not be rewritten"); //$NON-NLS-1$
		assertArrayEquals(updated, bytes(descriptor));
		assertTrue(updated.length > before.length);
	}

	@ParameterizedTest
	@ValueSource(strings= {
			"// import org.junit.jupiter.api.Test;\nclass Example {}", //$NON-NLS-1$
			"/* @org.junit.jupiter.api.Test */ class Example {}", //$NON-NLS-1$
			"/** {@link org.junit.jupiter.api.Test} */ class Example {}", //$NON-NLS-1$
			"class Example { String text= \"org.junit.jupiter.api.Test\"; }", //$NON-NLS-1$
			"class Example { String text= \"\"\"\nimport org.junit.jupiter.api.Test;\n\"\"\"; }", //$NON-NLS-1$
			"import other.org.junit.jupiter.api.Test; @Test class Example {}" //$NON-NLS-1$
	})
	void ignoresCommentsLiteralTestSourcesAndUnrelatedPackages(String after) throws Exception {
		IFile descriptor= create("META-INF/MANIFEST.MF", MANIFEST); //$NON-NLS-1$
		IFile source= create("Example.java", ORIGINAL); //$NON-NLS-1$
		Files.writeString(path(source), after, StandardCharsets.UTF_8);
		assertEquals(0, update(source, ORIGINAL, descriptor));
		assertEquals(MANIFEST, Files.readString(path(descriptor), StandardCharsets.UTF_8));
	}

	@Test
	void existingJupiterUsageDoesNotRewriteTheDescriptor() throws Exception {
		String before= "import org.junit.jupiter.api.Test; @Test class Example {}"; //$NON-NLS-1$
		IFile descriptor= create("META-INF/MANIFEST.MF", MANIFEST); //$NON-NLS-1$
		IFile source= create("Example.java", before); //$NON-NLS-1$
		Files.writeString(path(source), before + "\n// another cleanup changed this file\n", StandardCharsets.UTF_8); //$NON-NLS-1$
		assertEquals(0, update(source, before, descriptor));
		assertEquals(MANIFEST, Files.readString(path(descriptor), StandardCharsets.UTF_8));
	}

	@Test
	void doesNotApplyPdeImportsToMavenDescriptors() throws Exception {
		String pom= "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion></project>"; //$NON-NLS-1$
		IFile descriptor= create("pom.xml", pom); //$NON-NLS-1$
		IFile source= create("Example.java", ORIGINAL); //$NON-NLS-1$
		Files.writeString(path(source), "@org.junit.jupiter.api.Test class Example {}", StandardCharsets.UTF_8); //$NON-NLS-1$
		assertEquals(0, update(source, ORIGINAL, descriptor));
		assertEquals(pom, Files.readString(path(descriptor), StandardCharsets.UTF_8));
	}

	private static int update(IFile source, String before, IFile descriptor) throws Exception {
		Class<?> owner= ProjectWideCodeCleanupApplication.class;
		Class<?> sourceType= Class.forName(owner.getName() + "$SourceSnapshot"); //$NON-NLS-1$
		Class<?> resourceType= Class.forName(owner.getName() + "$ResourceSnapshot"); //$NON-NLS-1$
		Constructor<?> sourceConstructor= sourceType.getDeclaredConstructor(
				ICompilationUnit.class, IFile.class, Path.class, String.class, byte[].class);
		sourceConstructor.setAccessible(true);
		Constructor<?> resourceConstructor= resourceType.getDeclaredConstructor(IFile.class, Path.class, String.class, byte[].class);
		resourceConstructor.setAccessible(true);
		Object snapshot= sourceConstructor.newInstance(null, source, path(source),
				source.getProjectRelativePath().toString(), before.getBytes(StandardCharsets.UTF_8));
		Object resource= resourceConstructor.newInstance(descriptor, path(descriptor),
				descriptor.getProjectRelativePath().toString(), bytes(descriptor));
		List<Object> written= new ArrayList<>();
		Method update= owner.getDeclaredMethod("updateProjectMetadata", List.class, List.class, List.class, Map.class, MixedSupportImport.Bindings.class); //$NON-NLS-1$
		update.setAccessible(true);
		try {
			update.invoke(null, List.of(snapshot), List.of(resource), written, JavaCore.getOptions(), null);
		} catch (InvocationTargetException exception) {
			if (exception.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw exception;
		}
		return written.size();
	}

	private IFile create(String name, String content) throws Exception {
		IFile file= project.getFile(name);
		file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), false, null);
		return file;
	}

	private static Path path(IFile file) {
		return file.getLocation().toFile().toPath();
	}

	private static byte[] bytes(IFile file) throws Exception {
		return Files.readAllBytes(path(file));
	}
}
