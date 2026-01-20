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
 * Describes the source of a loop (what is being iterated over).
 * 
 * <p>This class provides an AST-independent representation of loop sources
 * like collections, arrays, or ranges.</p>
 * 
 * @since 1.0.0
 */
public class SourceDescriptor {
	
	/**
	 * Types of loop sources that can be converted to functional style.
	 */
	public enum SourceType {
		/** Iteration over a Collection */
		COLLECTION,
		/** Iteration over an array */
		ARRAY,
		/** Iteration over an Iterable */
		ITERABLE,
		/** Iteration over an Iterator */
		ITERATOR,
		/** Iteration over a Stream */
		STREAM,
		/** Iteration over an integer range */
		INT_RANGE,
		/** Iteration over an explicit range */
		EXPLICIT_RANGE
	}
	
	private SourceType type;
	private String expression;
	private String elementTypeName;
	
	/**
	 * Default constructor.
	 */
	public SourceDescriptor() {
	}
	
	/**
	 * Creates a new SourceDescriptor.
	 * 
	 * @param type the type of source
	 * @param expression the source expression (e.g., "myList", "array")
	 * @param elementTypeName the type name of elements
	 */
	public SourceDescriptor(SourceType type, String expression, String elementTypeName) {
		this.type = type;
		this.expression = expression;
		this.elementTypeName = elementTypeName;
	}
	
	/**
	 * Gets the source type.
	 * 
	 * @return the source type
	 */
	public SourceType getType() {
		return type;
	}
	
	/**
	 * Sets the source type.
	 * 
	 * @param type the source type
	 */
	public void setType(SourceType type) {
		this.type = type;
	}
	
	/**
	 * Gets the source expression.
	 * 
	 * @return the source expression
	 */
	public String getExpression() {
		return expression;
	}
	
	/**
	 * Sets the source expression.
	 * 
	 * @param expression the source expression
	 */
	public void setExpression(String expression) {
		this.expression = expression;
	}
	
	/**
	 * Gets the element type name.
	 * 
	 * @return the element type name
	 */
	public String getElementTypeName() {
		return elementTypeName;
	}
	
	/**
	 * Sets the element type name.
	 * 
	 * @param elementTypeName the element type name
	 */
	public void setElementTypeName(String elementTypeName) {
		this.elementTypeName = elementTypeName;
	}
	
	/**
	 * Creates a builder for constructing SourceDescriptor instances.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Builder for SourceDescriptor.
	 */
	public static class Builder {
		private SourceType type;
		private String expression;
		private String elementTypeName;
		
		/**
		 * Sets the source type.
		 * 
		 * @param type the source type
		 * @return this builder
		 */
		public Builder type(SourceType type) {
			this.type = type;
			return this;
		}
		
		/**
		 * Sets the source expression.
		 * 
		 * @param expression the source expression
		 * @return this builder
		 */
		public Builder expression(String expression) {
			this.expression = expression;
			return this;
		}
		
		/**
		 * Sets the element type name.
		 * 
		 * @param elementTypeName the element type name
		 * @return this builder
		 */
		public Builder elementTypeName(String elementTypeName) {
			this.elementTypeName = elementTypeName;
			return this;
		}
		
		/**
		 * Builds the SourceDescriptor.
		 * 
		 * @return the built SourceDescriptor
		 */
		public SourceDescriptor build() {
			return new SourceDescriptor(type, expression, elementTypeName);
		}
	}
	
	@Override
	public String toString() {
		return "SourceDescriptor[type=" + type + ", expression=" + expression 
				+ ", elementTypeName=" + elementTypeName + "]";
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		SourceDescriptor other = (SourceDescriptor) obj;
		return type == other.type
				&& java.util.Objects.equals(expression, other.expression)
				&& java.util.Objects.equals(elementTypeName, other.elementTypeName);
	}
	
	@Override
	public int hashCode() {
		return java.util.Objects.hash(type, expression, elementTypeName);
	}
}
