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
import org.junit.jupiter.params.provider.ValueSource;
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

	@Test
	public void anUnqualifiedAnnotationTargetRecognizesAnExistingFullyQualifiedAnnotation() throws Exception {
		ICompilationUnit unit= unit("package trial; public class Sample { @java.lang.Deprecated void old() {} }");
		String before= unit.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: annotation>\nvoid $name($params$)\n=> @Deprecated void $name($params$)\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
		assertEquals(before, unit.getSource());
	}

	@ParameterizedTest
	@ValueSource(strings= {
			"@java.lang.Deprecated void $name($params$) {} int ignored;",
			"@java.lang.Deprecated void $name($params$) {} void ignored() {}",
			"@java.lang.Deprecated void $name($params$) {} broken(",
			"@java.lang.Deprecated void $name($params$) {} } class Escaped {"
	})
	public void aMethodReplacementMustConsumeExactlyOneValidDeclaration(String replacement) throws Exception {
		ICompilationUnit target= unit("package trial; public class Sample { void old() {} }");
		String before= target.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: annotation>\nvoid $name($params$)\n=> " + replacement + "\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
		assertEquals(before, target.getSource());
	}

	@Test
	public void bodylessMethodPatternsStillAddAnAnnotation() throws Exception {
		unit("package trial; public class Sample { void old() {} }");
		String preview= preview("<!id: annotation>\nvoid $name($params$)\n=> @java.lang.Deprecated void $name($params$)\n;;\n");
		assertTrue(preview.contains("@Deprecated"), preview);
	}

	@Test
	public void statementReplacementPreservesLiteralContentsWhileShorteningCodeNames() throws Exception {
		unit("package trial; public class Sample { void value() { old(); } void old() {} void consume(Object... values) {} }");
		String preview= preview("<!id: literal>\nold();\n=> consume(java.util.Collections.emptyList(), \"java.util.Collections\", \"java.util.List\");\n;;\n");
		assertTrue(preview.contains("Collections.emptyList()"), preview);
		assertTrue(preview.contains("\"java.util.Collections\""), preview);
		assertTrue(preview.contains("\"java.util.List\""), preview);
		assertFalse(preview.contains("import java.util.List;"), preview);
	}

	@Test
	public void statementNamesHonorAnExistingConflictingImport() throws Exception {
		context.getSourceFolder().createPackageFragment("other", false, null)
				.createCompilationUnit("Collections.java",
						"package other; public class Collections { public static Object emptyList() { return \"other\"; } }", false, null);
		unit("package trial; import other.Collections; public class Sample { void value() { old(); } Object keep() { return Collections.emptyList(); } void old() {} void consume(Object value) {} }");
		String preview= preview("<!id: collision>\nold();\n=> consume(java.util.Collections.emptyList());\n;;\n");
		assertTrue(preview.contains("consume(java.util.Collections.emptyList());"), preview);
		assertTrue(preview.contains("import other.Collections;"), preview);
	}

	@Test
	public void statementReplacementPreservesTextBlockContents() throws Exception {
		unit("package trial; public class Sample { void value() { old(\"\"\"\njava.util.Collections\njava.util.List\n\"\"\"); } void old(String text) {} void consume(Object... values) {} }");
		String preview= preview("<!id: literal>\nold($text);\n=> consume(java.util.Collections.emptyList(), $text);\n;;\n");
		assertTrue(preview.contains("Collections.emptyList()"), preview);
		assertTrue(preview.contains("java.util.Collections\n"), preview);
		assertTrue(preview.contains("java.util.List\n"), preview);
		assertFalse(preview.contains("import java.util.List;"), preview);
	}

	@Test
	public void aStatementContainingCommentsIsDeclinedWithoutChangingSourceOrImports() throws Exception {
		ICompilationUnit target= unit("package trial; public class Sample { void value() { old(/* java.util.Collections */); } void old() {} }");
		String before= target.getSource();
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: comment>\nold();\n=> java.util.Collections.emptyList();\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
		assertEquals(before, target.getSource());
	}

	@Test
	public void statementReplacementPreservesImportsUsedByTheReplacement() throws Exception {
		unit("package trial; import java.util.List; public class Sample { void value() { List<String> names=null; } }");
		String preview= preview("<!id: imports>\nList<String> names=null;\n=> List<String> names=java.util.Collections.emptyList();\n;;\n");
		assertTrue(preview.contains("import java.util.List;"), preview);
		assertTrue(preview.contains("Collections.emptyList()"), preview);
	}

	@Test
	public void distinctQualifiedAnnotationsWithTheSameSimpleNameAreAdded() throws Exception {
		context.getSourceFolder().createPackageFragment("a", false, null)
				.createCompilationUnit("Flag.java", "package a; public @interface Flag {}", false, null);
		context.getSourceFolder().createPackageFragment("b", false, null)
				.createCompilationUnit("Flag.java", "package b; public @interface Flag {}", false, null);
		unit("package trial; public class Sample { @a.Flag void old() {} }");
		String preview= preview("<!id: annotation>\nvoid $name($params$)\n=> @a.Flag @b.Flag void $name($params$)\n;;\n");
		assertTrue(preview.contains("@a.Flag"), preview);
		assertTrue(preview.contains("@b.Flag") || preview.contains("import b.Flag;") && preview.contains("@Flag"), preview);
	}

	@Test
	public void nestedAnnotationNamesKeepTheirQualifiedIdentity() throws Exception {
		context.getSourceFolder().createPackageFragment("trial", false, null)
				.createCompilationUnit("Annotations.java", "package trial; public class Annotations { public @interface Enabled {} }", false, null);
		unit("package trial; public class Sample { void old() {} }");
		String preview= preview("<!id: annotation>\nvoid $name($params$)\n=> @trial.Annotations.Enabled void $name($params$)\n;;\n");
		assertTrue(preview.contains("import trial.Annotations.Enabled;") || preview.contains("@Annotations.Enabled")
				|| preview.contains("@trial.Annotations.Enabled"), preview);
	}

	@Test
	public void installedHintEditorContributesAnExecutableProjectTrialCommand() throws Exception {
		var registry= org.eclipse.core.runtime.Platform.getExtensionRegistry();
		String commandId= "org.sandbox.jdt.triggerpattern.commands.tryHintOnProject";
		assertTrue(java.util.Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui.commands"))
				.anyMatch(element -> commandId.equals(element.getAttribute("id"))));
		var handler= java.util.Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui.handlers"))
				.filter(element -> commandId.equals(element.getAttribute("commandId"))).findFirst().orElseThrow();
		assertEquals("org.sandbox.jdt.internal.ui.handlers.TryHintOnProjectHandler",
				handler.createExecutableExtension("class").getClass().getName());
		var menus= java.util.Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui.menus"))
				.filter(element -> "popup:#AbstractTextEditorContext".equals(element.getAttribute("locationURI")))
				.flatMap(element -> java.util.Arrays.stream(element.getChildren("menu")))
				.flatMap(element -> java.util.Arrays.stream(element.getChildren("command")))
				.filter(element -> commandId.equals(element.getAttribute("commandId"))).toList();
		assertEquals(1, menus.size());
		var expression= org.eclipse.core.expressions.ExpressionConverter.getDefault()
				.perform(menus.getFirst().getChildren("visibleWhen")[0].getChildren()[0]);
		var evaluation= new org.eclipse.core.expressions.EvaluationContext(null, new Object());
		var project= context.getJavaProject().getProject();
		evaluation.addVariable("activeEditorInput", new org.eclipse.ui.part.FileEditorInput(project.getFile("draft.sandbox-hint")));
		assertEquals(org.eclipse.core.expressions.EvaluationResult.TRUE, expression.evaluate(evaluation));
		evaluation.addVariable("activeEditorInput", new org.eclipse.ui.part.FileEditorInput(project.getFile("Sample.java")));
		assertEquals(org.eclipse.core.expressions.EvaluationResult.FALSE, expression.evaluate(evaluation));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"@java.lang.Deprecated void $name($params$) {} int ignored;",
			"@java.lang.Deprecated void $name($params$) {} int ignored = ;" })
	public void methodReplacementMustContainExactlyOneCompleteDeclaration(String replacement) throws Exception {
		unit("package trial; public class Sample { void old() {} }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: method>\nvoid $name($params$) {}\n=> " + replacement + "\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		assertEquals(0, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@Test
	public void statementReplacementDoesNotShortenNamesInsideStringLiterals() throws Exception {
		unit("package trial; public class Sample { void print() { System.out.println(\"old\"); } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: literal>\nSystem.out.println(\"old\");\n"
						+ "=> System.out.println(\"java.util.List\");\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("System.out.println(\"java.util.List\");"), preview);
		assertFalse(preview.contains("System.out.println(\"List\");"), preview);
	}

	@Test
	public void sequenceReplacementRetainsImportsUsedByInsertedStatements() throws Exception {
		unit("package trial; import java.util.List; public class Sample { void names() { List<String> names = null; } }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: list>\nList<String> names = null;\n"
						+ "=> List<String> names = java.util.Collections.emptyList();\n;;\n");
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("List<String> names = Collections.emptyList();"), preview);
		assertTrue(preview.contains("import java.util.List;"), preview);
	}

	@Test
	public void annotationDiffPreservesQualifiedAndNestedAnnotationIdentity() throws Exception {
		unit("package trial; public class Sample { void old() {} } "
				+ "class First { @interface Flag {} } class Second { @interface Flag {} } "
				+ "class Annotations { @interface Enabled {} }");
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(),
				"<!id: annotation-identities>\nvoid $name($params$)\n"
						+ "=> @trial.First.Flag @trial.Second.Flag @trial.Annotations.Enabled "
						+ "void $name($params$)\n;;\n");
		assertFalse(refactoring.checkInitialConditions(null).hasFatalError());
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length);
		String preview= ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
		assertTrue(preview.contains("First.Flag"), preview);
		assertTrue(preview.contains("Second.Flag"), preview);
		assertTrue(preview.contains("Annotations.Enabled"), preview);
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

	private String preview(String hint) throws Exception {
		ProjectRuleRefactoring refactoring= new ProjectRuleRefactoring(context.getJavaProject(), hint);
		var status= refactoring.checkInitialConditions(null);
		assertFalse(status.hasFatalError(), status::toString);
		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, change.getChildren().length, refactoring.getExplanation());
		return ((CompilationUnitChange) change.getChildren()[0]).getPreviewContent(null);
	}
}
