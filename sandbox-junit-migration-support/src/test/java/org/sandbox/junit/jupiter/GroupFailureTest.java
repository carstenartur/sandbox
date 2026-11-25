/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.junit.jupiter.ExternalResourceExtension.resourceHook;
import static org.sandbox.junit.jupiter.GroupLaunch.counts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.function.Executable;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.TestAbortedException;
import org.sandbox.junit.jupiter.ExternalResourceExtension.ResourceHook;

@Execution(ExecutionMode.SAME_THREAD)
class GroupFailureTest {
	private static final List<String> events= new ArrayList<>();
	private static Function<ExtensionContext, List<ResourceHook>> factory;
	private static Executable beforeUser, body, afterUser;

	@BeforeEach void reset() {
		events.clear();
		beforeUser= body= afterUser= () -> {};
	}

	static class PerMethod {
		@RegisterExtension static final Extension hooks= ExternalResourceExtension.perTestGroup(PerMethod.class, c -> factory.apply(c));
		@BeforeEach void before() throws Throwable { beforeUser.execute(); }
		@Test void test() throws Throwable { body.execute(); }
		@AfterEach void after() throws Throwable { afterUser.execute(); }
	}

	static class PerClass {
		@RegisterExtension static final Extension hooks= ExternalResourceExtension.perClassGroup(PerClass.class, c -> factory.apply(c));
		@Test void test() throws Throwable { body.execute(); }
	}

	private static ResourceHook hook(String name, Throwable setup, Error cleanup) {
		return resourceHook(() -> {
			events.add("select " + name);
			return name;
		}, selected -> {
			events.add("open " + selected);
			if (setup != null) {
				throw setup;
			}
		}, selected -> {
			events.add("close " + selected);
			if (cleanup != null) {
				throw cleanup;
			}
		});
	}

	private static Throwable failure(TestExecutionSummary summary, boolean classSetup) {
		counts(summary, 1, 0, classSetup ? 0 : 1, 0, 2, classSetup ? 1 : 0);
		assertEquals(1, summary.getFailures().size());
		return summary.getFailures().getFirst().getException();
	}

	@Test void validatesArgumentsAtRegistration() {
		assertThrows(NullPointerException.class, () -> resourceHook(null, o -> {}, o -> {}));
		assertThrows(NullPointerException.class, () -> resourceHook(Object::new, null, o -> {}));
		assertThrows(NullPointerException.class, () -> resourceHook(Object::new, o -> {}, null));
		assertThrows(NullPointerException.class, () -> ExternalResourceExtension.perClassGroup(null, c -> List.of()));
		assertThrows(NullPointerException.class, () -> ExternalResourceExtension.perTestGroup(null, c -> List.of()));
		assertThrows(NullPointerException.class, () -> ExternalResourceExtension.perClassGroup(PerClass.class, null));
		assertThrows(NullPointerException.class, () -> ExternalResourceExtension.perTestGroup(PerMethod.class, null));
	}

	@Test void rejectsNullListOrLateNullDescriptorBeforeSelectingAnything() {
		var outer= hook("outer", null, null);
		factory= c -> null;
		assertInstanceOf(NullPointerException.class, failure(GroupLaunch.run(PerClass.class), true));
		factory= c -> Arrays.asList(outer, null);
		assertInstanceOf(NullPointerException.class, failure(GroupLaunch.run(PerMethod.class), false));
		assertEquals(List.of(), events);
	}

	@Test void factoryAndLateSelectorFailuresRunNoHooks() {
		var problem= new IllegalStateException();
		factory= c -> { throw problem; };
		assertSame(problem, failure(GroupLaunch.run(PerMethod.class), false));
		factory= c -> List.of(hook("outer", null, null),
				resourceHook(() -> { throw problem; }, o -> events.add("unexpected setup"), o -> events.add("unexpected cleanup")));
		assertSame(problem, failure(GroupLaunch.run(PerClass.class), true));
		assertEquals(List.of("select outer"), events);
	}

	@Test void setupFailureAtEveryDepthPreservesOriginalThrowableAndUnwindsSuccessfulPrefix() {
		var traces= List.of(List.of("open 0"), List.of("open 0", "open 1", "close 0"),
				List.of("open 0", "open 1", "open 2", "close 1", "close 0"));
		for (int depth= 0; depth < 3; depth++) {
			var problem= new Throwable("setup " + depth);
			var hooks= new ArrayList<ResourceHook>();
			for (int i= 0; i < 3; i++) {
				hooks.add(hook(Integer.toString(i), i == depth ? problem : null, null));
			}
			factory= c -> hooks;
			for (var fixture : List.of(PerMethod.class, PerClass.class)) {
				events.clear();
				assertSame(problem, failure(GroupLaunch.run(fixture), fixture == PerClass.class));
				var expected= new ArrayList<>(List.of("select 0", "select 1", "select 2"));
				expected.addAll(traces.get(depth));
				assertEquals(expected, events);
			}
		}
	}

	@Test void cleanupThrowsOneOriginalOrFreshOrderedAggregatesIncludingRepeatedFailures() {
		var a= new AssertionError("outer");
		var b= new AssertionError("middle and inner");
		factory= c -> List.of(hook("outer", null, null), hook("inner", null, a));
		assertSame(a, failure(GroupLaunch.run(PerMethod.class), false));
		assertEquals("close outer", events.getLast());
		factory= c -> List.of(hook("outer", null, a), hook("middle", null, b), hook("inner", null, b));
		var first= failure(GroupLaunch.run(PerMethod.class), false);
		var second= failure(GroupLaunch.run(PerMethod.class), false);
		assertNotSame(first, second);
		for (var failure : List.of(first, second)) {
			assertEquals(List.of(b, b, a), assertInstanceOf(MultipleFailuresError.class, failure).getFailures());
		}
		assertEquals(0, a.getSuppressed().length);
		assertEquals(0, b.getSuppressed().length);
	}

	@Test void jupiterAttachesCleanupToSetupBodyOrUserLifecyclePrimary() {
		for (String phase : List.of("setup", "before", "body", "after")) {
			reset();
			var primary= new Throwable(phase);
			var cleanup= new AssertionError("cleanup");
			var other= new AssertionError("other");
			Executable fail= () -> { throw primary; };
			factory= c -> List.of(hook("outer", null, cleanup), hook("middle", null, other),
					hook("inner", phase.equals("setup") ? primary : null, null));
			switch (phase) {
				case "before" -> beforeUser= fail;
				case "body" -> body= fail;
				case "after" -> afterUser= fail;
			}
			assertSame(primary, failure(GroupLaunch.run(PerMethod.class), false));
			assertEquals(1, primary.getSuppressed().length);
			var aggregate= assertInstanceOf(MultipleFailuresError.class, primary.getSuppressed()[0]);
			assertEquals(List.of(other, cleanup), aggregate.getFailures());
			assertEquals("close outer", events.getLast());
		}
	}

	@Test void suppressionDisabledSetupStaysPrimaryEvenWhenCleanupFails() {
		var primary= new Throwable("opaque", null, false, false) { private static final long serialVersionUID= 1L; };
		factory= c -> List.of(hook("outer", null, new AssertionError()), hook("inner", primary, null));
		assertSame(primary, failure(GroupLaunch.run(PerMethod.class), false));
		assertEquals(0, primary.getSuppressed().length);
		assertEquals("close outer", events.getLast());
	}

	@Test void assumptionInSetupOrBodyStillUnwindsAndAborts() {
		var assumption= new TestAbortedException("condition");
		for (boolean inSetup : List.of(true, false)) {
			events.clear();
			factory= c -> List.of(hook("outer", null, null), hook("inner", inSetup ? assumption : null, null));
			body= () -> { throw assumption; };
			counts(GroupLaunch.run(PerMethod.class), 1, 0, 0, 1, 2, 0);
			assertEquals(inSetup ? List.of("select outer", "select inner", "open outer", "open inner", "close outer")
					: List.of("select outer", "select inner", "open outer", "open inner", "close inner", "close outer"), events);
		}
	}

	@Test void cleanupFailureAfterAnAssumptionUsesJupiterFailureClassification() {
		var assumption= new TestAbortedException("inner setup");
		var cleanup= new AssertionError("outer cleanup");
		factory= c -> List.of(hook("outer", null, cleanup), hook("inner", assumption, null));
		assertSame(cleanup, failure(GroupLaunch.run(PerMethod.class), false));
		assertEquals(List.of(assumption), List.of(cleanup.getSuppressed()));
		assertEquals(List.of("select outer", "select inner", "open outer", "open inner", "close outer"), events);
	}

	@Test void classCleanupFailureBelongsToContainerAfterSuccessfulBody() {
		var a= new AssertionError("outer");
		var b= new AssertionError("inner");
		factory= c -> List.of(hook("outer", null, a), hook("inner", null, b));
		body= () -> events.add("body");
		var summary= GroupLaunch.run(PerClass.class);
		counts(summary, 1, 1, 0, 0, 2, 1);
		assertEquals(1, summary.getFailures().size());
		var failure= summary.getFailures().getFirst().getException();
		assertEquals(List.of(b, a), assertInstanceOf(MultipleFailuresError.class, failure).getFailures());
		assertEquals(List.of("select outer", "select inner", "open outer", "open inner", "body", "close inner", "close outer"), events);
	}

	@Test void fatalFactoryAndSelectorEscapeBeforeAcquisition() {
		var fatal= new OutOfMemoryError("synthetic");
		factory= c -> { throw fatal; };
		assertFatal(PerClass.class, fatal);
		factory= c -> List.of(hook("outer", null, null),
				resourceHook(() -> { throw fatal; }, o -> events.add("unexpected setup"), o -> events.add("unexpected cleanup")));
		assertFatal(PerMethod.class, fatal);
		assertEquals(List.of("select outer"), events);
	}

	@Test void fatalSetupDrainsImmediatelyPreservingIdentityAndCleanupContext() {
		var cleanup= new AssertionError("ordinary");
		for (var fixture : List.of(PerClass.class, PerMethod.class)) {
			events.clear();
			var fatal= new OutOfMemoryError("synthetic setup");
			factory= c -> List.of(hook("outer", null, cleanup), hook("inner", fatal, null));
			assertFatal(fixture, fatal);
			assertEquals(List.of(cleanup), List.of(fatal.getSuppressed()));
			assertEquals(List.of("select outer", "select inner", "open outer", "open inner", "close outer"), events);
		}
	}

	@Test void fatalCleanupStopsDrainAndCarriesEarlierOrdinaryFailures() {
		for (boolean setupFails : List.of(true, false)) {
			events.clear();
			var setup= new OutOfMemoryError("synthetic setup");
			var cleanup= new OutOfMemoryError("synthetic cleanup");
			var a= new AssertionError("a");
			var b= new AssertionError("b");
			factory= c -> List.of(hook("not reached", null, null), hook("fatal", null, cleanup),
					hook("a", null, a), hook("b", null, b), hook("last", setupFails ? setup : null, null));
			assertFatal(PerMethod.class, setupFails ? setup : cleanup);
			if (setupFails) {
				assertEquals(List.of(cleanup), List.of(setup.getSuppressed()));
			}
			assertEquals(1, cleanup.getSuppressed().length);
			assertEquals(List.of(b, a), assertInstanceOf(MultipleFailuresError.class, cleanup.getSuppressed()[0]).getFailures());
			assertEquals("close fatal", events.getLast());
			assertFalse(events.contains("close not reached"));
		}
	}

	@Test void fatalDuringExitLeavesNothingForTheMatchingEngineCallbackToRetry() {
		var fatal= new OutOfMemoryError("synthetic cleanup");
		factory= context -> {
			body= () -> {
				var extension= (AfterEachCallback) PerMethod.hooks;
				assertSame(fatal, assertThrows(OutOfMemoryError.class, () -> extension.afterEach(context)));
			};
			return List.of(hook("outer", null, null), hook("inner", null, fatal));
		};
		counts(GroupLaunch.run(PerMethod.class), 1, 1, 0, 0, 2, 0);
		assertEquals(List.of("select outer", "select inner", "open outer", "open inner", "close inner"), events);
	}

	@Test void sameFatalAtSetupAndCleanupAvoidsSelfSuppression() {
		var fatal= new OutOfMemoryError("same object");
		factory= c -> List.of(hook("outer", null, fatal), hook("inner", fatal, null));
		assertFatal(PerMethod.class, fatal);
		assertEquals(List.of(), List.of(fatal.getSuppressed()));
		assertEquals("close outer", events.getLast());
	}

	@Test void bodyFatalCanBypassGroupExit() {
		var fatal= new OutOfMemoryError("synthetic body");
		factory= c -> List.of(hook("outer", null, null));
		body= () -> { throw fatal; };
		assertFatal(PerMethod.class, fatal);
		assertEquals(List.of("select outer", "open outer"), events);
	}

	private static void assertFatal(Class<?> fixture, OutOfMemoryError expected) {
		assertSame(expected, assertThrows(OutOfMemoryError.class, () -> GroupLaunch.run(fixture)));
	}
}
