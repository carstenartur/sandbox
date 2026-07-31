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
package org.sandbox.jdt.triggerpattern.cleanup.actions;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.CoreException;

import org.sandbox.jdt.triggerpattern.api.StructuredRewriteAction;

/** Thread-safe runtime registry for structured AST action handlers. */
public final class StructuredRewriteActionRegistry {

	private static final StructuredRewriteActionRegistry INSTANCE=
			new StructuredRewriteActionRegistry();

	private final Map<String, StructuredRewriteActionHandler> handlers=
			new ConcurrentHashMap<>();

	private StructuredRewriteActionRegistry() {
		BuiltInStructuredRewriteActions.registerAll(this);
	}

	public static StructuredRewriteActionRegistry getInstance() {
		return INSTANCE;
	}

	/** Registers one non-shadowing action handler. */
	public void register(String name, StructuredRewriteActionHandler handler) {
		if (name == null || name.isBlank() || handler == null) {
			throw new IllegalArgumentException("Structured action name and handler are required"); //$NON-NLS-1$
		}
		StructuredRewriteActionHandler previous= handlers.putIfAbsent(name.trim(), handler);
		if (previous != null && previous != handler) {
			throw new IllegalArgumentException("Structured action handler already registered: " + name); //$NON-NLS-1$
		}
	}

	/** Removes an extension-contributed handler by name. */
	public void unregister(String name) {
		if (name != null) {
			handlers.remove(name);
		}
	}

	public Set<String> registeredNames() {
		return Set.copyOf(handlers.keySet());
	}

	/** Executes one action or fails closed when no runtime handler is available. */
	public void execute(StructuredRewriteAction action, StructuredRewriteActionContext context)
			throws CoreException {
		StructuredRewriteActionHandler handler= handlers.get(action.name());
		if (handler == null) {
			throw context.failure("No runtime handler registered for structured action " + action.name()); //$NON-NLS-1$
		}
		handler.apply(action, context);
	}
}
