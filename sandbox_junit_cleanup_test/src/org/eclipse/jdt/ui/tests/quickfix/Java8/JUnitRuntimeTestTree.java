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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.jdt.core.IJavaElement;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.launcher.JUnitLaunchShortcut;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestElementContainer;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;

/** Captures one completed JDT JUnit runtime tree without retaining model elements. */
final class JUnitRuntimeTestTree {

	private static final String ATTR_TEST_RUNNER_KIND= "org.eclipse.jdt.junit.TEST_KIND"; //$NON-NLS-1$
	private static final String JUNIT5_TEST_KIND_ID= "org.eclipse.jdt.junit.loader.junit5"; //$NON-NLS-1$
	private static final long SESSION_TIMEOUT_SECONDS= 90;

	record Snapshot(List<String> entries, boolean successful) {
		Snapshot {
			entries= List.copyOf(entries);
		}
	}

	private JUnitRuntimeTestTree() {
	}

	static Snapshot capture(IJavaElement launchTarget) throws CoreException {
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
		CountDownLatch completed= new CountDownLatch(1);
		AtomicReference<Snapshot> captured= new AtomicReference<>();
		AtomicReference<Throwable> callbackFailure= new AtomicReference<>();
		TestRunListener listener= new TestRunListener() {
			@Override
			public void sessionFinished(ITestRunSession session) {
				try {
					captured.set(snapshot(session));
				} catch (Throwable failure) {
					callbackFailure.set(failure);
				} finally {
					completed.countDown();
				}
			}
		};

		ILaunchConfigurationWorkingCopy configuration= null;
		ILaunch launch= null;
		JUnitCore.addTestRunListener(listener);
		try {
			configuration= TestLaunchShortcut.createConfiguration(launchTarget);
			configuration.setAttribute(ATTR_TEST_RUNNER_KIND, JUNIT5_TEST_KIND_ID);
			launch= configuration.launch(ILaunchManager.RUN_MODE, null);
			if (!completed.await(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw failure("Timed out waiting for the JDT JUnit runtime test tree", null); //$NON-NLS-1$
			}
			if (callbackFailure.get() != null) {
				throw failure("Cannot snapshot the completed JDT JUnit runtime test tree", callbackFailure.get()); //$NON-NLS-1$
			}
			Snapshot result= captured.get();
			if (result == null) {
				throw failure("The JDT JUnit launch completed without a runtime test tree", null); //$NON-NLS-1$
			}
			if (result.entries().isEmpty()) {
				throw failure("The JDT JUnit runtime test tree contains no test elements", null); //$NON-NLS-1$
			}
			return result;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw failure("Interrupted while waiting for the JDT JUnit runtime test tree", e); //$NON-NLS-1$
		} finally {
			JUnitCore.removeTestRunListener(listener);
			cleanUp(launch, configuration);
		}
	}

	private static Snapshot snapshot(ITestRunSession session) {
		List<String> entries= new ArrayList<>();
		appendChildren(session, "", entries); //$NON-NLS-1$
		return new Snapshot(entries, session.getTestResult(true) == Result.OK);
	}

	private static void appendChildren(ITestElementContainer container, String parentPath, List<String> entries) {
		int occurrence= 0;
		for (ITestElement child : container.getChildren()) {
			String identity= identity(child);
			String path= parentPath + "/" + occurrence++ + ":" + identity; //$NON-NLS-1$ //$NON-NLS-2$
			entries.add(path + "=" + child.getTestResult(true)); //$NON-NLS-1$
			if (child instanceof ITestElementContainer childContainer) {
				appendChildren(childContainer, path, entries);
			}
		}
	}

	private static String identity(ITestElement element) {
		if (element instanceof ITestCaseElement testCase) {
			return "test:" + testCase.getTestClassName() + "#" + testCase.getTestMethodName(); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (element instanceof ITestSuiteElement suite) {
			return "suite:" + suite.getSuiteTypeName(); //$NON-NLS-1$
		}
		return element.getClass().getName();
	}

	private static void cleanUp(ILaunch launch, ILaunchConfigurationWorkingCopy configuration) {
		ILaunchManager manager= DebugPlugin.getDefault().getLaunchManager();
		if (launch != null) {
			try {
				if (!launch.isTerminated()) {
					launch.terminate();
				}
			} catch (DebugException e) {
				// The completed runtime snapshot is authoritative; cleanup is best effort.
			}
			manager.removeLaunch(launch);
		}
		if (configuration != null) {
			try {
				configuration.delete();
			} catch (CoreException e) {
				// The launch configuration is temporary and cleanup is best effort.
			}
		}
	}

	private static CoreException failure(String message, Throwable cause) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_junit_cleanup_test", message, cause)); //$NON-NLS-1$
	}

	private static final class TestLaunchShortcut extends JUnitLaunchShortcut {
		static ILaunchConfigurationWorkingCopy createConfiguration(IJavaElement element) throws CoreException {
			return new TestLaunchShortcut().createLaunchConfiguration(element, null);
		}
	}
}
