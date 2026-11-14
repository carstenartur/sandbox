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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLOSED_SOURCE_PARAMETER_MIGRATION;

import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerCompatibilityPreviewTest {
	@RegisterExtension
	final EclipseJava22 java= new EclipseJava22();

	@Test
	void previewNamesChangedSignaturesCallersAndCompatibilityWhileExportKeepsSourceNamesPrivate() throws Exception {
		var pkg= java.getSourceFolder().createPackageFragment("p", false, null); //$NON-NLS-1$
		String producerSource= """
				package p;
				import java.util.Arrays;
				final class Shipping {
				    static String[] collectPackages(String value) {
				        String[] items = new String[0];
				        items = Arrays.copyOf(items, items.length + 1);
				        items[items.length - 1] = value;
				        return items;
				    }
				}
				""";
		String consumerSource= """
				package p;
				final class Warehouse {
				    int countPackages(String value) {
				        java.util.function.Function<String,String[]> collect = Shipping::collectPackages;
				        String[] result=collect.apply(value); return result.length;
				    }
				}
				""";
		var producer= pkg.createCompilationUnit("Shipping.java", producerSource, true, null); //$NON-NLS-1$
		var consumer= pkg.createCompilationUnit("Warehouse.java", consumerSource, true, null); //$NON-NLS-1$
		var cleanup= new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] {producer, consumer}, new NullProgressMonitor());
		var candidate= cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).iterator().next();
		String details= candidate.get("details").toString(); //$NON-NLS-1$
		for (String expected : List.of("CLOSED_SOURCE_AUTOMATIC", "IMPOSSIBLE_RETURN_TYPE_ONLY", "Shipping.collectPackages", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Warehouse.countPackages", "Functional", "List<String>", "String[]")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertTrue(details.contains(expected), details);
		}
		String json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(json.contains("\"migrationMode\":\"CLOSED_SOURCE_AUTOMATIC\""), json); //$NON-NLS-1$
		assertTrue(json.contains("\"bridgeFeasibility\":\"IMPOSSIBLE_RETURN_TYPE_ONLY\""), json); //$NON-NLS-1$
		assertFalse(json.contains("Shipping"), json); //$NON-NLS-1$
		assertFalse(json.contains("Warehouse"), json); //$NON-NLS-1$
	}

	@Test
	void listsEveryDeclarationInTheParameterFamilyAndItsCaller() throws Exception {
		var pkg= java.getSourceFolder().createPackageFragment("p", false, null); //$NON-NLS-1$
		String declarations= """
				package p;
				interface Contract { void consume(String[] values); }
				final class First implements Contract {
				    public void consume(String[] values) { int count = values.length; }
				}
				final class Second implements Contract {
				    public void consume(String[] values) { int count = values.length; }
				}
				""";
		String calls= """
				package p;
				import java.util.Arrays;
				final class Caller {
				    void collect(Contract receiver, String item) {
				        String[] values = new String[0];
				        values = Arrays.copyOf(values, values.length + 1);
				        values[values.length - 1] = item;
				        receiver.consume(values);
				    }
				}
				""";
		var family= pkg.createCompilationUnit("Contract.java", declarations, true, null); //$NON-NLS-1$
		var caller= pkg.createCompilationUnit("Caller.java", calls, true, null); //$NON-NLS-1$
		var cleanup= new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { caller, family }, new NullProgressMonitor());
		var candidates= cleanup.getCoordinatedCleanUpPreview(java.getJavaProject());
		String json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertFalse(candidates.isEmpty(), json);
		String details= candidates.iterator().next().get("details").toString(); //$NON-NLS-1$
		for (String expected : List.of("Override family member", "Contract.consume", "First.consume", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Second.consume", "Caller.collect", "parameter 1", "NO_BRIDGE_REQUIRED")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			assertTrue(details.contains(expected), details);
		}
		assertFalse(json.contains("First.consume"), json); //$NON-NLS-1$

		// Selecting the caller alone retains a diagnosed manual boundary and produces no preview edits.
		cleanup.checkPostConditions(new NullProgressMonitor());
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { caller }, new NullProgressMonitor());
		assertTrue(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty());
		String refusal= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		for (String expected : List.of("SOURCE_CLOSURE_NOT_SELECTED", "MANUAL_REFACTORING", "UNKNOWN_EXTERNAL_CONSUMERS")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertTrue(refusal.contains(expected), refusal);
		}
	}

	@Test
	void reportsTheExternalReturnDeclarationAndTheObservedCallerBoundarySeparately() throws Exception {
		var pkg= java.getSourceFolder().createPackageFragment("p", false, null); //$NON-NLS-1$
		String source= """
				package p;
				import java.util.Arrays;
				public final class Export {
				    public static String[] create(String input) {
				        String[] values = new String[0];
				        values = Arrays.copyOf(values, values.length + 1);
				        values[values.length - 1] = input;
				        return values;
				    }
				    int count(String input) {
				        String[] result = create(input);
				        return result.length;
				    }
				}
				""";
		var unit= pkg.createCompilationUnit("Export.java", source, true, null); //$NON-NLS-1$
		var cleanup= new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		var monitor= new NullProgressMonitor();
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		String json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(json.contains("EXTERNAL_RETURN_DECLARATION"), json); //$NON-NLS-1$
		assertTrue(json.contains("UNKNOWN_EXTERNAL_CONSUMERS"), json); //$NON-NLS-1$
		assertFalse(json.contains("p.Export"), json); //$NON-NLS-1$
		cleanup.checkPostConditions(monitor);
		String privateReturn= source.replace("public static String[] create", "private static String[] create"); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(privateReturn);
		unit.save(monitor, true);
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertFalse(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty(), cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()));
		cleanup.checkPostConditions(monitor);
		String observesElements= privateReturn.replace("return result.length;", "return result[0].length();"); //$NON-NLS-1$ //$NON-NLS-2$
		unit.getBuffer().setContents(observesElements);
		unit.save(monitor, true);
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(json.contains("UNSUPPORTED_RETURN_CALLER"), json); //$NON-NLS-1$
		assertTrue(json.contains("MANUAL_REFACTORING"), json); //$NON-NLS-1$
		assertTrue(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty());
	}

	@Test
	void reportsPublicParameterBoundaryAndStillPlansPrivateHelperInsideThePublicType() throws Exception {
		var pkg= java.getSourceFolder().createPackageFragment("p", false, null); //$NON-NLS-1$
		String source= """
				package p;
				import java.util.Arrays;
				public final class Published {
				    public int measure(String[] array) { return array.length; }
				    int example(String item) {
				        String[] values = new String[0];
				        values = Arrays.copyOf(values, values.length + 1);
				        values[values.length - 1] = item;
				        return measure(values);
				    }
				}
				""";
		var unit= pkg.createCompilationUnit("Published.java", source, true, null); //$NON-NLS-1$
		var cleanup= new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		var monitor= new NullProgressMonitor();
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		String json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(json.contains("EXTERNALLY_VISIBLE_PARAMETER"), json); //$NON-NLS-1$
		assertTrue(json.contains("UNKNOWN_EXTERNAL_CONSUMERS"), json); //$NON-NLS-1$
		assertTrue(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty());
		cleanup.checkPostConditions(monitor);
		unit.getBuffer().setContents(source.replace("public int measure", "private int measure")); //$NON-NLS-1$ //$NON-NLS-2$
		unit.save(monitor, true);
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertFalse(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty(), cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()));
		cleanup.checkPostConditions(monitor);

		// A public nested type inside a package-visible outer class is still inaccessible to other packages.
		String nested= source.replace("public final class Published {", "final class Outer { public static class Nested {") + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		unit.getBuffer().setContents(nested);
		unit.save(monitor, true);
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit }, monitor);
		assertFalse(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty(), cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()));
		cleanup.checkPostConditions(monitor);

		// An exported subclass exposes even the inherited method without adding a declaration to the override group.
		var child= pkg.createCompilationUnit("Exported.java", "package p; public class Exported extends Outer.Nested {}", true, null); //$NON-NLS-1$ //$NON-NLS-2$
		cleanup.checkPreConditions(java.getJavaProject(), new ICompilationUnit[] { unit, child }, monitor);
		json= cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject());
		assertTrue(json.contains("EXTERNALLY_VISIBLE_PARAMETER"), json); //$NON-NLS-1$
		assertTrue(cleanup.getCoordinatedCleanUpPreview(java.getJavaProject()).isEmpty());
	}
}
