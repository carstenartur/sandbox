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

import java.util.List;
import java.util.Objects;

/** Immutable declaration model shared by core parsing and editor tooling. */
public record HintPredicateDefinition(String name, List<String> parameters,
		String expression, int lineNumber) {

	public HintPredicateDefinition {
		name= requireIdentifier(name, "predicate name"); //$NON-NLS-1$
		parameters= List.copyOf(Objects.requireNonNull(parameters, "parameters")); //$NON-NLS-1$
		for (String parameter : parameters) {
			if (parameter == null || parameter.length() < 2 || parameter.charAt(0) != '$') {
				throw new IllegalArgumentException("Predicate parameters must be placeholders: " + parameter); //$NON-NLS-1$
			}
			int end= parameter.endsWith("$") ? parameter.length() - 1 : parameter.length(); //$NON-NLS-1$
			if (end <= 1) {
				throw new IllegalArgumentException("Predicate parameters must be placeholders: " + parameter); //$NON-NLS-1$
			}
			requireIdentifier(parameter.substring(1, end), "predicate parameter"); //$NON-NLS-1$
		}
		if (parameters.stream().distinct().count() != parameters.size()) {
			throw new IllegalArgumentException("Predicate parameters must be unique: " + parameters); //$NON-NLS-1$
		}
		expression= Objects.requireNonNull(expression, "expression").trim(); //$NON-NLS-1$
		if (expression.isEmpty()) {
			throw new IllegalArgumentException("Predicate expression must not be blank"); //$NON-NLS-1$
		}
		if (lineNumber < 1) {
			throw new IllegalArgumentException("Predicate line number must be positive"); //$NON-NLS-1$
		}
	}

	/** Returns a compact signature suitable for outline and content-assist labels. */
	public String signature() {
		return name + '(' + String.join(", ", parameters) + ')'; //$NON-NLS-1$
	}

	private static String requireIdentifier(String value, String label) {
		String identifier= Objects.requireNonNull(value, label).trim();
		if (identifier.isEmpty() || !Character.isJavaIdentifierStart(identifier.charAt(0))) {
			throw new IllegalArgumentException("Invalid " + label + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
		}
		for (int index= 1; index < identifier.length(); index++) {
			if (!Character.isJavaIdentifierPart(identifier.charAt(index))) {
				throw new IllegalArgumentException("Invalid " + label + ": " + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return identifier;
	}
}
