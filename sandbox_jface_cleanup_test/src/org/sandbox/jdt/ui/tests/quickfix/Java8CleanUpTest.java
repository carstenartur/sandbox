/*******************************************************************************
 * Copyright (c) 2022
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer initial implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;


public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum JFaceCleanupCases{
		PositiveCase("""
			package test;
			import java.util.*;
			import org.eclipse.core.runtime.CoreException;
			import org.eclipse.core.runtime.IProgressMonitor;
			import org.eclipse.core.runtime.SubProgressMonitor;
			import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
			public class Test extends ArrayList<String> {
			    public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
					monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
					IProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
					IProgressMonitor subProgressMonitor2= new SubProgressMonitor(monitor, 2);
				}
			}
			""",

				"""
					package test;
					import java.util.*;
					import org.eclipse.core.runtime.CoreException;
					import org.eclipse.core.runtime.IProgressMonitor;
					import org.eclipse.core.runtime.SubMonitor;
					import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
					public class Test extends ArrayList<String> {
					    public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
							SubMonitor subMonitor=SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation,3);
							IProgressMonitor subProgressMonitor= subMonitor.split(1);
							IProgressMonitor subProgressMonitor2= subMonitor.split(2);
						}
					}
					"""),
		Twice("""
			package test;
			import java.util.*;
			import org.eclipse.core.runtime.CoreException;
			import org.eclipse.core.runtime.IProgressMonitor;
			import org.eclipse.core.runtime.SubProgressMonitor;
			import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
			public class Test extends ArrayList<String> {
			    public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
					monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
					IProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
					IProgressMonitor subProgressMonitor2= new SubProgressMonitor(monitor, 2);
				}
			    public void createPackageFragmentRoot2(IProgressMonitor monitor) throws CoreException, InterruptedException {
					monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
					IProgressMonitor subProgressMonitor3= new SubProgressMonitor(monitor, 1);
					IProgressMonitor subProgressMonitor4= new SubProgressMonitor(monitor, 2);
				}
			}
			""",

				"""
					package test;
					import java.util.*;
					import org.eclipse.core.runtime.CoreException;
					import org.eclipse.core.runtime.IProgressMonitor;
					import org.eclipse.core.runtime.SubMonitor;
					import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
					public class Test extends ArrayList<String> {
					    public void createPackageFragmentRoot(IProgressMonitor monitor) throws CoreException, InterruptedException {
							SubMonitor subMonitor=SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation,3);
							IProgressMonitor subProgressMonitor= subMonitor.split(1);
							IProgressMonitor subProgressMonitor2= subMonitor.split(2);
						}
					    public void createPackageFragmentRoot2(IProgressMonitor monitor) throws CoreException, InterruptedException {
							SubMonitor subMonitor=SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation,3);
							IProgressMonitor subProgressMonitor3= subMonitor.split(1);
							IProgressMonitor subProgressMonitor4= subMonitor.split(2);
						}
					}
					""");

		JFaceCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}

		String given, expected;
	}

	//	@Disabled
	@ParameterizedTest
	@EnumSource(JFaceCleanupCases.class)
	public void testJFaceCleanupParametrized(JFaceCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null);
		context.enable(MYCleanUpConstants.JFACE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NO_JFaceCleanupCases {

		NOCase(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            if (s.isEmpty()) {
					                it.remove();
					            } else {
					                System.out.println(s);
					            }
					        }
					    }
					}
					""")
		;

		NO_JFaceCleanupCases(String given) {
			this.given=given;
		}

		String given;
	}

	//	@Disabled
	@ParameterizedTest
	@EnumSource(NO_JFaceCleanupCases.class)
	public void testJFaceCleanupdonttouch(NO_JFaceCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null);
		context.enable(MYCleanUpConstants.JFACE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
