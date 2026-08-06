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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import org.sandbox.jdt.container.analysis.LocalUniqueSequenceAnalyzer;
import org.sandbox.jdt.container.analysis.UniqueSequenceContractInferrer;
import org.sandbox.jdt.container.analysis.UniqueSequenceLocalRewritePlanner;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class UniqueSequenceLocalRewriteIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void appliesTheStrictLocalOrderedSetRewrite() throws Exception {
		ICompilationUnit unit= createUnit(validSource());
		CompilationUnit root= parse(unit);

		UniqueSequenceLocalRewritePlan plan= plan(unit, root);
		UniqueSequenceLocalRewriteFix.create(unit, root, plan)
				.createChange(null).perform(null);

		String source= unit.getSource();
		assertTrue(source.contains("Set<String> values")); //$NON-NLS-1$
		assertTrue(source.contains("new LinkedHashSet<>()")); //$NON-NLS-1$
		assertTrue(source.contains("values.add(value);")); //$NON-NLS-1$
		assertTrue(source.contains("values.size()")); //$NON-NLS-1$
		assertTrue(source.contains("for (String current : values)")); //$NON-NLS-1$
		assertFalse(source.contains("values.contains(value)")); //$NON-NLS-1$
	}

	@Test
	void rejectsUnguardedOrMismatchedInsertion() throws Exception {
		ICompilationUnit unguarded= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void collect(String value) {
					List<String> values = new ArrayList<>();
					values.add(value);
				}
			}
			""");
		var unguardedProfile= new LocalUniqueSequenceAnalyzer()
				.analyze(parse(unguarded)).get(0);
		assertTrue(unguardedProfile.completeness() == AnalysisCompleteness.REJECTED);
		assertTrue(new UniqueSequenceContractInferrer().infer(unguardedProfile).isEmpty());

		ICompilationUnit mismatched= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void collect(String first, String second) {
					List<String> values = new ArrayList<>();
					if (!values.contains(first)) {
						values.add(second);
					}
				}
			}
			""");
		var mismatchedProfile= new LocalUniqueSequenceAnalyzer()
				.analyze(parse(mismatched)).get(0);
		assertTrue(mismatchedProfile.completeness() == AnalysisCompleteness.REJECTED);
	}

	@Test
	void rejectsAStaleGuardPlan() throws Exception {
		ICompilationUnit unit= createUnit(validSource());
		UniqueSequenceLocalRewritePlan plan= plan(unit, parse(unit));
		unit.getBuffer().setContents(validSource().replace(
				"if (!values.contains(value)) {", //$NON-NLS-1$
				"if (true) {")); //$NON-NLS-1$

		CoreException exception= assertThrows(
				CoreException.class,
				() -> UniqueSequenceLocalRewriteFix.create(unit, parse(unit), plan));
		assertTrue(exception.getMessage().contains("occurrence count changed")); //$NON-NLS-1$
	}

	private UniqueSequenceLocalRewritePlan plan(
			ICompilationUnit unit,
			CompilationUnit root) {
		var profile= new LocalUniqueSequenceAnalyzer().analyze(root).get(0);
		var recommendation= new UniqueSequenceContractInferrer()
				.infer(profile).orElseThrow();
		var readiness= new ContainerMigrationReadiness(
				recommendation.targetContract(),
				ExecutionStatus.AUTOMATIC,
				List.of());
		return new UniqueSequenceLocalRewritePlanner()
				.plan(unit.getHandleIdentifier(), recommendation, readiness)
				.plan().orElseThrow();
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

	private static String validSource() {
		return """
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				void collect(String value) {
					List<String> values = new ArrayList<>();
					if (!values.contains(value)) {
						values.add(value);
					}
					int count = values.size();
					for (String current : values) {
						System.out.println(current + count);
					}
				}
			}
			""";
	}
}
