/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import java.nio.charset.UnsupportedCharsetException;
import java.util.Hashtable;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;
@DisplayName("ExplicitEncodingCleanUpTest Java 10")
public class ExplicitEncodingCleanUpTest {

	@BeforeEach
	protected void setUp() throws Exception,UnsupportedCharsetException {
		Hashtable<String, String> defaultOptions= TestOptions.getDefaultOptions();
		defaultOptions.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, Integer.toString(120));
		JavaCore.setOptions(defaultOptions);
		TestOptions.initializeCodeGenerationOptions();
		// Use load since restore doesn't really restore the defaults.
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava10();

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatternsKeepBehavior.class)
	public void testExplicitEncodingParametrizedKeepBehavior(ExplicitEncodingPatternsKeepBehavior test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
//		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
//		context.enable(CleanUpConstants.REMOVE_UNNECESSARY_NLS_TAGS);
		if (test.skipCompileCheck) {
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		}
	}

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatternsPreferUTF8.class)
	public void testExplicitEncodingParametrizedPreferUTF8(ExplicitEncodingPatternsPreferUTF8 test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
		if (test.skipCompileCheck) {
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		}
	}

//	@Disabled("Not Implemented")
	@DisplayName("ExplicitEncodingCleanUpTest Java 10")
	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatternsAggregateUTF8.class)
	public void testExplicitEncodingParametrizedAggregateUTF8(ExplicitEncodingPatternsAggregateUTF8 test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
		if (test.skipCompileCheck) {
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		}
	}

	@Test
	public void testExplicitEncodingdonttouch() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E2.java",
				"""
						package test1;

						import java.io.ByteArrayOutputStream;
						import java.io.InputStreamReader;
						import java.io.IOException;
						import java.nio.charset.Charset;
						import java.io.FileInputStream;
						import java.io.FileNotFoundException;
						import java.io.UnsupportedEncodingException;

						public class E2 {
						    void method() throws UnsupportedEncodingException, IOException {
						        String s="asdf"; //$NON-NLS-1$
						        byte[] bytes= s.getBytes(Charset.defaultCharset());
						        System.out.println(bytes.length);
						        ByteArrayOutputStream ba=new ByteArrayOutputStream();
						        String result=ba.toString(Charset.defaultCharset().displayName());
						        try (
						            InputStreamReader is=new InputStreamReader(new FileInputStream(""), Charset.defaultCharset()); //$NON-NLS-1$
						           ){ } catch (FileNotFoundException e) {
						            e.printStackTrace();
						            }
						    }
						}
						""",
				false, null);

		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that encoding replacement ("UTF-8" → StandardCharsets.UTF_8) AND
	 * try-catch unwrapping (removing single UnsupportedEncodingException catch)
	 * both work correctly when combined in the same statement.
	 *
	 * <p>This is a known limitation: Eclipse's ASTRewrite.createMoveTarget()
	 * (used to move statements out of the try body) does not preserve child
	 * node replacements registered via rewrite.replace(). The encoding
	 * replacement is silently lost when the parent statement is moved.
	 *
	 * <p>The workaround in replaceTryBodyAndUnwrap uses text-based placeholder
	 * replacement, but this still needs to be verified in CI.
	 *
	 * @see AbstractExplicitEncoding#replaceTryBodyAndUnwrap
	 */
	@Disabled("Known limitation: encoding replacement inside try-catch with UnsupportedEncodingException unwrap — see replaceTryBodyAndUnwrap") //$NON-NLS-1$
	@Test
	public void testStringEncodingReplacementInsideTryCatchUnwrap() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", //$NON-NLS-1$
				"""
						package test1;

						import java.io.UnsupportedEncodingException;

						public class E1 {
						    static void methodWithCatchChange(String filename) {
						        byte[] b = {(byte) 59};
						        try {
						            String s1 = new String(b, "UTF-8");
						        } catch (UnsupportedEncodingException e) {
						            e.printStackTrace();
						        }
						    }
						}
						""",
				false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
						package test1;

						import java.nio.charset.StandardCharsets;

						public class E1 {
						    static void methodWithCatchChange(String filename) {
						        byte[] b = {(byte) 59};
						        String s1 = new String(b, StandardCharsets.UTF_8);
						    }
						}
						"""
		}, null);
	}
}
