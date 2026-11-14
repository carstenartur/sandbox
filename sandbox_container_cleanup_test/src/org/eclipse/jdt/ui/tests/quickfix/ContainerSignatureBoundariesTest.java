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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLOSED_SOURCE_PARAMETER_MIGRATION;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class ContainerSignatureBoundariesTest {
	@RegisterExtension
	final EclipseJava22 java= new EclipseJava22();
	private final NullProgressMonitor monitor= new NullProgressMonitor();

	private static final String PARAMETER= """
			package p;
			import java.util.Arrays;
			final class Sizes {
			    static int count(String value) {
			        String[] items = new String[0];
			        items = Arrays.copyOf(items, items.length + 1);
			        items[items.length - 1] = value;
			        return elements(items);
			    }
			    static int elements(String[] items) { return items.length; }
			}
			""";
	private static final String PRODUCER= """
			package p;
			import java.util.Arrays;
			final class Rows {
			    static String[] collectRows(String v) {
			        String[] rows = new String[0];
			        rows = Arrays.copyOf(rows, rows.length + 1);
			        rows[rows.length - 1] = v;
			        return rows;
			    }
			}
			""";
	private static final String CALLER= """
			package p;
			class Viewer { int count(String v) { String[] rows = Rows.collectRows(v); return rows.length; } }
			""";
	private ICompilationUnit create(String file, String source) throws CoreException {
		return java.getSourceFolder().createPackageFragment("p", false, null).createCompilationUnit(file, source, true, monitor); //$NON-NLS-1$
	}
	private CoordinatedContainerCleanUpCore plan(ICompilationUnit... units) throws CoreException {
		var cleanup= new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE, CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE));
		cleanup.checkPreConditions(java.getJavaProject(), units, monitor);
		return cleanup;
	}
	private CleanUpContext context(ICompilationUnit unit) {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return new CleanUpContext(unit, (CompilationUnit) parser.createAST(monitor));
	}

	@Test
	void newlyAddedDirectCallerInvalidatesAllParameterEdits() throws Exception {
		var sizes= create("Sizes.java", PARAMETER); //$NON-NLS-1$
		var cleanup= plan(sizes);
		assertNotNull(cleanup.createFix(context(sizes)));
		create("Late.java", "package p; class Late { int count() { return Sizes.elements(new String[2]); } }"); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(CoreException.class, () -> cleanup.createFix(context(sizes)));
		assertEquals(PARAMETER, sizes.getSource());
	}

	@Test
	void preexistingOverloadWithTheErasedTargetSignatureRejectsDirectParameterMigration() throws Exception {
		String source= PARAMETER.replace("static int elements(String[]", //$NON-NLS-1$
				"static int elements(java.util.List<Object> items) { return 2; } static int elements(String[]"); //$NON-NLS-1$
		var sizes= create("Sizes.java", source); //$NON-NLS-1$
		var cleanup= plan(sizes);
		assertNull(cleanup.createFix(context(sizes)));
		assertTrue(cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()).contains("SIGNATURE_OVERLOAD_CONFLICT")); //$NON-NLS-1$
		assertEquals(source, sizes.getSource());
	}

	@Test
	void newlyAddedReflectiveLookupOutsideClosedReturnCallersInvalidatesTheCandidate() throws Exception {
		var rows= create("Rows.java", PRODUCER); //$NON-NLS-1$
		var viewer= create("Viewer.java", CALLER); //$NON-NLS-1$
		var preview= plan(rows, viewer);
		assertNotNull(preview.createFix(context(rows)));
		var inspection= create("Inspect.java", """
				package p;
				class Inspect {
				    Object read(String method) throws Exception {
				        return (String[]) Rows.class.getDeclaredMethod(method, String.class).invoke(null, "x");
				    }
				}
				"""); //$NON-NLS-1$
		assertThrows(CoreException.class, () -> preview.createFix(context(viewer)));
		var cleanup= plan(rows, viewer);
		assertTrue(cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()).contains("REFLECTIVE_SIGNATURE_BOUNDARY")); //$NON-NLS-1$
		assertNull(cleanup.createFix(context(rows)));
		assertNull(cleanup.createFix(context(viewer)));
		assertEquals(PRODUCER, rows.getSource());
		assertEquals(CALLER, viewer.getSource());

		inspection.getBuffer().setContents("""
				package p;
				import java.lang.invoke.MethodHandles;
				import java.lang.invoke.MethodType;
				class Inspect {
				    Object read(String method, Class<?> type) throws Exception {
				        return MethodHandles.lookup().findStatic(type, method, MethodType.methodType(String[].class, String.class));
				    }
				}
				""");
		inspection.save(monitor, true);
		cleanup= plan(rows, viewer);
		assertTrue(cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()).contains("REFLECTIVE_SIGNATURE_BOUNDARY")); //$NON-NLS-1$
		assertNull(cleanup.createFix(context(rows)));

		for (String lookup : new String[] { "Rows.class.getMethods()", "Rows.class.getMethod(name, String.class)" }) { //$NON-NLS-1$ //$NON-NLS-2$
			inspection.getBuffer().setContents("package p; class Inspect { Object read(String name) throws Exception { return " + lookup + "; } }"); //$NON-NLS-1$ //$NON-NLS-2$
			inspection.save(monitor, true);
			cleanup= plan(rows, viewer);
			assertTrue(cleanup.getLastPlanningDiagnosticsJson(java.getJavaProject()).contains("REFLECTIVE_SIGNATURE_BOUNDARY"), lookup); //$NON-NLS-1$
			assertNull(cleanup.createFix(context(viewer)), lookup);
		}

		inspection.getBuffer().setContents("""
				package p;
				class Inspect {
				    Object read() throws Exception {
				        String typeName= Rows.class.getName();
				        Rows.class.getResource("/resource");
				        return String.class.getDeclaredMethod("length");
				    }
				}
				""");
		inspection.save(monitor, true);
		cleanup= plan(rows, viewer);
		var edits= new CompositeChange("Change producer and consumer"); //$NON-NLS-1$
		for (var unit : new ICompilationUnit[] {rows, viewer}) {
			edits.add(cleanup.createFix(context(unit)).createChange(monitor));
		}
		var undo= performAndCheck(edits, monitor, rows, viewer, inspection);
		assertTrue(rows.getSource().contains("List<String> collectRows")); //$NON-NLS-1$
		assertTrue(viewer.getSource().contains("rows.size()")); //$NON-NLS-1$
		undo.perform(monitor);
		assertEquals(PRODUCER, rows.getSource());
		assertEquals(CALLER, viewer.getSource());
	}
}
