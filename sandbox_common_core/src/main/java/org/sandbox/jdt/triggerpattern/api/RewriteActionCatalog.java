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

	private static final Set<String> TARGET= Set.of("target"); //$NON-NLS-1$

	private static final RewriteActionCatalog STANDARD= builder()
			.register(schema("addAnnotation", Set.of("annotation"), Set.of("target", "value"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"Add a marker, single-value or array annotation to the primary match or an explicit target")) //$NON-NLS-1$
			.register(schema("removeAnnotation", Set.of("annotation"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Remove one validated annotation from the primary match or an explicit target")) //$NON-NLS-1$
			.register(schema("addModifier", Set.of("modifier"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Add one Java declaration modifier")) //$NON-NLS-1$
			.register(schema("removeModifier", Set.of("modifier"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Remove one Java declaration modifier")) //$NON-NLS-1$
			.register(schema("removeSupertype", Set.of("type"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Remove one binding-validated superclass or interface")) //$NON-NLS-1$
			.register(schema("replaceSupertype", Set.of("type", "replacement"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"Replace one binding-validated superclass or interface")) //$NON-NLS-1$
			.register(schema("removeDeclaration", Set.of(), TARGET, //$NON-NLS-1$
					"Remove the primary matched declaration or an explicit target")) //$NON-NLS-1$
			.register(schema("qualifyInvocation", Set.of("owner"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Qualify one exact static method invocation")) //$NON-NLS-1$
			.register(schema("renameDeclaration", Set.of("name"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Rename one exact planned method or field declaration")) //$NON-NLS-1$
			.register(schema("replaceFieldType", Set.of("type"), TARGET, //$NON-NLS-1$ //$NON-NLS-2$
					"Replace the type of one single-fragment planned field")) //$NON-NLS-1$
			.register(schema("addParameter", Set.of("type", "name"), Set.of("target", "index"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"Add one parameter to an exact planned method or constructor")) //$NON-NLS-1$
			.register(schema("removeParameter", Set.of(), Set.of("target", "name", "index"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"Remove one parameter selected by stable name or, as an escape hatch, index")) //$NON-NLS-1$
			.register(schema("replaceParameterType", Set.of("type"), Set.of("target", "name", "index"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					"Replace one parameter type selected by stable name or index")) //$NON-NLS-1$
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
