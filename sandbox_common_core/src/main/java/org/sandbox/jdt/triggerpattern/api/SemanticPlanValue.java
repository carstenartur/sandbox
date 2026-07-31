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

import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;

/**
 * Closed typed value model for semantic-plan facts and relation attributes.
 *
 * <p>The model deliberately contains only immutable transport values. Project
 * planners may define contract-specific fact names, while the shared hint
 * engine can compare values without depending on JUnit, JDT Core or JDT UI
 * concepts.</p>
 */
public sealed interface SemanticPlanValue permits SemanticPlanValue.StringValue,
		SemanticPlanValue.BooleanValue, SemanticPlanValue.IntegerValue,
		SemanticPlanValue.NodeValue, SemanticPlanValue.ListValue {

	/** Stable value kinds used for contract validation and diagnostics. */
	enum Kind {
		STRING,
		BOOLEAN,
		INTEGER,
		NODE,
		LIST
	}

	/** Returns the stable kind of this value. */
	Kind kind();

	/** Creates an exact text value. */
	static StringValue string(String value) {
		return new StringValue(value);
	}

	/** Creates a boolean value. */
	static BooleanValue bool(boolean value) {
		return new BooleanValue(value);
	}

	/** Creates a signed integer value. */
	static IntegerValue integer(long value) {
		return new IntegerValue(value);
	}

	/** Creates a reference to another stable semantic-plan node. */
	static NodeValue node(NodeKey value) {
		return new NodeValue(value);
	}

	/** Creates a homogeneous immutable list value. */
	static ListValue list(SemanticPlanValue... values) {
		return new ListValue(values == null ? List.of() : Arrays.asList(values));
	}

	/**
	 * Parses the literal forms accepted by typed plan guards.
	 *
	 * <p>Quoted values are strings, {@code true}/{@code false} are booleans and
	 * signed decimal values are integers. Remaining unquoted values are exact
	 * strings, which keeps enum-like contract values concise.</p>
	 */
	static SemanticPlanValue fromGuardLiteral(String literal) {
		String value= literal == null ? "" : literal.trim(); //$NON-NLS-1$
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
			return string(value.substring(1, value.length() - 1));
		}
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) { //$NON-NLS-1$ //$NON-NLS-2$
			return bool(Boolean.parseBoolean(value));
		}
		try {
			return integer(Long.parseLong(value));
		} catch (NumberFormatException e) {
			return string(value);
		}
	}

	/** Exact text fact. */
	record StringValue(String value) implements SemanticPlanValue {
		public StringValue {
			Objects.requireNonNull(value);
		}

		@Override
		public Kind kind() {
			return Kind.STRING;
		}
	}

	/** Boolean fact. */
	record BooleanValue(boolean value) implements SemanticPlanValue {
		@Override
		public Kind kind() {
			return Kind.BOOLEAN;
		}
	}

	/** Signed integer fact. */
	record IntegerValue(long value) implements SemanticPlanValue {
		@Override
		public Kind kind() {
			return Kind.INTEGER;
		}
	}

	/** Reference to another stable semantic-plan node. */
	record NodeValue(NodeKey value) implements SemanticPlanValue {
		public NodeValue {
			Objects.requireNonNull(value);
		}

		@Override
		public Kind kind() {
			return Kind.NODE;
		}
	}

	/** Homogeneous immutable list fact. */
	record ListValue(List<SemanticPlanValue> values) implements SemanticPlanValue {
		public ListValue {
			values= List.copyOf(values == null ? List.of() : values);
			Kind elementKind= null;
			for (SemanticPlanValue value : values) {
				Objects.requireNonNull(value);
				if (elementKind == null) {
					elementKind= value.kind();
				} else if (elementKind != value.kind()) {
					throw new IllegalArgumentException("Semantic plan lists must be homogeneous"); //$NON-NLS-1$
				}
			}
		}

		@Override
		public Kind kind() {
			return Kind.LIST;
		}

		/** Returns the element kind, or {@code null} for an empty list. */
		public Kind elementKind() {
			return values.isEmpty() ? null : values.get(0).kind();
		}
	}
}
