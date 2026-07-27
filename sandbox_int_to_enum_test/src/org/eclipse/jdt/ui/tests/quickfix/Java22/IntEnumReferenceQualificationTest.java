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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;
import org.sandbox.jdt.ui.tests.quickfix.rules.MultiFileCleanUpLifecycleAssertions;

/** Qualification QA for generated nested enum references. */
public class IntEnumReferenceQualificationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void supportsGenericOwnerInDefaultPackage() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("", false, null); //$NON-NLS-1$
		ICompilationUnit owner= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
				"""
				public class OrderProcessor<T> {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""", false, null);
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"""
				public class OrderClient {
					void run(OrderProcessor<String> processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null);
		enableProjectWideCleanup();

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { owner, client }, new ICompilationUnit[] { owner, client }, new String[] {
						"""
						public class OrderProcessor<T> {
							enum Status {
								PENDING, APPROVED
							}

							void process(Status status) {
								if (status == Status.PENDING) {
									System.out.println("pending");
								} else if (status == Status.APPROVED) {
									System.out.println("approved");
								}
							}
						}
						""",
						"""
						public class OrderClient {
							void run(OrderProcessor<String> processor) {
								processor.process(OrderProcessor.Status.PENDING);
							}
						}
						""" });
	}

	@Test
	public void retainsQualifiedOwnerWhenSimpleNameConflicts() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit owner= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderProcessor {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""", false, null);
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderClient {
					static class OrderProcessor {
					}

					void run(test.OrderProcessor processor) {
						processor.process(test.OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null);
		enableProjectWideCleanup();

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { owner, client }, new ICompilationUnit[] { owner, client }, new String[] {
						"""
						package test;

						public class OrderProcessor {
							enum Status {
								PENDING, APPROVED
							}

							void process(Status status) {
								if (status == Status.PENDING) {
									System.out.println("pending");
								} else if (status == Status.APPROVED) {
									System.out.println("approved");
								}
							}
						}
						""",
						"""
						package test;

						public class OrderClient {
							static class OrderProcessor {
							}

							void run(test.OrderProcessor processor) {
								processor.process(test.OrderProcessor.Status.PENDING);
							}
						}
						""" });
	}

	private void enableProjectWideCleanup() throws CoreException {
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		context.enable(IntToEnumCleanUpOptions.PROJECT_WIDE);
	}
}
