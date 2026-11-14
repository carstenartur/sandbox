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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLOSED_SOURCE_PARAMETER_MIGRATION;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpDiagnosticsProvider;
import org.sandbox.jdt.cleanup.multifile.api.IMultiFileCleanUpScopeProvider;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUp;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class CoordinatedContainerCleanUpTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void scopeExpansionEmitsClosedSourceClosureOnce() throws Exception {
		ICompilationUnit caller= createUnit("Caller.java", callerSource()); //$NON-NLS-1$
		ICompilationUnit receiver= createUnit("Receiver.java", receiverSource()); //$NON-NLS-1$
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		NullProgressMonitor monitor= new NullProgressMonitor();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(
				context.getJavaProject(), List.of(caller), monitor);
		assertEquals(Set.of(caller.getHandleIdentifier(), receiver.getHandleIdentifier()),
				expanded.stream().map(ICompilationUnit::getHandleIdentifier)
						.collect(java.util.stream.Collectors.toSet()));

		assertTrue(cleanup.expandCleanUpScope(
				context.getJavaProject(), expanded, monitor).isEmpty(),
				"A complete source closure must be a stable fixed point"); //$NON-NLS-1$
	}

	@Test
	void plannedLifecycleAppliesAndUndoesClosedCallerParameterMigration() throws Exception {
		ICompilationUnit unit= createUnit("Sample.java", source()); //$NON-NLS-1$
		ICompilationUnit unrelated= createUnit("Unrelated.java", //$NON-NLS-1$
				"package test; class Unrelated { int value() { return 1; } }"); //$NON-NLS-1$
		String original= unit.getSource();
		String unrelatedOriginal= unrelated.getSource();
		NullProgressMonitor monitor= new NullProgressMonitor();
		CoordinatedContainerCleanUpCore cleanup= cleanup();

		Collection<ICompilationUnit> discovered= cleanup.expandCleanUpScope(
				context.getJavaProject(), List.of(unit, unrelated), monitor);
		assertTrue(discovered.isEmpty(),
				"The selected same-unit flow already contains its complete source closure"); //$NON-NLS-1$
		LinkedHashSet<ICompilationUnit> scope= new LinkedHashSet<>();
		scope.add(unit);
		scope.add(unrelated);

		RefactoringStatus status= cleanup.checkPreConditions(
				context.getJavaProject(), scope.toArray(ICompilationUnit[]::new), monitor);
		assertFalse(status.hasFatalError(), status.toString());
		Collection<Map<String, Object>> preview=
				cleanup.getCoordinatedCleanUpPreview(context.getJavaProject());
		assertEquals(1, preview.size());
		Object previewUnits= preview.iterator().next().get("compilationUnits"); //$NON-NLS-1$
		assertTrue(previewUnits instanceof List<?>);
		assertEquals(List.of(unit), previewUnits,
				"Candidate preview must list edited units, not every selected analysis unit"); //$NON-NLS-1$
		String diagnostics= cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject());
		assertTrue(diagnostics.contains("PROJECT_CLOSED")); //$NON-NLS-1$
		assertFalse(diagnostics.contains(unit.getHandleIdentifier()),
				"Explicit JSON diagnostics must anonymize Java-model handles"); //$NON-NLS-1$
		assertFalse(diagnostics.contains(unrelated.getHandleIdentifier()),
				"Unrelated selected units must not leak through exported diagnostics"); //$NON-NLS-1$

		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix);
		Change undo= fix.createChange(null).perform(monitor);
		assertNotNull(undo);

		String transformed= unit.getSource();
		assertTrue(transformed.contains("List<String> values")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("void consume(List<String> values)")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.size()")); //$NON-NLS-1$
		assertFalse(transformed.contains("Arrays.copyOf(values")); //$NON-NLS-1$
		assertEquals(unrelatedOriginal, unrelated.getSource());

		cleanup.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(original, unit.getSource());
		assertEquals(unrelatedOriginal, unrelated.getSource());
	}

	@Test
	void migratesPrivateConstructorArgumentAndRestoresBothDeclarations() throws Exception {
		String source= """
			package test;
			import java.util.Arrays;
			class Sample {
				final int size;
				private Sample(String[] items) {
					size = items.length;
				}
				static Sample collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return new Sample(values);
				}
			}
			""";
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		NullProgressMonitor monitor= new NullProgressMonitor();
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
		Change undo= fix.createChange(null).perform(monitor);
		assertNotNull(undo);
		String transformed= unit.getSource();
		assertTrue(transformed.contains("private Sample(List<String> items)")); //$NON-NLS-1$
		assertTrue(transformed.contains("size = items.size()")); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(value)")); //$NON-NLS-1$
		assertTrue(transformed.contains("return new Sample(values)")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		cleanup.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(source, unit.getSource());
	}

	@Test
	void migratesConstructorOwnedFieldWithItsCallerAndRejectsEscapesAndStaleReaders() throws Exception {
		String source= """
				package test;
				import java.util.Arrays;
				final class Sample {
				    private final String[] entries;
				    private Sample(String[] items) { this.entries = items; }
				    int rowCount() { return entries.length; }
				    static Sample collect(String item) {
				        String[] values = new String[0];
				        values = Arrays.copyOf(values, values.length + 1);
				        values[values.length - 1] = item;
				        return new Sample(values);
				    }
				}
				""";
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		var monitor= new NullProgressMonitor();
		var cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
		Change undo= fix.createChange(null).perform(monitor);
		assertNotNull(undo);
		String transformed= unit.getSource();
		assertTrue(transformed.contains("private final List<String> entries;"), transformed); //$NON-NLS-1$
		assertTrue(transformed.contains("private Sample(List<String> items)"), transformed); //$NON-NLS-1$
		assertTrue(transformed.contains("this.entries = items"), transformed); //$NON-NLS-1$
		assertTrue(transformed.contains("return entries.size()"), transformed); //$NON-NLS-1$
		assertTrue(transformed.contains("values.add(item)"), transformed); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		cleanup.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(source, unit.getSource());
		var planned= cleanup();
		planned.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		// Append an identity observation after the original argument so every recorded source range stays valid.
		String stale= source.stripTrailing().substring(0, source.stripTrailing().length() - 1)
				+ "int observed() {return System.identityHashCode(entries);}}"; //$NON-NLS-1$
		unit.getBuffer().setContents(stale);
		unit.save(monitor, true);
		try {
			planned.createFix(new CleanUpContext(unit, parse(unit)));
			org.junit.jupiter.api.Assertions.fail("Stale field observation must reject the whole fix"); //$NON-NLS-1$
		} catch (CoreException expected) {
			assertTrue(expected.getMessage().contains("owned field or its readers changed"), expected::getMessage); //$NON-NLS-1$
		}
		assertEquals(stale, unit.getSource());
		planned.checkPostConditions(monitor);
		for (String unsafe : List.of(
				source.replace("return entries.length;", "return System.identityHashCode(entries);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("String[] entries", "Object[] entries").replace("String[] items", "Object[] items"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				source.replace("private final String[]", "final String[]"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return entries.length;", "entries[0] = null; return entries.length;"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("final class Sample {", "final class Sample implements java.io.Serializable {"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return new Sample(values);", "try { return new Sample(values); } finally { values[0] = null; }"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return entries.length;", "return this.getClass().getDeclaredFields().length;"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return new Sample(values);", "Sample result = new Sample(values); values[0] = null; return result;"))) { //$NON-NLS-1$ //$NON-NLS-2$
			unit.getBuffer().setContents(unsafe);
			unit.save(monitor, true);
			cleanup= cleanup();
			cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
			assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))), unsafe);
			assertEquals(unsafe, unit.getSource());
			cleanup.checkPostConditions(monitor);
		}
	}

	@Test
	void rejectsConstructorWhenAnotherOverloadCouldCaptureTheList() throws Exception {
		String source= """
			package test;
			import java.util.Arrays;
			import java.util.List;
			class Sample {
				private Sample(String[] items) {}
				private Sample(List<String> items) {}
				static Sample collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					return new Sample(values);
				}
			}
			""";
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, new NullProgressMonitor());
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		assertEquals(source, unit.getSource());
	}

	@Test
	void plannedLifecycleCreatesFixForWorkingCopy() throws Exception {
		ICompilationUnit unit= createUnit("WorkingCopy.java", source()); //$NON-NLS-1$
		NullProgressMonitor monitor= new NullProgressMonitor();
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		RefactoringStatus status= cleanup.checkPreConditions(
				context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertFalse(status.hasFatalError(), status.toString());

		ICompilationUnit workingCopy= unit.getWorkingCopy(null);
		try {
			assertTrue(workingCopy.isWorkingCopy());
			assertEquals(unit, workingCopy.getPrimary(),
					"The plan identity must resolve through the JDT primary compilation unit"); //$NON-NLS-1$
			ICleanUpFix fix= cleanup.createFix(
					new CleanUpContext(workingCopy, parse(workingCopy)));
			assertNotNull(fix,
					"A plan keyed by the primary unit must still resolve for its working copy"); //$NON-NLS-1$
		} finally {
			workingCopy.discardWorkingCopy();
			cleanup.checkPostConditions(monitor);
		}
	}

	@Test
	void wrapperExposesMultiFileContractsWithoutSaveActionRegistration() throws Exception {
		CoordinatedContainerCleanUp cleanup= new CoordinatedContainerCleanUp(Map.of(
				CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		IMultiFileCleanUpScopeProvider scopeProvider= cleanup;
		IMultiFileCleanUpDiagnosticsProvider diagnosticsProvider= cleanup;
		assertTrue(scopeProvider.expandCleanUpScope(
				context.getJavaProject(), List.of(), new NullProgressMonitor()).isEmpty());
		assertEquals("", diagnosticsProvider.getLastPlanningDiagnosticsJson(context.getJavaProject())); //$NON-NLS-1$

		var registry= Platform.getExtensionRegistry();
		assertNotNull(registry);
		var elements= registry.getConfigurationElementsFor(
				"org.eclipse.jdt.ui.cleanUps"); //$NON-NLS-1$
		assertTrue(Arrays.stream(elements).anyMatch(element ->
				"cleanUp".equals(element.getName()) //$NON-NLS-1$
						&& "org.sandbox.jdt.ui.cleanup.container_contracts.closed_source_parameter_migration" //$NON-NLS-1$
								.equals(element.getAttribute("id")))); //$NON-NLS-1$
		assertFalse(Arrays.stream(elements).anyMatch(element ->
				"org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup.SandboxCodeTabPage" //$NON-NLS-1$
						.equals(element.getAttribute("class")) //$NON-NLS-1$
						&& "saveAction".equals(element.getAttribute("cleanUpKind")))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private CoordinatedContainerCleanUpCore cleanup() {
		return new CoordinatedContainerCleanUpCore(Map.of(
				CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
	}

	private ICompilationUnit createUnit(String name, String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit(name, source, true, null);
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

	private static String callerSource() {
		return """
			package test;
			import java.util.Arrays;
			class Caller {
				void collect(Receiver receiver, String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					receiver.consume(values);
				}
			}
			""";
	}

	private static String receiverSource() {
		return """
			package test;
			class Receiver {
				void consume(String[] values) {
					System.out.println(values.length);
					for (String current : values) {
						System.out.println(current);
					}
				}
			}
			""";
	}

	@Test
	void preservesTheElementSubstitutionOfAClosedGenericCall() throws Exception {
		String source= genericConsumer("consume(values);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		NullProgressMonitor monitor= new NullProgressMonitor();
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		ICleanUpFix fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
		Change undo= fix.createChange(null).perform(monitor);
		assertNotNull(undo);
		assertTrue(unit.getSource().contains("List<String> values")); //$NON-NLS-1$
		assertTrue(unit.getSource().contains("<T> void consume(List<T> items)")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		cleanup.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(source, unit.getSource());
	}

	@Test
	void rejectsWidenedElementSubstitutionAtAClosedGenericCall() throws Exception {
		String source= genericConsumer("this.<Object>consume(values);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, new NullProgressMonitor());
		assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		assertEquals(source, unit.getSource());
	}

	@Test
	void revalidatesTheGenericSubstitutionAtTheSameArgumentRange() throws Exception {
		String source= genericConsumer("this.<String>consume(values);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit("Sample.java", source); //$NON-NLS-1$
		NullProgressMonitor monitor= new NullProgressMonitor();
		CoordinatedContainerCleanUpCore cleanup= cleanup();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertNotNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))));

		String changed= source.replace("<String>consume", "<Object>consume"); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(changed);
		unit.save(monitor, true);
		CoreException failure= assertThrows(CoreException.class,
				() -> cleanup.createFix(new CleanUpContext(unit, parse(unit))));
		assertTrue(failure.getMessage().contains("argument-transfer target changed")); //$NON-NLS-1$
		assertEquals(changed, unit.getSource());
	}

	private static String genericConsumer(String call) {
		return """
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					CALL_SITE
				}
				private <T> void consume(T[] items) {
					System.out.println(items.length);
					for (T item : items) System.out.println(item);
				}
			}
			""".replace("CALL_SITE", call); //$NON-NLS-1$
	}

	private static String source() {
		return """
			package test;
			import java.util.Arrays;
			class Sample {
				void collect(String value) {
					String[] values = new String[0];
					values = Arrays.copyOf(values, values.length + 1);
					values[values.length - 1] = value;
					consume(values);
				}
				void consume(String[] values) {
					System.out.println(values.length);
					for (String current : values) {
						System.out.println(current);
					}
				}
			}
			""";
	}
}
