/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.APPEND_ARRAY_TO_LIST;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.PARALLEL_ARRAYS_TO_RECORDS;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.UNIQUE_SEQUENCE_TO_SET;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpRule.ExecutionMode;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.ContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerCleanUpTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesBothExecutableContainerRulesInOneCleanupFix() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			class Sample {
				void collect(String value) {
					String[] appended = new String[0];
					appended = Arrays.copyOf(appended, appended.length + 1);
					appended[appended.length - 1] = value;
					System.out.println(appended.length);
					List<String> unique = new ArrayList<>();
					if (!unique.contains(value)) {
						unique.add(value);
					}
					for (String current : unique) {
						System.out.println(current);
					}
				}
			}
			""");
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.TRUE,
				APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE,
				UNIQUE_SEQUENCE_TO_SET, CleanUpOptions.TRUE));

		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		fix.createChange(null).perform(null);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("List<String> appended")); //$NON-NLS-1$
		assertTrue(transformed.contains("appended.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("appended.size()")); //$NON-NLS-1$
		assertFalse(transformed.contains("Arrays.copyOf(appended")); //$NON-NLS-1$
		assertTrue(transformed.contains("Set<String> unique")); //$NON-NLS-1$
		assertTrue(transformed.contains("new LinkedHashSet<>()")); //$NON-NLS-1$
		assertFalse(transformed.contains("unique.contains(value)")); //$NON-NLS-1$
	}

	@Test
	void preservesPublishedArrayTypeAndUndoesLocalBuilderMigration(@TempDir Path classesDirectory) throws Exception {
		String source= """
			package test;
			import java.util.Arrays;
			public class Sample {
				public static String[] collect(String... input) {
					String[] values = new String[0];
					for (String value : input) {
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = value;
					}
					return values;
				}
			}
			""";
		ICompilationUnit unit= createUnit(source);
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.TRUE, APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE));

		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		var undo= fix.createChange(null).perform(null);
		String transformed= unit.getSource();
		assertTrue(transformed.contains("String[] collect(String... input)")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("return values.toArray(new String[0]);")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		verifyPublishedArrays(transformed, classesDirectory);
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		assertNotNull(undo);
		undo.perform(null);
		assertEquals(source, unit.getSource());
	}

	@Test
	void preservesObservableArrayStoreAndIterationBehavior(@TempDir Path directory) throws Exception {
		String source= """
			package test;
			import java.util.Arrays;
			public class Sample {
				public static int collected(Object item) {
					Object[] values = new String[0];
					try {
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = item;
					} catch (ArrayStoreException expected) { return values.length; }
					return -1;
				}
				public static int traversed(String item) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = item;
					for (String old : values) {
						values = Arrays.copyOf(values, values.length + 1);
						values[values.length - 1] = old;
					}
					return values.length;
				}
			}
			""";
		ICompilationUnit unit= createUnit(source);
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.TRUE, APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE));
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		assertEquals(source, unit.getSource());
		Path file= Files.writeString(directory.resolve("Sample.java"), unit.getSource()); //$NON-NLS-1$
		assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", directory.toString(), file.toString())); //$NON-NLS-1$
		try (var classes= new URLClassLoader(new URL[] { directory.toUri().toURL() }, null)) {
			Class<?> sample= classes.loadClass("test.Sample"); //$NON-NLS-1$
			assertEquals(1, sample.getMethod("collected", Object.class).invoke(null, new Object())); //$NON-NLS-1$
			assertEquals(2, sample.getMethod("traversed", String.class).invoke(null, "value")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static void verifyPublishedArrays(String source, Path directory) throws Exception {
		Path file= Files.writeString(directory.resolve("Sample.java"), source); //$NON-NLS-1$
		int exit= javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", directory.toString(), file.toString()); //$NON-NLS-1$
		assertEquals(0, exit);
		try (var classes= new URLClassLoader(new URL[] { directory.toUri().toURL() }, null)) {
			var collect= classes.loadClass("test.Sample").getMethod("collect", String[].class); //$NON-NLS-1$ //$NON-NLS-2$
			String[] input= { "repeat", null, "repeat" }; //$NON-NLS-1$ //$NON-NLS-2$
			String[] result= (String[]) collect.invoke(null, (Object) input);
			assertArrayEquals(input, result);
			assertNotSame(input, result);
			assertEquals(String[].class, result.getClass());
			assertEquals(0, ((String[]) collect.invoke(null, (Object) new String[0])).length);
		}
	}

	@Test
	void migratesClosedParallelArraysOnlyAtRecordSourceLevels(@TempDir Path temp) throws Exception {
		String source= """
				package test;
				import java.util.Arrays;
				public class Sample {
				    static String pair(String label, Integer value) { return label + "=" + value + ";"; }
				    public static String collect(String[] input) {
				        String[] labels = new String[0];
				        Integer[] values = new Integer[0];
				        int count = 0;
				        for (String label : input) {
				            labels = Arrays.copyOf(labels, count + 1);
				            values = Arrays.copyOf(values, count + 1);
				            labels[count] = label;
				            values[count] = label == null ? null : label.length();
				            count++;
				        }
				        StringBuilder text = new StringBuilder();
				        for (int i = 0; i < count; i++) text.append(pair(labels[i], values[i]));
				        return text.toString();
				    }
				}
				""";
		var unit= createUnit(source);
		var cleanup= new ContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				PARALLEL_ARRAYS_TO_RECORDS, CleanUpOptions.TRUE));
		var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		var undo= fix.createChange(null).perform(null);
		assertNotNull(undo);
		String migrated= unit.getSource();
		assertTrue(migrated.contains("record Row("), migrated); //$NON-NLS-1$
		assertTrue(migrated.contains("new Row("), migrated); //$NON-NLS-1$
		assertFalse(migrated.contains("Arrays.copyOf("), migrated); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()), migrated);
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		Path output= Files.createDirectories(temp.resolve("classes")); //$NON-NLS-1$
		Path java= Files.writeString(temp.resolve("Sample.java"), migrated); //$NON-NLS-1$
		assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "16", "-d", output.toString(), java.toString())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try (var classes= new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
			var collect= classes.loadClass("test.Sample").getMethod("collect", String[].class); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("ab=2;null=null;ab=2;", collect.invoke(null, (Object) new String[] { "ab", null, "ab" })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("", collect.invoke(null, (Object) new String[0])); //$NON-NLS-1$
		}
		undo.perform(null);
		assertEquals(source, unit.getSource());
		var project= unit.getJavaProject();
		var before= project.getOptions(false);
		try {
			project.setOption(org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE, "15"); //$NON-NLS-1$
			assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		} finally {
			project.setOptions(before);
		}
		String conflict= source.replace("String[] labels =", "class Row {} int rows = 0; String[] labels ="); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(conflict);
		unit.save(null, true);
		var renamed= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(renamed);
		renamed.createChange(null).perform(null);
		assertTrue(unit.getSource().contains("record Row2("), unit.getSource()); //$NON-NLS-1$
		assertTrue(unit.getSource().contains("rows2.add("), unit.getSource()); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));

		for (String blocked : List.of(source.replace("i < count", "i <= count"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("for (String label : input)", "try { for (String label : input)") //$NON-NLS-1$ //$NON-NLS-2$
						.replace("StringBuilder text =", "} catch (RuntimeException ex) {} StringBuilder text ="), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("Integer[] values", "Number[] values"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("values[count] = label", "/* keep component origin */ values[count] = label"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("values[count] = label", "values[0] = label"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("StringBuilder text =", "Object alias = values; StringBuilder text ="))) { //$NON-NLS-1$ //$NON-NLS-2$
			unit.getBuffer().setContents(blocked);
			unit.save(null, true);
			assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))), blocked);
		}
	}

	@Test
	void disabledMasterProducesNoFix() throws CoreException {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
				}
			}
			""");
		ContainerCleanUpCore cleanup= new ContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.FALSE,
				APPEND_ARRAY_TO_LIST, CleanUpOptions.TRUE));
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
	}

	@Test
	void cleanupExtensionIsRegisteredWithoutSaveActionConfiguration() {
		var registry= Platform.getExtensionRegistry();
		assertNotNull(registry);
		var elements= registry.getConfigurationElementsFor(
				"org.eclipse.jdt.ui.cleanUps"); //$NON-NLS-1$
		assertTrue(Arrays.stream(elements).anyMatch(element ->
				"cleanUp".equals(element.getName()) //$NON-NLS-1$
						&& "org.sandbox.jdt.ui.cleanup.container_contracts".equals( //$NON-NLS-1$
								element.getAttribute("id")))); //$NON-NLS-1$
		assertFalse(Arrays.stream(elements).anyMatch(element ->
				"org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup.SandboxCodeTabPage".equals( //$NON-NLS-1$
							element.getAttribute("class")) //$NON-NLS-1$
						&& "saveAction".equals(element.getAttribute("cleanUpKind")))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void projectClosedRuleIsRegisteredButNotExposedAsLocalCleanup() {
		ContainerCleanUpRule projectRule=
				ContainerCleanUpRule.CLOSED_SOURCE_PARAMETER_MIGRATION;
		assertEquals(ExecutionMode.PROJECT_CLOSED, projectRule.executionMode());
		assertFalse(projectRule.isLocalCleanUp());
		assertEquals(
				List.of(
						ContainerCleanUpRule.APPEND_ARRAY_TO_LIST,
						ContainerCleanUpRule.UNIQUE_SEQUENCE_TO_ORDERED_SET, ContainerCleanUpRule.PARALLEL_ARRAYS_TO_RECORDS),
				ContainerCleanUpRule.localCleanUps());
		assertTrue(Arrays.stream(ContainerCleanUpRule.values())
				.allMatch(rule -> !rule.helpContextId().isBlank()));
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(
				"Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
