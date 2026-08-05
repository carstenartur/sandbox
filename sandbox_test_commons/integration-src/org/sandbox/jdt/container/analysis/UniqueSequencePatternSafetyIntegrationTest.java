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
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

class UniqueSequencePatternSafetyIntegrationTest {

	@RegisterExtension
	final AbstractEclipseJava context= new EclipseJava22();

	@Test
	void rejectsRepeatedFieldReadsAsAStableGuardValue() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				volatile String current;
				void collect() {
					List<String> values = new ArrayList<>();
					if (!values.contains(current)) {
						values.add(current);
					}
				}
			}
			""");

		assertRejected(unit);
	}

	@Test
	void rejectsElementTypesWithUnprovenHashStability() throws Exception {
		ICompilationUnit unit= createUnit("""
			package test;
			import java.util.ArrayList;
			import java.util.List;
			class Sample {
				static final class MutableValue {
					int state;
					@Override public int hashCode() { return state; }
				}
				void collect(MutableValue value) {
					List<MutableValue> values = new ArrayList<>();
					if (!values.contains(value)) {
						values.add(value);
					}
				}
			}
			""");

		assertRejected(unit);
	}

	private void assertRejected(ICompilationUnit unit) {
		var profile= new LocalUniqueSequenceAnalyzer().analyze(parse(unit)).get(0);
		assertEquals(AnalysisCompleteness.REJECTED, profile.completeness());
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
