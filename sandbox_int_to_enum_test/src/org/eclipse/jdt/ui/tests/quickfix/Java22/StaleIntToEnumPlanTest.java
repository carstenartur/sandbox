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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Verifies fail-closed re-resolution of a coordinated Int-to-Enum plan. */
public class StaleIntToEnumPlanTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	void earlierWorkingCopyChangeInvalidatesTheWholeProjectPlan() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		String processorSource= """
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
				""";
		String originalClientSource= """
				package test;

				public class OrderClient {
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""";
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", processorSource, false, null); //$NON-NLS-1$
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", originalClientSource, false, null); //$NON-NLS-1$
		IntToEnumCleanUpCore cleanup= new IntToEnumCleanUpCore(Map.of(
				MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE,
				IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.TRUE));

		RefactoringStatus status= cleanup.checkPreConditions(processor.getJavaProject(),
				new ICompilationUnit[] { processor, client }, new NullProgressMonitor());
		assertFalse(status.hasError(), () -> "Initial coordinated plan was rejected: " + status); //$NON-NLS-1$

		String changedClientSource= """
				package test;

				public class OrderClient {
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
						System.out.println(OrderProcessor.STATUS_APPROVED);
					}
				}
				""";
		client.getBuffer().setContents(changedClientSource);
		client.save(new NullProgressMonitor(), true);

		CoreException exception= assertThrows(CoreException.class,
				() -> cleanup.createFix(new CleanUpContext(client, parse(client))));
		assertTrue(exception.getStatus().getMessage().contains("plan is stale"), //$NON-NLS-1$
				() -> "Unexpected stale-plan diagnostic: " + exception.getStatus().getMessage()); //$NON-NLS-1$

		assertNull(cleanup.createFix(new CleanUpContext(processor, parse(processor))),
				"A failed file re-resolution must invalidate the retained project plan"); //$NON-NLS-1$
		assertEquals(processorSource, processor.getBuffer().getContents(),
				"No partial owner change may be applied after stale-plan detection"); //$NON-NLS-1$
		assertEquals(changedClientSource, client.getBuffer().getContents(),
				"The earlier working-copy change must remain untouched"); //$NON-NLS-1$
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(unit);
		parser.setProject(unit.getJavaProject());
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		return (CompilationUnit) parser.createAST(null);
	}
}
