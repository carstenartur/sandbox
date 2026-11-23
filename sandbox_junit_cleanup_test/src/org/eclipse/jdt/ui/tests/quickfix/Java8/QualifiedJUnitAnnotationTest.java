/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

class QualifiedJUnitAnnotationTest {
	@RegisterExtension final EclipseJava17 context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	void setup() throws CoreException {
		pack= JUnitMigrationFixtureClasspath.createJUnit4And5Root(context)
				.createPackageFragment("sample", true, null);
	}

	@ParameterizedTest
	@CsvSource({
			"@org.junit.Test,org.junit.jupiter.api.Test",
			"@org.junit.Before,org.junit.jupiter.api.BeforeEach",
			"@org.junit.After,org.junit.jupiter.api.AfterEach",
			"@org.junit.BeforeClass,org.junit.jupiter.api.BeforeAll",
			"@org.junit.AfterClass,org.junit.jupiter.api.AfterAll",
			"@org.junit.Ignore,org.junit.jupiter.api.Disabled",
			"@org.junit.Ignore(\"reason\"),org.junit.jupiter.api.Disabled",
			"@org.junit.Ignore(value=\"reason\"),org.junit.jupiter.api.Disabled" })
	void qualifiedSourceMatchesItsDeclaredAnnotationType(String annotation, String destination) throws CoreException {
		verifyMigration(annotation, destination, "");
	}

	@Test
	void qualifiedTestUsesJupiterEvenWhenAMemberHasTheSameSimpleName() throws CoreException {
		verifyMigration("@org.junit.Test", "org.junit.jupiter.api.Test", "public @interface Test {}");
	}

	private void verifyMigration(String annotation, String destination, String members) throws CoreException {
		String original= source(annotation, members);
		var unit= pack.createCompilationUnit("Subject.java", original, false, null);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var options= unit.getOptions(true);
		var classpath= unit.getJavaProject().getRawClasspath();
		var fix= fix(unit);
		assertNotNull(fix, original);
		var monitor= new NullProgressMonitor();
		var change= fix.createChange(monitor);
		try {
			String preview= change.getPreviewContent(monitor);
			assertEquals(original, unit.getSource());
			var undo= change.perform(monitor);
			assertNotNull(undo);
			try {
				assertEquals(preview, unit.getSource());
				var type= (TypeDeclaration) parse(unit).types().getFirst();
				var binding= type.getMethods()[0].resolveBinding().getAnnotations()[0];
				assertEquals(destination, binding.getAnnotationType().getQualifiedName());
				if (annotation.contains("reason")) assertEquals("reason", binding.getAllMemberValuePairs()[0].getValue());
				diagnostics.assertNoNewDiagnostics();
				assertNull(fix(unit), "The migrated destination must be stable");
			} finally {
				var redo= undo.perform(monitor);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
		}
		assertEquals(original, unit.getSource());
		assertEquals(options, unit.getOptions(true));
		assertArrayEquals(classpath, unit.getJavaProject().getRawClasspath());
		diagnostics.assertRestored();
	}

	@Test
	void customTimeoutHandlerKeepsItsOriginalSourceSpellingWhenTargetsAreShadowed() throws CoreException {
		String original= source("@org.junit.Test(timeout=250)", "public @interface Timeout {}");
		var unit= pack.createCompilationUnit("Subject.java", original, false, null);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		assertNull(fix(unit));
		assertEquals(original, unit.getSource());
		diagnostics.assertRestored();
	}

	@ParameterizedTest
	@ValueSource(strings= {"@Test", "@Subject.Test", "@missing.Test", "@missing.Before", "@missing.Ignore(\"reason\")"})
	void skipsForeignAndUnresolvedAnnotations(String annotation) throws CoreException {
		String original= source(annotation, "public @interface Test {}");
		var unit= pack.createCompilationUnit("Subject.java", original, false, null);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		assertNull(fix(unit));
		assertEquals(original, unit.getSource());
		diagnostics.assertRestored();
	}

	private static String source(String annotation, String members) {
		return "package sample;\npublic class Subject {\n" + members + "\n"
				+ annotation + " public " + (annotation.endsWith("Class") ? "static " : "") + "void execute() {}\n}\n";
	}

	private ICleanUpFix fix(ICompilationUnit unit) throws CoreException {
		var cleanup= new JUnitCleanUpCore(Map.of(MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, CleanUpOptions.TRUE));
		var status= cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] {unit}, null);
		assertFalse(status.hasFatalError(), status.toString());
		return cleanup.createFix(new CleanUpContext(unit, parse(unit)));
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
