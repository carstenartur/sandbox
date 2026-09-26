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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.UnionType;
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

/** Removing a catch alternative must not leave or over-remove its import. */
class MultiCatchEncodingImportTest {
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
				Stream.of(false, true).flatMap(retained ->
						Stream.of(false, true).map(union -> Arguments.of(mode, retained, union))));
	}

	@ParameterizedTest
	@MethodSource("cases") //$NON-NLS-1$
	void removesOnlyTheLastExceptionTypeUse(ChangeBehavior mode, boolean retained, boolean union) throws Exception {
		String original= """
				package test1;
				import java.io.FileInputStream;
				import java.io.FileNotFoundException;
				import java.io.InputStreamReader;
				import java.io.UnsupportedEncodingException;
				class E1 {
				    RETAINED_FIELD
				    InputStreamReader open(String file) {
				        try {
				            return new InputStreamReader(new FileInputStream(file), "UTF-8");
				        } catch (FileNotFoundException | UnsupportedEncodingException EXTRA_ALTERNATIVE e) {
				            return null;
				        }
				    }
				}
				""".replace("RETAINED_FIELD", retained ? "UnsupportedEncodingException retained;" : "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				.replace("EXTRA_ALTERNATIVE", union ? "| IllegalArgumentException" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
			assertEquals(retained, preview.contains("import java.io.UnsupportedEncodingException;"), preview); //$NON-NLS-1$
			assertTrue(preview.contains("import java.io.FileNotFoundException;"), preview); //$NON-NLS-1$
			change.initializeValidationData(null);
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				assertEquals(preview, unit.getSource());
				assertNoDiagnostics(unit);
				int[] counts= new int[2];
				parse(unit).accept(new ASTVisitor() {
					@Override
					public boolean visit(ClassInstanceCreation node) {
						var binding= node.resolveConstructorBinding();
						assertNotNull(binding);
						if ("java.io.InputStreamReader".equals(binding.getDeclaringClass().getQualifiedName())) { //$NON-NLS-1$
							assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[1].getQualifiedName()); //$NON-NLS-1$
							counts[0]++;
						}
						return true;
					}

					@Override
					public boolean visit(CatchClause node) {
						assertEquals(union, node.getException().getType() instanceof UnionType);
						assertFalse(node.getException().getType().toString().contains("UnsupportedEncodingException")); //$NON-NLS-1$
						counts[1]++;
						return true;
					}
				});
				assertEquals(1, counts[0]);
				assertEquals(1, counts[1]);
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
