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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/**
 * One ordered directed relation between two stable semantic-plan nodes.
 *
 * <p>Relations are stored in plan order and may occur more than once. This is
 * intentional: suite construction and runtime trees can contain duplicate test
 * occurrences, while relation attributes can record order, matrix coordinates
 * or other contract-specific data without adding project concepts to the core
 * model.</p>
 */
public record SemanticPlanRelation(String kind, NodeKey source, NodeKey target,
		Map<String, SemanticPlanValue> attributes) {

	/** Creates a defensive immutable relation. */
	public SemanticPlanRelation {
		kind= requireName(kind, "Relation kind"); //$NON-NLS-1$
		Objects.requireNonNull(source);
		Objects.requireNonNull(target);
		Map<String, SemanticPlanValue> copy= new LinkedHashMap<>();
		if (attributes != null) {
			attributes.forEach((name, value) -> copy.put(requireName(name, "Relation attribute"), //$NON-NLS-1$
					Objects.requireNonNull(value)));
		}
		attributes= Map.copyOf(copy);
	}

	/** Creates a relation without attributes. */
	public SemanticPlanRelation(String kind, NodeKey source, NodeKey target) {
		this(kind, source, target, Map.of());
	}

	/** Returns one typed relation attribute. */
	public Optional<SemanticPlanValue> attribute(String name) {
		return Optional.ofNullable(attributes.get(name));
	}

	/** Returns whether an attribute equals the supplied typed value. */
	public boolean hasAttribute(String name, SemanticPlanValue expected) {
		return expected != null && expected.equals(attributes.get(name));
	}

	private static String requireName(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank"); //$NON-NLS-1$
		}
		return value.trim();
	}
}
