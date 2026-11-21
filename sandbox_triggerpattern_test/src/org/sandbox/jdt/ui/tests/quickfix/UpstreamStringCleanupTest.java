/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.ltk.core.refactoring.Change;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.StringSimplificationCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;

/** Complete source regressions for failures observed while cleaning SWT and JDT Core. */
@SuppressWarnings("nls")
class UpstreamStringCleanupTest {

	private IProject resource;
	private IJavaProject project;
	private boolean autoBuilding;
	private IVMInstall originalVm;
	private IVMInstall createdVm;

	@BeforeEach
	void createProject() throws Exception {
		var workspace= ResourcesPlugin.getWorkspace();
		var description= workspace.getDescription();
		autoBuilding= description.isAutoBuilding();
		description.setAutoBuilding(false);
		workspace.setDescription(description);
		originalVm= JavaRuntime.getDefaultVMInstall();
		if (originalVm == null) {
			createdVm= JavaRuntime.getVMInstallTypes()[0].createVMInstall("string-cleanup-regression");
			createdVm.setName("String cleanup regression");
			createdVm.setInstallLocation(Path.of(System.getProperty("java.home")).toFile());
			JavaRuntime.setDefaultVMInstall(createdVm, null);
		}
		resource= workspace.getRoot().getProject("UpstreamStringCleanup");
		resource.create(null);
		resource.open(null);
		var projectDescription= resource.getDescription();
		projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID });
		resource.setDescription(projectDescription, null);
		resource.getFolder("src").create(true, true, null);
		project= JavaCore.create(resource);
		project.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
				JavaCore.newSourceEntry(resource.getFullPath().append("src")),
				JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath()) },
				resource.getFullPath().append("bin"), null);
		var options= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		project.setOptions(options);
	}

	@AfterEach
	void restoreWorkspace() throws Exception {
		try {
			if (resource != null && resource.exists()) resource.delete(true, true, null);
		} finally {
			if (createdVm != null) {
				JavaRuntime.setDefaultVMInstall(originalVm, null);
				createdVm.getVMInstallType().disposeVMInstall(createdVm.getId());
			}
			var workspace= ResourcesPlugin.getWorkspace();
			var description= workspace.getDescription();
			description.setAutoBuilding(autoBuilding);
			workspace.setDescription(description);
		}
	}

	@Test
	void negatedCompoundConditionKeepsItsPrecedence() throws Exception {
		String actual= apply("boolean test(Object value, boolean flag) { return value != null && flag ? false : true; }", true);
		assertTrue(actual.contains("!(value != null && flag)"));
	}

	@Test
	void explicitAssignmentComparisonKeepsItsDiagnosticIntent() throws Exception {
		apply("boolean test(boolean state, boolean flag) { if ((state= flag) == true) return true; return state; }", false);
	}

	@Test
	void boxedBooleanComparisonRetainsUnboxing() throws Exception {
		apply("Boolean test(Boolean value) { return value == true; }", false);
	}

	@Test
	void boxedBooleanConditionalRetainsUnboxing() throws Exception {
		apply("Boolean test(Boolean value) { return value ? true : false; }", false);
	}

	@Test
	void primitiveBooleanSimplificationsRemainAvailable() throws Exception {
		String actual= apply("""
				boolean positive(boolean value) { return value == true; }
				boolean negative(boolean value) { return value == false; }
				""", true);
		assertTrue(actual.contains("return value;"));
		assertTrue(actual.contains("return !value;"));
	}

	@Test
	void charArrayConcatenationKeepsObjectConversion() throws Exception {
		apply("String text(char[] value) { return \"\" + value; }", false);
	}

	@Test
	void charArraySuffixKeepsObjectConversion() throws Exception {
		apply("String text(char[] value) { return value + \"\"; }", false);
	}

	@Test
	void nullLiteralConcatenationDoesNotSelectTheCharArrayOverload() throws Exception {
		apply("String text() { return \"\" + null; }", false);
	}

	@Test
	void charArrayFormatKeepsObjectConversion() throws Exception {
		apply("String text(char[] value) { return String.format(\"%s\", value); }", false);
	}

	@Test
	void objectArrayFormatKeepsVarargsExpansion() throws Exception {
		apply("String text(Object[] value) { return String.format(\"%s\", value); }", false);
	}

	@Test
	void stringArrayFormatKeepsVarargsExpansion() throws Exception {
		apply("String text(String[] value) { return String.format(\"%s\", value); }", false);
	}

	@Test
	void formattableKeepsItsFormatToDispatch() throws Exception {
		apply("String text(java.util.Formattable value) { return String.format(\"%s\", value); }", false);
	}

	@Test
	void objectFormatCannotAssumeAbsenceOfFormattable() throws Exception {
		apply("String text(Object value) { return String.format(\"%s\", value); }", false);
	}

	@Test
	void shadowedStringFormatRetainsTheUserMethod() throws Exception {
		apply("""
				static class String {
				    static java.lang.String format(java.lang.String format, Object value) { return "custom"; }
				}
				java.lang.String text(int value) { return String.format("%s", value); }
				""", false);
	}

	@Test
	void stringAndBoxedIntegerFormatsStillSimplify() throws Exception {
		String actual= apply("""
				String text(String value) { return String.format("%s", value); }
				String number(Integer value) { return String.format("%s", value); }
				""", true);
		assertEquals(2, actual.split("String.valueOf", -1).length - 1);
	}

	@Test
	void extendedConcatenationRetainsEveryOperand() throws Exception {
		apply("String text(int first, int second) { return \"\" + first + second; }", false);
	}

	@Test
	void stringAndPrimitiveConversionStillSimplify() throws Exception {
		String actual= apply("""
				String text(String value) { return "" + value; }
				String number(int value) { return "" + value; }
				""", true);
		assertEquals(2, actual.split("String.valueOf", -1).length - 1);
	}

	@Test
	void removedPrefixAndSuffixLiteralsRemoveTheirOwnNlsTags() throws Exception {
		String actual= apply("""
				String attributes(Object[] value) {
				    return "" + value; //$NON-NLS-1$
				}
				String version(int value) {
				    return value + ""; //$NON-NLS-1$
				}
				""", true);
		assertEquals(2, actual.split("String.valueOf", -1).length - 1);
		assertFalse(actual.contains("$NON-NLS-"));
	}

	@Test
	void remainingLiteralTagsAreRenumbered() throws Exception {
		String actual= apply("""
				String text(int value) {
				    return ("" + value) + "suffix"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				""", true);
		assertTrue(actual.contains("String.valueOf(value)"));
		assertTrue(actual.contains("\"suffix\""));
		assertTrue(actual.contains("//$NON-NLS-1$"));
		assertFalse(actual.contains("$NON-NLS-2$"));
	}

	@Test
	void removingAnUntaggedLiteralStillRenumbersTheLaterTaggedLiteral() throws Exception {
		String actual= apply("""
				String text(int value) {
				    return ("" + value) + "suffix"; //$NON-NLS-2$
				}
				""", true);
		assertTrue(actual.contains("String.valueOf(value)"));
		assertTrue(actual.contains("//$NON-NLS-1$"));
		assertFalse(actual.contains("$NON-NLS-2$"));
	}

	@Test
	void multipleReplacementsOnOneLineShareTheNlsUpdate() throws Exception {
		String actual= apply("""
				String text(int value) {
				    String first= "" + value; String second= value + ""; //$NON-NLS-1$ //$NON-NLS-2$
				    return first + second;
				}
				""", true);
		assertEquals(2, actual.split("String.valueOf", -1).length - 1);
		assertFalse(actual.contains("$NON-NLS-"));
	}

	@Test
	void equalsEmptyRemovesTheEmptyLiteralTag() throws Exception {
		String actual= apply("""
				boolean empty(String value) {
				    return value.equals(""); //$NON-NLS-1$
				}
				""", true);
		assertTrue(actual.contains("value.isEmpty()"));
		assertFalse(actual.contains("$NON-NLS-"));
	}

	@Test
	void formatRemovesTheFormatLiteralTag() throws Exception {
		String actual= apply("""
				String text(int value) {
				    return String.format("%s", value); //$NON-NLS-1$
				}
				""", true);
		assertTrue(actual.contains("String.valueOf(value)"));
		assertFalse(actual.contains("$NON-NLS-"));
	}

	@Test
	void fileLengthDoesNotBecomeAStringMethod() throws Exception {
		apply("boolean empty(java.io.File value) { return value.length() == 0; }", false);
	}

	@Test
	void objectEqualsDoesNotBecomeAStringMethod() throws Exception {
		apply("boolean empty(Object value) { return value.equals(\"\"); }", false);
	}

	@Test
	void boundStringLengthStillBecomesIsEmpty() throws Exception {
		String actual= apply("boolean empty(String value) { return value.length() == 0; }", true);
		assertTrue(actual.contains("value.isEmpty()"));
	}

	@Test
	void nullableFallbackRetainsTheOriginalNullResult() throws Exception {
		apply("String choose(String value, String fallback) { return value != null ? value : fallback; }", false);
	}

	@Test
	void fallbackEvaluationRemainsLazy() throws Exception {
		apply("""
				String fallback() { throw new IllegalStateException(); }
				String choose(String value) { return value != null ? value : fallback(); }
				""", false);
	}

	@Test
	void repeatedReceiverCallsKeepTheirEvaluationCount() throws Exception {
		apply("""
				String next() { throw new IllegalStateException(); }
				String choose() { return next() != null ? next() : "fallback"; }
				""", false);
	}

	@Test
	void volatileReceiverReadsRemainDistinct() throws Exception {
		apply("""
				volatile String value;
				String choose() { return value != null ? value : "fallback"; }
				""", false);
	}

	@Test
	void localStringWithNonNullLiteralFallbackStillSimplifies() throws Exception {
		String actual= apply("String choose(String value) { return value != null ? value : \"fallback\"; }", true);
		assertTrue(actual.contains("Objects.requireNonNullElse(value, \"fallback\")"));
	}

	private String apply(String members, boolean changesExpected) throws Exception {
		String original= "package test;\nclass Sample {\n" + members + "\n}\n";
		ICompilationUnit unit= project.getPackageFragmentRoot(resource.getFolder("src"))
				.createPackageFragment("test", false, null).createCompilationUnit("Sample.java", original, false, null);
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		for (var problem : root.getProblems()) assertFalse(problem.isError(), problem::getMessage);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var cleanup= new StringSimplificationCleanUpCore(Map.of(
				MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP, CleanUpOptions.TRUE));
		var fix= cleanup.createFix(new CleanUpContext(unit, root));
		if (!changesExpected) {
			assertNull(fix, "This source must retain its original evaluation and API contract");
			assertEquals(original, unit.getSource());
			return original;
		}
		assertNotNull(fix, "The safe String cleanup must remain available");
		Change change= fix.createChange(null);
		Change undo= null;
		try {
			undo= change.perform(null);
			diagnostics.assertNoNewDiagnostics();
			return unit.getSource();
		} finally {
			if (undo != null) {
				Change redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
			change.dispose();
			assertEquals(original, unit.getSource());
			diagnostics.assertRestored();
		}
	}
}
