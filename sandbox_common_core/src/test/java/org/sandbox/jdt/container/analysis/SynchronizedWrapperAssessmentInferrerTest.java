/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

class SynchronizedWrapperAssessmentInferrerTest {

	private final SynchronizedWrapperProtocolAnalyzer analyzer= new SynchronizedWrapperProtocolAnalyzer();
	private final SynchronizedWrapperAssessmentInferrer inferrer= new SynchronizedWrapperAssessmentInferrer();

	@Test
	void warnsAboutUnprotectedWrapperIteration() {
		ConcurrencyProtocol protocol= analyze("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());

				void notifyListeners() {
					for (String listener : listeners) {
						System.out.println(listener);
					}
				}
			}
			""");

		DiagnosticOnly assessment= inferrer.assess(protocol);

		assertEquals(Severity.WARNING, assessment.severity());
		assertFalse(assessment.permitsSourceRewrite());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, assessment.protocol().completeness());
	}

	@Test
	void lockedObservedIterationRemainsInformationalSeed() {
		ConcurrencyProtocol protocol= analyze("""
			import java.util.ArrayList;
			import java.util.Collections;
			import java.util.List;

			class Sample {
				private final List<String> listeners = Collections.synchronizedList(new ArrayList<>());

				void notifyListeners() {
					synchronized (listeners) {
						for (String listener : listeners) {
							System.out.println(listener);
						}
					}
				}
			}
			""");

		DiagnosticOnly assessment= inferrer.assess(protocol);

		assertEquals(Severity.INFO, assessment.severity());
		assertFalse(assessment.permitsSourceRewrite());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, assessment.protocol().completeness());
	}

	@Test
	void rejectsProtocolsThatAreNotSynchronizedWrappers() {
		ConcurrencyProtocol protocol= new ConcurrencyProtocol(
				new ContainerIdentity("Lsample;.values", "values", 1, 6), //$NON-NLS-1$ //$NON-NLS-2$
				new ConcurrencyProfile(
						ThreadExposure.UNKNOWN,
						SynchronizationKind.CONCURRENT_COLLECTION,
						IterationSemantics.WEAKLY_CONSISTENT,
						AtomicityRequirement.UNKNOWN,
						WorkloadShape.UNKNOWN),
				ReentrancyContract.UNKNOWN,
				AnalysisCompleteness.LOCAL_SEED,
				List.of());

		assertThrows(IllegalArgumentException.class, () -> inferrer.assess(protocol));
	}

	private ConcurrencyProtocol analyze(String source) {
		List<ConcurrencyProtocol> protocols= analyzer.analyze(parse(source));
		assertEquals(1, protocols.size());
		return protocols.get(0);
	}

	private static CompilationUnit parse(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		return (CompilationUnit) parser.createAST(null);
	}
}
