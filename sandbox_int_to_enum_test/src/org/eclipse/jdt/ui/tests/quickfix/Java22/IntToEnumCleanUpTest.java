/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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

import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for int to enum conversion cleanup.
 */
public class IntToEnumCleanUpTest {

	@BeforeEach
	protected void setUp() throws Exception {
		Hashtable<String, String> defaultOptions = TestOptions.getDefaultOptions();
		defaultOptions.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, Integer.toString(120));
		JavaCore.setOptions(defaultOptions);
		TestOptions.initializeCodeGenerationOptions();
	}

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Test
	public void testNoTransformationWhenDisabled() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E1 {
				    public static final int STATUS_PENDING = 0;
				    public static final int STATUS_APPROVED = 1;
				    
				    public void process(int status) {
				        if (status == STATUS_PENDING) {
				            System.out.println("Pending");
				        } else if (status == STATUS_APPROVED) {
				            System.out.println("Approved");
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		
		// Cleanup disabled - no transformation expected
		context.disable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Disabled("Transformation logic not yet implemented - test documents expected behavior")
	@Test
	public void testBasicIfElseToEnumSwitch() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E2 {
				    public static final int STATUS_PENDING = 0;
				    public static final int STATUS_APPROVED = 1;
				    public static final int STATUS_REJECTED = 2;
				    
				    public void process(int status) {
				        if (status == STATUS_PENDING) {
				            System.out.println("Pending");
				        } else if (status == STATUS_APPROVED) {
				            System.out.println("Approved");
				        } else if (status == STATUS_REJECTED) {
				            System.out.println("Rejected");
				        }
				    }
				}
				""";
		
		ICompilationUnit cu = pack.createCompilationUnit("E2.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		
		// If-else to enum transformation not yet implemented
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testSwitchIntToEnumBasic() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E3 {
				    public static final int STATUS_PENDING = 0;
				    public static final int STATUS_APPROVED = 1;
				    public static final int STATUS_REJECTED = 2;
				
				    public void process(int status) {
				        switch (status) {
				        case STATUS_PENDING:
				            System.out.println("Pending");
				            break;
				        case STATUS_APPROVED:
				            System.out.println("Approved");
				            break;
				        case STATUS_REJECTED:
				            System.out.println("Rejected");
				            break;
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E3.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		// Expected output matches Eclipse ASTRewrite formatting:
		// - Newly generated enum body/closing and method use tabs
		// - Existing code (switch body) keeps original 4-space indentation
		String expected = "package test1;\n"
				+ "\n"
				+ "public class E3 {\n"
				+ "    public enum Status {\n"
				+ "\t\tPENDING, APPROVED, REJECTED\n"
				+ "\t}\n"
				+ "\n"
				+ "\tpublic void process(Status status) {\n"
				+ "        switch (status) {\n"
				+ "        case PENDING:\n"
				+ "            System.out.println(\"Pending\");\n"
				+ "            break;\n"
				+ "        case APPROVED:\n"
				+ "            System.out.println(\"Approved\");\n"
				+ "            break;\n"
				+ "        case REJECTED:\n"
				+ "            System.out.println(\"Rejected\");\n"
				+ "            break;\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n";
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	@Test
	public void testNoTransformSwitchWithSingleConstant() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E4 {
				    public static final int STATUS_PENDING = 0;
				
				    public void process(int status) {
				        switch (status) {
				        case STATUS_PENDING:
				            System.out.println("Pending");
				            break;
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E4.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoTransformSwitchOnLocalVariable() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E5 {
				    public static final int STATUS_PENDING = 0;
				    public static final int STATUS_APPROVED = 1;
				
				    public void process() {
				        int status = STATUS_PENDING;
				        switch (status) {
				        case STATUS_PENDING:
				            System.out.println("Pending");
				            break;
				        case STATUS_APPROVED:
				            System.out.println("Approved");
				            break;
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E5.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		// Switch selector is a local variable, not a method parameter - skip
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoTransformConstantsUsedElsewhere() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E6 {
				    public static final int STATUS_PENDING = 0;
				    public static final int STATUS_APPROVED = 1;
				
				    public void process(int status) {
				        switch (status) {
				        case STATUS_PENDING:
				            System.out.println("Pending");
				            break;
				        case STATUS_APPROVED:
				            System.out.println("Approved");
				            break;
				        }
				    }
				
				    public int getDefault() {
				        return STATUS_PENDING;
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E6.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		// Constants referenced outside the switch - skip to avoid broken references
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testNoTransformWithoutCommonUnderscorePrefix() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;
				
				public class E7 {
				    public static final int A1 = 0;
				    public static final int A2 = 1;
				
				    public void process(int status) {
				        switch (status) {
				        case A1:
				            System.out.println("One");
				            break;
				        case A2:
				            System.out.println("Two");
				            break;
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E7.java", given, false, null);
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		// Constants have no underscore-delimited prefix - skip
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
