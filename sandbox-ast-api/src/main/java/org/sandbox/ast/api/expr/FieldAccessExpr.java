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

import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;

/**
 * Immutable record representing a field access expression.
 * Provides fluent access to receiver and field information.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof FieldAccess) {
 *     FieldAccess fa = (FieldAccess) node;
 *     Expression receiver = fa.getExpression();
 *     SimpleName field = fa.getName();
 *     // ...
 * }
 * 
 * // New style:
 * expr.asFieldAccess()
 *     .filter(fa -&gt; fa.fieldName().equals("size"))
 *     .map(FieldAccessExpr::receiver)
 *     .ifPresent(receiver -&gt; { });
 * </pre>
 */
public record FieldAccessExpr(
	ASTExpr receiver,
	String fieldName,
	Optional<VariableInfo> field,
	Optional<TypeInfo> type
) implements ASTExpr {
	
	/**
	 * Creates a FieldAccessExpr record.
	 * 
	 * @param receiver the receiver expression
	 * @param fieldName the field name
	 * @param field the resolved field information
	 * @param type the field type
	 */
	public FieldAccessExpr {
		if (receiver == null) {
			throw new IllegalArgumentException("Receiver cannot be null");
		}
		if (fieldName == null) {
			throw new IllegalArgumentException("Field name cannot be null");
		}
		if (fieldName.isEmpty()) {
			throw new IllegalArgumentException("Field name cannot be empty");
		}
		field = field == null ? Optional.empty() : field;
		type = type == null ? Optional.empty() : type;
	}
	
	/**
	 * Checks if this accesses a static field.
	 * 
	 * @return true if field is static
	 */
	public boolean isStatic() {
		return field.map(VariableInfo::isStatic).orElse(false);
	}
	
	/**
	 * Checks if this accesses a final field.
	 * 
	 * @return true if field is final
	 */
	public boolean isFinal() {
		return field.map(VariableInfo::isFinal).orElse(false);
	}
	
	/**
	 * Checks if the receiver has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if receiver has this type
	 */
	public boolean receiverHasType(String qualifiedTypeName) {
		return receiver.type()
		               .map(t -> t.is(qualifiedTypeName))
		               .orElse(false);
	}
	
	/**
	 * Checks if the field has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if field has this type
	 */
	public boolean fieldHasType(String qualifiedTypeName) {
		return field.map(f -> f.hasType(qualifiedTypeName)).orElse(false);
	}
	
	/**
	 * Builder for creating FieldAccessExpr instances.
	 */
	public static class Builder {
		private ASTExpr receiver;
		private String fieldName;
		private Optional<VariableInfo> field = Optional.empty();
		private Optional<TypeInfo> type = Optional.empty();
		
		/**
		 * Sets the receiver expression.
		 * 
		 * @param receiver the receiver
		 * @return this builder
		 */
		public Builder receiver(ASTExpr receiver) {
			this.receiver = receiver;
			return this;
		}
		
		/**
		 * Sets the field name.
		 * 
		 * @param fieldName the field name
		 * @return this builder
		 */
		public Builder fieldName(String fieldName) {
			this.fieldName = fieldName;
			return this;
		}
		
		/**
		 * Sets the field information.
		 * 
		 * @param field the field
		 * @return this builder
		 */
		public Builder field(VariableInfo field) {
			this.field = Optional.ofNullable(field);
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
		 * Builds the FieldAccessExpr.
		 * 
		 * @return the field access expression
		 */
		public FieldAccessExpr build() {
			return new FieldAccessExpr(receiver, fieldName, field, type);
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
