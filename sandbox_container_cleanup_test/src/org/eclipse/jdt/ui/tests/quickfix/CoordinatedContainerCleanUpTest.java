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
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.RETAIN_PROVEN_PARAMETER_BRIDGE;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

	@Test
	void migratesPackageReturnAndItsSourceCallersAtomically() throws Exception {
		String producer= """
				package test;
				import java.util.Arrays;
				final class Rows {
				    static String[] collect(String value) {
				        String[] rows = new String[0];
				        rows = Arrays.copyOf(rows, rows.length + 1);
				        rows[rows.length - 1] = value;
				        return rows;
				    }
				}
				""";
		String consumer= """
				package test;
				final class Display {
				    int count(String value) {
				        String[] received = Rows.collect(value);
				        return received.length;
				    }
				}
				""";
		ICompilationUnit rows= createUnit("Rows.java", producer); //$NON-NLS-1$
		ICompilationUnit display= createUnit("Display.java", consumer); //$NON-NLS-1$
		String otherSource= consumer.replace("class Display", "class Other"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit other= createUnit("Other.java", otherSource); //$NON-NLS-1$
		var scope= new ICompilationUnit[] { rows, display, other };
		var monitor= new NullProgressMonitor();
		var cleanup= cleanup();
		var expanded= cleanup.expandCleanUpScope(context.getJavaProject(), List.of(rows), monitor);
		assertTrue(expanded.containsAll(List.of(display, other)));
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { rows, display }, monitor);
		assertNull(cleanup.createFix(new CleanUpContext(rows, parse(rows))));
		cleanup.checkPostConditions(monitor);
		cleanup.checkPreConditions(context.getJavaProject(), scope, monitor);
		ICleanUpFix producerFix= cleanup.createFix(new CleanUpContext(rows, parse(rows)));
		assertNotNull(producerFix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
		ICleanUpFix callerFix= cleanup.createFix(new CleanUpContext(display, parse(display)));
		assertNotNull(callerFix);
		var change= new org.eclipse.ltk.core.refactoring.CompositeChange("Migrate return and callers"); //$NON-NLS-1$
		change.add(producerFix.createChange(null));
		change.add(callerFix.createChange(null));
		var otherFix= cleanup.createFix(new CleanUpContext(other, parse(other)));
		assertNotNull(otherFix);
		change.add(otherFix.createChange(null));
		change.initializeValidationData(monitor);
		Change undo= change.perform(monitor);
		assertNotNull(undo);
		assertTrue(rows.getSource().contains("static List<String> collect("), rows.getSource()); //$NON-NLS-1$
		assertTrue(rows.getSource().contains("rows.add(value)")); //$NON-NLS-1$
		assertTrue(rows.getSource().contains("return rows;")); //$NON-NLS-1$
		assertTrue(display.getSource().contains("List<String> received = Rows.collect(value)")); //$NON-NLS-1$
		assertTrue(display.getSource().contains("return received.size();")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(rows).getProblems()).anyMatch(problem -> problem.isError()));
		assertFalse(Arrays.stream(parse(display).getProblems()).anyMatch(problem -> problem.isError()));
		assertTrue(other.getSource().contains("return received.size();")); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(other).getProblems()).anyMatch(problem -> problem.isError()));
		cleanup.checkPostConditions(monitor);
		var secondPass= cleanup();
		secondPass.checkPreConditions(context.getJavaProject(), scope, monitor);
		for (ICompilationUnit migrated : scope) {
			assertNull(secondPass.createFix(new CleanUpContext(migrated, parse(migrated))));
		}
		secondPass.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(producer, rows.getSource());
		assertEquals(consumer, display.getSource());
		assertEquals(otherSource, other.getSource());

		// The return has no Java overload that could retain this signature for external callers.
		String exposed= producer.replace("final class Rows", "public final class Rows") //$NON-NLS-1$ //$NON-NLS-2$
				.replace("static String[] collect", "public static String[] collect"); //$NON-NLS-1$ //$NON-NLS-2$
		rows.getBuffer().setContents(exposed);
		rows.save(monitor, true);
		var external= cleanup();
		external.checkPreConditions(context.getJavaProject(), scope, monitor);
		assertNull(external.createFix(new CleanUpContext(rows, parse(rows))));
		assertEquals(exposed, rows.getSource());
		external.checkPostConditions(monitor);
		// An early return with a separate array has no producer-flow edge.
		String alternative= producer.replace("String[] rows =", //$NON-NLS-1$
				"if (value == null) return new String[0]; String[] rows ="); //$NON-NLS-1$
		rows.getBuffer().setContents(alternative);
		rows.save(monitor, true);
		var multipleReturns= cleanup();
		multipleReturns.checkPreConditions(context.getJavaProject(), scope, monitor);
		assertNull(multipleReturns.createFix(new CleanUpContext(rows, parse(rows))));
		assertEquals(alternative, rows.getSource());
		multipleReturns.checkPostConditions(monitor);
		rows.getBuffer().setContents(producer);
		rows.save(monitor, true);

		// A new observation in another file invalidates even the producer's unperformed edit.
		var planned= cleanup();
		planned.checkPreConditions(context.getJavaProject(), scope, monitor);
		String stale= consumer.replace("return received.length;", "return System.identityHashCode(received);"); //$NON-NLS-1$ //$NON-NLS-2$
		display.getBuffer().setContents(stale);
		display.save(monitor, true);
		try {
			planned.createFix(new CleanUpContext(rows, parse(rows)));
			org.junit.jupiter.api.Assertions.fail("A stale return consumer must invalidate the producer"); //$NON-NLS-1$
		} catch (CoreException expected) {
			assertTrue(expected.getMessage().contains("return"), expected::getMessage); //$NON-NLS-1$
		}
		assertEquals(producer, rows.getSource());
		assertEquals(stale, display.getSource());
		planned.checkPostConditions(monitor);

		for (String unsupported : List.of(
				consumer.replace("return received.length;", "return System.identityHashCode(received);"), //$NON-NLS-1$ //$NON-NLS-2$
				consumer.replace("String[] received", "Object[] received"), //$NON-NLS-1$ //$NON-NLS-2$
				consumer.replace("return received.length;", "received[0] = value; return received.length;"), //$NON-NLS-1$ //$NON-NLS-2$
				consumer.replace("String[] received = Rows.collect(value);", "java.util.function.Function<String, String[]> f = Rows::collect; String[] received = f.apply(value); System.identityHashCode(f);"))) { //$NON-NLS-1$ //$NON-NLS-2$
			display.getBuffer().setContents(unsupported);
			display.save(monitor, true);
			var rejected= cleanup();
			rejected.checkPreConditions(context.getJavaProject(), scope, monitor);
			assertNull(rejected.createFix(new CleanUpContext(rows, parse(rows))), unsupported);
			assertEquals(producer, rows.getSource());
			assertEquals(otherSource, other.getSource());
			rejected.checkPostConditions(monitor);
		}
	}

	@Test
	void migratesLocalMethodReferenceAndForwardingLambdaReturnTargets() throws Exception {
		String factorySource= """
				package test;
				import java.util.Arrays;
				final class Factory {
				    static String[] rows(String value) {
				        String[] items = new String[0];
				        items = Arrays.copyOf(items, items.length + 1);
				        items[items.length - 1] = value;
				        return items;
				    }
				}
				""";
		String source= """
				package test;
				import java.util.function.Function;
				class Readers {
				    int count(String value) {
				        Function<String, String[]> reference = Factory::rows;
				        Function<String, String[]> forward = v -> Factory.rows(v);
				        String[] first = reference.apply(value);
				        String[] second = forward.apply(value);
				        String[] third = reference.apply(null);
				        return first.length + second.length + third.length;
				    }
				}
				""";
		var factory= createUnit("Factory.java", factorySource); //$NON-NLS-1$
		var readers= createUnit("Readers.java", source); //$NON-NLS-1$
		var scope= new ICompilationUnit[] { factory, readers };
		var monitor= new NullProgressMonitor();
		var clean= cleanup();
		assertTrue(clean.expandCleanUpScope(context.getJavaProject(), List.of(factory), monitor).contains(readers));
		clean.checkPreConditions(context.getJavaProject(), scope, monitor);
		var diagnostic= clean.getLastPlanningDiagnosticsJson(context.getJavaProject());
		var change= new org.eclipse.ltk.core.refactoring.CompositeChange("Migrate function targets"); //$NON-NLS-1$
		for (var unit : scope) {
			var fix= clean.createFix(new CleanUpContext(unit, parse(unit)));
			assertNotNull(fix, diagnostic);
			change.add(fix.createChange(monitor));
		}
		change.initializeValidationData(monitor);
		Change undo= change.perform(monitor);
		assertNotNull(undo);
		assertTrue(readers.getSource().contains("Function<String, List<String>> reference = Factory::rows;"), readers.getSource()); //$NON-NLS-1$
		assertTrue(readers.getSource().contains("Function<String, List<String>> forward = v -> Factory.rows(v);")); //$NON-NLS-1$
		assertTrue(readers.getSource().contains("first.size() + second.size() + third.size()")); //$NON-NLS-1$
		for (var unit : scope) {
			assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		}
		clean.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(factorySource, factory.getSource());
		assertEquals(source, readers.getSource());

		String overload= factorySource.replace("static String[] rows(String value)", //$NON-NLS-1$
				"static Object rows(Object value) { return null; } static String[] rows(String value)"); //$NON-NLS-1$
		factory.getBuffer().setContents(overload);
		factory.save(monitor, true);
		var ambiguous= cleanup();
		ambiguous.checkPreConditions(context.getJavaProject(), scope, monitor);
		assertNull(ambiguous.createFix(new CleanUpContext(factory, parse(factory))));
		ambiguous.checkPostConditions(monitor);
		factory.getBuffer().setContents(factorySource);
		factory.save(monitor, true);

		for (String extra : List.of("System.identityHashCode(reference);", "reference = forward;", //$NON-NLS-1$ //$NON-NLS-2$
				"Function<String, String[]> stolen = forward;", "reference.andThen(x -> x.length);")) { //$NON-NLS-1$ //$NON-NLS-2$
			var exposed= source.replace("return first.length", extra + " return first.length"); //$NON-NLS-1$ //$NON-NLS-2$
			readers.getBuffer().setContents(exposed);
			readers.save(monitor, true);
			var rejected= cleanup();
			rejected.checkPreConditions(context.getJavaProject(), scope, monitor);
			assertNull(rejected.createFix(new CleanUpContext(factory, parse(factory))), extra);
			rejected.checkPostConditions(monitor);
			assertEquals(factorySource, factory.getSource());
		}
	}

	@Test
	void retainsProvenArrayLengthBridgeOnlyByExplicitChoice(@TempDir Path output) throws Exception {
		String source= """
				package test;
				import java.util.Arrays;
				final class Sizes {
				    static int count(String value) {
				        String[] items = new String[0];
				        items = Arrays.copyOf(items, items.length + 1);
				        items[items.length - 1] = value;
				        return size(items);
				    }
				    static int size(String[] items) { return items.length; }
				}
				""";
		var unit= createUnit("Sizes.java", source); //$NON-NLS-1$
		var scope= new ICompilationUnit[] { unit };
		var monitor= new NullProgressMonitor();
		var options= Map.of(CLEANUP, CleanUpOptions.TRUE, CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE,
				RETAIN_PROVEN_PARAMETER_BRIDGE, CleanUpOptions.TRUE);
		var clean= new CoordinatedContainerCleanUpCore(options);
		clean.checkPreConditions(context.getJavaProject(), scope, monitor);
		var fix= clean.createFix(new CleanUpContext(unit, parse(unit)));
		assertNotNull(fix, clean.getLastPlanningDiagnosticsJson(context.getJavaProject()));
		var undo= fix.createChange(null).perform(monitor);
		assertNotNull(undo);
		String migrated= unit.getSource();
		assertTrue(migrated.contains("size(List<String> items)"), migrated); //$NON-NLS-1$
		assertTrue(migrated.contains("@Deprecated"), migrated); //$NON-NLS-1$
		assertTrue(migrated.contains("size(String[] items)"), migrated); //$NON-NLS-1$
		assertTrue(migrated.contains("Arrays.asList(items)"), migrated); //$NON-NLS-1$
		assertTrue(migrated.contains("items == null ? null :"), migrated); //$NON-NLS-1$
		assertFalse(Arrays.stream(parse(unit).getProblems()).anyMatch(problem -> problem.isError()));
		// This client is compiled against the old JVM descriptor and never recompiled.
		Path sizes= Files.writeString(output.resolve("Sizes.java"), source); //$NON-NLS-1$
		Path client= Files.writeString(output.resolve("PreviousClient.java"), //$NON-NLS-1$
				"package test; public class PreviousClient { public static int read(String[] xs) { return Sizes.size(xs); } }"); //$NON-NLS-1$
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), sizes.toString(), client.toString())); //$NON-NLS-1$
		Files.writeString(sizes, migrated);
		assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), sizes.toString())); //$NON-NLS-1$
		try (var classes= new URLClassLoader(new URL[] { output.toUri().toURL() }, null)) {
			var read= classes.loadClass("test.PreviousClient").getMethod("read", String[].class); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(3, read.invoke(null, (Object) new String[] { null, "same", "same" })); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(0, read.invoke(null, (Object) new String[0]));
			try {
				read.invoke(null, (Object) null);
				org.junit.jupiter.api.Assertions.fail("The original null length operation must still throw"); //$NON-NLS-1$
			} catch (InvocationTargetException expected) {
				assertEquals(NullPointerException.class, expected.getCause().getClass());
			}
		}
		clean.checkPostConditions(monitor);
		undo.perform(monitor);
		assertEquals(source, unit.getSource());
		// A caller added after preview invalidates the entire candidate, including the original producer.
		var stale= new CoordinatedContainerCleanUpCore(options);
		stale.checkPreConditions(context.getJavaProject(), scope, monitor);
		var other= createUnit("FreshCaller.java", "package test; class FreshCaller { int read() { return Sizes.size(null); } }"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			stale.createFix(new CleanUpContext(unit, parse(unit)));
			org.junit.jupiter.api.Assertions.fail("An ambiguous new caller must block the selected bridge"); //$NON-NLS-1$
		} catch (CoreException expected) {
			assertTrue(expected.getMessage().contains("callers")); //$NON-NLS-1$
		}
		assertEquals(source, unit.getSource());
		stale.checkPostConditions(monitor);
		other.delete(true, monitor);

		for (String blocked : List.of(
				source.replace("return size(items);", "return size(items) + size(null);"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("static int count", "static int size(java.util.List<Object> items) { return 1; } static int count"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("static int size(String[]", "public static int size(String[]"), //$NON-NLS-1$ //$NON-NLS-2$
				source.replace("return items.length;", "items[0] = null; return items.length;"))) { //$NON-NLS-1$ //$NON-NLS-2$
			unit.getBuffer().setContents(blocked);
			unit.save(monitor, true);
			var rejected= new CoordinatedContainerCleanUpCore(options);
			rejected.checkPreConditions(context.getJavaProject(), scope, monitor);
			assertNull(rejected.createFix(new CleanUpContext(unit, parse(unit))), blocked);
			rejected.checkPostConditions(monitor);
			assertEquals(blocked, unit.getSource());
		}
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
