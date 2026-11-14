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
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RecommendedMigration;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RetainExistingLocking;
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

	@Test
	void classifiesTheSharedProducerAndDrainMonitorFromResolvedSource() {
		String source= """
				import java.util.*;
				final class Sample {
				    private final List<String> pending = Collections.synchronizedList(new ArrayList<>());
				    void enqueue(String item) { pending.add(item); }
				    List<String> takeBatch() {
				        synchronized (pending) {
				            var result = new ArrayList<>(pending);
				            pending.clear();
				            return result;
				        }
				    }
				}
				""";
		var assessments= analyzer.assessDrains(parse(source, true));
		assertEquals(1, assessments.size());
		assertTrue(assessments.getFirst() instanceof RetainExistingLocking);
		var assessment= assessments.getFirst();
		assertFalse(assessment.permitsSourceRewrite());
		var protocol= assessment.protocol();
		assertTrue(protocol.hasSingleProtectingLock());
		assertEquals(AtomicityRequirement.DRAIN, protocol.summary().atomicity());
		assertEquals(WorkloadShape.PRODUCER_CONSUMER, protocol.summary().workload());
		assertEquals(ReentrancyContract.NO_CALLBACKS_OBSERVED, protocol.reentrancy());
		assertEquals(AnalysisCompleteness.LOCAL_USAGE_COMPLETE, protocol.completeness());
		assertTrue(analyzer.assessDrains(parse(source, false)).isEmpty());
	}

	@Test
	void reportsCheckThenAddOnlyForTheSameListAndStableElement() {
		String source= """
				import java.util.*;
				class Sample {
					private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
					private final List<String> other = Collections.synchronizedList(new ArrayList<>());
					void add(String value, String different) {
						synchronized (this.entries) {
							if (!entries.contains(value)) { this.entries.add(value); }
						}
						synchronized (this) {
							if (!entries.contains(value)) entries.add(value);
						}
						if (!entries.contains(value)) other.add(value);
						if (!entries.contains(value)) entries.add(different);
						if (!entries.contains(value)) { entries.add(value); entries.clear(); }
						synchronized (entries) {
							Runnable deferred = () -> { if (!entries.contains(value)) entries.add(value); };
						}
					}
				}
				""";
		var reports= analyze(source);
		assertEquals(3, reports.size());
		var locked= reports.get(0);
		assertEquals(AtomicityRequirement.CHECK_THEN_ACT, locked.protocol().summary().atomicity());
		assertTrue(locked.protocol().hasSingleProtectingLock());
		assertEquals(List.of("entries.contains(value)", "this.entries.add(value)"), //$NON-NLS-1$ //$NON-NLS-2$
				locked.protocol().evidence().stream().map(e -> anchor(source, e)).toList());
		for (var open : reports.subList(1, 3)) {
			assertTrue(open.protocol().hasUnresolvedBoundary());
			assertFalse(open.protocol().hasSingleProtectingLock());
			assertFalse(open.permitsSourceRewrite());
		}
		assertTrue(analyzer.analyze(parse(source, false)).isEmpty());
	}

	@Test
	void closesFifoAndOrderedPendingWorkOnlyWithTheActualProducerAndMonitor() {
		String source= """
				import java.util.*;
				final class Sample {
					private final List<String> queue = Collections.synchronizedList(new ArrayList<>());
					private final Set<String> pending = Collections.synchronizedSet(new LinkedHashSet<>());
					void offer(String value) { queue.add(Objects.requireNonNull(value)); }
					String take() { synchronized (queue) { return queue.isEmpty() ? null : queue.remove(0); } }
					void schedule(String value) { pending.add(value); }
					Set<String> drain() { synchronized (pending) {
						var batch = new LinkedHashSet<>(pending);
						pending.clear();
						return batch;
					} }
				}
				""";
		var reports= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		assertEquals(List.of("CONCURRENT_QUEUE", "PURPOSE_SPECIFIC_ABSTRACTION"), reports.stream()
				.map(report -> ((RecommendedMigration) report).strategy().name()).toList());
		assertTrue(reports.stream().noneMatch(ConcurrencyProtocolAssessment::permitsSourceRewrite));
		for (String changed : List.of(source.replace("synchronized (queue)", "synchronized (this)"),
				source.replace("Objects.requireNonNull(value)", "value"),
				source.replace("String take()", "int size() { return queue.size(); } String take()"),
				source.replace("return batch;", "schedule(null); return batch;"),
				source.replace("new LinkedHashSet<>(pending)", "new LinkedHashSet<String>()"))) {
			var rejected= ContainerConcurrencyAnalyzer.analyze(parse(changed, true), () -> {});
			assertTrue(rejected.stream().anyMatch(DiagnosticOnly.class::isInstance), changed);
		}
	}

	@Test
	void locatesPublishedReferencesCallbackBoundariesAndExplicitLocksWithoutClaimingSafety() {
		String source= """
				import java.util.*;
				import java.util.concurrent.atomic.*;
				import java.util.concurrent.locks.*;
				final class Sample {
					private final List<Runnable> jobs = new ArrayList<>();
					private final Lock guard = new ReentrantLock();
					private volatile Runnable[] listeners = {};
					private final AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of());
					void work(Runnable callback) {
						guard.lock();
						try { for (Runnable job : jobs) job.run(); } finally { guard.unlock(); }
						for (Runnable r : listeners) r.run();
						snapshot.set(List.of("value"));
					}
					List<Runnable> leak() { return jobs; }
				}
				""";
		var observations= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		var jobs= observations.stream().filter(report -> "jobs".equals(report.protocol().identity().displayName())).findFirst().orElseThrow();
		assertEquals(SynchronizationKind.EXPLICIT_LOCK, jobs.protocol().summary().synchronization());
		assertTrue(jobs.protocol().evidence().stream().anyMatch(e -> e.kind() == EvidenceKind.CALLBACK_UNDER_LOCK && "job.run()".equals(anchor(source, e))));
		assertTrue(jobs.protocol().evidence().stream().anyMatch(e -> e.kind() == EvidenceKind.LIVE_WRAPPER_RETURN));
		assertTrue(observations.stream().anyMatch(r -> r.protocol().summary().synchronization() == SynchronizationKind.VOLATILE_SNAPSHOT));
		assertTrue(observations.stream().anyMatch(r -> r.protocol().summary().synchronization() == SynchronizationKind.ATOMIC_REFERENCE));
		assertTrue(observations.stream().allMatch(DiagnosticOnly.class::isInstance));
		var changed= source.replace("guard.unlock()", "new ReentrantLock().unlock()");
		assertTrue(ContainerConcurrencyAnalyzer.analyze(parse(changed, true), () -> {}).stream()
				.noneMatch(r -> r.protocol().summary().synchronization() == SynchronizationKind.EXPLICIT_LOCK));
	}

	@Test
	void sharesTheExactMembershipAndCallbackProofsWithTheRewrites() {
		String source= """
				import java.util.*;
				final class Sample {
					private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
					private final List<Runnable> jobs = new ArrayList<>();
					boolean mark(String key) { return seen.add(Objects.requireNonNull(key)); }
					void register(Runnable value) { synchronized (jobs) { jobs.add(value); } }
					void notifyAllJobs() {
						Runnable[] snapshot;
						synchronized (jobs) { snapshot = jobs.toArray(new Runnable[0]); }
						for (Runnable action : snapshot) action.run();
					}
				}
				""";
		var reports= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		assertEquals(List.of("CONCURRENT_MEMBERSHIP_SET", "COPY_ON_WRITE_SEQUENCE"), reports.stream()
				.map(report -> ((RecommendedMigration) report).strategy().name()).toList());
		for (String changed : List.of(source.replace("return seen.add", "if (seen.contains(\"x\")) return false; return seen.add"),
				source.replace("action.run();", "if (action != null) action.run();"))) {
			assertTrue(ContainerConcurrencyAnalyzer.analyze(parse(changed, true), () -> {}).stream().anyMatch(DiagnosticOnly.class::isInstance));
		}
	}

	@Test
	void groupsBindingResolvedOperationsAndLockedIterationWithoutClaimingClosure() {
		String source= """
				import java.util.*;
				class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    void add(String text) { entries.add(text); }
				    boolean contains(String text) { return entries.contains(text); }
				    void inspect() { synchronized (entries) { for (String item : this.entries) { } } }
				    List<String> live() { return entries; }
				}
				""";
		var reports= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		assertEquals(1, reports.size());
		var report= reports.getFirst();
		assertFalse(report.permitsSourceRewrite());
		assertEquals(AnalysisCompleteness.LOCAL_SEED, report.protocol().completeness());
		assertEquals(List.of(EvidenceKind.LOCKED_WRITE, EvidenceKind.LOCKED_READ,
				EvidenceKind.LOCKED_ITERATION, EvidenceKind.LIVE_WRAPPER_RETURN),
				report.protocol().evidence().stream().map(Evidence::kind).toList());
		assertEquals(1, report.protocol().lockIdentities().size());
		for (var evidence : report.protocol().evidence()) {
			String snippet= source.substring(evidence.sourceStart(), evidence.sourceStart() + evidence.sourceLength());
			assertFalse(snippet.isBlank());
		}
	}

	@Test
	void detectsUnprotectedIterationAndUnclassifiedMethodsWithoutRewriting() {
		String source= """
				import java.util.*;
				class Sample {
				    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());
				    void inspect() { for (String item : entries) { } }
				    void stream() { entries.stream().count(); }
				}
				""";
		var reports= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		assertEquals(1, reports.size());
		assertTrue(reports.getFirst().protocol().hasUnresolvedBoundary());
		assertEquals(List.of(EvidenceKind.UNRESOLVED_BOUNDARY, EvidenceKind.UNRESOLVED_BOUNDARY),
				reports.getFirst().protocol().evidence().stream().map(Evidence::kind).toList());
		assertFalse(reports.getFirst().permitsSourceRewrite());
		assertTrue(ContainerConcurrencyAnalyzer.analyze(parse(source, false), () -> {}).isEmpty());
	}

	@Test
	void keepsDistinctPrivateFieldsApartAndRejectsFactoryLookalikes() {
		String source= """
				import java.util.*;
				class Sample {
				    static class Collections {
				        static <T> List<T> synchronizedList(List<T> list) { return list; }
				    }
				    private final List<String> first = java.util.Collections.synchronizedList(new ArrayList<>());
				    private final List<String> second = java.util.Collections.synchronizedList(new ArrayList<>());
				    private final List<String> fake = Collections.synchronizedList(new ArrayList<>());
				    void add(String text) { first.add(text); second.add(text); fake.add(text); }
				}
				""";
		var reports= ContainerConcurrencyAnalyzer.analyze(parse(source, true), () -> {});
		assertEquals(2, reports.size());
		assertEquals(List.of("first", "second"),
				reports.stream().map(r -> r.protocol().identity().displayName()).toList()); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(reports.stream().allMatch(r -> r.protocol().evidence().size() == 1));
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
