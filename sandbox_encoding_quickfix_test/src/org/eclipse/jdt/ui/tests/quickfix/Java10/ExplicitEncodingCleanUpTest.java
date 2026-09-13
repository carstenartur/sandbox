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
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), false, test.expected) }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), false, test.expected) }, null);
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
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), false, test.expected) }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), false, test.expected) }, null);
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
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), true, test.expected) }, null);
		} else {
			context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] { expected(test.name(), true, test.expected) }, null);
		}
	}

	// Correct only the known legacy fixture fragments; never transform the actual cleanup output.
	private static String expected(String name, boolean aggregate, String expected) {
		String before, after;
		switch (name) {
			case "OUTPUTSTREAMWRITER" -> { //$NON-NLS-1$
				String indent= aggregate ? "    " : "\t"; //$NON-NLS-1$ //$NON-NLS-2$
				String end= "// Datei nicht gefunden\n" + indent.repeat(3) + "e.printStackTrace();\n" + indent.repeat(2); //$NON-NLS-1$ //$NON-NLS-2$
				before= end + "}"; //$NON-NLS-1$
				after= end + "} catch (UnsupportedEncodingException e) {\n" + indent.repeat(3) //$NON-NLS-1$
						+ "// Hier wird die UnsupportedEncodingException abgefangen\n" + indent.repeat(3) //$NON-NLS-1$
						+ "e.printStackTrace();\n" + indent.repeat(2) + "}"; //$NON-NLS-1$ //$NON-NLS-2$
			}
			case "STRING" -> { //$NON-NLS-1$
				before= "static void bla(String filename) throws FileNotFoundException {"; //$NON-NLS-1$
				after= "static void bla(String filename) throws FileNotFoundException, UnsupportedEncodingException {"; //$NON-NLS-1$
			}
			case "PROPERTIESSTORETOXML" -> { //$NON-NLS-1$
				int method= expected.indexOf("void storeWithoutTryWithResources("); //$NON-NLS-1$
				int fin= expected.indexOf("        } finally {", method); //$NON-NLS-1$
				if (method < 0 || fin < method) throw new AssertionError("Missing Properties fixture"); //$NON-NLS-1$
				before= expected.substring(method, fin);
				after= before + "        } catch (UnsupportedEncodingException e) {\n" //$NON-NLS-1$
						+ "            System.err.println(\"Unexpected UnsupportedEncodingException\");\n"; //$NON-NLS-1$
			}
			default -> { return expected; }
		}
		int first= expected.indexOf(before);
		if (first < 0 || expected.indexOf(before, first + before.length()) >= 0)
			throw new AssertionError("Ambiguous expected-source fixture: " + name); //$NON-NLS-1$
		return expected.replace(before, after);
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
	 * <p>The replacement and try unwrapping must be emitted as one rewrite so
	 * moving the parent statement cannot discard the child charset change.
	 *
	 * @see AbstractExplicitEncoding#replaceTryBodyAndUnwrap
	 */
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
