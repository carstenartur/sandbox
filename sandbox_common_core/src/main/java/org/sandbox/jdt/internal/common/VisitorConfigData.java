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
package org.sandbox.jdt.internal.common;

import java.util.Arrays;

/**
 * Immutable configuration data for visitor predicates and consumers.
 * This class replaces the stringly-typed {@code Map<String, Object>} pattern
 * with a type-safe, immutable value object.
 * 
 * <p>Use the builder pattern to construct instances:</p>
 * <pre>
 * VisitorConfigData config = VisitorConfigData.builder()
 *     .methodName("toString")
 *     .typeof(String.class)
 *     .build();
 * </pre>
 *
 * @since 1.15
 */
public final class VisitorConfigData {
	
	private final Class<?> typeof;
	private final String typeofByName;
	private final String methodName;
	private final String annotationName;
	private final String importName;
	private final String superClassName;
	private final String[] paramTypeNames;
	private final String operator;
	private final String typeName;
	private final Class<?> exceptionType;
	
	private VisitorConfigData(Builder builder) {
		this.typeof = builder.typeof;
		this.typeofByName = builder.typeofByName;
		this.methodName = builder.methodName;
		this.annotationName = builder.annotationName;
		this.importName = builder.importName;
		this.superClassName = builder.superClassName;
		this.paramTypeNames = builder.paramTypeNames;
		this.operator = builder.operator;
		this.typeName = builder.typeName;
		this.exceptionType = builder.exceptionType;
	}
	
	/**
	 * Creates a new builder for constructing {@code VisitorConfigData} instances.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Gets the type filter (as a Class object).
	 * 
	 * @return the type filter, or {@code null} if not set
	 */
	public Class<?> getTypeof() {
		return typeof;
	}
	
	/**
	 * Gets the type filter by fully qualified name.
	 * This is used to avoid deprecation warnings when filtering deprecated types.
	 * 
	 * @return the fully qualified type name, or {@code null} if not set
	 */
	public String getTypeofByName() {
		return typeofByName;
	}
	
	/**
	 * Gets the method name filter.
	 * 
	 * @return the method name, or {@code null} if not set
	 */
	public String getMethodName() {
		return methodName;
	}
	
	/**
	 * Gets the annotation name filter.
	 * 
	 * @return the annotation name, or {@code null} if not set
	 */
	public String getAnnotationName() {
		return annotationName;
	}
	
	/**
	 * Gets the import name filter.
	 * 
	 * @return the import name, or {@code null} if not set
	 */
	public String getImportName() {
		return importName;
	}
	
	/**
	 * Gets the superclass name filter.
	 * 
	 * @return the superclass name, or {@code null} if not set
	 */
	public String getSuperClassName() {
		return superClassName;
	}
	
	/**
	 * Gets the parameter type names filter.
	 * 
	 * @return a defensive copy of the parameter type names, or {@code null} if not set
	 */
	public String[] getParamTypeNames() {
		return paramTypeNames != null ? Arrays.copyOf(paramTypeNames, paramTypeNames.length) : null;
	}
	
	/**
	 * Gets the operator filter.
	 * 
	 * @return the operator, or {@code null} if not set
	 */
	public String getOperator() {
		return operator;
	}
	
	/**
	 * Gets the type name filter.
	 * 
	 * @return the type name, or {@code null} if not set
	 */
	public String getTypeName() {
		return typeName;
	}
	
	/**
	 * Gets the exception type filter.
	 * 
	 * @return the exception type, or {@code null} if not set
	 */
	public Class<?> getExceptionType() {
		return exceptionType;
	}
	
	/**
	 * Builder for constructing immutable {@code VisitorConfigData} instances.
	 */
	public static final class Builder {
		private Class<?> typeof;
		private String typeofByName;
		private String methodName;
		private String annotationName;
		private String importName;
		private String superClassName;
		private String[] paramTypeNames;
		private String operator;
		private String typeName;
		private Class<?> exceptionType;
		
		private Builder() {
		}
		
		/**
		 * Sets the type filter (as a Class object).
		 * 
		 * @param typeof the type to filter
		 * @return this builder
		 */
		public Builder typeof(Class<?> typeof) {
			this.typeof = typeof;
			return this;
		}
		
		/**
		 * Sets the type filter by fully qualified name.
		 * Use this to avoid deprecation warnings when filtering deprecated types.
		 * 
		 * @param typeofByName the fully qualified type name
		 * @return this builder
		 */
		public Builder typeofByName(String typeofByName) {
			this.typeofByName = typeofByName;
			return this;
		}
		
		/**
		 * Sets the method name filter.
		 * 
		 * @param methodName the method name
		 * @return this builder
		 */
		public Builder methodName(String methodName) {
			this.methodName = methodName;
			return this;
		}
		
		/**
		 * Sets the annotation name filter.
		 * 
		 * @param annotationName the annotation name
		 * @return this builder
		 */
		public Builder annotationName(String annotationName) {
			this.annotationName = annotationName;
			return this;
		}
		
		/**
		 * Sets the import name filter.
		 * 
		 * @param importName the import name
		 * @return this builder
		 */
		public Builder importName(String importName) {
			this.importName = importName;
			return this;
		}
		
		/**
		 * Sets the superclass name filter.
		 * 
		 * @param superClassName the superclass name
		 * @return this builder
		 */
		public Builder superClassName(String superClassName) {
			this.superClassName = superClassName;
			return this;
		}
		
		/**
		 * Sets the parameter type names filter.
		 * 
		 * @param paramTypeNames the parameter type names
		 * @return this builder
		 */
		public Builder paramTypeNames(String[] paramTypeNames) {
			this.paramTypeNames = paramTypeNames != null ? Arrays.copyOf(paramTypeNames, paramTypeNames.length) : null;
			return this;
		}
		
		/**
		 * Sets the operator filter.
		 * 
		 * @param operator the operator
		 * @return this builder
		 */
		public Builder operator(String operator) {
			this.operator = operator;
			return this;
		}
		
		/**
		 * Sets the type name filter.
		 * 
		 * @param typeName the type name
		 * @return this builder
		 */
		public Builder typeName(String typeName) {
			this.typeName = typeName;
			return this;
		}
		
		/**
		 * Sets the exception type filter.
		 * 
		 * @param exceptionType the exception type
		 * @return this builder
		 */
		public Builder exceptionType(Class<?> exceptionType) {
			this.exceptionType = exceptionType;
			return this;
		}
		
		/**
		 * Builds an immutable {@code VisitorConfigData} instance.
		 * 
		 * @return the constructed {@code VisitorConfigData}
		 */
		public VisitorConfigData build() {
			return new VisitorConfigData(this);
		}
	}
}
