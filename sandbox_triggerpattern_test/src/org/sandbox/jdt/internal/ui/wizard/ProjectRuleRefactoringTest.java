/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Exercises the production rewrite path without opening the preview wizard. */
public class ProjectRuleRefactoringTest {

	@RegisterExtension
	EclipseJava22 context= new EclipseJava22();

	private static final String RULE= "<!id: trial>\n<!binding-policy: required>\n1 + 2\n=> 3\n;;\n";

	@Test
	public void previewDoesNotWriteAndApplyHasAnUndoAndIsIdempotent() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		String before= unit.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(), RULE);
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertFalse(refactoring.checkFinalConditions(null).hasError());
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(before, unit.getSource());
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("return 3;"), preview);
		change.initializeValidationData(null);
		Change undo= change.perform(new NullProgressMonitor());
		assertEquals(preview, unit.getSource());
		ProjectRuleRefactoring second= new ProjectRuleRefactoring(context.getJavaProject(), RULE);
		assertFalse(second.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) second.createChange(null)).getChildren().length);
		undo.initializeValidationData(null);
		undo.perform(new NullProgressMonitor());
		assertEquals(before, unit.getSource());
	}

	@Test
	public void staleSourceInvalidatesPreview() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(), RULE);
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		unit.getBuffer().setContents(unit.getSource().replace("1 + 2", "4 + 5"));
		assertTrue(refactoring.checkFinalConditions(null).hasFatalError());
	}

	@Test
	public void replacementWithATypeErrorIsRejected() throws Exception {
		unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				RULE.replace("=> 3", "=> doesNotExist()"));
		assertTrue(refactoring.checkInitialConditions(null).hasFatalError());
	}

	@Test
	public void sequenceRewritePreservesTheSurroundingStatements() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { void value() { int keep=0; int a=1; int b=2; int tail=4; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: sequence>\nint a=1;\nint b=2;\n=> int total=3;\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("int keep"), preview);
		assertTrue(preview.contains("int tail"), preview);
		assertTrue(preview.contains("int total"), preview);
		assertFalse(preview.contains("int a="), preview);
		assertFalse(preview.contains("int b="), preview);
		assertTrue(unit.getSource().contains("int a=1;"));
	}

	@Test
	public void aNewSourceFileInvalidatesThePreview() throws Exception {
		unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(), RULE);
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Added.java", "package trial; class Added {}", false, null);
		assertTrue(refactoring.checkFinalConditions(null).hasFatalError());
	}

	@Test
	public void unknownRequirementsCannotAuthorizeAnOptionalDraft() throws Exception {
		unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: optional>\n<!binding-policy: optional>\n1 + 2 :: !hasNoSideEffect($missing)\n=> 3\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
		assertTrue(refactoring.getExplanation().contains("UNKNOWN"));
	}

	@Test
	public void malformedTrailingReplacementIsNeverPartiallyApplied() throws Exception {
		unit("package trial; public class Sample { void value() { int a=1; int b=2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: malformed>\nint a=1;\nint b=2;\n=> int total=3; broken(\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@Test
	public void replacementCannotEscapeTheStatementParserWrapper() throws Exception {
		unit("package trial; public class Sample { void value() { int a=1; int b=2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: malformed>\nint a=1;\nint b=2;\n=> } int ignored; {\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@Test
	public void aSingleStatementCanBecomeSeveralStatements() throws Exception {
		unit("package trial; public class Sample { void value() { old(); } void old() {} void prepare() {} void modern() {} }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: expansion>\nold();\n=> prepare();\nmodern();\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("prepare();"), preview);
		assertTrue(preview.contains("modern();"), preview);
		assertFalse(preview.contains("old();"), preview);
	}

	@Test
	public void anExpressionCannotPartiallyConsumeItsReplacement() throws Exception {
		unit("package trial; public class Sample { int value() { return 1 + 2; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				RULE.replace("=> 3", "=> 3; unexpected"));
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@Test
	public void charsetFollowUpCannotLeaveAnUnchangedCallerWithAnUnreachableCatch() throws Exception {
		ICompilationUnit target= unit("package trial; public class Sample { public String decode(byte[] bytes) throws java.io.UnsupportedEncodingException { return new String(bytes, \"UTF-8\"); } }");
		ICompilationUnit caller= context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Caller.java", "package trial; class Caller { String use(Sample sample, byte[] bytes) { try { return sample.decode(bytes); } catch(java.io.UnsupportedEncodingException e) { return \"\"; } } }", false, null);
		String before= target.getSource();
		String callerBefore= caller.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: encoding>\nnew java.lang.String($bytes, \"UTF-8\")\n=> new java.lang.String($bytes, java.nio.charset.StandardCharsets.UTF_8)\n;;\n");
		assertTrue(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(before, target.getSource());
		assertEquals(callerBefore, caller.getSource());
	}

	@Test
	public void charsetAndCallerChangesAreValidatedTogether() throws Exception {
		ICompilationUnit target= unit("package trial; public class Sample { public String decode(byte[] bytes) throws java.io.UnsupportedEncodingException { return new String(bytes, \"UTF-8\"); } }");
		ICompilationUnit caller= context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Caller.java", "package trial; class Caller { String use(Sample sample, byte[] bytes) { try { return sample.decode(bytes); } catch(java.io.UnsupportedEncodingException e) { return \"\"; } } }", false, null);
		String before= target.getSource();
		String callerBefore= caller.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: encoding-and-caller>\nnew java.lang.String($bytes, \"UTF-8\")\n=> new java.lang.String($bytes, java.nio.charset.StandardCharsets.UTF_8)\n;;\n"
				+ "{ try { return sample.decode(bytes); } catch(java.io.UnsupportedEncodingException e) { return \"\"; } }\n=> { return sample.decode(bytes); }\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(2, change.getChildren().length);
		assertEquals(before, target.getSource());
		assertEquals(callerBefore, caller.getSource());
		change.initializeValidationData(null);
		Change undo= change.perform(new NullProgressMonitor());
		assertFalse(target.getSource().contains("throws"), target.getSource());
		assertFalse(caller.getSource().contains("catch"), caller.getSource());
		undo.initializeValidationData(null);
		undo.perform(new NullProgressMonitor());
		assertEquals(before, target.getSource());
		assertEquals(callerBefore, caller.getSource());
	}

	@Test
	public void anAlreadyPresentAnnotationDoesNotProduceANullChangeError() throws Exception {
		unit("package trial; public class Sample { @Deprecated void old() {} }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: annotation>\nvoid $name($params$)\n=> @java.lang.Deprecated void $name($params$)\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@ParameterizedTest
	@CsvSource({"true,true", "true,false", "false,true", "false,false"})
	public void deselectingADependentFileOrEditGroupCannotApplyAnInvalidSubset(boolean disableTarget,
			boolean disableGroup) throws Exception {
		ICompilationUnit target= unit("package trial; public class Sample { public String decode(byte[] bytes) throws java.io.UnsupportedEncodingException { return new String(bytes, \"UTF-8\"); } }");
		ICompilationUnit caller= context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Caller.java", "package trial; class Caller { String use(Sample sample, byte[] bytes) { try { return sample.decode(bytes); } catch(java.io.UnsupportedEncodingException e) { return \"\"; } } }", false, null);
		String before= target.getSource();
		String callerBefore= caller.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: encoding-and-caller>\nnew java.lang.String($bytes, \"UTF-8\")\n=> new java.lang.String($bytes, java.nio.charset.StandardCharsets.UTF_8)\n;;\n"
				+ "{ try { return sample.decode(bytes); } catch(java.io.UnsupportedEncodingException e) { return \"\"; } }\n=> { return sample.decode(bytes); }\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		ICompilationUnit disabledUnit= disableTarget ? target : caller;
		for (Change child : change.getChildren()) {
			if (disabledUnit.equals(child.getModifiedElement())) {
				if (disableGroup) {
					((CompilationUnitChange) child).getTextEditChangeGroups()[0].setEnabled(false);
				} else {
					child.setEnabled(false);
				}
			}
		}
		change.initializeValidationData(null);
		assertTrue(change.isValid(new NullProgressMonitor()).hasFatalError());
		assertThrows(CoreException.class, () -> change.perform(new NullProgressMonitor()));
		assertEquals(before, target.getSource());
		assertEquals(callerBefore, caller.getSource());
	}

	@Test
	public void aDifferentAnnotationWithTheSameSimpleNameDoesNotHideTheTarget() throws Exception {
		unit("package trial; public class Sample { @Deprecated void old() {} } @interface Deprecated {}");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: annotation>\nvoid $name($params$)\n=> @java.lang.Deprecated void $name($params$)\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("@java.lang.Deprecated"), preview);
		assertTrue(preview.contains("@Deprecated"), preview);
	}

	@Test
	public void unsupportedIncludesAreReportedRatherThanIgnored() throws Exception {
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!include: shared>\n" + RULE);
		assertTrue(refactoring.checkInitialConditions(null).hasFatalError());
	}

	@Test
	public void canceledTrialDoesNotProduceChanges() {
		NullProgressMonitor monitor= new NullProgressMonitor();
		monitor.setCanceled(true);
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(), RULE);
		assertThrows(OperationCanceledException.class, () -> refactoring.checkInitialConditions(monitor));
	}

	private ICompilationUnit unit(String source) throws Exception {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("trial", false, null);
		return pack.createCompilationUnit("Sample.java", source, true, null);
	}
}
