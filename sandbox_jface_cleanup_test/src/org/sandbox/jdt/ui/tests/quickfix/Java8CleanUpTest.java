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

/**
 * Tests for JFace cleanup that migrates from deprecated SubProgressMonitor to SubMonitor.
 * 
 * <p>This test suite validates the SubProgressMonitor → SubMonitor migration cleanup.
 * For detailed migration patterns and documentation, see the JFace Cleanup section
 * in the main repository README.md.</p>
 * 
 * <p>Test coverage includes:</p>
 * <ul>
 * <li>Basic transformation patterns (beginTask + SubProgressMonitor → convert + split)</li>
 * <li>Multiple SubProgressMonitor instances per method</li>
 * <li>Style flags (2-arg and 3-arg constructors)</li>
 * <li>Variable name collision handling</li>
 * <li>Idempotence verification (already converted code remains unchanged)</li>
 * <li>Mixed state scenarios (some methods converted, others not)</li>
 * <li>Nested/inner class scenarios</li>
 * <li>Lambda expressions with progress monitors</li>
 * <li>Import handling when both SubProgressMonitor and SubMonitor APIs are imported together</li>
 * </ul>
 * 
 * @see MYCleanUpConstants#JFACE_CLEANUP
 */
public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum JFaceCleanupCases{
		PositiveCase(
"""
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
""", //$NON-NLS-1$
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
"""), //$NON-NLS-1$
		Twice(
"""
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
""", //$NON-NLS-1$
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
"""), //$NON-NLS-1$
		WithFlags(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		monitor.beginTask("Task", 100);
		IProgressMonitor sub= new SubProgressMonitor(monitor, 50, 1);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub= subMonitor.split(50, 1);
	}
}
"""), //$NON-NLS-1$
		UniqueVariableName(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		String subMonitor = "test";
		monitor.beginTask("Task", 100);
		IProgressMonitor sub= new SubProgressMonitor(monitor, 50);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		String subMonitor = "test";
		SubMonitor subMonitor2=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub= subMonitor2.split(50);
	}
}
"""), //$NON-NLS-1$
	IdempotenceAlreadyConverted(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub= subMonitor.split(50);
		IProgressMonitor sub2= subMonitor.split(30);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub= subMonitor.split(50);
		IProgressMonitor sub2= subMonitor.split(30);
	}
}
"""), //$NON-NLS-1$
	MixedStateOneConvertedOneNot(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	// This method already uses SubMonitor - should not be modified
	public void alreadyConverted(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Already converted",50);
		IProgressMonitor sub= subMonitor.split(25);
	}
	// This method still uses SubProgressMonitor - should be converted
	public void needsConversion(IProgressMonitor monitor) {
		monitor.beginTask("Needs conversion", 100);
		IProgressMonitor sub= new SubProgressMonitor(monitor, 60);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	// This method already uses SubMonitor - should not be modified
	public void alreadyConverted(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Already converted",50);
		IProgressMonitor sub= subMonitor.split(25);
	}
	// This method still uses SubProgressMonitor - should be converted
	public void needsConversion(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Needs conversion",100);
		IProgressMonitor sub= subMonitor.split(60);
	}
}
"""), //$NON-NLS-1$
	NestedInnerClass(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void outerMethod(IProgressMonitor monitor) {
		monitor.beginTask("Outer task", 50);
		IProgressMonitor sub= new SubProgressMonitor(monitor, 25);
	}
	class InnerClass {
		public void innerMethod(IProgressMonitor monitor) {
			monitor.beginTask("Inner task", 100);
			IProgressMonitor sub= new SubProgressMonitor(monitor, 50);
		}
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void outerMethod(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Outer task",50);
		IProgressMonitor sub= subMonitor.split(25);
	}
	class InnerClass {
		public void innerMethod(IProgressMonitor monitor) {
			SubMonitor subMonitor=SubMonitor.convert(monitor,"Inner task",100);
			IProgressMonitor sub= subMonitor.split(50);
		}
	}
}
"""), //$NON-NLS-1$
	LambdaScenario(
"""
package test;
import java.util.function.Consumer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void withLambda(IProgressMonitor monitor) {
		Consumer<IProgressMonitor> task = m -> {
			m.beginTask("Lambda task", 100);
			IProgressMonitor sub = new SubProgressMonitor(m, 50);
		};
		task.accept(monitor);
	}
}
""", //$NON-NLS-1$
"""
package test;
import java.util.function.Consumer;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void withLambda(IProgressMonitor monitor) {
		Consumer<IProgressMonitor> task = m -> {
			SubMonitor subMonitor=SubMonitor.convert(m,"Lambda task",100);
			IProgressMonitor sub = subMonitor.split(50);
		};
		task.accept(monitor);
	}
}
"""), //$NON-NLS-1$
	// Test standalone SubProgressMonitor (without beginTask)
	StandaloneSubProgressMonitor(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		IProgressMonitor sub = new SubProgressMonitor(monitor, 50);
		// Use sub monitor
		sub.worked(10);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		IProgressMonitor sub = SubMonitor.convert(monitor, 50);
		// Use sub monitor
		sub.worked(10);
	}
}
"""), //$NON-NLS-1$
	// Test standalone SubProgressMonitor with flags (flags are dropped)
	StandaloneSubProgressMonitorWithFlags(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		IProgressMonitor sub = new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
		sub.worked(10);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		IProgressMonitor sub = SubMonitor.convert(monitor, 50);
		sub.worked(10);
	}
}
"""), //$NON-NLS-1$
	// Test flag mapping: SUPPRESS_SUBTASK_LABEL -> SUPPRESS_SUBTASK
	SuppressSubtaskLabelFlag(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		monitor.beginTask("Task", 100);
		IProgressMonitor sub = new SubProgressMonitor(monitor, 50, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub = subMonitor.split(50, SubMonitor.SUPPRESS_SUBTASK);
	}
}
"""), //$NON-NLS-1$
	// Test PREPEND_MAIN_LABEL_TO_SUBTASK flag is dropped
	PrependMainLabelToSubtaskFlag(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		monitor.beginTask("Task", 100);
		IProgressMonitor sub = new SubProgressMonitor(monitor, 50, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task",100);
		IProgressMonitor sub = subMonitor.split(50);
	}
}
"""), //$NON-NLS-1$
	BothImportsCoexist(
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
// Simulating a scenario where both imports might coexist
public class Test {
	public void doWork(IProgressMonitor monitor) {
		monitor.beginTask("Task with both imports", 100);
		// Only Eclipse's SubProgressMonitor should be converted
		IProgressMonitor sub= new SubProgressMonitor(monitor, 50);
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
// Simulating a scenario where both imports might coexist
public class Test {
	public void doWork(IProgressMonitor monitor) {
		SubMonitor subMonitor=SubMonitor.convert(monitor,"Task with both imports",100);
		// Only Eclipse's SubProgressMonitor should be converted
		IProgressMonitor sub= subMonitor.split(50);
	}
}
"""); //$NON-NLS-1$

		String given;
		String expected;

		JFaceCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	//	@Disabled
	@ParameterizedTest
	@EnumSource(JFaceCleanupCases.class)
	public void testJFaceCleanupParametrized(JFaceCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JFACE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NOJFaceCleanupCases {
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
""") //$NON-NLS-1$
		;

		NOJFaceCleanupCases(String given) {
			this.given=given;
		}

		String given;
	}

	//	@Disabled
	@ParameterizedTest
	@EnumSource(NOJFaceCleanupCases.class)
	public void testJFaceCleanupdonttouch(NOJFaceCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JFACE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
