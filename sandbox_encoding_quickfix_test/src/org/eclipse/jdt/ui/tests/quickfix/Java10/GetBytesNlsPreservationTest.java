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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Adding a Charset must not reformat retained literals away from their NLS tags. */
@SuppressWarnings("nls")
class GetBytesNlsPreservationTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	@ParameterizedTest
	@EnumSource(value= ChangeBehavior.class, names= { "KEEP_BEHAVIOR", "ENFORCE_UTF8" })
	void preservesLongTaggedLiteralReceiver(ChangeBehavior mode) throws Exception {
		verify(mode, """
				void create(byte[] bytes, boolean force, boolean history, Object monitor) { }
				void write() {
					create("package p1.p2; public class GeneratedType { public static void method(){} }".getBytes(), true, false, null); //$NON-NLS-1$
				}
				""");
	}

	@ParameterizedTest
	@EnumSource(value= ChangeBehavior.class, names= { "KEEP_BEHAVIOR", "ENFORCE_UTF8" })
	void preservesMultipleTagsOnTheOriginalLine(ChangeBehavior mode) throws Exception {
		verify(mode, """
				void create(String name, byte[] bytes) { }
				void write() {
					create("GeneratedType.java", "package p1.p2; public class GeneratedType { public static void method(){} }".getBytes()); //$NON-NLS-1$ //$NON-NLS-2$
				}
				""");
	}

	@ParameterizedTest
	@EnumSource(value= ChangeBehavior.class, names= { "KEEP_BEHAVIOR", "ENFORCE_UTF8" })
	void stillAddsCharsetToChainedStringReceiver(ChangeBehavior mode) throws Exception {
		verify(mode, """
				byte[] read(String value) { return value.trim().getBytes(); }
				""");
	}

	private String verify(ChangeBehavior mode, String members) throws Exception {
		var compilerOptions= context.getJavaProject().getOptions(false);
		compilerOptions.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		compilerOptions.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "80");
		context.getJavaProject().setOptions(compilerOptions);
		String original= "package test;\npublic class Example {\n" + members + "}\n";
		ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test", false, null)
				.createCompilationUnit("Example.java", original, false, null);
		CompilationUnit root= parse(unit);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		String option= mode == ChangeBehavior.KEEP_BEHAVIOR
				? MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR : MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
		var cleanup= new UseExplicitEncodingCleanUpCore(Map.of(
				MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", option, "true"));
		var fix= cleanup.createFix(new CleanUpContext(unit, root));
		assertNotNull(fix, "String encoding must remain enabled");
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				String generated= unit.getSource();
				diagnostics.assertNoNewDiagnostics();
				int[] calls= { 0 };
				parse(unit).accept(new ASTVisitor() {
					@Override public boolean visit(MethodInvocation node) {
						if ("getBytes".equals(node.getName().getIdentifier())) {
							var binding= node.resolveMethodBinding();
							assertNotNull(binding);
							assertEquals("java.lang.String", binding.getDeclaringClass().getQualifiedName());
							assertEquals(1, binding.getParameterTypes().length);
							assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[0].getQualifiedName());
							calls[0]++;
						}
						return true;
					}
				});
				assertEquals(1, calls[0]);
				assertNull(cleanup.createFix(new CleanUpContext(unit, parse(unit))), "A second pass must be unchanged");
				return generated;
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			assertEquals(original, unit.getSource());
			diagnostics.assertRestored();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
