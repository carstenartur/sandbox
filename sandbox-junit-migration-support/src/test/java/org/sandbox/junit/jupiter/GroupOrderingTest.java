/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.sandbox.junit.jupiter.ExternalResourceExtension.resourceHook;
import static org.sandbox.junit.jupiter.GroupLaunch.counts;

import java.util.ArrayList;
import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.rules.ExternalResource;

@Execution(ExecutionMode.SAME_THREAD)
public class GroupOrderingTest {
	private static final List<String> events= new ArrayList<>();
	private static final RuntimeException setupFailure= new IllegalStateException("inner setup");
	private static boolean failInner;

	public static class Fixture extends ExternalResource implements AutoCloseable {
		final String name;
		final Runnable setup;

		Fixture(String name, Runnable setup) {
			this.name= name;
			this.setup= setup;
		}

		Fixture(String name) {
			this(name, () -> {});
		}

		@Override public void before() {
			events.add("open " + name);
			setup.run();
		}

		@Override public void after() { events.add("close " + name); }
		@Override public void close() { events.add("unwanted store close " + name); }
	}

	private static Fixture inner() {
		return new Fixture("inner", () -> {
			if (failInner) {
				throw setupFailure;
			}
		});
	}

	public static class Legacy {
		@ClassRule(order= 0) public static Fixture project= new Fixture("project", () -> Legacy.model= new Fixture("replaced class peer"));
		@ClassRule(order= 1) public static Fixture model= new Fixture("model");
		@Rule(order= 0) public Fixture outer= new Fixture("outer", () -> this.peer= new Fixture("replaced in setup"));
		@Rule(order= 1) public Fixture peer;

		public Legacy() { peer= inner(); }

		@org.junit.BeforeClass public static void beforeClass() { events.add("before class"); }
		@org.junit.Before public void before() { events.add("before test"); }
		@org.junit.Test public void body() {
			events.add("body");
			peer= new Fixture("replaced in body");
		}
		@org.junit.After public void after() { events.add("after test"); }
		@org.junit.AfterClass public static void afterClass() {
			events.add("after class");
			model= new Fixture("model");
		}
	}

	static class Migrated {
		@RegisterExtension static final Extension classHooks= ExternalResourceExtension.perClassGroup(Migrated.class,
				context -> List.of(resourceHook(() -> Migrated.project, Fixture::before, Fixture::after),
						resourceHook(() -> Migrated.model, Fixture::before, Fixture::after)));
		static Fixture project= new Fixture("project", () -> Migrated.model= new Fixture("replaced class peer"));
		static Fixture model= new Fixture("model");
		@RegisterExtension static final Extension testHooks= ExternalResourceExtension.perTestGroup(Migrated.class, context -> {
			var test= (Migrated) context.getRequiredTestInstance();
			return List.of(resourceHook(() -> test.outer, Fixture::before, Fixture::after),
					resourceHook(() -> test.peer, Fixture::before, Fixture::after));
		});
		Fixture outer= new Fixture("outer", () -> this.peer= new Fixture("replaced in setup"));
		Fixture peer;
		Migrated() { peer= inner(); }

		@org.junit.jupiter.api.BeforeAll static void beforeClass() { events.add("before class"); }
		@org.junit.jupiter.api.BeforeEach void before() { events.add("before test"); }
		@Test void body() {
			events.add("body");
			peer= new Fixture("replaced in body");
		}
		@org.junit.jupiter.api.AfterEach void after() { events.add("after test"); }
		@org.junit.jupiter.api.AfterAll static void afterClass() {
			events.add("after class");
			model= new Fixture("model");
		}
	}

	@Test void bothScopesMatchVintageAcrossUserLifecycleAndPeerReassignment() {
		compare(false, List.of("open project", "open model", "before class", "open outer", "open inner",
				"before test", "body", "after test", "close inner", "close outer", "after class", "close model", "close project"));
	}

	@Test void failingInnerSetupUnwindsOnlyAcquiredResources() {
		compare(true, List.of("open project", "open model", "before class", "open outer", "open inner",
				"close outer", "after class", "close model", "close project"));
	}

	private static void compare(boolean fail, List<String> expected) {
		failInner= fail;
		for (boolean vintage : List.of(true, false)) {
			events.clear();
			var summary= GroupLaunch.run(vintage ? Legacy.class : Migrated.class,
					vintage ? "junit-vintage" : "junit-jupiter", false);
			counts(summary, 1, fail ? 0 : 1, fail ? 1 : 0, 0, 2, 0);
			if (fail) {
				assertSame(setupFailure, summary.getFailures().getFirst().getException());
			}
			assertEquals(expected, events);
		}
	}
}
