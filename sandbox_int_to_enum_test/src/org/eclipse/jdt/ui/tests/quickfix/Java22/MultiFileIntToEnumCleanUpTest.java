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

/** Integration test for a package-scoped state API and a caller in another file. */
public class MultiFileIntToEnumCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void completeSelectionMigratesOwnerAndCallerButNotUnrelatedSource() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
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
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null);
		ICompilationUnit unrelated= pack.createCompilationUnit("Unrelated.java", //$NON-NLS-1$
				"""
				package test;

				public class Unrelated {
					String value() {
						return "unchanged";
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		context.enable(IntToEnumCleanUpOptions.PROJECT_WIDE);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { processor, client, unrelated },
				new ICompilationUnit[] { processor, client, unrelated },
				new String[] {
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
							void run(OrderProcessor processor) {
								processor.process(test.OrderProcessor.Status.PENDING);
							}
						}
						""",
						"""
						package test;

						public class Unrelated {
							String value() {
								return "unchanged";
							}
						}
						""" });
	}

	@Test
	public void doesNotMigrateWhenGeneratedEnumNameConflictsWithNestedEnum() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderProcessor {
					enum Status {
						EXISTING
					}

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
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);
		context.enable(IntToEnumCleanUpOptions.PROJECT_WIDE);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { processor, client });
	}
}
