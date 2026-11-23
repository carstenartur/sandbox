/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import static org.junit.jupiter.api.Assertions.*;
import static org.sandbox.junit.jupiter.ExternalResourceExtension.*;
import static org.sandbox.junit.jupiter.GroupLaunch.counts;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;
import org.junit.runner.Description;

@Execution(ExecutionMode.SAME_THREAD)
class GroupInvocationTest {
	private static final List<String> events= new ArrayList<>();

	@BeforeEach void reset() { events.clear(); }

	static class Aliases {
		final Object shared= new Object();
		Object alias= shared;

		@RegisterExtension static final Extension empty= perClassGroup(Aliases.class, c -> {
			events.add("empty factory");
			return List.of();
		});

		@RegisterExtension final Extension group= perTestGroup(Aliases.class, c -> {
			events.add("test factory");
			var hooks= new ArrayList<ResourceHook>();
			var repeated= resourceHook(() -> {
				hooks.clear();
				events.add("select repeated");
				return shared;
			}, value -> {
				assertSame(shared, value);
				events.add("open repeated");
			}, value -> {
				assertSame(shared, value);
				events.add("close repeated");
			});
			hooks.add(repeated);
			hooks.add(resourceHook(() -> {
				events.add("select null");
				return null;
			}, o -> fail("null setup"), o -> fail("null cleanup")));
			hooks.add(resourceHook(() -> {
				events.add("select alias");
				return alias;
			}, o -> events.add("open alias"), o -> {
				assertSame(shared, o);
				events.add("close alias");
			}));
			hooks.add(repeated);
			return hooks;
		});

		@Test void replace() { alias= null; }
	}

	@Test void snapshotsMutableListBeforeSelectionAndRetainsEveryNonNullOccurrence() {
		counts(GroupLaunch.run(Aliases.class), 1, 1, 0, 0, 2, 0);
		assertEquals(List.of("empty factory", "test factory", "select repeated", "select null", "select alias",
				"select repeated", "open repeated", "open alias", "open repeated", "close repeated",
				"close alias", "close repeated"), events);
	}

	static class Base {
		String sameName= "base";
		String inherited= "inherited";
		@RegisterExtension static final Extension baseClass= perClassGroup(Base.class, c -> {
			throw new AssertionError("class factory invoked for another class");
		});
		@RegisterExtension static final Extension baseTest= perTestGroup(Base.class, c -> {
			throw new AssertionError("test factory invoked for another class");
		});
	}

	static class Child extends Base {
		String sameName= "child";
		@RegisterExtension static final Extension hooks= perTestGroup(Child.class, c -> {
			var child= (Child) c.getRequiredTestInstance();
			events.add("child factory");
			return List.of(resourceHook(() -> child.inherited, events::add, events::add),
					resourceHook(() -> ((Base) child).sameName, events::add, events::add),
					resourceHook(() -> child.sameName, events::add, events::add));
		});

		@Test void test() { events.add("child body"); }

		@Nested class Inner {
			@Test void test() { events.add("nested body"); }
		}
	}

	@Test void exactClassGuardProtectsInheritedFactoriesAndNestedInstances() {
		counts(GroupLaunch.run(Child.class), 2, 2, 0, 0, 3, 0);
		var trace= List.of("child factory", "inherited", "base", "child", "child body", "child", "base", "inherited");
		assertTrue(events.remove("nested body"));
		assertEquals(trace, events);
	}

	public static class AccessibleName extends TestName {
		public void start(ExtensionContext context) {
			starting(Description.createTestDescription(context.getRequiredTestClass(), context.getRequiredTestMethod().getName()));
		}
	}

	public static class LegacyName {
		@Rule(order= 0) public AccessibleName name= new AccessibleName();
		@Rule(order= 1) public ExternalResource peer= new ExternalResource() {
			@Override protected void before() { events.add("peer sees " + name.getMethodName()); }
		};

		@org.junit.Before public void before() { events.add("user sees " + name.getMethodName()); }
		@org.junit.Test public void example() { events.add("body"); }
	}

	static class MigratedName {
		final AccessibleName name= new AccessibleName();

		@RegisterExtension final Extension group= perTestGroup(MigratedName.class, c -> List.of(
				resourceHook(() -> name, receiver -> receiver.start(c), receiver -> {}),
				resourceHook(() -> this, test -> events.add("peer sees " + test.name.getMethodName()), test -> {})));

		@BeforeEach void before() { events.add("user sees " + name.getMethodName()); }
		@Test void example() { events.add("body"); }
	}

	@Test void explicitlyPositionedTestNameIsVisibleToPeerSetupAndUserBeforeEach() {
		for (boolean vintage : List.of(true, false)) {
			events.clear();
			var summary= GroupLaunch.run(vintage ? LegacyName.class : MigratedName.class,
					vintage ? "junit-vintage" : "junit-jupiter", false);
			counts(summary, 1, 1, 0, 0, 2, 0);
			assertEquals(List.of("peer sees example", "user sees example", "body"), events);
		}
	}

	static class Reentry {
		@RegisterExtension static final Extension group= perTestGroup(Reentry.class, context -> List.of(
				resourceHook(() -> "outer", s -> {}, events::add),
				resourceHook(() -> "inner", s -> {}, s -> {
					events.add(s);
					exit(context);
				})));
		static void exit(ExtensionContext context) {
			try {
				((AfterEachCallback) group).afterEach(context);
			} catch (Exception failure) {
				throw new AssertionError(failure);
			}
		}

		@Test void body() { events.add("body"); }
	}

	@Test void removesPendingStateBeforeReentrantExit() {
		counts(GroupLaunch.run(Reentry.class), 1, 1, 0, 0, 2, 0);
		assertEquals(List.of("body", "inner", "outer"), events);
	}

	@Execution(ExecutionMode.CONCURRENT)
	static class Concurrent {
		static final CyclicBarrier overlap= new CyclicBarrier(2);
		static final ConcurrentLinkedQueue<Concurrent> instances= new ConcurrentLinkedQueue<>();
		final List<String> trace= new ArrayList<>();

		static List<ResourceHook> fixtures(ExtensionContext c) {
			var test= (Concurrent) c.getRequiredTestInstance();
			var owner= Thread.currentThread();
			return List.of(resourceHook(() -> test, t -> t.trace.add("open"), t -> {
				assertSame(owner, Thread.currentThread());
				t.trace.add("close");
			}));
		}

		@RegisterExtension static final Extension first= perTestGroup(Concurrent.class, Concurrent::fixtures);
		@RegisterExtension static final Extension second= perTestGroup(Concurrent.class, Concurrent::fixtures);

		@RepeatedTest(2) void body() throws Exception {
			instances.add(this);
			trace.add("body");
			overlap.await(10, TimeUnit.SECONDS);
		}
	}

	@Test void concurrentInvocationsAndSameScopeRegistrationsDrainIndependentlyOnRepeatLaunch() {
		for (int launch= 0; launch < 2; launch++) {
			Concurrent.instances.clear();
			counts(GroupLaunch.run(Concurrent.class, "junit-jupiter", true), 2, 2, 0, 0, 3, 0);
			var tests= List.copyOf(Concurrent.instances);
			assertEquals(2, tests.size());
			assertNotSame(tests.getFirst(), tests.getLast());
			for (var test : tests) {
				assertEquals(List.of("open", "open", "body", "close", "close"), test.trace);
			}
		}
	}

	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	static class SingleInstance {
		@RegisterExtension static final Extension group= perClassGroup(SingleInstance.class, c -> List.of(
				resourceHook(() -> c.getRequiredTestInstance(), test -> events.add("class setup"), test -> events.add("class cleanup"))));
		SingleInstance() { events.add("constructor"); }
		@Test void body() { events.add("body"); }
	}

	@Test void perClassInstanceIsConstructedBeforeGroupSetup() {
		counts(GroupLaunch.run(SingleInstance.class), 1, 1, 0, 0, 2, 0);
		assertEquals(List.of("constructor", "class setup", "body", "class cleanup"), events);
	}
}
