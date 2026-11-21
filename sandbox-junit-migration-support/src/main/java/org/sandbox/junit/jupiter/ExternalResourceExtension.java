/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import java.util.Objects;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.function.Executable;

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

	private static final class Hooks {
		private final Executable before;
		private final Runnable after;

		Hooks(Executable before, Runnable after) {
			this.before= Objects.requireNonNull(before);
			this.after= Objects.requireNonNull(after);
		}

		// Scoped to the invocation and this registration, including repeated tests and nested classes.
		private ExtensionContext.Store store(ExtensionContext context) {
			return context.getStore(ExtensionContext.Namespace.create(this, context.getUniqueId()));
		}

		void start(ExtensionContext context) throws Throwable {
			before.execute();
			store(context).put("started", Boolean.TRUE);
		}

		void finish(ExtensionContext context) {
			if (store(context).remove("started") != null) {
				after.run();
			}
		}
	}

	private static void startForJupiter(Hooks hooks, ExtensionContext context) throws Exception {
		try {
			hooks.start(context);
		} catch (Exception | Error failure) {
			throw failure;
		} catch (Throwable failure) {
			// JUnit 4 allows any Throwable, including instances outside Exception and Error.
			throw originalFailure(failure);
		}
	}

	@SuppressWarnings("unchecked")
	private static <E extends Throwable> RuntimeException originalFailure(Throwable failure) throws E {
		throw (E) failure;
	}

	private record PerTest(Hooks hooks) implements BeforeEachCallback, AfterEachCallback {
		@Override public void beforeEach(ExtensionContext context) throws Exception { startForJupiter(hooks, context); }
		@Override public void afterEach(ExtensionContext context) { hooks.finish(context); }
	}

	private record PerClass(Hooks hooks) implements BeforeAllCallback, AfterAllCallback {
		@Override public void beforeAll(ExtensionContext context) throws Exception { startForJupiter(hooks, context); }
		@Override public void afterAll(ExtensionContext context) { hooks.finish(context); }
	}
}
