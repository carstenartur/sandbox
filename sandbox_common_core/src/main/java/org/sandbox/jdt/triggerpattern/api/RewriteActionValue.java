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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value expression used by a {@link StructuredRewriteAction}.
 *
 * <p>Action values remain declarative. They can reference one pattern binding,
 * read a typed semantic-plan fact, or describe Java annotation expressions such
 * as class literals, names and arrays without embedding arbitrary Java code.</p>
 */
public sealed interface RewriteActionValue permits RewriteActionValue.Literal,
		RewriteActionValue.Binding, RewriteActionValue.PlanValue,
		RewriteActionValue.ClassLiteral, RewriteActionValue.Name,
		RewriteActionValue.ListValue {

	/** Creates a typed literal value. */
	static Literal literal(SemanticPlanValue value) {
		return new Literal(value);
	}

	/** Creates a reference to an exact pattern binding such as {@code $method}. */
	static Binding binding(String placeholder) {
		return new Binding(placeholder);
	}

	/** Reads one plan fact from the matched node. */
	static PlanValue planValue(String factName) {
		return new PlanValue(null, factName);
	}

	/** Reads one plan fact from an exact pattern binding. */
	static PlanValue planValue(String placeholder, String factName) {
		return new PlanValue(placeholder, factName);
	}

	/** Interprets the resolved nested value as a Java class literal. */
	static ClassLiteral classLiteral(RewriteActionValue typeName) {
		return new ClassLiteral(typeName);
	}

	/** Interprets the resolved nested value as a qualified Java name expression. */
	static Name name(RewriteActionValue qualifiedName) {
		return new Name(qualifiedName);
	}

	/** Creates an ordered immutable list expression. */
	static ListValue list(RewriteActionValue... values) {
		return new ListValue(values == null ? List.of() : Arrays.asList(values));
	}

	/** Typed literal transported directly into an action handler. */
	record Literal(SemanticPlanValue value) implements RewriteActionValue {
		public Literal {
			Objects.requireNonNull(value);
		}
	}

	/** Exact pattern-binding reference. */
	record Binding(String placeholder) implements RewriteActionValue {
		public Binding {
			placeholder= requirePlaceholder(placeholder);
		}
	}

	/** Typed semantic-plan fact reference. */
	record PlanValue(String placeholder, String factName) implements RewriteActionValue {
		public PlanValue {
			if (placeholder != null) {
				placeholder= requirePlaceholder(placeholder);
			}
			factName= requireName(factName, "Plan fact"); //$NON-NLS-1$
		}
	}

	/** Java class-literal expression such as {@code SomeType.class}. */
	record ClassLiteral(RewriteActionValue typeName) implements RewriteActionValue {
		public ClassLiteral {
			Objects.requireNonNull(typeName);
		}
	}

	/** Qualified Java name expression such as one enum constant. */
	record Name(RewriteActionValue qualifiedName) implements RewriteActionValue {
		public Name {
			Objects.requireNonNull(qualifiedName);
		}
	}

	/** Ordered list expression, primarily for annotation array values. */
	record ListValue(List<RewriteActionValue> values) implements RewriteActionValue {
		public ListValue {
			values= List.copyOf(values == null ? List.of() : values);
			values.forEach(Objects::requireNonNull);
		}
	}

	private static String requirePlaceholder(String value) {
		String placeholder= requireName(value, "Binding placeholder"); //$NON-NLS-1$
		if (!placeholder.startsWith("$") || placeholder.length() == 1) { //$NON-NLS-1$
			throw new IllegalArgumentException("Binding placeholder must start with '$': " + placeholder); //$NON-NLS-1$
		}
		return placeholder;
	}

	private static String requireName(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank"); //$NON-NLS-1$
		}
		return value.trim();
	}
}
