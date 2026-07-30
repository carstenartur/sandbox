/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.Map;
import java.util.Objects;

/** Thread-confined semantic plan scope used while invoking the existing hint backend. */
public final class SemanticRewritePlanContext {

	private record State(SemanticRewritePlan plan, Map<String, String> compilerOptions) {
		State {
			plan= Objects.requireNonNullElseGet(plan, SemanticRewritePlan::empty);
			compilerOptions= Map.copyOf(compilerOptions == null ? Map.of() : compilerOptions);
		}
	}

	private static final ThreadLocal<State> CURRENT=
			ThreadLocal.withInitial(() -> new State(SemanticRewritePlan.empty(), Map.of()));

	private SemanticRewritePlanContext() {
	}

	/** Returns the plan active for the current synchronous hint-processing call. */
	public static SemanticRewritePlan current() {
		return CURRENT.get().plan();
	}

	/** Returns the compiler options active in the current plan-aware scope. */
	public static Map<String, String> currentCompilerOptions() {
		return CURRENT.get().compilerOptions();
	}

	/** Installs a plan with no additional compiler options. */
	public static Scope install(SemanticRewritePlan plan) {
		return install(plan, Map.of());
	}

	/** Installs a plan and compiler options until the returned scope is closed. */
	public static Scope install(SemanticRewritePlan plan, Map<String, String> compilerOptions) {
		State previous= CURRENT.get();
		CURRENT.set(new State(plan, compilerOptions));
		return new Scope(previous);
	}

	/** Restores the previous scope exactly once. */
	public static final class Scope implements AutoCloseable {
		private final State previous;
		private boolean closed;

		private Scope(State previous) {
			this.previous= previous;
		}

		@Override
		public void close() {
			if (!closed) {
				CURRENT.set(previous);
				closed= true;
			}
		}
	}
}
