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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable record representing a method.
 * Provides fluent query methods for method checking and comparisons.
 */
public record MethodInfo(
	String name,
	TypeInfo declaringType,
	TypeInfo returnType,
	List<ParameterInfo> parameters,
	Set<Modifier> modifiers
) {
	
	/**
	 * Creates a MethodInfo record.
	 * 
	 * @param name method name
	 * @param declaringType type that declares this method
	 * @param returnType return type
	 * @param parameters list of parameters
	 * @param modifiers set of modifiers
	 */
	public MethodInfo {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Method name cannot be null or empty");
		}
		if (returnType == null) {
			throw new IllegalArgumentException("Return type cannot be null");
		}
		parameters = parameters == null ? List.of() : List.copyOf(parameters);
		modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers);
	}
	
	/**
	 * Checks if this is Math.max().
	 * 
	 * @return true if Math.max
	 */
	public boolean isMathMax() {
		return name.equals("max") && 
			   declaringType != null && 
			   declaringType.is("java.lang.Math") &&
			   parameters.size() == 2;
	}
	
	/**
	 * Checks if this is Math.min().
	 * 
	 * @return true if Math.min
	 */
	public boolean isMathMin() {
		return name.equals("min") && 
			   declaringType != null && 
			   declaringType.is("java.lang.Math") &&
			   parameters.size() == 2;
	}
	
	/**
	 * Checks if this is List.add().
	 * 
	 * @return true if List.add
	 */
	public boolean isListAdd() {
		return name.equals("add") && 
			   declaringType != null && 
			   declaringType.isList() &&
			   parameters.size() == 1;
	}
	
	/**
	 * Checks if this is List.get().
	 * 
	 * @return true if List.get
	 */
	public boolean isListGet() {
		return name.equals("get") && 
			   declaringType != null && 
			   declaringType.isList() &&
			   parameters.size() == 1;
	}
	
	/**
	 * Checks if this is Collection.stream().
	 * 
	 * @return true if Collection.stream
	 */
	public boolean isCollectionStream() {
		return name.equals("stream") && 
			   declaringType != null && 
			   declaringType.isCollection() &&
			   parameters.isEmpty();
	}
	
	/**
	 * Checks if this method has the given signature (name + parameter types).
	 * 
	 * @param methodName method name
	 * @param parameterTypes parameter type names (qualified)
	 * @return true if signature matches
	 */
	public boolean hasSignature(String methodName, String... parameterTypes) {
		if (!name.equals(methodName)) {
			return false;
		}
		if (parameters.size() != parameterTypes.length) {
			return false;
		}
		for (int i = 0; i < parameterTypes.length; i++) {
			if (!parameters.get(i).type().is(parameterTypes[i])) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Checks if this method has the specified modifier.
	 * 
	 * @param modifier modifier to check
	 * @return true if modifier is present
	 */
	public boolean hasModifier(Modifier modifier) {
		return modifiers.contains(modifier);
	}
	
	/**
	 * Checks if this method is static.
	 * 
	 * @return true if static
	 */
	public boolean isStatic() {
		return hasModifier(Modifier.STATIC);
	}
	
	/**
	 * Checks if this method is public.
	 * 
	 * @return true if public
	 */
	public boolean isPublic() {
		return hasModifier(Modifier.PUBLIC);
	}
	
	/**
	 * Checks if this method is private.
	 * 
	 * @return true if private
	 */
	public boolean isPrivate() {
		return hasModifier(Modifier.PRIVATE);
	}
	
	/**
	 * Checks if this method is abstract.
	 * 
	 * @return true if abstract
	 */
	public boolean isAbstract() {
		return hasModifier(Modifier.ABSTRACT);
	}
	
	/**
	 * Checks if this method is final.
	 * 
	 * @return true if final
	 */
	public boolean isFinal() {
		return hasModifier(Modifier.FINAL);
	}
	
	/**
	 * Returns the signature as a string (for debugging).
	 * 
	 * @return method signature
	 */
	public String signature() {
		String params = parameters.stream()
			.map(p -> p.type().simpleName())
			.collect(Collectors.joining(", "));
		return String.format("%s(%s)", name, params);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof MethodInfo other)) return false;
		return Objects.equals(name, other.name) &&
			   Objects.equals(declaringType, other.declaringType) &&
			   Objects.equals(returnType, other.returnType) &&
			   Objects.equals(parameters, other.parameters);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(name, declaringType, returnType, parameters);
	}
	
	/**
	 * Builder for creating MethodInfo instances.
	 */
	public static class Builder {
		private String name;
		private TypeInfo declaringType;
		private TypeInfo returnType;
		private List<ParameterInfo> parameters = List.of();
		private Set<Modifier> modifiers = Set.of();
		
		/**
		 * Creates a builder with the given method name.
		 * 
		 * @param name method name
		 * @return new Builder
		 */
		public static Builder named(String name) {
			Builder builder = new Builder();
			builder.name = name;
			return builder;
		}
		
		/**
		 * Sets the declaring type.
		 * 
		 * @param declaringType declaring type
		 * @return this builder
		 */
		public Builder declaringType(TypeInfo declaringType) {
			this.declaringType = declaringType;
			return this;
		}
		
		/**
		 * Sets the return type.
		 * 
		 * @param returnType return type
		 * @return this builder
		 */
		public Builder returnType(TypeInfo returnType) {
			this.returnType = returnType;
			return this;
		}
		
		/**
		 * Sets the parameters.
		 * 
		 * @param parameters list of parameters
		 * @return this builder
		 */
		public Builder parameters(List<ParameterInfo> parameters) {
			this.parameters = parameters;
			return this;
		}
		
		/**
		 * Adds a parameter.
		 * 
		 * @param parameter parameter to add
		 * @return this builder
		 */
		public Builder addParameter(ParameterInfo parameter) {
			if (parameters.isEmpty()) {
				parameters = new java.util.ArrayList<>();
			} else if (!(parameters instanceof java.util.ArrayList)) {
				parameters = new java.util.ArrayList<>(parameters);
			}
			parameters.add(parameter);
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
		 * Builds the MethodInfo instance.
		 * 
		 * @return new MethodInfo
		 */
		public MethodInfo build() {
			return new MethodInfo(name, declaringType, returnType, parameters, modifiers);
		}
	}
}
