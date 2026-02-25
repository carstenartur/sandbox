/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.mining.core.engine;

/**
 * Supported engine types for DSL sequence generation.
 *
 * @since 1.2.6
 */
public enum EngineType {

	/**
	 * LLM-based engine using a configurable AI provider.
	 */
	LLM;

	/**
	 * Case-insensitive lookup of engine type by name.
	 *
	 * @param name the engine type name string
	 * @return the matching EngineType
	 * @throws IllegalArgumentException if no matching type is found
	 */
	public static EngineType fromString(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Engine type name must not be null"); //$NON-NLS-1$
		}
		for (EngineType t : values()) {
			if (t.name().equalsIgnoreCase(name.trim())) {
				return t;
			}
		}
		throw new IllegalArgumentException("Unknown engine type: " + name); //$NON-NLS-1$
	}
}
