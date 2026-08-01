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
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import java.util.Hashtable;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** Import-removal contracts for combined encoding replacement and try unwrapping. */
public class EncodingImportRetentionTest {

	@BeforeEach
	protected void setUp() throws Exception {
		Hashtable<String, String> defaultOptions= TestOptions.getDefaultOptions();
		defaultOptions.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, Integer.toString(120));
		JavaCore.setOptions(defaultOptions);
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava10();

	@Test
	public void retainsImportsUsedByTheInlinedTryBody() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("E1.java", //$NON-NLS-1$
				"""
						package test1;

						import java.io.UnsupportedEncodingException;
						import java.util.List;

						public class E1 {
						    static void methodWithCatchChange(byte[] bytes) {
						        try {
						            List<String> values = List.of("value");
						            String text = new String(bytes, "UTF-8");
						            System.out.println(values.get(0) + text);
						        } catch (UnsupportedEncodingException exception) {
						            exception.printStackTrace();
						        }
						    }
						}
						""",
				false, null);

		context.enable(MYCleanUpConstants.EXPLICITENCODING_CLEANUP);
		context.enable(MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8);
		context.disable(MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8);
		context.assertRefactoringResultAsExpectedWithCompileCheck(new ICompilationUnit[] { cu }, new String[] {
				"""
						package test1;

						import java.nio.charset.StandardCharsets;
						import java.util.List;

						public class E1 {
						    static void methodWithCatchChange(byte[] bytes) {
						        List<String> values = List.of("value");
						        String text = new String(bytes, StandardCharsets.UTF_8);
						        System.out.println(values.get(0) + text);
						    }
						}
						"""
		}, null);
	}
}
