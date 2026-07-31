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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Declarative contract for one structured rewrite action.
 *
 * @param name stable action name
 * @param requiredArguments required named arguments
 * @param optionalArguments optional named arguments
 * @param description concise editor documentation
 */
public record RewriteActionSchema(String name, Set<String> requiredArguments,
		Set<String> optionalArguments, String description) {

	public RewriteActionSchema {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Action schema name must not be blank"); //$NON-NLS-1$
		}
		name= name.trim();
		requiredArguments= Set.copyOf(requiredArguments == null ? Set.of() : requiredArguments);
		optionalArguments= Set.copyOf(optionalArguments == null ? Set.of() : optionalArguments);
		Set<String> overlap= new LinkedHashSet<>(requiredArguments);
		overlap.retainAll(optionalArguments);
		if (!overlap.isEmpty()) {
			throw new IllegalArgumentException("Action arguments cannot be both required and optional: " + overlap); //$NON-NLS-1$
		}
		description= description == null ? "" : description.trim(); //$NON-NLS-1$
	}

	/** Validates one parsed action against this exact schema. */
	public void validate(StructuredRewriteAction action) {
		if (!name.equals(action.name())) {
			throw new IllegalArgumentException("Schema " + name + " cannot validate action " + action.name()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Set<String> missing= new LinkedHashSet<>(requiredArguments);
		missing.removeAll(action.arguments().keySet());
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Action " + name + " is missing arguments " + missing); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Set<String> allowed= new LinkedHashSet<>(requiredArguments);
		allowed.addAll(optionalArguments);
		Set<String> unknown= new LinkedHashSet<>(action.arguments().keySet());
		unknown.removeAll(allowed);
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("Action " + name + " has unknown arguments " + unknown); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
