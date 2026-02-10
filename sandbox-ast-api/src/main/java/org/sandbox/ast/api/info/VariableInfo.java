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

import java.util.Set;

/**
 * Immutable record representing a variable (field, local variable, parameter, record component).
 */
public record VariableInfo(
	String name,
	TypeInfo type,
	Set<Modifier> modifiers,
	boolean isField,
	boolean isParameter,
	boolean isRecordComponent
) {
	
	/**
	 * Creates a VariableInfo record.
	 * 
	 * @param name variable name
	 * @param type variable type
	 * @param modifiers set of modifiers
	 * @param isField true if this is a field
	 * @param isParameter true if this is a parameter
	 * @param isRecordComponent true if this is a record component
	 */
	public VariableInfo {
		if (name == null) {
			throw new IllegalArgumentException("Variable name cannot be null");
		}
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Variable name cannot be empty");
		}
		if (type == null) {
			throw new IllegalArgumentException("Variable type cannot be null");
		}
		modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
	}
	
	/**
	 * Checks if this variable has the specified modifier.
	 * 
	 * @param modifier modifier to check
	 * @return true if modifier is present
	 */
	public boolean hasModifier(Modifier modifier) {
		return modifiers.contains(modifier);
	}
	
	/**
	 * Checks if this variable is static.
	 * 
	 * @return true if static
	 */
	public boolean isStatic() {
		return hasModifier(Modifier.STATIC);
	}
	
	/**
	 * Checks if this variable is final.
	 * 
	 * @return true if final
	 */
	public boolean isFinal() {
		return hasModifier(Modifier.FINAL);
	}
	
	/**
	 * Checks if this variable is public.
	 * 
	 * @return true if public
	 */
	public boolean isPublic() {
		return hasModifier(Modifier.PUBLIC);
	}
	
	/**
	 * Checks if this variable is private.
	 * 
	 * @return true if private
	 */
	public boolean isPrivate() {
		return hasModifier(Modifier.PRIVATE);
	}
	
	/**
	 * Checks if this variable has the given type.
	 * 
	 * @param typeName qualified type name
	 * @return true if type matches
	 */
	public boolean hasType(String typeName) {
		return type.is(typeName);
	}
	
	/**
	 * Checks if this variable has the given type.
	 * 
	 * @param clazz class to compare
	 * @return true if type matches
	 */
	public boolean hasType(Class<?> clazz) {
		return type.is(clazz);
	}
	
	/**
	 * Returns true if this is a local variable (not a field, parameter, or record component).
	 * 
	 * @return true if local variable
	 */
	public boolean isLocalVariable() {
		return !isField && !isParameter && !isRecordComponent;
	}
	
	/**
	 * Builder for creating VariableInfo instances.
	 */
	public static class Builder {
		private String name;
		private TypeInfo type;
		private Set<Modifier> modifiers = Set.of();
		private boolean isField;
		private boolean isParameter;
		private boolean isRecordComponent;
		
		/**
		 * Creates a builder with the given name.
		 * 
		 * @param name variable name
		 * @return new Builder
		 */
		public static Builder named(String name) {
			Builder builder = new Builder();
			builder.name = name;
			return builder;
		}
		
		/**
		 * Sets the variable type.
		 * 
		 * @param type variable type
		 * @return this builder
		 */
		public Builder type(TypeInfo type) {
			this.type = type;
			return this;
		}
		
		/**
		 * Sets the modifiers.
		 * 
		 * @param modifiers set of modifiers
		 * @return this builder
		 */
		public Builder modifiers(Set<Modifier> modifiers) {
			this.modifiers = modifiers;
			return this;
		}
		
		/**
		 * Marks this as a field.
		 * 
		 * @return this builder
		 */
		public Builder field() {
			this.isField = true;
			return this;
		}
		
		/**
		 * Marks this as a parameter.
		 * 
		 * @return this builder
		 */
		public Builder parameter() {
			this.isParameter = true;
			return this;
		}
		
		/**
		 * Marks this as a record component.
		 * 
		 * @return this builder
		 */
		public Builder recordComponent() {
			this.isRecordComponent = true;
			return this;
		}
		
		/**
		 * Builds the VariableInfo instance.
		 * 
		 * @return new VariableInfo
		 */
		public VariableInfo build() {
			return new VariableInfo(name, type, modifiers, isField, isParameter, isRecordComponent);
		}
	}
}
