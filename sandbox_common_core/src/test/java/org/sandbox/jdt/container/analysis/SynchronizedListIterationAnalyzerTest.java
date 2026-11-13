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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;

class SynchronizedListIterationAnalyzerTest {

	private final SynchronizedListIterationAnalyzer analyzer= new SynchronizedListIterationAnalyzer();

	@Test
	void recognizesAnOuterMatchingMonitorDespiteAnInnerDifferentLock() {
		String source= """
				import java.util.ArrayList;
				import java.util.List;
				import static java.util.Collections.synchronizedList;
				class Sample {
					private final List<String> entries = synchronizedList(new ArrayList<>());
					void inspect(Object other) {
						synchronized ((entries)) {
							synchronized (other) {
								for (String entry : this.entries) { }
							}
						}
					}
				}
				""";

		List<DiagnosticOnly> observations= analyze(source);
		assertEquals(1, observations.size());
		DiagnosticOnly observation= observations.getFirst();
		ConcurrencyProtocol protocol= observation.protocol();
		assertEquals(Severity.INFO, observation.severity());
		assertFalse(observation.permitsSourceRewrite());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, protocol.completeness());
		assertEquals(SynchronizationKind.SYNCHRONIZED_WRAPPER, protocol.summary().synchronization());
		assertEquals(IterationSemantics.EXTERNALLY_LOCKED, protocol.summary().iteration());
		assertEquals(ThreadExposure.UNKNOWN, protocol.summary().exposure());
		assertEquals(AtomicityRequirement.UNKNOWN, protocol.summary().atomicity());
		assertEquals(WorkloadShape.UNKNOWN, protocol.summary().workload());
		assertEquals(ReentrancyContract.UNKNOWN, protocol.reentrancy());
		assertEquals(Set.of(protocol.identity().bindingKey()), protocol.lockIdentities());
		var evidence= protocol.evidence().getFirst();
		assertEquals(EvidenceKind.LOCKED_ITERATION, evidence.kind());
		assertEquals("this.entries", source.substring(evidence.sourceStart(), //$NON-NLS-1$
				evidence.sourceStart() + evidence.sourceLength()));
	}

	@Test
	void keepsShadowedAliasOtherReceiverAndDeferredMonitorsUnverified() {
		String source= """
				import java.util.ArrayList;
				import java.util.Collections;
				import java.util.List;
				class Sample {
					private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
					void inspect(Sample other, Object entries) {
						synchronized (entries) {
							for (String entry : this.entries) { }
						}
						synchronized (other.entries) {
							for (String entry : this.entries) { }
						}
						Object alias = this.entries;
						synchronized (alias) {
							for (String entry : this.entries) { }
						}
						synchronized (this.entries) {
							Runnable later = () -> { for (String entry : this.entries) { } };
						}
					}
					synchronized void inspectOnThis() {
						for (String entry : entries) { }
					}
				}
				""";

		List<DiagnosticOnly> observations= analyze(source);
		assertEquals(5, observations.size());
		for (DiagnosticOnly observation : observations) {
			ConcurrencyProtocol protocol= observation.protocol();
			assertEquals(Severity.INFO, observation.severity());
			assertEquals(IterationSemantics.UNKNOWN, protocol.summary().iteration());
			assertTrue(protocol.lockIdentities().isEmpty());
			assertEquals(EvidenceKind.UNRESOLVED_BOUNDARY, protocol.evidence().getFirst().kind());
		}
	}

	@Test
	void skipsLookalikeFactoriesAndFieldsWithoutTheRequiredInitialization() {
		String source= """
				import java.util.ArrayList;
				import java.util.List;
				class Sample {
					static class Collections {
						static <T> List<T> synchronizedList(List<T> values) { return values; }
					}
					private final List<String> lookalike = Collections.synchronizedList(new ArrayList<>());
					private final List<String> backing = new ArrayList<>();
					private final List<String> sharedBacking = java.util.Collections.synchronizedList(backing);
					private List<String> mutable = java.util.Collections.synchronizedList(new ArrayList<>());
					private static final List<String> shared = java.util.Collections.synchronizedList(new ArrayList<>());
					void inspect() {
						for (String entry : lookalike) { }
						for (String entry : sharedBacking) { }
						for (String entry : mutable) { }
						for (String entry : shared) { }
					}
				}
				""";

		assertTrue(analyze(source).isEmpty());
	}

	@Test
	void observesDirectIterationWithoutInferringAWrapperMonitor() {
		String source= """
				import java.util.ArrayList;
				import java.util.Collections;
				import java.util.List;
				class Sample {
					private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
					void inspect() {
						for (String entry : entries) { }
					}
				}
				""";

		List<DiagnosticOnly> observations= analyze(source);
		assertEquals(1, observations.size());
		assertTrue(observations.getFirst().protocol().hasUnresolvedBoundary());
		assertTrue(analyzer.analyze(parse(source, false)).isEmpty());
	}

	@Test
	void distinguishesLiveReturnsSnapshotsAndDeferredReturns() {
		String source= """
				import java.util.*;
				import java.util.function.Supplier;
				class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    List<String> live() { return entries; }
				    List<String> snapshot() { synchronized(entries) { return List.copyOf(this.entries); } }
				    List<String> backingCopy() { return new ArrayList<>(entries); }
				    List<String> other(Sample other) { return other.entries; }
				    Supplier<List<String>> later() { return () -> { return entries; }; }
				    private List<String> internal() { return entries; }
				}
				""";
		List<DiagnosticOnly> results= analyze(source);
		assertEquals(2, results.size());
		var live= results.get(0).protocol();
		assertEquals("LIVE_WRAPPER_RETURN", live.evidence().getFirst().kind().name()); //$NON-NLS-1$
		assertTrue(live.evidence().getFirst().summary().contains("monitor")); //$NON-NLS-1$
		assertEquals("entries", anchor(source, live.evidence().getFirst())); //$NON-NLS-1$
		var copy= results.get(1).protocol();
		assertEquals(EvidenceKind.SNAPSHOT_PUBLICATION, copy.evidence().getFirst().kind());
		assertEquals(IterationSemantics.IMMUTABLE_SNAPSHOT, copy.summary().iteration());
		assertTrue(copy.hasSingleProtectingLock());
		assertEquals("List.copyOf(this.entries)", anchor(source, copy.evidence().getFirst())); //$NON-NLS-1$
		assertFalse(results.get(1).permitsSourceRewrite());
		assertTrue(analyzer.analyze(parse(source, false)).isEmpty());
	}

	private static String anchor(String source, ConcurrencyProtocol.Evidence evidence) {
		return source.substring(evidence.sourceStart(), evidence.sourceStart() + evidence.sourceLength());
	}

	private List<DiagnosticOnly> analyze(String source) {
		CompilationUnit unit= parse(source, true);
		assertTrue(Arrays.stream(unit.getProblems()).noneMatch(IProblem::isError), unit::toString);
		return analyzer.analyze(unit);
	}

	private static CompilationUnit parse(String source, boolean resolveBindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(resolveBindings);
		parser.setBindingsRecovery(true);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(null, null, null, true);
		var options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
