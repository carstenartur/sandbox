/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** Removed constructor types must not leave unused imports or remove surviving uses. */
class ReplacedConstructorImportTest {
	@RegisterExtension
	final EclipseJava10 context= new EclipseJava10();

	@BeforeEach
	void setUp() throws Exception {
		var options= TestOptions.getDefaultOptions();
		options.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		JavaCore.setOptions(options);
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	static Stream<Arguments> cases() {
		return Arrays.stream(ChangeBehavior.values()).flatMap(mode ->
				Stream.of("FileReader", "FileWriter", "PrintWriter").flatMap(type -> //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						Stream.of(false, true).map(retained -> Arguments.of(mode, type, retained))));
	}

	@ParameterizedTest
	@MethodSource("cases") //$NON-NLS-1$
	void removesOnlyTheLastConstructorTypeUse(ChangeBehavior mode, String type, boolean retained) throws Exception {
		String resultType= "FileReader".equals(type) ? "java.io.Reader" : "java.io.Writer"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String importLine= "import java.io." + type + ";"; //$NON-NLS-1$ //$NON-NLS-2$
		String original= """
				package test1;
				IMPORT_LINE
				class E1 {
				    RETAINED_FIELD
				    RESULT_TYPE open(String file) throws java.io.IOException { return new CONSTRUCTOR_TYPE(file); }
				}
				""".replace("IMPORT_LINE", importLine) //$NON-NLS-1$
				.replace("RETAINED_FIELD", retained ? type + " retained;" : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				.replace("RESULT_TYPE", resultType).replace("CONSTRUCTOR_TYPE", type); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null) //$NON-NLS-1$
				.createCompilationUnit("E1.java", original, false, null); //$NON-NLS-1$
		assertNoDiagnostics(unit);
		var options= CharsetScopeTest.options(mode);
		var cleanup= new UseExplicitEncodingCleanUpCore(options);
		try {
			assertFalse(cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null).hasError());
			var fix= cleanup.createFix(new CleanUpContext(unit, parse(unit)));
			assertNotNull(fix);
			var change= fix.createChange(null);
			String preview= change.getPreviewContent(null);
			assertEquals(retained, preview.contains(importLine), preview);
			change.initializeValidationData(null);
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				assertEquals(preview, unit.getSource());
				assertNoDiagnostics(unit);
				assertNull(new UseExplicitEncodingCleanUpCore(options).createFix(new CleanUpContext(unit, parse(unit))));
			} finally {
				undo.perform(null);
			}
			assertEquals(original, unit.getSource());
		} finally {
			cleanup.checkPostConditions(null);
		}
	}

	private static void assertNoDiagnostics(ICompilationUnit unit) {
		var problems= Arrays.stream(parse(unit).getProblems()).filter(p -> p.isError() || p.isWarning()).toList();
		assertEquals(0, problems.size(), problems.toString());
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		var parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
