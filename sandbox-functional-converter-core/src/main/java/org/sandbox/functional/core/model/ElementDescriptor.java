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
package org.sandbox.functional.core.model;

/**
 * Describes the loop element (the variable being iterated).
 * 
 * <p>This class provides metadata about the loop variable, such as its name,
 * type, and whether it's declared as final.</p>
 * 
 * @since 1.0.0
 */
public class ElementDescriptor {
	
	private String variableName;
	private String typeName;
	private boolean isFinal;
	
	/**
	 * Default constructor.
	 */
	public ElementDescriptor() {
	}
	
	/**
	 * Creates a new ElementDescriptor.
	 * 
	 * @param variableName the variable name
	 * @param typeName the type name
	 * @param isFinal whether the variable is final
	 */
	public ElementDescriptor(String variableName, String typeName, boolean isFinal) {
		this.variableName = variableName;
		this.typeName = typeName;
		this.isFinal = isFinal;
	}
	
	/**
	 * Gets the variable name.
	 * 
	 * @return the variable name
	 */
	public String getVariableName() {
		return variableName;
	}
	
	/**
	 * Sets the variable name.
	 * 
	 * @param variableName the variable name
	 */
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}
	
	/**
	 * Gets the type name.
	 * 
	 * @return the type name
	 */
	public String getTypeName() {
		return typeName;
	}
	
	/**
	 * Sets the type name.
	 * 
	 * @param typeName the type name
	 */
	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}
	
	/**
	 * Checks if the variable is final.
	 * 
	 * @return true if the variable is final
	 */
	public boolean isFinal() {
		return isFinal;
	}
	
	/**
	 * Sets whether the variable is final.
	 * 
	 * @param isFinal true if the variable is final
	 */
	public void setFinal(boolean isFinal) {
		this.isFinal = isFinal;
	}
	
	@Override
	public String toString() {
		return "ElementDescriptor[variableName=" + variableName + ", typeName=" + typeName 
				+ ", isFinal=" + isFinal + "]";
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ElementDescriptor other = (ElementDescriptor) obj;
		return isFinal == other.isFinal
				&& java.util.Objects.equals(variableName, other.variableName)
				&& java.util.Objects.equals(typeName, other.typeName);
	}
	
	@Override
	public int hashCode() {
		return java.util.Objects.hash(variableName, typeName, isFinal);
	}
}
