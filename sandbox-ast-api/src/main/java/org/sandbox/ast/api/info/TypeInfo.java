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
import java.util.Optional;

/**
 * Immutable record representing a Java type.
 * Provides fluent query methods for type checking and comparisons.
 */
public record TypeInfo(
	String qualifiedName,
	String simpleName,
	List<TypeInfo> typeArguments,
	boolean isPrimitive,
	boolean isArray,
	int arrayDimensions
) {
	
	/**
	 * Creates a TypeInfo record.
	 * 
	 * @param qualifiedName fully qualified name (e.g., "java.util.List")
	 * @param simpleName simple name (e.g., "List")
	 * @param typeArguments type arguments for generics
	 * @param isPrimitive true if primitive type
	 * @param isArray true if array type
	 * @param arrayDimensions number of array dimensions
	 */
	public TypeInfo {
		if (qualifiedName == null || qualifiedName.isEmpty()) {
			throw new IllegalArgumentException("Qualified name cannot be null or empty");
		}
		if (simpleName == null || simpleName.isEmpty()) {
			throw new IllegalArgumentException("Simple name cannot be null or empty");
		}
		typeArguments = typeArguments == null ? List.of() : List.copyOf(typeArguments);
		if (arrayDimensions < 0) {
			throw new IllegalArgumentException("Array dimensions cannot be negative");
		}
	}
	
	/**
	 * Checks if this type matches the given class.
	 * 
	 * @param clazz class to compare
	 * @return true if qualified names match
	 */
	public boolean is(Class<?> clazz) {
		return qualifiedName.equals(clazz.getName());
	}
	
	/**
	 * Checks if this type matches the given qualified name.
	 * 
	 * @param qualifiedName qualified name to compare
	 * @return true if names match
	 */
	public boolean is(String qualifiedName) {
		return this.qualifiedName.equals(qualifiedName);
	}
	
	/**
	 * Checks if this type is a collection type (List, Set, Collection, etc.).
	 * 
	 * @return true if collection type
	 */
	public boolean isCollection() {
		return qualifiedName.equals("java.util.Collection") ||
			   qualifiedName.equals("java.util.List") ||
			   qualifiedName.equals("java.util.Set") ||
			   qualifiedName.equals("java.util.Queue") ||
			   qualifiedName.equals("java.util.Deque") ||
			   qualifiedName.equals("java.util.ArrayList") ||
			   qualifiedName.equals("java.util.LinkedList") ||
			   qualifiedName.equals("java.util.HashSet") ||
			   qualifiedName.equals("java.util.TreeSet");
	}
	
	/**
	 * Checks if this type is a List.
	 * 
	 * @return true if List type
	 */
	public boolean isList() {
		return qualifiedName.equals("java.util.List") ||
			   qualifiedName.equals("java.util.ArrayList") ||
			   qualifiedName.equals("java.util.LinkedList");
	}
	
	/**
	 * Checks if this type is a Stream.
	 * 
	 * @return true if Stream type
	 */
	public boolean isStream() {
		return qualifiedName.equals("java.util.stream.Stream") ||
			   qualifiedName.equals("java.util.stream.IntStream") ||
			   qualifiedName.equals("java.util.stream.LongStream") ||
			   qualifiedName.equals("java.util.stream.DoubleStream");
	}
	
	/**
	 * Checks if this type is Optional.
	 * 
	 * @return true if Optional type
	 */
	public boolean isOptional() {
		return qualifiedName.equals("java.util.Optional") ||
			   qualifiedName.equals("java.util.OptionalInt") ||
			   qualifiedName.equals("java.util.OptionalLong") ||
			   qualifiedName.equals("java.util.OptionalDouble");
	}
	
	/**
	 * Checks if this type is numeric (int, Integer, double, Double, etc.).
	 * 
	 * @return true if numeric type
	 */
	public boolean isNumeric() {
		return isPrimitive && (
			qualifiedName.equals("int") ||
			qualifiedName.equals("long") ||
			qualifiedName.equals("double") ||
			qualifiedName.equals("float") ||
			qualifiedName.equals("short") ||
			qualifiedName.equals("byte")
		) || (
			qualifiedName.equals("java.lang.Integer") ||
			qualifiedName.equals("java.lang.Long") ||
			qualifiedName.equals("java.lang.Double") ||
			qualifiedName.equals("java.lang.Float") ||
			qualifiedName.equals("java.lang.Short") ||
			qualifiedName.equals("java.lang.Byte") ||
			qualifiedName.equals("java.math.BigInteger") ||
			qualifiedName.equals("java.math.BigDecimal")
		);
	}
	
	/**
	 * Returns the boxed version of a primitive type.
	 * 
	 * @return Optional containing boxed type, or empty if not primitive
	 */
	public Optional<TypeInfo> boxed() {
		if (!isPrimitive) {
			return Optional.empty();
		}
		return switch (qualifiedName) {
			case "int" -> Optional.of(Builder.of("java.lang.Integer").build());
			case "long" -> Optional.of(Builder.of("java.lang.Long").build());
			case "double" -> Optional.of(Builder.of("java.lang.Double").build());
			case "float" -> Optional.of(Builder.of("java.lang.Float").build());
			case "boolean" -> Optional.of(Builder.of("java.lang.Boolean").build());
			case "byte" -> Optional.of(Builder.of("java.lang.Byte").build());
			case "short" -> Optional.of(Builder.of("java.lang.Short").build());
			case "char" -> Optional.of(Builder.of("java.lang.Character").build());
			default -> Optional.empty();
		};
	}
	
	/**
	 * Checks if this type has type arguments (is generic).
	 * 
	 * @return true if has type arguments
	 */
	public boolean hasTypeArguments() {
		return !typeArguments.isEmpty();
	}
	
	/**
	 * Gets the first type argument if present.
	 * 
	 * @return Optional containing first type argument
	 */
	public Optional<TypeInfo> firstTypeArgument() {
		return typeArguments.isEmpty() ? Optional.empty() : Optional.of(typeArguments.get(0));
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof TypeInfo other)) return false;
		return Objects.equals(qualifiedName, other.qualifiedName) &&
			   isPrimitive == other.isPrimitive &&
			   isArray == other.isArray &&
			   arrayDimensions == other.arrayDimensions &&
			   Objects.equals(typeArguments, other.typeArguments);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(qualifiedName, isPrimitive, isArray, arrayDimensions, typeArguments);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(qualifiedName);
		if (!typeArguments.isEmpty()) {
			sb.append("<");
			for (int i = 0; i < typeArguments.size(); i++) {
				if (i > 0) sb.append(", ");
				sb.append(typeArguments.get(i).simpleName);
			}
			sb.append(">");
		}
		if (isArray) {
			sb.append("[]".repeat(arrayDimensions));
		}
		return sb.toString();
	}
	
	/**
	 * Builder for creating TypeInfo instances.
	 */
	public static class Builder {
		private String qualifiedName;
		private String simpleName;
		private List<TypeInfo> typeArguments = List.of();
		private boolean isPrimitive;
		private boolean isArray;
		private int arrayDimensions;
		
		private Builder(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			// Extract simple name from qualified name
			int lastDot = qualifiedName.lastIndexOf('.');
			this.simpleName = lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
		}
		
		/**
		 * Creates a builder for the given qualified name.
		 * 
		 * @param qualifiedName fully qualified type name
		 * @return new Builder
		 */
		public static Builder of(String qualifiedName) {
			return new Builder(qualifiedName);
		}
		
		/**
		 * Creates a builder for a class.
		 * 
		 * @param clazz the class
		 * @return new Builder
		 */
		public static Builder of(Class<?> clazz) {
			return new Builder(clazz.getName());
		}
		
		/**
		 * Sets the simple name explicitly.
		 * 
		 * @param simpleName simple name
		 * @return this builder
		 */
		public Builder simpleName(String simpleName) {
			this.simpleName = simpleName;
			return this;
		}
		
		/**
		 * Sets type arguments for generics.
		 * 
		 * @param typeArguments type arguments
		 * @return this builder
		 */
		public Builder typeArguments(List<TypeInfo> typeArguments) {
			this.typeArguments = typeArguments;
			return this;
		}
		
		/**
		 * Adds a single type argument.
		 * 
		 * @param typeArgument type argument to add
		 * @return this builder
		 */
		public Builder addTypeArgument(TypeInfo typeArgument) {
			if (typeArguments.isEmpty()) {
				typeArguments = new java.util.ArrayList<>();
			} else if (!(typeArguments instanceof java.util.ArrayList)) {
				typeArguments = new java.util.ArrayList<>(typeArguments);
			}
			typeArguments.add(typeArgument);
			return this;
		}
		
		/**
		 * Marks this type as primitive.
		 * 
		 * @return this builder
		 */
		public Builder primitive() {
			this.isPrimitive = true;
			return this;
		}
		
		/**
		 * Marks this type as an array.
		 * 
		 * @param dimensions number of array dimensions
		 * @return this builder
		 */
		public Builder array(int dimensions) {
			this.isArray = true;
			this.arrayDimensions = dimensions;
			return this;
		}
		
		/**
		 * Marks this type as a 1-dimensional array.
		 * 
		 * @return this builder
		 */
		public Builder array() {
			return array(1);
		}
		
		/**
		 * Builds the TypeInfo instance.
		 * 
		 * @return new TypeInfo
		 */
		public TypeInfo build() {
			return new TypeInfo(qualifiedName, simpleName, typeArguments, 
							   isPrimitive, isArray, arrayDimensions);
		}
	}
}
