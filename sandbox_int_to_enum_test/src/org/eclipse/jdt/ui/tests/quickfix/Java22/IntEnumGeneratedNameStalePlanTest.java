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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMultiFilePlanner;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Apply-time QA for generated nested enum names stored in a migration plan. */
public class IntEnumGeneratedNameStalePlanTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void rejectsNameThatBecameUnavailableAfterPlanning() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", initialProcessor(), false, null); //$NON-NLS-1$
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderClient {
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null); //$NON-NLS-1$
		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= IntEnumMultiFilePlanner.create(
				context.getJavaProject(), new ICompilationUnit[] { processor, client }, true, null);
		assertEquals(1, result.plan().candidates().size());

		processor.getBuffer().setContents(processorWithConflictingField());
		processor.save(null, true);
		CompilationUnit currentRoot= parse(processor);
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<ASTNode> processedNodes= new LinkedHashSet<>();

		CoreException exception= assertThrows(CoreException.class,
				() -> result.plan().addOperationsFor(processor, currentRoot, operations, processedNodes));

		assertTrue(exception.getMessage().contains("generated name Status is not available")); //$NON-NLS-1$
		assertTrue(exception.getMessage().contains("field")); //$NON-NLS-1$
		assertTrue(operations.isEmpty());
		assertTrue(processedNodes.isEmpty());
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String initialProcessor() {
		return """
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
				"""; //$NON-NLS-1$
	}

	private static String processorWithConflictingField() {
		return """
				package test;

				public class OrderProcessor {
					Object Status;

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
				"""; //$NON-NLS-1$
	}
}