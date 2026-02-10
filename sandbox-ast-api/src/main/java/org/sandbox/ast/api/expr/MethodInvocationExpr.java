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

import java.util.List;
import java.util.Optional;

import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Immutable record representing a method invocation expression.
 * Provides fluent access to receiver, arguments, and method information.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof MethodInvocation) {
 *     MethodInvocation mi = (MethodInvocation) node;
 *     Expression receiver = mi.getExpression();
 *     if (receiver instanceof SimpleName) {
 *         SimpleName name = (SimpleName) receiver;
 *         // ...
 *     }
 * }
 * 
 * // New style:
 * expr.asMethodInvocation()
 *     .flatMap(MethodInvocationExpr::receiver)
 *     .filter(ASTExpr::isSimpleName)
 *     .ifPresent(name -&gt; { });
 * </pre>
 */
public record MethodInvocationExpr(
	Optional<ASTExpr> receiver,
	List<ASTExpr> arguments,
	Optional<MethodInfo> method,
	Optional<TypeInfo> type
) implements ASTExpr {
	
	/**
	 * Creates a MethodInvocationExpr record.
	 * 
	 * @param receiver the receiver expression (empty for unqualified calls)
	 * @param arguments the method arguments
	 * @param method the resolved method information
	 * @param type the result type of the invocation
	 */
	public MethodInvocationExpr {
		receiver = receiver == null ? Optional.empty() : receiver;
		arguments = arguments == null ? List.of() : List.copyOf(arguments);
		method = method == null ? Optional.empty() : method;
		type = type == null ? Optional.empty() : type;
	}
	
	/**
	 * Checks if this invocation has a receiver expression.
	 * 
	 * @return true if receiver is present
	 */
	public boolean hasReceiver() {
		return receiver.isPresent();
	}
	
	/**
	 * Gets the number of arguments.
	 * 
	 * @return argument count
	 */
	public int argumentCount() {
		return arguments.size();
	}
	
	/**
	 * Checks if this is a call to a specific method.
	 * 
	 * @param methodName the method name
	 * @param paramCount the expected parameter count
	 * @return true if method matches
	 */
	public boolean isMethodCall(String methodName, int paramCount) {
		return method.map(m -> m.name().equals(methodName) && 
		                       m.parameters().size() == paramCount)
		             .orElse(false);
	}
	
	/**
	 * Checks if this is a call to a method on a specific type.
	 * 
	 * @param typeName the fully qualified type name
	 * @param methodName the method name
	 * @return true if method matches
	 */
	public boolean isMethodCall(String typeName, String methodName) {
		return method.map(m -> m.name().equals(methodName) && 
		                       m.declaringType() != null &&
		                       m.declaringType().is(typeName))
		             .orElse(false);
	}
	
	/**
	 * Checks if this is a static method call.
	 * 
	 * @return true if static
	 */
	public boolean isStatic() {
		return method.map(MethodInfo::isStatic).orElse(false);
	}
	
	/**
	 * Checks if the receiver has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if receiver has this type
	 */
	public boolean receiverHasType(String qualifiedTypeName) {
		return receiver.flatMap(ASTExpr::type)
		               .map(t -> t.is(qualifiedTypeName))
		               .orElse(false);
	}
	
	/**
	 * Gets an argument by index.
	 * 
	 * @param index the argument index
	 * @return the argument, or empty if index out of bounds
	 */
	public Optional<ASTExpr> argument(int index) {
		if (index < 0 || index >= arguments.size()) {
			return Optional.empty();
		}
		return Optional.of(arguments.get(index));
	}
	
	/**
	 * Checks if this is a chained method call (receiver is also a method invocation).
	 * 
	 * @return true if chained
	 */
	public boolean isChained() {
		return receiver.map(ASTExpr::isMethodInvocation).orElse(false);
	}
	
	/**
	 * Gets the method name if available.
	 * 
	 * @return the method name, or empty if not resolved
	 */
	public Optional<String> methodName() {
		return method.map(MethodInfo::name);
	}
	
	/**
	 * Checks if this method has the given name.
	 * 
	 * @param name the method name
	 * @return true if method has this name
	 */
	public boolean isMethodNamed(String name) {
		return method.map(m -> m.name().equals(name)).orElse(false);
	}

	/**
	 * Gets the qualified name of the declaring type, or empty if unresolved.
	 */
	public Optional<String> declaringTypeQualifiedName() {
		return method.map(m -> m.declaringType())
					 .filter(t -> t != null)
					 .map(TypeInfo::qualifiedName);
	}

	/**
	 * Checks if this is a call to the given method on the given declaring type.
	 */
	public boolean isCallOn(String declaringTypeName, String methodName) {
		return isMethodNamed(methodName) 
			&& declaringTypeQualifiedName().filter(n -> n.equals(declaringTypeName)).isPresent();
	}
	
	/**
	 * Gets the receiver's identifier if receiver is a SimpleName.
	 * 
	 * @return the receiver identifier, or empty if not a simple name
	 */
	public Optional<String> receiverIdentifier() {
		return receiver.flatMap(ASTExpr::asSimpleName)
		               .map(SimpleNameExpr::identifier);
	}
	
	/**
	 * Checks if this method's return type matches the given qualified name.
	 * 
	 * @param qualifiedName the fully qualified type name
	 * @return true if return type matches
	 */
	public boolean returnsType(String qualifiedName) {
		return method.map(m -> m.returnType().is(qualifiedName)).orElse(false);
	}
	
	/**
	 * Builder for creating MethodInvocationExpr instances.
	 */
	public static class Builder {
		private Optional<ASTExpr> receiver = Optional.empty();
		private java.util.ArrayList<ASTExpr> arguments = new java.util.ArrayList<>();
		private Optional<MethodInfo> method = Optional.empty();
		private Optional<TypeInfo> type = Optional.empty();
		
		/**
		 * Sets the receiver expression.
		 * 
		 * @param receiver the receiver
		 * @return this builder
		 */
		public Builder receiver(ASTExpr receiver) {
			this.receiver = Optional.ofNullable(receiver);
			return this;
		}
		
		/**
		 * Sets the arguments.
		 * 
		 * @param arguments the arguments
		 * @return this builder
		 */
		public Builder arguments(List<ASTExpr> arguments) {
			this.arguments = new java.util.ArrayList<>(arguments == null ? List.of() : arguments);
			return this;
		}
		
		/**
		 * Adds a single argument.
		 * 
		 * @param argument the argument
		 * @return this builder
		 */
		public Builder addArgument(ASTExpr argument) {
			this.arguments.add(argument);
			return this;
		}
		
		/**
		 * Sets the method information.
		 * 
		 * @param method the method
		 * @return this builder
		 */
		public Builder method(MethodInfo method) {
			this.method = Optional.ofNullable(method);
			return this;
		}
		
		/**
		 * Sets the type information.
		 * 
		 * @param type the type
		 * @return this builder
		 */
		public Builder type(TypeInfo type) {
			this.type = Optional.ofNullable(type);
			return this;
		}
		
		/**
		 * Builds the MethodInvocationExpr.
		 * 
		 * @return the method invocation
		 */
		public MethodInvocationExpr build() {
			return new MethodInvocationExpr(receiver, arguments, method, type);
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
}
