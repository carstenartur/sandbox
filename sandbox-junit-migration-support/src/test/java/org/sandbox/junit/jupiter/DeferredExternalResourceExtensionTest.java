/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.rules.ExternalResource;

@Execution(ExecutionMode.SAME_THREAD)
public class DeferredExternalResourceExtensionTest {

	private static final ConcurrentLinkedQueue<String> events= new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<Fixture> opened= new ConcurrentLinkedQueue<>();
	private static final ConcurrentLinkedQueue<Fixture> closed= new ConcurrentLinkedQueue<>();
	private static final RuntimeException supplierFailure= new IllegalStateException("supplier");
	private static final Throwable setupFailure= new Throwable("setup");
	private static final AssertionError testTeardownFailure= new AssertionError("test teardown");
	private static final AssertionError classTeardownFailure= new AssertionError("class teardown");

	public static class Fixture extends ExternalResource implements AutoCloseable {
		private final String name;

		Fixture(String name) {
			this.name= name;
		}

		@Override protected void before() {
			opened.add(this);
			events.add("open " + name);
		}

		@Override protected void after() {
			closed.add(this);
			events.add("close " + name);
		}

		@Override public void close() {
			events.add("unexpected store disposal " + name);
		}
	}

	public static class LegacyReceivers {
		@ClassRule public static Fixture project= new Fixture("class");
		@Rule public Fixture fixture;

		public LegacyReceivers() {
			fixture= new Fixture("test");
		}

		@org.junit.Test public void replaceFields() {
			events.add("body");
			fixture= new Fixture("replacement test");
			project= new Fixture("replacement class");
		}
	}

	static class DeferredReceivers {
		@RegisterExtension static final Extension classHooks= ExternalResourceExtension.perClass(
				() -> read("class", DeferredReceivers.project), Fixture::before, Fixture::after);
		// The field is still null when classHooks is registered.
		private static Fixture project= new Fixture("class");

		private Fixture fixture;
		@RegisterExtension final Extension testHooks= ExternalResourceExtension.perTest(
				() -> read("test", fixture), Fixture::before, Fixture::after);

		DeferredReceivers() {
			fixture= new Fixture("test");
		}

		@Test void replaceFields() {
			events.add("body");
			fixture= new Fixture("replacement test");
			project= new Fixture("replacement class");
		}
	}

	static class NullReceivers {
		@RegisterExtension static final Extension classHooks= ExternalResourceExtension.perClass(
				() -> read("null class", null), Fixture::before, Fixture::after);
		@RegisterExtension final Extension testHooks= ExternalResourceExtension.perTest(
				() -> read("null test", null), Fixture::before, Fixture::after);

		@Test void runs() { events.add("body"); }
	}

	static class TestSupplierFails {
		@RegisterExtension final Extension hooks= ExternalResourceExtension.perTest(
				DeferredExternalResourceExtensionTest::failSupplier, Fixture::before, Fixture::after);

		@Test void neverReached() { events.add("unexpected body"); }
	}

	static class ClassSupplierFails {
		@RegisterExtension static final Extension hooks= ExternalResourceExtension.perClass(
				DeferredExternalResourceExtensionTest::failSupplier, Fixture::before, Fixture::after);

		@Test void neverReached() { events.add("unexpected body"); }
	}

	static class TestSetupFails {
		@RegisterExtension final Extension hooks= ExternalResourceExtension.perTest(
				() -> read("test", new Fixture("test")), DeferredExternalResourceExtensionTest::failSetup, Fixture::after);

		@Test void neverReached() { events.add("unexpected body"); }
	}

	static class ClassSetupFails {
		@RegisterExtension static final Extension hooks= ExternalResourceExtension.perClass(
				() -> read("class", new Fixture("class")), DeferredExternalResourceExtensionTest::failSetup, Fixture::after);

		@Test void neverReached() { events.add("unexpected body"); }
	}

	static class BodyAndTeardownFail {
		private static AssertionError thrownBodyFailure;

		@RegisterExtension static final Extension classHooks= ExternalResourceExtension.perClass(
				() -> read("class", new Fixture("class")), Fixture::before, fixture -> {
					fixture.after();
					throw classTeardownFailure;
				});
		@RegisterExtension final Extension testHooks= ExternalResourceExtension.perTest(
				() -> read("test", new Fixture("test")), Fixture::before, fixture -> {
					fixture.after();
					throw testTeardownFailure;
				});

		@Test void fails() {
			events.add("body");
			thrownBodyFailure= new AssertionError("body");
			throw thrownBodyFailure;
		}
	}

	static class MultipleRegistrations {
		@Order(1)
		@RegisterExtension final Extension first= ExternalResourceExtension.perTest(
				() -> read("first", new Fixture("first")), Fixture::before, Fixture::after);
		@Order(2)
		@RegisterExtension final Extension second= ExternalResourceExtension.perTest(
				() -> read("second", new Fixture("second")), Fixture::before, Fixture::after);

		@Test void runs() { events.add("body"); }
	}

	@Execution(ExecutionMode.CONCURRENT)
	static class OverlappingRepetitions {
		private static final AtomicInteger sequence= new AtomicInteger();
		private static final CyclicBarrier bodies= new CyclicBarrier(2);

		@RegisterExtension static final Extension hooks= ExternalResourceExtension.perTest(
				() -> {
					String name= "repeat " + sequence.incrementAndGet();
					return read(name, new Fixture(name));
				}, Fixture::before, Fixture::after);

		@RepeatedTest(2)
		void overlaps() throws Exception {
			events.add("body");
			bodies.await(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void bindsConstructorAndLateStaticFixturesToTheSameReceiversAsVintage() {
		LegacyReceivers.project= new Fixture("class");
		assertSuccessful(run(LegacyReceivers.class, false), 1);
		assertEvents("open class", "open test", "body", "close test", "close class");
		assertReverseCloseOrder();

		DeferredReceivers.project= new Fixture("class");
		assertSuccessful(run(DeferredReceivers.class, false), 1);
		assertEvents("read class", "open class", "read test", "open test", "body", "close test", "close class");
		assertReverseCloseOrder();
	}

	@Test
	void rejectsNullRegistrationArgumentsInBothScopes() {
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perTest(null, Fixture::before, Fixture::after));
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perTest(() -> new Fixture("test"), null, Fixture::after));
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perTest(() -> new Fixture("test"), Fixture::before, null));
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perClass(null, Fixture::before, Fixture::after));
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perClass(() -> new Fixture("class"), null, Fixture::after));
		assertThrows(NullPointerException.class,
				() -> ExternalResourceExtension.perClass(() -> new Fixture("class"), Fixture::before, null));
	}

	@Test
	void nullReceiverSkipsBothHooksAndRunsTheBody() {
		assertSuccessful(run(NullReceivers.class, false), 1);
		assertEvents("read null class", "read null test", "body");
	}

	@Test
	void supplierFailureRunsNeitherHookAndKeepsTheOriginalThrowable() {
		assertFailure(run(TestSupplierFails.class, false), supplierFailure, false);
		assertEvents("read failing supplier");
		assertFailure(run(ClassSupplierFails.class, false), supplierFailure, true);
		assertEvents("read failing supplier");
	}

	@Test
	void setupFailureRunsNoTeardownAndKeepsTheOriginalThrowable() {
		assertFailure(run(TestSetupFails.class, false), setupFailure, false);
		assertEvents("read test", "open test");
		assertFailure(run(ClassSetupFails.class, false), setupFailure, true);
		assertEvents("read class", "open class");
	}

	@Test
	void bodyAndTeardownFailuresKeepJupiterReportingAndCloseEachReceiverOnce() {
		var summary= run(BodyAndTeardownFail.class, false);
		var bodyFailure= BodyAndTeardownFail.thrownBodyFailure;
		assertCounts(summary, 1, 1, 1, 0, 1);
		assertEquals(List.of(bodyFailure, classTeardownFailure),
				summary.getFailures().stream().map(TestExecutionSummary.Failure::getException).toList());
		assertEquals(List.of(testTeardownFailure), List.of(bodyFailure.getSuppressed()));
		assertEvents("read class", "open class", "read test", "open test", "body", "close test", "close class");
		assertReverseCloseOrder();
	}

	@Test
	void registrationsAtTheSameScopeHaveSeparateReceivers() {
		assertSuccessful(run(MultipleRegistrations.class, false), 1);
		assertEvents("read first", "open first", "read second", "open second", "body", "close second", "close first");
		assertReverseCloseOrder();
	}

	@Test
	void overlappingRepetitionsKeepIndependentReceiversForOneRegistration() {
		OverlappingRepetitions.sequence.set(0);
		OverlappingRepetitions.bodies.reset();
		assertSuccessful(run(OverlappingRepetitions.class, true), 2);
		assertEquals(2, OverlappingRepetitions.sequence.get());
		assertEquals(2, opened.size());
		assertEquals(2, closed.size());
		var trace= new ArrayList<>(events);
		assertEquals(8, trace.size());
		assertEquals(2, trace.stream().filter("body"::equals).count());
		for (Fixture receiver : opened) {
			assertEquals(1, closed.stream().filter(fixture -> fixture == receiver).count());
			int read= trace.indexOf("read " + receiver.name);
			int open= trace.indexOf("open " + receiver.name);
			int close= trace.indexOf("close " + receiver.name);
			assertTrue(read >= 0 && read < open && open < close, trace::toString);
			assertTrue(trace.lastIndexOf("body") < close, trace::toString);
		}
	}

	private static Fixture read(String label, Fixture fixture) {
		events.add("read " + label);
		return fixture;
	}

	private static Fixture failSupplier() {
		events.add("read failing supplier");
		throw supplierFailure;
	}

	private static void failSetup(Fixture fixture) throws Throwable {
		fixture.before();
		throw setupFailure;
	}

	private static void assertReverseCloseOrder() {
		var setup= List.copyOf(opened);
		var teardown= List.copyOf(closed);
		assertEquals(2, setup.size());
		assertEquals(2, teardown.size());
		assertSame(setup.get(1), teardown.get(0));
		assertSame(setup.get(0), teardown.get(1));
	}

	private static void assertEvents(String... expected) {
		assertEquals(List.of(expected), List.copyOf(events));
	}

	private static void assertSuccessful(TestExecutionSummary summary, long tests) {
		assertEquals(List.of(), summary.getFailures().stream().map(TestExecutionSummary.Failure::getException).toList());
		assertCounts(summary, tests, tests, 0, tests, 0);
	}

	private static void assertFailure(TestExecutionSummary summary, Throwable failure, boolean classScope) {
		if (classScope) {
			assertCounts(summary, 1, 0, 1, 0, 0);
		} else {
			assertCounts(summary, 1, 1, 0, 0, 1);
		}
		assertEquals(1, summary.getFailures().size());
		assertSame(failure, summary.getFailures().getFirst().getException());
	}

	private static void assertCounts(TestExecutionSummary summary, long found, long started, long failedContainers,
			long succeeded, long failedTests) {
		assertEquals(found, summary.getTestsFoundCount());
		assertEquals(started, summary.getTestsStartedCount());
		assertEquals(succeeded, summary.getTestsSucceededCount());
		assertEquals(failedTests, summary.getTestsFailedCount());
		assertEquals(failedContainers, summary.getContainersFailedCount());
		assertEquals(0, summary.getTestsSkippedCount());
		assertEquals(0, summary.getTestsAbortedCount());
	}

	private static TestExecutionSummary run(Class<?> fixture, boolean parallel) {
		events.clear();
		opened.clear();
		closed.clear();
		var listener= new SummaryGeneratingListener();
		var request= LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
				.configurationParameter("junit.jupiter.execution.parallel.enabled", Boolean.toString(parallel))
				.configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
				.configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
				.build();
		LauncherFactory.create().execute(request, listener);
		return listener.getSummary();
	}
}
