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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable catalog of schema-validated structured rewrite actions. */
public final class RewriteActionCatalog {

	private static final RewriteActionCatalog STANDARD= builder()
			.register(schema("addAnnotation", Set.of("target", "annotation"), Set.of("value"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"Add a marker, single-value or array annotation")) //$NON-NLS-1$
			.register(schema("removeAnnotation", Set.of("target", "annotation"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Remove one validated annotation")) //$NON-NLS-1$
			.register(schema("addModifier", Set.of("target", "modifier"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Add one Java declaration modifier")) //$NON-NLS-1$
			.register(schema("removeModifier", Set.of("target", "modifier"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Remove one Java declaration modifier")) //$NON-NLS-1$
			.register(schema("removeSupertype", Set.of("target", "type"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Remove one binding-validated superclass or interface")) //$NON-NLS-1$
			.register(schema("replaceSupertype", Set.of("target", "type", "replacement"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"Replace one binding-validated superclass or interface")) //$NON-NLS-1$
			.register(schema("removeDeclaration", Set.of("target"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$
					"Remove one exact declaration")) //$NON-NLS-1$
			.register(schema("qualifyInvocation", Set.of("target", "owner"), Set.of(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Qualify one exact static method invocation")) //$NON-NLS-1$
			.build();

	private final Map<String, RewriteActionSchema> schemas;

	private RewriteActionCatalog(Map<String, RewriteActionSchema> schemas) {
		this.schemas= Map.copyOf(schemas);
	}

	/** Returns the canonical built-in action catalog. */
	public static RewriteActionCatalog standard() {
		return STANDARD;
	}

	/** Creates an empty catalog builder for tests and extension composition. */
	public static Builder builder() {
		return new Builder();
	}

	/** Creates a builder initialized from this immutable catalog. */
	public Builder toBuilder() {
		return new Builder(schemas);
	}

	public Optional<RewriteActionSchema> schema(String name) {
		return Optional.ofNullable(name == null ? null : schemas.get(name));
	}

	public Collection<RewriteActionSchema> schemas() {
		return schemas.values();
	}

	public Set<String> names() {
		return schemas.keySet();
	}

	/** Validates one parsed action or rejects an unknown action name. */
	public void validate(StructuredRewriteAction action) {
		RewriteActionSchema schema= schemas.get(action.name());
		if (schema == null) {
			throw new IllegalArgumentException("Unknown structured rewrite action " + action.name()); //$NON-NLS-1$
		}
		schema.validate(action);
	}

	private static RewriteActionSchema schema(String name, Set<String> required,
			Set<String> optional, String description) {
		return new RewriteActionSchema(name, required, optional, description);
	}

	/** Mutable builder that rejects action-name shadowing. */
	public static final class Builder {
		private final Map<String, RewriteActionSchema> schemas= new LinkedHashMap<>();

		private Builder() {
		}

		private Builder(Map<String, RewriteActionSchema> initial) {
			schemas.putAll(initial);
		}

		public Builder register(RewriteActionSchema schema) {
			RewriteActionSchema previous= schemas.putIfAbsent(schema.name(), schema);
			if (previous != null && !previous.equals(schema)) {
				throw new IllegalArgumentException("Conflicting schema for action " + schema.name()); //$NON-NLS-1$
			}
			return this;
		}

		public RewriteActionCatalog build() {
			return new RewriteActionCatalog(schemas);
		}
	}
}
