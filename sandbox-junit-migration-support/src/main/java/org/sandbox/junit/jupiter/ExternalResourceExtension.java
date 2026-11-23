/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import java.util.Objects;
import java.util.function.Consumer;
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

	private static final class Hooks {
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

		// Scoped to the invocation and this registration, including repeated tests and nested classes.
		private ExtensionContext.Store store(ExtensionContext context) {
			return context.getStore(ExtensionContext.Namespace.create(this, context.getUniqueId()));
		}

		void start(ExtensionContext context) throws Throwable {
			Runnable cleanup= setup.get();
			if (cleanup != null) {
				// Store the action so Jupiter does not auto-close an AutoCloseable receiver.
				store(context).put("cleanup", cleanup);
			}
		}

		void finish(ExtensionContext context) {
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

	private record PerTest(Hooks hooks) implements BeforeEachCallback, AfterEachCallback {
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

	private record PerClass(Hooks hooks) implements BeforeAllCallback, AfterAllCallback {
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
