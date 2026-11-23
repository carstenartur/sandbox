/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.internal.junit.runner.ITestReference;
import org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public class InheritedTestsFilterTest {

	interface Contract {
		@Test default void interfaceTest() {}
	}

	static class Base implements Contract {
		@Test final void inherited() {}
		@Test void overridden() {}
		@RepeatedTest(2) void inheritedTemplate() {}
	}

	@IgnoreInheritedTests
	static class Selected extends Base {
		@Override @Test void overridden() {}
		@Test void local() {}
	}

	static class Unmarked extends Base {}

	static class Descendant extends Selected {
		@Test void youngest() {}
	}

	static class Outer {
		@Nested @IgnoreInheritedTests
		class Inner extends Base {
			@Test void nested() {}
		}
	}

	@org.eclipse.jdt.ui.tests.IgnoreInheritedTests
	public static class Vintage extends LegacyBase {
		@org.junit.Test public void ownLegacyMethod() {}
	}

	@IgnoreInheritedTests
	public static class Mixed extends LegacyBase {
		@Test void directlyJupiter() {}
		@org.junit.Test public void directlyVintage() {}
	}

	@IgnoreInheritedTests
	public static class JupiterLeaf extends LegacyBase {
		@Test void directlyJupiter() {}
	}

	public static class LegacyBase {
		@org.junit.Test public void inheritedLegacyMethod() {}
	}

	@Test
	void excludesInheritedMethodsAndTemplatesFromThePlanAndExecution() {
		assertPlanAndRun(Selected.class, Set.of("overridden", "local"), 2);
		assertPlanAndRun(Descendant.class, Set.of("youngest"), 1);
		assertPlanAndRun(Outer.class, Set.of("nested"), 1);
	}

	@Test
	void keepsUnmarkedJupiterAndVintageDiscoveryIntact() {
		assertPlanAndRun(Unmarked.class,
				Set.of("interfaceTest", "inherited", "overridden", "inheritedTemplate"), 5);
		assertPlanAndRun(Vintage.class, Set.of("inheritedLegacyMethod", "ownLegacyMethod"), 2);
	}

	@Test
	void migratedLeafCanExtendAnUnmigratedBaseWithBothEnginesActive() {
		assertPlanAndRun(Mixed.class, Set.of("directlyJupiter", "directlyVintage"), 2);
		assertPlanAndRun(JupiterLeaf.class, Set.of("directlyJupiter"), 1);
		assertPlanAndRun(LegacyBase.class, Set.of("inheritedLegacyMethod"), 1);
	}

	@Test
	void eclipseJUnit5LoaderReportsOnlyDirectMethods() {
		var loader= new JUnit5TestLoader();
		var references= loader.loadTests(new Class<?>[] { Selected.class, Vintage.class },
				null, null, null, new String[2][], null, null);
		assertEquals(List.of(2, 2), Arrays.stream(references).map(ITestReference::countTestCases).toList());
	}

	private static void assertPlanAndRun(Class<?> fixture, Set<String> methods, long executions) {
		var launcher= LauncherFactory.create();
		var request= LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build();
		var plan= launcher.discover(request);
		var methodNames= plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream())
				.map(TestIdentifier::getSource).flatMap(java.util.Optional::stream)
				.filter(MethodSource.class::isInstance).map(MethodSource.class::cast)
				.map(MethodSource::getMethodName).collect(Collectors.toSet());
		assertEquals(methods, methodNames);
		var listener= new SummaryGeneratingListener();
		launcher.execute(plan, listener);
		var summary= listener.getSummary();
		assertEquals(executions, summary.getTestsFoundCount());
		assertEquals(executions, summary.getTestsSucceededCount());
		assertEquals(0, summary.getTestsSkippedCount());
	}
}
