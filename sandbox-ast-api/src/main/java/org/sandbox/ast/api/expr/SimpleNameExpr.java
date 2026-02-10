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
package org.sandbox.ast.api.expr;

import java.util.Optional;

import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;

/**
 * Immutable record representing a simple name expression.
 * Provides type-safe binding resolution for variables, methods, and types.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof SimpleName) {
 *     SimpleName name = (SimpleName) node;
 *     IBinding binding = name.resolveBinding();
 *     if (binding instanceof IVariableBinding) {
 *         IVariableBinding varBinding = (IVariableBinding) binding;
 *         // ...
 *     }
 * }
 * 
 * // New style:
 * expr.asSimpleName()
 *     .flatMap(SimpleNameExpr::resolveVariable)
 *     .filter(var -&gt; var.hasType("java.util.List"))
 *     .ifPresent(var -&gt; { });
 * </pre>
 */
public record SimpleNameExpr(
	String identifier,
	Optional<VariableInfo> variableBinding,
	Optional<MethodInfo> methodBinding,
	Optional<TypeInfo> typeBinding,
	Optional<TypeInfo> type
) implements ASTExpr {
	
	/**
	 * Creates a SimpleNameExpr record.
	 * 
	 * @param identifier the name identifier
	 * @param variableBinding the resolved variable binding
	 * @param methodBinding the resolved method binding
	 * @param typeBinding the resolved type binding
	 * @param type the expression type
	 */
	public SimpleNameExpr {
		if (identifier == null) {
			throw new IllegalArgumentException("Identifier cannot be null");
		}
		if (identifier.isEmpty()) {
			throw new IllegalArgumentException("Identifier cannot be empty");
		}
		variableBinding = variableBinding == null ? Optional.empty() : variableBinding;
		methodBinding = methodBinding == null ? Optional.empty() : methodBinding;
		typeBinding = typeBinding == null ? Optional.empty() : typeBinding;
		type = type == null ? Optional.empty() : type;
	}
	
	/**
	 * Resolves this name as a variable.
	 * 
	 * @return the variable binding, or empty if not a variable
	 */
	public Optional<VariableInfo> resolveVariable() {
		return variableBinding;
	}
	
	/**
	 * Resolves this name as a method.
	 * 
	 * @return the method binding, or empty if not a method
	 */
	public Optional<MethodInfo> resolveMethod() {
		return methodBinding;
	}
	
	/**
	 * Resolves this name as a type.
	 * 
	 * @return the type binding, or empty if not a type
	 */
	public Optional<TypeInfo> resolveType() {
		return typeBinding;
	}
	
	/**
	 * Checks if this name refers to a variable.
	 * 
	 * @return true if variable binding is present
	 */
	public boolean isVariable() {
		return variableBinding.isPresent();
	}
	
	/**
	 * Checks if this name refers to a method.
	 * 
	 * @return true if method binding is present
	 */
	public boolean isMethod() {
		return methodBinding.isPresent();
	}
	
	/**
	 * Checks if this name refers to a type.
	 * 
	 * @return true if type binding is present
	 */
	public boolean isType() {
		return typeBinding.isPresent();
	}
	
	/**
	 * Checks if this name refers to a final variable.
	 * 
	 * @return true if variable is final
	 */
	public boolean isFinalVariable() {
		return variableBinding.map(VariableInfo::isFinal).orElse(false);
	}
	
	/**
	 * Checks if this name refers to a static variable.
	 * 
	 * @return true if variable is static
	 */
	public boolean isStaticVariable() {
		return variableBinding.map(VariableInfo::isStatic).orElse(false);
	}
	
	/**
	 * Checks if this name refers to a field.
	 * 
	 * @return true if variable is a field
	 */
	public boolean isField() {
		return variableBinding.map(VariableInfo::isField).orElse(false);
	}
	
	/**
	 * Checks if this name refers to a parameter.
	 * 
	 * @return true if variable is a parameter
	 */
	public boolean isParameter() {
		return variableBinding.map(VariableInfo::isParameter).orElse(false);
	}
	
	/**
	 * Checks if this name refers to a record component.
	 * 
	 * @return true if variable is a record component
	 */
	public boolean isRecordComponent() {
		return variableBinding.map(VariableInfo::isRecordComponent).orElse(false);
	}
	
	/**
	 * Checks if this name refers to a local variable (not field, not parameter, not record component).
	 * 
	 * @return true if variable is a local variable
	 */
	public boolean isLocalVariable() {
		return variableBinding.map(VariableInfo::isLocalVariable).orElse(false);
	}
	
	/**
	 * Checks if the variable has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if variable has this type
	 */
	public boolean variableHasType(String qualifiedTypeName) {
		return variableBinding.map(v -> v.hasType(qualifiedTypeName)).orElse(false);
	}
	
	/**
	 * Builder for creating SimpleNameExpr instances.
	 */
	public static class Builder {
		private String identifier;
		private Optional<VariableInfo> variableBinding = Optional.empty();
		private Optional<MethodInfo> methodBinding = Optional.empty();
		private Optional<TypeInfo> typeBinding = Optional.empty();
		private Optional<TypeInfo> type = Optional.empty();
		
		/**
		 * Sets the identifier.
		 * 
		 * @param identifier the identifier
		 * @return this builder
		 */
		public Builder identifier(String identifier) {
			this.identifier = identifier;
			return this;
		}
		
		/**
		 * Sets the variable binding.
		 * 
		 * @param variableBinding the variable binding
		 * @return this builder
		 */
		public Builder variableBinding(VariableInfo variableBinding) {
			this.variableBinding = Optional.ofNullable(variableBinding);
			return this;
		}
		
		/**
		 * Sets the method binding.
		 * 
		 * @param methodBinding the method binding
		 * @return this builder
		 */
		public Builder methodBinding(MethodInfo methodBinding) {
			this.methodBinding = Optional.ofNullable(methodBinding);
			return this;
		}
		
		/**
		 * Sets the type binding.
		 * 
		 * @param typeBinding the type binding
		 * @return this builder
		 */
		public Builder typeBinding(TypeInfo typeBinding) {
			this.typeBinding = Optional.ofNullable(typeBinding);
			return this;
		}
		
		/**
		 * Sets the type.
		 * 
		 * @param type the type
		 * @return this builder
		 */
		public Builder type(TypeInfo type) {
			this.type = Optional.ofNullable(type);
			return this;
		}
		
		/**
		 * Builds the SimpleNameExpr.
		 * 
		 * @return the simple name expression
		 */
		public SimpleNameExpr build() {
			return new SimpleNameExpr(identifier, variableBinding, methodBinding, typeBinding, type);
		}
	}
	
	/**
	 * Creates a new builder.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	/**
	 * Creates a simple name with just an identifier.
	 * 
	 * @param identifier the identifier
	 * @return a new simple name expression
	 */
	public static SimpleNameExpr of(String identifier) {
		return new SimpleNameExpr(identifier, Optional.empty(), Optional.empty(), 
		                          Optional.empty(), Optional.empty());
	}
}
