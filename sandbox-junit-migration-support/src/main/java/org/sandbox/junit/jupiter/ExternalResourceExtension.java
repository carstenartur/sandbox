/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.opentest4j.MultipleFailuresError;

/** Registers the setup and teardown hooks of an existing fixture at a Jupiter lifecycle boundary. */
public final class ExternalResourceExtension {
	private ExternalResourceExtension() {}

	/** Register on an instance field for the lifecycle of a JUnit 4 {@code @Rule}. */
	public static Extension perTest(Executable before, Runnable after) {
		return new PerTest(new Hooks(before, after));
	}

	/** Register on a static field for the lifecycle of a JUnit 4 {@code @ClassRule}. */
	public static Extension perClass(Executable before, Runnable after) {
		return new PerClass(new Hooks(before, after));
	}

	/**
	 * Select the fixture once at each test's setup boundary. A null fixture skips
	 * both hooks. After successful setup, teardown uses the selected fixture.
	 * The supplier and both callbacks must be non-null.
	 */
	public static <T> Extension perTest(Supplier<? extends T> fixture,
			ThrowingConsumer<? super T> before, Consumer<? super T> after) {
		return new PerTest(Hooks.deferred(fixture, before, after));
	}

	/**
	 * Select the fixture once at class setup. A null fixture skips both hooks.
	 * After successful setup, teardown uses the selected fixture.
	 * The supplier and both callbacks must be non-null.
	 */
	public static <T> Extension perClass(Supplier<? extends T> fixture,
			ThrowingConsumer<? super T> before, Consumer<? super T> after) {
		return new PerClass(Hooks.deferred(fixture, before, after));
	}

	/** A deferred fixture selection and its hooks, with no per-invocation state. */
	public static final class ResourceHook {
		private final Supplier<Selected> select;

		private ResourceHook(Supplier<Selected> select) {
			this.select= select;
		}
	}

	private record Selected(Executable before, Runnable after) {}

	/** Describe one entry in an explicitly ordered group. A null receiver skips both hooks. */
	public static <T> ResourceHook resourceHook(Supplier<? extends T> fixture,
			ThrowingConsumer<? super T> before, Consumer<? super T> after) {
		Objects.requireNonNull(fixture);
		Objects.requireNonNull(before);
		Objects.requireNonNull(after);
		return new ResourceHook(() -> {
			T receiver= fixture.get();
			return receiver == null ? null : new Selected(() -> before.accept(receiver), () -> after.accept(receiver));
		});
	}

	/** Capture all receivers before test setup for the given concrete class. Supply outer hooks first. */
	public static Extension perTestGroup(Class<?> exactTestClass,
			Function<? super ExtensionContext, ? extends List<ResourceHook>> factory) {
		return new PerTest(new Group(exactTestClass, factory));
	}

	/** Capture all receivers before class setup for the given concrete class. Supply outer hooks first. */
	public static Extension perClassGroup(Class<?> exactTestClass,
			Function<? super ExtensionContext, ? extends List<ResourceHook>> factory) {
		return new PerClass(new Group(exactTestClass, factory));
	}

	private interface Lifecycle {
		void start(ExtensionContext context) throws Throwable;
		void finish(ExtensionContext context);

		// Keep inherited stores, repetitions and distinct registrations isolated.
		default ExtensionContext.Store store(ExtensionContext context) {
			return context.getStore(ExtensionContext.Namespace.create(this, context.getUniqueId()));
		}
	}

	private static final class Group implements Lifecycle {
		private final Class<?> exactTestClass;
		private final Function<? super ExtensionContext, ? extends List<ResourceHook>> factory;

		Group(Class<?> exactTestClass, Function<? super ExtensionContext, ? extends List<ResourceHook>> factory) {
			this.exactTestClass= Objects.requireNonNull(exactTestClass);
			this.factory= Objects.requireNonNull(factory);
		}

		@Override public void start(ExtensionContext context) throws Throwable {
			if (context.getRequiredTestClass() != exactTestClass) {
				return;
			}
			var hooks= List.copyOf(factory.apply(context));
			var selected= hooks.stream().map(hook -> hook.select.get()).filter(Objects::nonNull).toList();
			var pending= new Pending(selected.size());
			store(context).put("cleanup", pending);
			try {
				for (var entry : selected) {
					entry.before().execute();
					pending.actions.add(entry.after());
				}
			} catch (OutOfMemoryError fatal) {
				// Jupiter can escape without afterEach/afterAll for this error.
				try {
					finish(context);
				} catch (Throwable cleanup) {
					if (cleanup != fatal) {
						fatal.addSuppressed(cleanup);
					}
				}
				throw fatal;
			}
		}

		@Override public void finish(ExtensionContext context) {
			if (context.getRequiredTestClass() == exactTestClass) {
				var pending= store(context).remove("cleanup", Pending.class);
				if (pending != null) {
					pending.drain();
				}
			}
		}
	}

	private static final class Pending {
		final List<Runnable> actions;

		Pending(int selectedCount) {
			actions= new ArrayList<>(selectedCount);
		}

		void drain() {
			List<Throwable> failures= new ArrayList<>();
			for (Runnable action : actions.reversed()) {
				try {
					action.run();
				} catch (OutOfMemoryError fatal) {
					if (!failures.isEmpty()) {
						fatal.addSuppressed(cleanupFailure(failures));
					}
					throw fatal;
				} catch (Throwable failure) {
					failures.add(failure);
				}
			}
			if (!failures.isEmpty()) {
				rethrowOriginalFailure(cleanupFailure(failures));
			}
		}

		private static Throwable cleanupFailure(List<Throwable> failures) {
			return failures.size() == 1 ? failures.getFirst() : new MultipleFailuresError("Fixture cleanup", failures);
		}
	}

	private static final class Hooks implements Lifecycle {
		private final ThrowingSupplier<Runnable> setup;

		Hooks(Executable before, Runnable after) {
			Objects.requireNonNull(before);
			Runnable cleanup= Objects.requireNonNull(after)::run;
			this.setup= () -> {
				before.execute();
				return cleanup;
			};
		}

		private Hooks(ThrowingSupplier<Runnable> setup) {
			this.setup= setup;
		}

		static <T> Hooks deferred(Supplier<? extends T> fixture,
				ThrowingConsumer<? super T> before, Consumer<? super T> after) {
			Objects.requireNonNull(fixture);
			Objects.requireNonNull(before);
			Objects.requireNonNull(after);
			return new Hooks(() -> {
				T selected= fixture.get();
				if (selected == null) {
					return null;
				}
				before.accept(selected);
				return () -> after.accept(selected);
			});
		}

		@Override public void start(ExtensionContext context) throws Throwable {
			Runnable cleanup= setup.get();
			if (cleanup != null) {
				// Store the action so Jupiter does not auto-close an AutoCloseable receiver.
				store(context).put("cleanup", cleanup);
			}
		}

		@Override public void finish(ExtensionContext context) {
			Runnable cleanup= store(context).remove("cleanup", Runnable.class);
			if (cleanup != null) {
				cleanup.run();
			}
		}
	}

	// JUnit 4 allows any Throwable, including instances outside Exception and Error.
	@SuppressWarnings("unchecked")
	private static <E extends Throwable> void rethrowOriginalFailure(Throwable failure) throws E {
		throw (E) failure;
	}

	private record PerTest(Lifecycle hooks) implements BeforeEachCallback, AfterEachCallback {
		@Override
		public void beforeEach(ExtensionContext context) throws Exception {
			try {
				hooks.start(context);
			} catch (Exception | Error failure) {
				throw failure;
			} catch (Throwable failure) {
				ExternalResourceExtension.<Exception>rethrowOriginalFailure(failure);
			}
		}

		@Override public void afterEach(ExtensionContext context) { hooks.finish(context); }
	}

	private record PerClass(Lifecycle hooks) implements BeforeAllCallback, AfterAllCallback {
		@Override
		public void beforeAll(ExtensionContext context) throws Exception {
			try {
				hooks.start(context);
			} catch (Exception | Error failure) {
				throw failure;
			} catch (Throwable failure) {
				ExternalResourceExtension.<Exception>rethrowOriginalFailure(failure);
			}
		}

		@Override public void afterAll(ExtensionContext context) { hooks.finish(context); }
	}
}
