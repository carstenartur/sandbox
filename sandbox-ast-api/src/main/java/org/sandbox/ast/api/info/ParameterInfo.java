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
package org.sandbox.ast.api.info;

/**
 * Immutable record representing a method parameter.
 * 
 * @param name the parameter name
 * @param type the parameter type
 * @param varargs true if this is a varargs parameter
 */
public record ParameterInfo(String name, TypeInfo type, boolean varargs) {
	
	/**
	 * Creates a new parameter info.
	 * 
	 * @param name parameter name (must not be null)
	 * @param type parameter type (must not be null)
	 * @param varargs true if varargs parameter
	 */
	public ParameterInfo {
		if (name == null) {
			throw new IllegalArgumentException("Parameter name cannot be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("Parameter type cannot be null");
		}
	}
	
	/**
	 * Creates a parameter without varargs.
	 * 
	 * @param name parameter name
	 * @param type parameter type
	 * @return new ParameterInfo
	 */
	public static ParameterInfo of(String name, TypeInfo type) {
		return new ParameterInfo(name, type, false);
	}
	
	/**
	 * Creates a varargs parameter.
	 * 
	 * @param name parameter name
	 * @param type parameter type
	 * @return new ParameterInfo with varargs=true
	 */
	public static ParameterInfo varargs(String name, TypeInfo type) {
		return new ParameterInfo(name, type, true);
	}
}
