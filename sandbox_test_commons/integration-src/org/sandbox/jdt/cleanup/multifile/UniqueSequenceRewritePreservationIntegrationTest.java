/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.analysis.ContainerContractInferrer;
import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.UniqueSequenceLocalRewritePlanner;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class UniqueSequenceRewritePreservationIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void acceptsEnumElementsAndPreservesInsertionComments() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				enum Mode { FIRST, SECOND }
				void collect(Mode mode) {
					List<Mode> values = new ArrayList<>();
					if (!values.contains(mode)) {
						// Keep the first encounter only.
						values.add(mode);
					}
				}
			}
			""");
		CompilationUnit root= parse(unit);
		var profile= new LocalUniqueSequenceAnalyzer().analyze(root).get(0);
		var recommendation= new ContainerContractInferrer().infer(profile).orElseThrow();
		var readiness= new ContainerMigrationReadiness(
				recommendation.targetContract(), ExecutionStatus.AUTOMATIC, List.of());
		var plan= new UniqueSequenceLocalRewritePlanner()
				.plan(unit.getHandleIdentifier(), recommendation, readiness)
				.plan().orElseThrow();

		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, profile.completeness());
		assertEquals(ContainerShape.SET, recommendation.targetContract().shape());

		UniqueSequenceLocalRewriteFix.create(unit, root, plan)
				.createChange(null).perform(null);

		String source= unit.getSource();
		assertTrue(source.contains("Set<Mode> values")); //$NON-NLS-1$
		assertTrue(source.contains("// Keep the first encounter only.")); //$NON-NLS-1$
		assertTrue(source.contains("values.add(mode);")); //$NON-NLS-1$
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		IPackageFragment fragment= context.getSourceFolder()
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		return fragment.createCompilationUnit("Sample.java", source, true, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
