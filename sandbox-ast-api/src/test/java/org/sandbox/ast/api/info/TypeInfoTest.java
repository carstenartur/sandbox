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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Tests for TypeInfo.
 */
class TypeInfoTest {
	
	@Test
	void testSimpleType() {
		TypeInfo type = TypeInfo.Builder.of("java.lang.String").build();
		assertThat(type.qualifiedName()).isEqualTo("java.lang.String");
		assertThat(type.simpleName()).isEqualTo("String");
		assertThat(type.isPrimitive()).isFalse();
		assertThat(type.isArray()).isFalse();
	}
	
	@Test
	void testPrimitiveType() {
		TypeInfo type = TypeInfo.Builder.of("int").primitive().build();
		assertThat(type.qualifiedName()).isEqualTo("int");
		assertThat(type.isPrimitive()).isTrue();
	}
	
	@Test
	void testArrayType() {
		TypeInfo type = TypeInfo.Builder.of("java.lang.String").array(2).build();
		assertThat(type.isArray()).isTrue();
		assertThat(type.arrayDimensions()).isEqualTo(2);
	}
	
	@Test
	void testGenericType() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo listType = TypeInfo.Builder.of("java.util.List")
			.addTypeArgument(stringType)
			.build();
		
		assertThat(listType.hasTypeArguments()).isTrue();
		assertThat(listType.typeArguments()).hasSize(1);
		assertThat(listType.firstTypeArgument()).isPresent();
		assertThat(listType.firstTypeArgument().get()).isEqualTo(stringType);
	}
	
	@Test
	void testIs_withClass() {
		TypeInfo type = TypeInfo.Builder.of(String.class).build();
		assertThat(type.is(String.class)).isTrue();
		assertThat(type.is(Integer.class)).isFalse();
	}
	
	@Test
	void testIs_withQualifiedName() {
		TypeInfo type = TypeInfo.Builder.of("java.util.List").build();
		assertThat(type.is("java.util.List")).isTrue();
		assertThat(type.is("java.util.Set")).isFalse();
	}
	
	@Test
	void testIsCollection() {
		assertThat(TypeInfo.Builder.of("java.util.List").build().isCollection()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.ArrayList").build().isCollection()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.Set").build().isCollection()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.Collection").build().isCollection()).isTrue();
		assertThat(TypeInfo.Builder.of("java.lang.String").build().isCollection()).isFalse();
	}
	
	@Test
	void testIsList() {
		assertThat(TypeInfo.Builder.of("java.util.List").build().isList()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.ArrayList").build().isList()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.LinkedList").build().isList()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.Set").build().isList()).isFalse();
	}
	
	@Test
	void testIsStream() {
		assertThat(TypeInfo.Builder.of("java.util.stream.Stream").build().isStream()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.stream.IntStream").build().isStream()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.List").build().isStream()).isFalse();
	}
	
	@Test
	void testIsOptional() {
		assertThat(TypeInfo.Builder.of("java.util.Optional").build().isOptional()).isTrue();
		assertThat(TypeInfo.Builder.of("java.util.OptionalInt").build().isOptional()).isTrue();
		assertThat(TypeInfo.Builder.of("java.lang.String").build().isOptional()).isFalse();
	}
	
	@Test
	void testIsNumeric_primitive() {
		assertThat(TypeInfo.Builder.of("int").primitive().build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("long").primitive().build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("double").primitive().build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("boolean").primitive().build().isNumeric()).isFalse();
	}
	
	@Test
	void testIsNumeric_wrapper() {
		assertThat(TypeInfo.Builder.of("java.lang.Integer").build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("java.lang.Double").build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("java.math.BigDecimal").build().isNumeric()).isTrue();
		assertThat(TypeInfo.Builder.of("java.lang.String").build().isNumeric()).isFalse();
	}
	
	@Test
	void testBoxed() {
		Optional<TypeInfo> boxed = TypeInfo.Builder.of("int").primitive().build().boxed();
		assertThat(boxed).isPresent();
		assertThat(boxed.get().is("java.lang.Integer")).isTrue();
		
		boxed = TypeInfo.Builder.of("double").primitive().build().boxed();
		assertThat(boxed).isPresent();
		assertThat(boxed.get().is("java.lang.Double")).isTrue();
		
		boxed = TypeInfo.Builder.of("java.lang.String").build().boxed();
		assertThat(boxed).isEmpty();
	}
	
	@Test
	void testToString_simple() {
		TypeInfo type = TypeInfo.Builder.of("java.lang.String").build();
		assertThat(type.toString()).isEqualTo("java.lang.String");
	}
	
	@Test
	void testToString_generic() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo listType = TypeInfo.Builder.of("java.util.List")
			.addTypeArgument(stringType)
			.build();
		assertThat(listType.toString()).isEqualTo("java.util.List<String>");
	}
	
	@Test
	void testToString_array() {
		TypeInfo type = TypeInfo.Builder.of("java.lang.String").array(2).build();
		assertThat(type.toString()).isEqualTo("java.lang.String[][]");
	}
	
	@Test
	void testEquals() {
		TypeInfo type1 = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo type2 = TypeInfo.Builder.of("java.lang.String").build();
		assertThat(type1).isEqualTo(type2);
	}
	
	@Test
	void testEquals_withGeneric() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo list1 = TypeInfo.Builder.of("java.util.List").typeArguments(List.of(stringType)).build();
		TypeInfo list2 = TypeInfo.Builder.of("java.util.List").typeArguments(List.of(stringType)).build();
		assertThat(list1).isEqualTo(list2);
	}
	
	@Test
	void testValidation_nullQualifiedName() {
		assertThatThrownBy(() -> new TypeInfo(null, "String", List.of(), false, false, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Qualified name cannot be null");
	}
	
	@Test
	void testValidation_emptyQualifiedName() {
		assertThatThrownBy(() -> new TypeInfo("", "String", List.of(), false, false, 0))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Qualified name cannot be null");
	}
	
	@Test
	void testValidation_negativeArrayDimensions() {
		assertThatThrownBy(() -> new TypeInfo("java.lang.String", "String", List.of(), false, false, -1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Array dimensions cannot be negative");
	}
	
	@Test
	void testBuilder_of() {
		TypeInfo type = TypeInfo.Builder.of("java.util.List").build();
		assertThat(type.qualifiedName()).isEqualTo("java.util.List");
		assertThat(type.simpleName()).isEqualTo("List");
	}
	
	@Test
	void testBuilder_ofClass() {
		TypeInfo type = TypeInfo.Builder.of(String.class).build();
		assertThat(type.qualifiedName()).isEqualTo("java.lang.String");
		assertThat(type.simpleName()).isEqualTo("String");
	}
}
