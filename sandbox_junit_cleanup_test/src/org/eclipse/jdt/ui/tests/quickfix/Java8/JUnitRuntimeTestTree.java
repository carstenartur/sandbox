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
import java.util.Map;
import java.util.Objects;
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

import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.Node;
import org.sandbox.jdt.triggerpattern.api.ExecutionTreeSnapshot.NodeKind;

/** Captures one completed JDT JUnit runtime tree without retaining model elements. */
final class JUnitRuntimeTestTree {

	private static final String ATTR_TEST_RUNNER_KIND= "org.eclipse.jdt.junit.TEST_KIND"; //$NON-NLS-1$
	private static final long SESSION_TIMEOUT_SECONDS= 90;

	/** JDT test kinds used as the authoritative migration oracle. */
	enum TestKind {
		JUNIT3("org.eclipse.jdt.junit.loader.junit3"), //$NON-NLS-1$
		JUNIT5("org.eclipse.jdt.junit.loader.junit5"); //$NON-NLS-1$

		private final String id;

		TestKind(String id) {
			this.id= id;
		}

		String id() {
			return id;
		}
	}

	private JUnitRuntimeTestTree() {
	}

	static ExecutionTreeSnapshot capture(IJavaElement launchTarget, TestKind testKind) throws CoreException {
		Objects.requireNonNull(launchTarget);
		Objects.requireNonNull(testKind);
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
		CountDownLatch completed= new CountDownLatch(1);
		AtomicReference<ExecutionTreeSnapshot> captured= new AtomicReference<>();
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
			configuration.setAttribute(ATTR_TEST_RUNNER_KIND, testKind.id());
			launch= configuration.launch(ILaunchManager.RUN_MODE, null);
			if (!completed.await(SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				throw failure("Timed out waiting for the JDT " + testKind //$NON-NLS-1$
						+ " runtime test tree", null); //$NON-NLS-1$
			}
			if (callbackFailure.get() != null) {
				throw failure("Cannot snapshot the completed JDT " + testKind //$NON-NLS-1$
						+ " runtime test tree", callbackFailure.get()); //$NON-NLS-1$
			}
			ExecutionTreeSnapshot result= captured.get();
			if (result == null) {
				throw failure("The JDT " + testKind //$NON-NLS-1$
						+ " launch completed without a runtime test tree", null); //$NON-NLS-1$
			}
			if (result.isEmpty()) {
				throw failure("The JDT " + testKind //$NON-NLS-1$
						+ " runtime test tree contains no test elements", null); //$NON-NLS-1$
			}
			return result;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw failure("Interrupted while waiting for the JDT " + testKind //$NON-NLS-1$
					+ " runtime test tree", e); //$NON-NLS-1$
		} finally {
			JUnitCore.removeTestRunListener(listener);
			cleanUp(launch, configuration);
		}
	}

	private static ExecutionTreeSnapshot snapshot(ITestRunSession session) {
		return new ExecutionTreeSnapshot(snapshotChildren(session),
				session.getTestResult(true) == Result.OK);
	}

	private static List<Node> snapshotChildren(ITestElementContainer container) {
		List<Node> children= new ArrayList<>();
		for (ITestElement child : container.getChildren()) {
			children.add(snapshotNode(child));
		}
		return List.copyOf(children);
	}

	private static Node snapshotNode(ITestElement element) {
		String identity= identity(element);
		String result= String.valueOf(element.getTestResult(true));
		if (element instanceof ITestCaseElement testCase) {
			return new Node(NodeKind.TEST, identity, identity, result,
					Map.of("testClass", text(testCase.getTestClassName()), //$NON-NLS-1$
							"testMethod", text(testCase.getTestMethodName())), List.of()); //$NON-NLS-1$
		}
		if (element instanceof ITestSuiteElement suite) {
			return new Node(NodeKind.CONTAINER, identity, identity, result,
					Map.of("suiteType", text(suite.getSuiteTypeName())), snapshotChildren(suite)); //$NON-NLS-1$
		}
		List<Node> children= element instanceof ITestElementContainer container
				? snapshotChildren(container) : List.of();
		return new Node(NodeKind.OTHER, identity, identity, result,
				Map.of("modelType", element.getClass().getName()), children); //$NON-NLS-1$
	}

	private static String identity(ITestElement element) {
		if (element instanceof ITestCaseElement testCase) {
			return "test:" + text(testCase.getTestClassName()) + "#" //$NON-NLS-1$ //$NON-NLS-2$
					+ text(testCase.getTestMethodName());
		}
		if (element instanceof ITestSuiteElement suite) {
			return "suite:" + text(suite.getSuiteTypeName()); //$NON-NLS-1$
		}
		return element.getClass().getName();
	}

	private static String text(String value) {
		return value == null ? "<unknown>" : value; //$NON-NLS-1$
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
