/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

public class StringLastIndexOfCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@BeforeEach
	void enableStringSimplification() throws CoreException {
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
	}

	@Test
	void rewritesBothTaggedCallsInTheArchivePathPattern() throws CoreException {
		assertRewrite("""
				package test1;
				class E1 {
				    int searchRoots(String entryName) {
				        int index = entryName.lastIndexOf("/"); //$NON-NLS-1$
				        while (index > 0) {
				            entryName = entryName.substring(0, index);
				            index = entryName.lastIndexOf("/"); //$NON-NLS-1$
				        }
				        return index;
				    }
				}
				""", """
				package test1;
				class E1 {
				    int searchRoots(String entryName) {
				        int index = entryName.lastIndexOf('/');
				        while (index > 0) {
				            entryName = entryName.substring(0, index);
				            index = entryName.lastIndexOf('/');
				        }
				        return index;
				    }
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void renumbersSurvivingTagsAndKeepsCommentText() throws CoreException {
		assertRewrite("""
				package test1;
				class E1 {
				    String search(String text) {
				        String path = "/" + text.lastIndexOf("/") + text.lastIndexOf(".") + "tail"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ keep
				        String index = text.lastIndexOf("/") + path + "suffix"; //$NON-NLS-2$
				        return index + text.lastIndexOf("/" //$NON-NLS-1$ path separator
				                );
				    }
				}
				""", """
				package test1;
				class E1 {
				    String search(String text) {
				        String path = "/" + text.lastIndexOf('/') + text.lastIndexOf('.') + "tail"; //$NON-NLS-1$ //$NON-NLS-2$ keep
				        String index = text.lastIndexOf('/') + path + "suffix"; //$NON-NLS-1$
				        return index + text.lastIndexOf('/' // path separator
				                );
				    }
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void preservesTheDecodedCharacterAndAdjacentComments() throws CoreException {
		assertRewrite("""
				package test1;
				class E1 {
				    int search(String text) {
				        return text.lastIndexOf(/* delimiter */ "'") + text.lastIndexOf("\\\\")
				                + text.lastIndexOf("\\n") + text.lastIndexOf("é") + text.lastIndexOf("\\ud83d"); // C:\\temp
				    }
				}
				""", """
				package test1;
				class E1 {
				    int search(String text) {
				        return text.lastIndexOf(/* delimiter */ '\\'') + text.lastIndexOf('\\\\')
				                + text.lastIndexOf('\\n') + text.lastIndexOf('é') + text.lastIndexOf('\\ud83d'); // C:\\temp
				    }
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void leavesOtherOverloadsReceiversAndNonliteralArgumentsAlone() throws CoreException {
		assertNoChange("""
				package test1;
				class E1 {
				    int lastIndexOf(String s) { return 0; }
				    int search(String text, StringBuilder builder, String needle) {
				        final String separator = "/"; //$NON-NLS-1$
				        return text.lastIndexOf("/", 0) + text.lastIndexOf('/') + text.lastIndexOf(needle) //$NON-NLS-1$
				                + text.lastIndexOf(separator) + text.lastIndexOf("ab") + text.lastIndexOf("") //$NON-NLS-1$ //$NON-NLS-2$
				                + text.lastIndexOf("😀") + builder.lastIndexOf("/") + lastIndexOf("/"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				    }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void leavesLinesWithAmbiguousNlsTagsAlone() throws CoreException {
		assertNoChange("""
				package test1;
				class E1 {
				    int search(String text) {
				        int a = text.lastIndexOf("/"); //$NON-NLS-1$ //$NON-NLS-1$
				        int b = text.lastIndexOf("."); // Unicode \\u0020 before //$NON-NLS-1$
				        int c = (switch (text.length()) { default -> "prefix"; }).lastIndexOf("."); //$NON-NLS-1$
				        return a + b + c;
				    }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void doesNotCreateAUnicodeEscapeByRemovingATag() throws CoreException {
		ICompilationUnit unit= createUnit("""
				package test1;
				class E1 {
				    int search(String text) {
				        return text.lastIndexOf("/"); // \\ //$NON-NLS-1$u000a bad token
				    }
				}
				"""); //$NON-NLS-1$
		assertCompilesWithoutNlsProblems(unit);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	void keepsEscapedNonAsciiCharactersInAnAsciiFile() throws CoreException {
		ICompilationUnit unit= createUnit("""
				package test1;
				class E1 {
				    int search(String text) { return text.lastIndexOf("\\u20ac"); } //$NON-NLS-1$
				}
				"""); //$NON-NLS-1$
		((IFile) unit.getResource()).setCharset(StandardCharsets.US_ASCII.name(), null);
		assertRewrite(unit, """
				package test1;
				class E1 {
				    int search(String text) { return text.lastIndexOf('\\u20ac'); }
				}
				"""); //$NON-NLS-1$
	}

	@Test
	void rewritesTheLiteralAfterStringSimplificationChangesItsParent() throws CoreException {
		assertRewrite("""
				package test1;
				class E1 {
				    String search(String text) {
				        int separate = text.lastIndexOf("."); //$NON-NLS-1$
				        return new StringBuilder().append(text.lastIndexOf("/") + separate).toString(); //$NON-NLS-1$
				    }
				}
				""", """
				package test1;
				class E1 {
				    String search(String text) {
				        int separate = text.lastIndexOf('.');
				        return String.valueOf(text.lastIndexOf('/') + separate);
				    }
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void requiresTheStringSimplificationOption() throws CoreException {
		ICompilationUnit unit= createUnit("""
				package test1;
				class E1 {
				    int search(String text) { return text.lastIndexOf("/"); } //$NON-NLS-1$
				}
				"""); //$NON-NLS-1$
		context.disable(MYCleanUpConstants.TRIGGERPATTERN_STRING_SIMPLIFICATION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void assertRewrite(String before, String expected) throws CoreException {
		assertRewrite(createUnit(before), expected);
	}

	private void assertRewrite(ICompilationUnit unit, String expected) throws CoreException {
		context.assertRefactoringResultAsExpectedWithFullCompileCheck(new ICompilationUnit[] { unit }, new String[] { expected }, null);
		assertCompilesWithoutNlsProblems(unit);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private static void assertCompilesWithoutNlsProblems(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var options= new HashMap<>(unit.getJavaProject().getOptions(true));
		options.put(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.ERROR);
		parser.setCompilerOptions(options);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		String errors= Arrays.stream(root.getProblems()).filter(IProblem::isError)
				.map(IProblem::getMessage).collect(Collectors.joining("\n")); //$NON-NLS-1$
		assertEquals("", errors); //$NON-NLS-1$
	}

	private void assertNoChange(String source) throws CoreException {
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { createUnit(source) });
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		return pack.createCompilationUnit("E1.java", source, false, null); //$NON-NLS-1$
	}
}
