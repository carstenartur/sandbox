/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

final class GroupLaunch {
	private GroupLaunch() {}

	static TestExecutionSummary run(Class<?> fixture) {
		return run(fixture, "junit-jupiter", false);
	}

	static TestExecutionSummary run(Class<?> fixture, String engine, boolean parallel) {
		var listener= new SummaryGeneratingListener();
		var request= LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture))
				.filters(EngineFilter.includeEngines(engine))
				.configurationParameter("junit.jupiter.execution.parallel.enabled", Boolean.toString(parallel))
				.configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
				.configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
				.build();
		LauncherFactory.create().execute(request, listener);
		return listener.getSummary();
	}

	static void counts(TestExecutionSummary summary, int found, int passed, int failed, int aborted,
			int containers, int failedContainers) {
		assertEquals(found, summary.getTestsFoundCount());
		assertEquals(passed + failed + aborted, summary.getTestsStartedCount());
		assertEquals(passed, summary.getTestsSucceededCount());
		assertEquals(failed, summary.getTestsFailedCount());
		assertEquals(aborted, summary.getTestsAbortedCount());
		assertEquals(0, summary.getTestsSkippedCount());
		assertEquals(containers, summary.getContainersFoundCount());
		assertEquals(containers, summary.getContainersStartedCount());
		assertEquals(containers - failedContainers, summary.getContainersSucceededCount());
		assertEquals(failedContainers, summary.getContainersFailedCount());
		assertEquals(0, summary.getContainersSkippedCount());
		assertEquals(0, summary.getContainersAbortedCount());
	}
}
