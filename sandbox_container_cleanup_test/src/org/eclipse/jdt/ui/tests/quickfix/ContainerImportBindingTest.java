/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLOSED_SOURCE_PARAMETER_MIGRATION;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.RETAIN_PROVEN_PARAMETER_BRIDGE;

import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.container.analysis.AppendOnlyArraySeedDetector;
import org.sandbox.jdt.container.analysis.LocalArrayUsageAnalyzer;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequencePlanner;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.ClosedUniqueSequenceCleanUp;
import org.sandbox.jdt.container.cleanup.internal.ui.fix.CoordinatedContainerCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Source dependency imports must remain safe after a coordinated AST batch finishes. */
public class ContainerImportBindingTest {

	@RegisterExtension final AbstractEclipseJava context = new EclipseJava22();
	@RegisterExtension final AbstractEclipseJava dependency = new EclipseJava22();

	@ParameterizedTest
	@ValueSource(strings= { "unrelated", "values" }) //$NON-NLS-1$ //$NON-NLS-2$
	void sourceDependencyStaticImportDoesNotBreakArrayMigration(String importedMethod) throws Exception {
		ICompilationUnit library = dependency.getSourceFolder().createPackageFragment("library", false, null) //$NON-NLS-1$
				.createCompilationUnit("Library.java", """
						package library;
						public class Library { public static void IMPORTED_METHOD() { } }
						""".replace("IMPORTED_METHOD", importedMethod), false, null); //$NON-NLS-1$ //$NON-NLS-2$
		AbstractEclipseJava.addToClasspath(context.getJavaProject(), JavaCore.newProjectEntry(dependency.getJavaProject().getPath()));
		ICompilationUnit caller = context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Sample.java", """
						package test;
						import static library.Library.IMPORTED_METHOD;
						import java.util.Arrays;
						class Sample {
						    void collect(String value) {
						        IMPORTED_METHOD();
						        String[] values = new String[0];
						        values = Arrays.copyOf(values, values.length + 1);
						        values[values.length - 1] = value;
						        consume(values);
						    }
						    void consume(String[] values) {
						        System.out.println(values.length);
						        for (String value : values) System.out.println(value);
						    }
						}
						""".replace("IMPORTED_METHOD", importedMethod), false, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertCompiles(caller, library);
		String original = caller.getSource(), originalLibrary = library.getSource();
		var before = CompilerDiagnosticAssertions.capture(caller, library);
		var cleanup = new CoordinatedContainerCleanUpCore(Map.of(CLEANUP, CleanUpOptions.TRUE,
				CLOSED_SOURCE_PARAMETER_MIGRATION, CleanUpOptions.TRUE, RETAIN_PROVEN_PARAMETER_BRIDGE, CleanUpOptions.FALSE));
		var monitor = new NullProgressMonitor();
		var status = cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { caller }, monitor);
		assertFalse(status.hasFatalError(), status.toString());
		var buffers = FileBuffers.getTextFileBufferManager();
		buffers.connect(caller.getPath(), LocationKind.IFILE, monitor);
		try {
			var fix = cleanup.createFix(new CleanUpContext(caller, parse(caller)));
			assertNotNull(fix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
			var change = fix.createChange(monitor);
			var undo = CompilerDiagnosticAssertions.performAndCheck(change, monitor, caller, library);
			assertTrue(caller.getSource().contains("List<String> values"), caller.getSource()); //$NON-NLS-1$
			assertTrue(caller.getSource().contains("void consume(List<String> values)"), caller.getSource()); //$NON-NLS-1$
			assertEquals(originalLibrary, library.getSource());
			cleanup.checkPostConditions(monitor);
			undo.perform(monitor);
			assertEquals(original, caller.getSource());
			before.assertRestored();
			undo.dispose();
			change.dispose();
		} finally {
			buffers.disconnect(caller.getPath(), LocationKind.IFILE, monitor);
		}
	}

	@Test
	void importedCandidateFieldRemainsAnUnsupportedArrayUse() throws Exception {
		var library= dependency.getSourceFolder().createPackageFragment("library", false, null) //$NON-NLS-1$
				.createCompilationUnit("Library.java", """
						package library;
						public class Library { public static String[] values = new String[0]; }
						""", false, null); //$NON-NLS-1$
		AbstractEclipseJava.addToClasspath(context.getJavaProject(), JavaCore.newProjectEntry(dependency.getJavaProject().getPath()));
		var caller= context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Sample.java", """
						package test;
						import static library.Library.values;
						import java.util.Arrays;
						class Sample {
						    void append(String value) {
						        values = Arrays.copyOf(values, values.length + 1);
						        values[values.length - 1] = value;
						    }
						}
						""", false, null); //$NON-NLS-1$
		assertCompiles(caller, library);
		var before= CompilerDiagnosticAssertions.capture(caller, library);
		var root= ClosedUniqueSequencePlanner.parse(context.getJavaProject(), List.of(caller), null)
				.get(caller.getHandleIdentifier());
		var seeds= new AppendOnlyArraySeedDetector().findSeeds(root);
		assertEquals(1, seeds.size());
		var profile= new LocalArrayUsageAnalyzer().analyze(root, seeds.getFirst());
		var imported= (ImportDeclaration) root.imports().getFirst();
		assertTrue(profile.evidence().stream().anyMatch(evidence -> evidence.kind() == Kind.UNCLASSIFIED_USAGE
				&& evidence.sourceStart() >= imported.getStartPosition()
				&& evidence.sourceStart() < imported.getStartPosition() + imported.getLength()), profile::toString);
		before.assertRestored();
	}

	@ParameterizedTest
	@ValueSource(booleans= { false, true })
	void sameNamedForeignImportIsSafeButImportedProducerStillRejectsMigration(boolean importProducer) throws Exception {
		var library= dependency.getSourceFolder().createPackageFragment("library", false, null) //$NON-NLS-1$
				.createCompilationUnit("Library.java", """
						package library;
						public class Library { public static void unique() { } }
						""", false, null); //$NON-NLS-1$
		AbstractEclipseJava.addToClasspath(context.getJavaProject(), JavaCore.newProjectEntry(dependency.getJavaProject().getPath()));
		var pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		var producer= pack.createCompilationUnit("Names.java", """
				package test;
				import java.util.ArrayList;
				import java.util.List;
				final class Names {
				    static List<String> unique(String[] input) {
				        List<String> names = new ArrayList<>();
				        for (String item : input) if (!names.contains(item)) { names.add(item); }
				        return names;
				    }
				}
				""", false, null); //$NON-NLS-1$
		var caller= pack.createCompilationUnit("Report.java", """
				package test;
				import static IMPORTED_OWNER.unique;
				import java.util.List;
				class Report {
				    int count(String[] input) {
				        FOREIGN_CALL
				        List<String> names = PRODUCER_CALL(input);
				        return names.size();
				    }
				}
				""".replace("IMPORTED_OWNER", importProducer ? "test.Names" : "library.Library") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.replace("FOREIGN_CALL", importProducer ? "" : "unique();") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						.replace("PRODUCER_CALL", importProducer ? "unique" : "Names.unique"), false, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertCompiles(producer, caller, library);
		var original= List.of(producer.getSource(), caller.getSource(), library.getSource());
		var before= CompilerDiagnosticAssertions.capture(producer, caller, library);
		var cleanup= new ClosedUniqueSequenceCleanUp(Map.of(CLEANUP, CleanUpOptions.TRUE,
				ClosedUniqueSequenceCleanUp.OPTION, CleanUpOptions.TRUE));
		var monitor= new NullProgressMonitor();
		cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { producer, caller }, monitor);
		var buffers= FileBuffers.getTextFileBufferManager();
		for (var unit : List.of(producer, caller)) buffers.connect(unit.getPath(), LocationKind.IFILE, monitor);
		try {
			var combined= new CompositeChange("Container import regression"); //$NON-NLS-1$
			for (var unit : List.of(producer, caller)) {
				var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
				if (importProducer) assertNull(fix, "An imported candidate method is still an unsupported reference"); //$NON-NLS-1$
				else {
					assertNotNull(fix, cleanup.getLastPlanningDiagnosticsJson(context.getJavaProject()));
					combined.add(fix.createChange(monitor));
				}
			}
			cleanup.checkPostConditions(monitor);
			if (!importProducer) {
				var undo= CompilerDiagnosticAssertions.performAndCheck(combined, monitor, producer, caller, library);
				assertTrue(producer.getSource().contains("Set<String> unique"), producer.getSource()); //$NON-NLS-1$
				assertTrue(caller.getSource().contains("Set<String> names"), caller.getSource()); //$NON-NLS-1$
				undo.perform(monitor);
				undo.dispose();
			}
			before.assertRestored();
			assertEquals(original, List.of(producer.getSource(), caller.getSource(), library.getSource()));
			combined.dispose();
		} finally {
			for (var unit : List.of(producer, caller)) buffers.disconnect(unit.getPath(), LocationKind.IFILE, monitor);
		}
	}

	private static void assertCompiles(ICompilationUnit... units) {
		for (var unit : units) {
			for (var problem : parse(unit).getProblems()) assertFalse(problem.isError(), problem::getMessage);
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		var parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
