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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions.performAndCheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.cleanup.multifile.AbstractPlannedMultiFileCleanUp;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ClosedUniqueSequenceCleanUpTest {
	@RegisterExtension
	final EclipseJava22 java= new EclipseJava22();
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	private static final String PRODUCER= """
			package p;
			import java.util.ArrayList;
			import java.util.List;
			final class Names {
				static List<String> unique(String[] input) {
					List<String> names = new ArrayList<>();
					for (String item : input) {
						if (!names.contains(item)) { names.add(item); }
					}
					return names;
				}
			}
			""";
	private static final String CALLER= """
			package p;
			import java.util.List;
			public class Report {
				public static String run(String[] input) {
					List<String> names = Names.unique(input);
					return Reader.print(names);
				}
			}
			""";
	private static final String READER= """
			package p;
			import java.util.List;
			final class Reader {
				static String print(List<String> names) {
					StringBuilder result = new StringBuilder();
					for (String value : names) result.append(value).append(',');
					return result.toString();
				}
			}
			""";
	private ICompilationUnit create(String name, String source) throws Exception {
		return java.getSourceFolder().createPackageFragment("p", false, null).createCompilationUnit(name, source, true, monitor);
	}

	private AbstractPlannedMultiFileCleanUp<?> cleanup(boolean enabled) throws Exception {
		var extension= Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor("org.eclipse.jdt.ui.cleanUps"))
				.filter(element -> "org.sandbox.jdt.ui.cleanup.container_contracts.closed_unique_sequence".equals(element.getAttribute("id"))).findFirst();
		assertTrue(extension.isPresent(), "The coordinated ordered-set cleanup must be available through the Eclipse extension registry");
		var result= (AbstractPlannedMultiFileCleanUp<?>) extension.get().createExecutableExtension("class");
		var options= new CleanUpOptions();
		options.setOption(CLEANUP, CleanUpOptions.TRUE);
		options.setOption("cleanup.container_contracts.closed_unique_sequence", enabled ? CleanUpOptions.TRUE : CleanUpOptions.FALSE);
		result.setOptions(options);
		return result;
	}
	private CleanUpContext context(ICompilationUnit unit) {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return new CleanUpContext(unit, (CompilationUnit) parser.createAST(monitor));
	}
	private String execute(List<ICompilationUnit> units, Path target) throws Exception {
		Files.createDirectories(target);
		List<String> args= new ArrayList<>(List.of("-d", target.toString()));
		for (var unit : units) {
			args.add(Files.writeString(target.resolve(unit.getElementName()), unit.getSource()).toString());
		}
		assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
		try (var loader= new URLClassLoader(new URL[] {target.toUri().toURL()}, null)) {
			return (String) loader.loadClass("p.Report").getMethod("run", String[].class).invoke(null,
					(Object) new String[] {"z", null, "z", "a", null});
		}
	}

	@Test
	void rewritesUniqueReturnDirectCallerAndReaderParameterTogether(@TempDir Path directory) throws Exception {
		var producer= create("Names.java", PRODUCER);
		var caller= create("Report.java", CALLER);
		var reader= create("Reader.java", READER);
		var units= List.of(producer, caller, reader);
		var disabled= cleanup(false);
		disabled.checkPreConditions(java.getJavaProject(), units.toArray(ICompilationUnit[]::new), monitor);
		assertNull(disabled.createFix(context(producer)));
		var cleanup= cleanup(true);
		var discovered= cleanup.expandCleanUpScope(java.getJavaProject(), List.of(producer), monitor);
		assertTrue(discovered.contains(caller));
		assertTrue(discovered.contains(reader));
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] {producer, caller}, monitor);
		assertNull(cleanup.createFix(context(producer)));
		assertEquals("z,null,a,", execute(units, directory.resolve("before")));
		cleanup.checkPreConditions(java.getJavaProject(), units.toArray(ICompilationUnit[]::new), monitor);
		var preview= cleanup.getCoordinatedCleanUpPreview(java.getJavaProject());
		assertEquals(1, preview.size());
		var shown= preview.iterator().next().get("details").toString();
		for (String expected : List.of("CLOSED_SOURCE_AUTOMATIC", "IMPOSSIBLE_RETURN_TYPE_ONLY", "p.Names.unique", "p.Reader.print", "Report.java")) {
			assertTrue(shown.contains(expected), shown);
		}
		var combined= new CompositeChange("Unique set closure");
		for (var unit : units) {
			var fix= cleanup.createFix(context(unit));
			assertNotNull(fix, unit.getElementName());
			combined.add(fix.createChange(monitor));
		}
		var diagnostics= (IMultiFileCleanUpDiagnosticsProvider) cleanup;
		String planned= diagnostics.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(planned.contains("CLOSED_SOURCE_AUTOMATIC"));
		assertFalse(planned.contains("\"selectedCompilationUnits\":[]"), planned);
		assertFalse(planned.contains("Names.java"));
		cleanup.checkPostConditions(monitor);
		assertEquals(planned, diagnostics.getLastPlanningDiagnosticsJson(java.getJavaProject()),
				"The headless application reads diagnostics after Eclipse checks all conditions.");
		assertTrue(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty());
		var undo= performAndCheck(combined, monitor, units.toArray(ICompilationUnit[]::new));
		assertTrue(producer.getSource().contains("Set<String> unique"));
		assertTrue(producer.getSource().contains("new LinkedHashSet<>()"));
		assertFalse(producer.getSource().contains("!names.contains"));
		assertTrue(caller.getSource().contains("Set<String> names"));
		assertTrue(reader.getSource().contains("print(Set<String> names)"));
		assertEquals("z,null,a,", execute(units, directory.resolve("after")));
		cleanup.checkPreConditions(java.getJavaProject(), units.toArray(ICompilationUnit[]::new), monitor);
		for (var unit : units) {
			assertNull(cleanup.createFix(context(unit)));
		}
		undo.perform(monitor);
		assertEquals(PRODUCER, producer.getSource());
		assertEquals(CALLER, caller.getSource());
		assertEquals(READER, reader.getSource());
	}

	@Test
	void excludesLateUnmigratedReaderCallAndPositionalObservation() throws Exception {
		var producer= create("Names.java", PRODUCER);
		var caller= create("Report.java", CALLER);
		var reader= create("Reader.java", READER);
		var units= new ICompilationUnit[] {producer, caller, reader};
		var cleanup= cleanup(true);
		cleanup.checkPreConditions(java.getJavaProject(), units, monitor);
		assertNotNull(cleanup.createFix(context(producer)));
		var other= create("Late.java", "package p; class Late { String text() { return Reader.print(java.util.List.of(\"x\")); } }");
		assertThrows(CoreException.class, () -> cleanup.createFix(context(caller)));
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] {producer, caller, reader, other}, monitor);
		assertNull(cleanup.createFix(context(producer)));
		other.delete(true, monitor);
		reader.getBuffer().setContents(READER.replace("result.toString()", "names.get(0)"));
		reader.save(monitor, true);
		cleanup.checkPreConditions(java.getJavaProject(), units, monitor);
		for (var unit : units) {
			assertNull(cleanup.createFix(context(unit)));
		}
		assertEquals(PRODUCER, producer.getSource());
		assertEquals(CALLER, caller.getSource());
	}
}
