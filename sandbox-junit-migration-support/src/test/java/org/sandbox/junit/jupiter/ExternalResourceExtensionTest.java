/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;
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
import org.opentest4j.MultipleFailuresError;

@Execution(ExecutionMode.SAME_THREAD)
public class ExternalResourceExtensionTest {

	private static final List<String> events= new ArrayList<>();
	private static final IOException setupFailure= new IOException("setup");
	private static final AssertionError teardownFailure= new AssertionError("teardown");

	public static class ProjectSetup extends ExternalResource {
		private final String name;

		ProjectSetup(String name) {
			this.name= name;
		}

		@Override protected void before() throws Throwable { events.add("open " + name); }
		@Override protected void after() { events.add("close " + name); }

		Extension perTest() { return ExternalResourceExtension.perTest(this::before, this::after); }
		Extension perClass() { return ExternalResourceExtension.perClass(this::before, this::after); }
	}

	public static class Legacy {
		@ClassRule public static final ProjectSetup project= new ProjectSetup("class");
		@Rule public final ProjectSetup each= new ProjectSetup("test");
		@org.junit.Test public void one() { events.add("body"); }
		@org.junit.Test public void two() { events.add("body"); }
	}

	static class Migrated {
		@RegisterExtension static final Extension project= new ProjectSetup("class").perClass();
		@RegisterExtension final Extension each= new ProjectSetup("test").perTest();
		@Test void one() { events.add("body"); }
		@Test void two() { events.add("body"); }
	}

	static class SetupFails {
		@RegisterExtension final Extension resource= ExternalResourceExtension.perTest(
				() -> { throw setupFailure; }, () -> events.add("unexpected close"));
		@Test void neverReached() { events.add("unexpected body"); }
	}

	static class BodyFails {
		@RegisterExtension final Extension resource= new ProjectSetup("test").perTest();
		@Test void fails() { throw new AssertionError("body"); }
	}

	static class TeardownFails {
		@RegisterExtension static final Extension resource= ExternalResourceExtension.perClass(
				() -> events.add("setup"), () -> { throw teardownFailure; });
		@Test void runs() { events.add("body"); }
	}

	static class FailingTeardown extends ProjectSetup {
		FailingTeardown() { super("both"); }
		@Override protected void after() {
			super.after();
			throw new IllegalStateException("teardown failed");
		}
	}

	public static class LegacyDoubleFailure {
		@Rule public final ProjectSetup resource= new FailingTeardown();
		@org.junit.Test public void fails() { throw new AssertionError("body failed"); }
	}

	static class JupiterDoubleFailure {
		@RegisterExtension final Extension resource= new FailingTeardown().perTest();
		@Test void fails() { throw new AssertionError("body failed"); }
	}

	static class FailingSetup extends ProjectSetup {
		FailingSetup() { super("failed class"); }
		@Override protected void before() throws Throwable {
			super.before();
			throw setupFailure;
		}
	}

	public static class LegacyClassSetupFailure {
		@ClassRule public static final ProjectSetup resource= new FailingSetup();
		@org.junit.Test public void neverReached() { events.add("unexpected body"); }
	}

	static class JupiterClassSetupFailure {
		@RegisterExtension static final Extension resource= new FailingSetup().perClass();
		@Test void neverReached() { events.add("unexpected body"); }
	}

	@Test
	void bothEnginesPreserveBothFailuresAndCallTeardownOnce() {
		var legacy= run(LegacyDoubleFailure.class);
		assertEquals(1, legacy.getTestsFailedCount());
		var aggregate= assertInstanceOf(MultipleFailuresError.class, legacy.getFailures().get(0).getException());
		assertEquals(List.of("body failed", "teardown failed"),
				aggregate.getFailures().stream().map(Throwable::getMessage).toList());
		assertEquals(List.of("open both", "close both"), events);

		var migrated= run(JupiterDoubleFailure.class);
		assertEquals(1, migrated.getTestsFailedCount());
		var primary= assertInstanceOf(AssertionError.class, migrated.getFailures().get(0).getException());
		assertEquals("body failed", primary.getMessage());
		assertEquals(List.of("teardown failed"),
				java.util.Arrays.stream(primary.getSuppressed()).map(Throwable::getMessage).toList());
		assertEquals(List.of("open both", "close both"), events);
	}

	@Test
	void failedClassSetupSkipsBodyAndTeardownUnderBothEngines() {
		for (Class<?> consumer : List.of(LegacyClassSetupFailure.class, JupiterClassSetupFailure.class)) {
			var summary= run(consumer);
			assertEquals(1, summary.getContainersFailedCount());
			assertSame(setupFailure, summary.getFailures().get(0).getException());
			assertEquals(List.of("open failed class"), events);
		}
	}

	@Test
	void sameRuleTypeSupportsLegacyAndMigratedConsumersAtBothScopes() {
		for (Class<?> consumer : List.of(Legacy.class, Migrated.class)) {
			var summary= run(consumer);
			assertEquals(2, summary.getTestsSucceededCount());
			assertEquals(List.of("open class", "open test", "body", "close test",
					"open test", "body", "close test", "close class"), events);
		}
	}

	@Test
	void failedSetupDoesNotCallTeardownOrRunBody() {
		var summary= run(SetupFails.class);
		assertEquals(1, summary.getTestsFailedCount());
		assertSame(setupFailure, summary.getFailures().get(0).getException());
		assertEquals(List.of(), events);
	}

	@Test
	void successfulSetupIsClosedAfterTheTestFails() {
		var summary= run(BodyFails.class);
		assertEquals(1, summary.getTestsFailedCount());
		assertEquals(List.of("open test", "close test"), events);
	}

	@Test
	void failedClassTeardownIsReportedOnTheContainer() {
		var summary= run(TeardownFails.class);
		assertEquals(1, summary.getContainersFailedCount());
		assertSame(teardownFailure, summary.getFailures().get(0).getException());
		assertEquals(List.of("setup", "body"), events);
	}

	private static TestExecutionSummary run(Class<?> consumer) {
		events.clear();
		var listener= new SummaryGeneratingListener();
		var request= LauncherDiscoveryRequestBuilder.request().selectors(selectClass(consumer))
				.configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
		LauncherFactory.create().execute(request, listener);
		return listener.getSummary();
	}
}
