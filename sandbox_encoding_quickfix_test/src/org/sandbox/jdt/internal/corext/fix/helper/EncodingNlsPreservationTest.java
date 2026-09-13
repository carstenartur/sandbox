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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.text.edits.TextEditGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** Regression contracts for the imperative encoding rewrite used by PR review. */
public class EncodingNlsPreservationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava10();

	@BeforeEach
	void setUp() throws Exception {
		Hashtable<String, String> options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "120"); //$NON-NLS-1$
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4"); //$NON-NLS-1$
		options.put(DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE, "4"); //$NON-NLS-1$
		JavaCore.setOptions(options);
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	@Test
	void alreadyExplicitTryResourceProducesNoCleanupChange() throws CoreException {
		String before= source("""
				try (var reader= java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(".github/patched-jdt-ui.env"), //$NON-NLS-1$
				        StandardCharsets.UTF_8)) {
				    new java.util.Properties().load(reader);
				}
				""");
		ICompilationUnit cu= createUnit(before);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		assertEquals(before, cu.getSource());
		assertCompilesWithoutNlsProblems(cu);
	}

	@Test
	void identicalArgumentDoesNotTouchCommentsOrIndentation() throws CoreException {
		String before= source("""
				try (var reader= java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get("data"), //$NON-NLS-1$
				        StandardCharsets.UTF_8)) {
				    System.out.println("loaded"); //$NON-NLS-1$
				}
				""");
		assertRewrite(before, before, "StandardCharsets.UTF_8", 1); //$NON-NLS-1$
	}

	@Test
	void shorteningCharsetRetainsUnrelatedPathTag() throws CoreException {
		assertRewrite(source("""
				try (var reader= java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get("data"), //$NON-NLS-1$
				        java.nio.charset.StandardCharsets.UTF_8)) {
				    System.out.println("loaded"); //$NON-NLS-1$
				}
				"""), source("""
				try (var reader= java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get("data"), //$NON-NLS-1$
				        StandardCharsets.UTF_8)) {
				    System.out.println("loaded"); //$NON-NLS-1$
				}
				"""), "java.nio.charset.StandardCharsets.UTF_8", 1); //$NON-NLS-1$
	}

	@Test
	void removesOnlyTheSelectedLiteralAndItsTag() throws CoreException {
		assertRewrite(source("""
				String value= "UTF-8" + new String(bytes, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
				"""), source("""
				String value= "UTF-8" + new String(bytes, StandardCharsets.UTF_8); //$NON-NLS-1$
				"""), "\"UTF-8\"", 2); //$NON-NLS-1$
	}

	@Test
	void renumbersTheSurvivingTagAfterRemovingTheFirstLiteral() throws CoreException {
		assertRewrite(source("""
				String value= new String(bytes, "UTF-8") + "suffix"; //$NON-NLS-1$ //$NON-NLS-2$
				"""), source("""
				String value= new String(bytes, StandardCharsets.UTF_8) + "suffix"; //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void renumbersTagsEvenWhenTheRemovedLiteralHasNoTag() throws CoreException {
		assertRewrite(source("""
				String value= new String(bytes, "UTF-8") + "suffix"; //$NON-NLS-2$
				"""), source("""
				String value= new String(bytes, StandardCharsets.UTF_8) + "suffix"; //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void preservesContinuationIndentationAndOtherLines() throws CoreException {
		assertRewrite(source("""
				String value= new String(
				        bytes,
				        "UTF-8"); //$NON-NLS-1$
				System.out.println("done"); //$NON-NLS-1$
				"""), source("""
				String value= new String(
				        bytes,
				        StandardCharsets.UTF_8);
				System.out.println("done"); //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void keepsExplanatoryCommentAfterRemovingItsOnlyTag() throws CoreException {
		assertRewrite(source("""
				String value= new String(bytes, "UTF-8"); //$NON-NLS-1$ wire format
				"""), source("""
				String value= new String(bytes, StandardCharsets.UTF_8); // wire format
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void unwrapsEncodingCatchWithoutFlatteningTheMovedStatement() throws CoreException {
		assertRewrite(source("""
				try {
				    String value= new String(
				            bytes, "UTF-8"); //$NON-NLS-1$
				    System.out.println("done"); //$NON-NLS-1$
				} catch (java.io.UnsupportedEncodingException exception) {
				    throw new IllegalStateException(exception);
				}
				"""), source("""
				String value= new String(
				        bytes, StandardCharsets.UTF_8);
				System.out.println("done"); //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void preservesCrLfLineDelimiters() throws CoreException {
		String before= source("""
				String value= new String(
				        bytes, "UTF-8"); //$NON-NLS-1$
				""").replace("\n", "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
		String after= source("""
				String value= new String(
				        bytes, StandardCharsets.UTF_8);
				""").replace("\n", "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
		assertRewrite(before, after, "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void multipleArgumentsInOneStatementAreBothReplaced() throws CoreException {
		assertRewrite(source("""
				String value= new String(bytes, "UTF-8") + new String(bytes, "UTF-8") + "suffix"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"""), source("""
				String value= new String(bytes, StandardCharsets.UTF_8) + new String(bytes, StandardCharsets.UTF_8) + "suffix"; //$NON-NLS-1$
				"""), "\"UTF-8\"", 1, 2); //$NON-NLS-1$
	}

	@Test
	void multipleStatementsInOneTryAreUnwrappedExactlyOnce() throws CoreException {
		assertRewrite(source("""
				try {
				    String first= new String(bytes, "UTF-8"); //$NON-NLS-1$
				    String second= new String(bytes, "UTF-8"); //$NON-NLS-1$
				    System.out.println(first + second + "done"); //$NON-NLS-1$
				} catch (java.io.UnsupportedEncodingException exception) {
				    throw new IllegalStateException(exception);
				}
				"""), source("""
				String first= new String(bytes, StandardCharsets.UTF_8);
				String second= new String(bytes, StandardCharsets.UTF_8);
				System.out.println(first + second + "done"); //$NON-NLS-1$
				"""), "\"UTF-8\"", 1, 2); //$NON-NLS-1$
	}

	@Test
	void nlsLookingTextInsideAnotherLiteralIsNotAComment() throws CoreException {
		assertRewrite(source("""
				String value= "//$NON-NLS-1$" + new String(bytes, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
				"""), source("""
				String value= "//$NON-NLS-1$" + new String(bytes, StandardCharsets.UTF_8); //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void canonicalCharsetIsSkippedExceptInAggregationMode() throws CoreException {
		ICompilationUnit cu= createUnit(source("""
				java.nio.file.Files.readAllLines(java.nio.file.Paths.get("data"), StandardCharsets.UTF_8); //$NON-NLS-1$
				"""));
		String text= cu.getSource();
		String selected= "StandardCharsets.UTF_8"; //$NON-NLS-1$
		ASTNode argument= NodeFinder.perform(parse(cu), text.indexOf(selected), selected.length());
		MethodInvocation invocation= (MethodInvocation) argument.getParent();
		assertNull(AbstractExplicitEncoding.getEncodingValue(argument, invocation, ChangeBehavior.KEEP_BEHAVIOR));
		assertNull(AbstractExplicitEncoding.getEncodingValue(argument, invocation, ChangeBehavior.ENFORCE_UTF8));
		assertEquals("UTF-8", AbstractExplicitEncoding.getEncodingValue(argument, invocation, //$NON-NLS-1$
				ChangeBehavior.ENFORCE_UTF8_AGGREGATE));
	}

	@Test
	void nlsValidationReportsAnUnmarkedLiteral() throws CoreException {
		ICompilationUnit cu= createUnit(source("""
				System.out.println("missing marker");
				"""));
		assertTrue(Arrays.stream(parse(cu, true).getProblems()).anyMatch(problem -> problem.isError()
				&& problem.getID() == IProblem.NonExternalizedStringLiteral),
				"The validation parser must not suppress NLS diagnostics"); //$NON-NLS-1$
	}

	@Test
	void successfulNlsValidationDoesNotChangeCompilerOptions() throws CoreException {
		ICompilationUnit cu= createUnit(source("""
				System.out.println("marked"); //$NON-NLS-1$
				"""));
		Map<String, String> projectOptions= cu.getJavaProject().getOptions(false);
		Map<String, String> workspaceOptions= JavaCore.getOptions();
		assertCompilesWithoutNlsProblems(cu);
		assertEquals(projectOptions, cu.getJavaProject().getOptions(false));
		assertEquals(workspaceOptions, JavaCore.getOptions());
	}

	@Test
	void failingNlsValidationDoesNotChangeCompilerOptions() throws CoreException {
		ICompilationUnit cu= createUnit(source("""
				System.out.println("missing marker");
				"""));
		Map<String, String> projectOptions= cu.getJavaProject().getOptions(false);
		Map<String, String> workspaceOptions= JavaCore.getOptions();
		assertThrows(AssertionError.class, () -> assertCompilesWithoutNlsProblems(cu));
		assertEquals(projectOptions, cu.getJavaProject().getOptions(false));
		assertEquals(workspaceOptions, JavaCore.getOptions());
	}

	@Test
	void unwrappingRetainsAnotherStatementsPendingRewrite() throws CoreException {
		String before= source("""
				try {
				    String value= new String(bytes, "UTF-8"); //$NON-NLS-1$
				    System.out.println("before"); //$NON-NLS-1$
				} catch (java.io.UnsupportedEncodingException exception) {
				    throw new IllegalStateException(exception);
				}
				""");
		String expected= source("""
				String value= new String(bytes, StandardCharsets.UTF_8);
				System.out.println("after"); //$NON-NLS-1$
				""");
		assertRewrite(before, expected, "\"UTF-8\"", cuRewrite -> { //$NON-NLS-1$
			String original= "\"before\""; //$NON-NLS-1$
			ASTNode otherArgument= NodeFinder.perform(cuRewrite.getRoot(), before.indexOf(original), original.length());
			StringLiteral replacement= cuRewrite.getRoot().getAST().newStringLiteral();
			replacement.setLiteralValue("after"); //$NON-NLS-1$
			cuRewrite.getASTRewrite().replace(otherArgument, replacement, new TextEditGroup("other cleanup")); //$NON-NLS-1$
		}, 1);
	}

	@Test
	void renumbersTagsOnAnotherStatementInTheSameLine() throws CoreException {
		assertRewrite(source("""
				String first= new String(bytes, "UTF-8"); String second= "suffix"; //$NON-NLS-1$ //$NON-NLS-2$
				"""), source("""
				String first= new String(bytes, StandardCharsets.UTF_8); String second= "suffix"; //$NON-NLS-1$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void retainsAnotherRewriteInTheSameStatement() throws CoreException {
		String before= source("""
				String value= "before" + new String(bytes, "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
				""");
		String expected= source("""
				String value= "after" + new String(bytes, StandardCharsets.UTF_8); //$NON-NLS-1$
				""");
		assertRewrite(before, expected, "\"UTF-8\"", cuRewrite -> { //$NON-NLS-1$
			String original= "\"before\""; //$NON-NLS-1$
			ASTNode otherArgument= NodeFinder.perform(cuRewrite.getRoot(), before.indexOf(original), original.length());
			StringLiteral replacement= cuRewrite.getRoot().getAST().newStringLiteral();
			replacement.setLiteralValue("after"); //$NON-NLS-1$
			cuRewrite.getASTRewrite().replace(otherArgument, replacement, new TextEditGroup("other cleanup")); //$NON-NLS-1$
		}, 1);
	}

	@Test
	void unwrappingPreservesSameLineStatementsAndTags() throws CoreException {
		assertRewrite(source("""
				try {
				    String first= "prefix" + new String(bytes, "UTF-8"); String second= "suffix"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				} catch (java.io.UnsupportedEncodingException exception) {
				    throw new IllegalStateException(exception);
				}
				"""), source("""
				String first= "prefix" + new String(bytes, StandardCharsets.UTF_8); String second= "suffix"; //$NON-NLS-1$ //$NON-NLS-2$
				"""), "\"UTF-8\"", 1); //$NON-NLS-1$
	}

	@Test
	void regularCleanupShortensCharsetAndIsRepeatable() throws CoreException {
		String before= source("""
				try (var reader= java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get("data"), //$NON-NLS-1$
				        java.nio.charset.StandardCharsets.UTF_8)) {
				    System.out.println("loaded"); //$NON-NLS-1$
				}
				""");
		String expected= before.replace("java.nio.charset.StandardCharsets.UTF_8", "StandardCharsets.UTF_8"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit cu= createUnit(before);
		var cleanup= new UseExplicitEncodingCleanUpCore(Map.of(
				MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", //$NON-NLS-1$
				MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR, "true")); //$NON-NLS-1$
		var fix= cleanup.createFix(new CleanUpContext(cu, parse(cu)));
		assertTrue(fix instanceof EncodingCleanUpFix);
		for (int attempt= 0; attempt < 2; attempt++) {
			CompilationUnitChange change= fix.createChange(null);
			try {
				assertEquals(expected, change.getPreviewContent(null));
			} finally {
				change.dispose();
			}
		}
		assertEquals(before, cu.getSource());
		cu.getBuffer().setContents(expected);
		assertCompilesWithoutNlsProblems(cu);
		assertNull(cleanup.createFix(new CleanUpContext(cu, parse(cu))));
	}

	@Test
	void multipleEncodingChangesSupportPreviewApplyAndUndo() throws CoreException {
		String before= source("""
				try {
				    String first= new String(bytes, "UTF-8"); //$NON-NLS-1$
				    String second= new String(bytes, "UTF-8"); //$NON-NLS-1$
				} catch (java.io.UnsupportedEncodingException exception) {
				    throw new IllegalStateException(exception);
				}
				""");
		String expected= source("""
				String first= new String(bytes, StandardCharsets.UTF_8);
				String second= new String(bytes, StandardCharsets.UTF_8);
				""");
		ICompilationUnit cu= createUnit(before);
		CompilationUnit root= parse(cu);
		CompilationUnitRewriteOperation operation= new CompilationUnitRewriteOperation() {
			@Override
			public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
				String selected= "\"UTF-8\""; //$NON-NLS-1$
				for (int offset= before.indexOf(selected); offset >= 0; offset= before.indexOf(selected, offset + 1)) {
					ASTNode argument= NodeFinder.perform(root, offset, selected.length());
					AbstractExplicitEncoding.replaceArgumentAndRemoveNLS(cuRewrite.getASTRewrite(), argument,
							root.getAST().newName("StandardCharsets.UTF_8"), //$NON-NLS-1$
							new TextEditGroup("encoding"), cuRewrite); //$NON-NLS-1$
				}
			}
		};
		var fix= new EncodingCleanUpFix("encoding", root, new CompilationUnitRewriteOperation[] { operation }); //$NON-NLS-1$
		for (int attempt= 0; attempt < 2; attempt++) {
			CompilationUnitChange change= fix.createChange(null);
			try {
				assertEquals(expected, change.getPreviewContent(null));
			} finally {
				change.dispose();
			}
		}
		CompilationUnitChange change= fix.createChange(null);
		Change undo= null;
		try {
			change.initializeValidationData(null);
			undo= change.perform(null);
			assertEquals(expected, cu.getSource());
			assertCompilesWithoutNlsProblems(cu);
			assertNotNull(undo);
			Change redo= undo.perform(null);
			if (redo != null) {
				redo.dispose();
			}
			assertEquals(before, cu.getSource());
		} finally {
			if (undo != null) {
				undo.dispose();
			}
			change.dispose();
		}
	}

	@Test
	void unwrappingPreservesTextBlockValue() throws CoreException {
		var project= context.getSourceFolder().getJavaProject();
		Map<String, String> options= project.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		project.setOptions(options);
		String literal= "\"\"\"\n        alpha\n          beta\n        \"\"\""; //$NON-NLS-1$
		String before= source("try {\n    String message= " + literal.replace("\n", "\n    ") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ "; //$NON-NLS-1$\n    String value= new String(bytes, \"UTF-8\"); //$NON-NLS-1$\n" //$NON-NLS-1$
				+ "} catch (java.io.UnsupportedEncodingException exception) {\n    throw new IllegalStateException(exception);\n}\n"); //$NON-NLS-1$
		String expected= source("String message= " + literal //$NON-NLS-1$
				+ "; //$NON-NLS-1$\nString value= new String(bytes, StandardCharsets.UTF_8);\n"); //$NON-NLS-1$
		assertEquals("alpha\n  beta\n", textBlockValue(before)); //$NON-NLS-1$
		assertRewrite(before, expected, "\"UTF-8\"", 1); //$NON-NLS-1$
		assertEquals(textBlockValue(before), textBlockValue(expected));
	}

	private static String textBlockValue(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		String[] value= { null };
		((CompilationUnit) parser.createAST(null)).accept(new ASTVisitor() {
			@Override
			public boolean visit(TextBlock node) {
				value[0]= node.getLiteralValue();
				return false;
			}
		});
		assertNotNull(value[0]);
		return value[0];
	}

	private static String source(String body) {
		return """
				package test1;

				import java.nio.charset.StandardCharsets;

				public class E1 {
				    void run(byte[] bytes) throws java.io.IOException {
				""" + body.indent(8) + "    }\n}\n"; //$NON-NLS-1$
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		return context.getSourceFolder().createPackageFragment("test1", false, null) //$NON-NLS-1$
				.createCompilationUnit("E1.java", source, false, null); //$NON-NLS-1$
	}

	private void assertRewrite(String before, String expected, String selected, int... occurrences) throws CoreException {
		assertRewrite(before, expected, selected, rewrite -> { }, occurrences);
	}

	private void assertRewrite(String before, String expected, String selected,
			Consumer<CompilationUnitRewrite> additionalEdits, int... occurrences) throws CoreException {
		ICompilationUnit cu= createUnit(before);
		CompilationUnit root= parse(cu);
		CompilationUnitRewrite cuRewrite= new CompilationUnitRewrite(cu, root);
		for (int occurrence : occurrences) {
			int offset= -1;
			for (int i= 0; i < occurrence; i++) {
				offset= before.indexOf(selected, offset + 1);
				assertTrue(offset >= 0, "Selected argument must exist"); //$NON-NLS-1$
			}
			ASTNode argument= NodeFinder.perform(root, offset, selected.length());
			assertNotNull(argument);
			assertEquals(offset, argument.getStartPosition());
			assertEquals(selected.length(), argument.getLength());
			AbstractExplicitEncoding.replaceArgumentAndRemoveNLS(cuRewrite.getASTRewrite(), argument,
					root.getAST().newName("StandardCharsets.UTF_8"), //$NON-NLS-1$
					new TextEditGroup("encoding"), cuRewrite); //$NON-NLS-1$
		}
		additionalEdits.accept(cuRewrite);
		CompilationUnitChange change= cuRewrite.createChange(true, null);
		String actual;
		try {
			if (change != null) {
				EncodingSourceRewrite.complete(root, change);
			}
			actual= change == null ? cu.getSource() : change.getPreviewContent(null);
		} finally {
			if (change != null) {
				change.dispose();
			}
		}
		assertEquals(expected, actual);
		cu.getBuffer().setContents(actual);
		assertCompilesWithoutNlsProblems(cu);
	}

	private static void assertCompilesWithoutNlsProblems(ICompilationUnit cu) {
		String errors= Arrays.stream(parse(cu, true).getProblems()).filter(IProblem::isError)
				.map(IProblem::getMessage).collect(Collectors.joining("\n")); //$NON-NLS-1$
		assertEquals("", errors); //$NON-NLS-1$
	}

	private static CompilationUnit parse(ICompilationUnit cu) {
		return parse(cu, false);
	}

	private static CompilationUnit parse(ICompilationUnit cu, boolean reportNlsErrors) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		// RefactoringASTParser suppresses optional errors, including NLS diagnostics.
		Map<String, String> options= new HashMap<>(cu.getJavaProject().getOptions(true));
		if (reportNlsErrors) {
			// The extension reuses its project. Strict validation must not change its preferences.
			options.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.ERROR);
		}
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
