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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import java.nio.charset.UnsupportedCharsetException;
import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

import org.eclipse.jdt.internal.ui.JavaPlugin;

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
	AbstractEclipseJava context= new EclipseJava22();

	@ParameterizedTest
	@EnumSource(ExplicitEncodingPatterns.class)
	public void testExplicitEncodingParametrized(ExplicitEncodingPatterns test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
//		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
		context.enable(CleanUpConstants.REMOVE_UNNECESSARY_NLS_TAGS);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	@Test
	public void testExplicitEncodingdonttouch() throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
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
}
