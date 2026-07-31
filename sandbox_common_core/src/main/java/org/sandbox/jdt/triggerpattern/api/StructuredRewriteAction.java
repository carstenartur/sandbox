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

/**
 * One schema-validated structured rewrite action attached to a rewrite
 * alternative.
 *
 * @param name stable registered action name
 * @param arguments immutable named argument expressions
 * @param lineNumber one-based source line used for diagnostics
 */
public record StructuredRewriteAction(String name, Map<String, RewriteActionValue> arguments,
		int lineNumber) {

	public StructuredRewriteAction {
		name= requireIdentifier(name, "Action name"); //$NON-NLS-1$
		Map<String, RewriteActionValue> copy= new LinkedHashMap<>();
		if (arguments != null) {
			arguments.forEach((argumentName, value) -> copy.put(
					requireIdentifier(argumentName, "Action argument"), Objects.requireNonNull(value))); //$NON-NLS-1$
		}
		arguments= Map.copyOf(copy);
		if (lineNumber < 1) {
			throw new IllegalArgumentException("Action line number must be positive"); //$NON-NLS-1$
		}
	}

	/** Returns one required argument or fails with an actionable diagnostic. */
	public RewriteActionValue requiredArgument(String argumentName) {
		RewriteActionValue value= arguments.get(argumentName);
		if (value == null) {
			throw new IllegalArgumentException("Action " + name + " requires argument " + argumentName); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return value;
	}

	private static String requireIdentifier(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " must not be blank"); //$NON-NLS-1$
		}
		String identifier= value.trim();
		if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
			throw new IllegalArgumentException(label + " is not an identifier: " + identifier); //$NON-NLS-1$
		}
		for (int index= 1; index < identifier.length(); index++) {
			if (!Character.isJavaIdentifierPart(identifier.charAt(index))) {
				throw new IllegalArgumentException(label + " is not an identifier: " + identifier); //$NON-NLS-1$
			}
		}
		return identifier;
	}
}
