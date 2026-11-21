/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Complete units for the copy-source failures in the pinned JDT loop corpus. */
@SuppressWarnings("nls")
class IteratorWhileSourceRangeTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava8();

	static Stream<Arguments> loops() {
		return Stream.of(
				Arguments.of("block with trailing comment", """
						// before-loop
						for (String value : values) /* before-body */ {
							// inside-loop
							System.out.println(value);
						} // after-loop
						"""),
				Arguments.of("comment after the final block", """
						// before-loop
						for (String value : values) {
							// inside-loop
							System.out.println(value);
						}
						// after-loop
						"""),
				Arguments.of("labeled block with trailing comment", """
						// before-loop
						scan: for (String value : values) {
							// inside-loop
							if (value.isEmpty()) continue scan;
							System.out.println(value);
						} // after-loop
						"""),
				Arguments.of("unbraced body with trailing comment", """
						// before-loop
						for (String value : values)
							// before-body
							System.out.println(value); // after-loop
						"""),
				Arguments.of("body without attached comments", """
						for (String value : values) {
							System.out.println(value);
						}
						"""));
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("loops")
	void retainsCommentBoundariesWhenCopyingTheBody(String description, String loop) throws Exception {
		String original= "package probe;\npublic class Sample {\n"
				+ "public static void run(java.util.List<String> values) {\n" + loop + "}\n}\n";
		var unit= context.getSourceFolder().createPackageFragment("probe", false, null)
				.createCompilationUnit("Sample.java", original, false, null);
		var options= context.getJavaProject().getOptions(false);
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var parserOptions= context.getJavaProject().getOptions(true);
		parserOptions.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		parser.setCompilerOptions(parserOptions);
		var root= (CompilationUnit) parser.createAST(null);
		assertFalse(Arrays.stream(root.getProblems()).anyMatch(problem -> problem.isError()),
				Arrays.toString(root.getProblems()));
		var before= CompilerDiagnosticAssertions.capture(unit);
		var fix= LoopConversionService.analyze(root, LoopConversionService.handlers(LoopTargetFormat.WHILE_LOOP))
				.createFix("Iterator source range regression");
		assertNotNull(fix, description);
		var monitor= new NullProgressMonitor();
		var buffers= FileBuffers.getTextFileBufferManager();
		buffers.connect(unit.getPath(), LocationKind.IFILE, monitor);
		try {
			var change= fix.createChange(monitor);
			var undo= change.perform(monitor);
			try {
				String generated= unit.getSource();
				assertTrue(generated.contains("while ("), generated);
				assertFalse(generated.contains("for ("), generated);
				for (String marker : new String[] { "before-loop", "before-body", "inside-loop", "after-loop" }) {
					assertEquals(original.split(marker, -1).length, generated.split(marker, -1).length, generated);
				}
				before.assertNoNewDiagnostics();
			} finally {
				assertNotNull(undo);
				var redo= undo.perform(monitor);
				if (redo != null) redo.dispose();
				undo.dispose();
				change.dispose();
			}
		} finally {
			buffers.disconnect(unit.getPath(), LocationKind.IFILE, monitor);
		}
		assertEquals(original, unit.getSource());
		assertEquals(options, context.getJavaProject().getOptions(false));
		before.assertRestored();
	}
}
