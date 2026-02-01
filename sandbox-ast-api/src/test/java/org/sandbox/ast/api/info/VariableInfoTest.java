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

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for VariableInfo.
 */
class VariableInfoTest {
	
	@Test
	void testSimpleVariable() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		VariableInfo var = VariableInfo.Builder.named("name")
			.type(stringType)
			.build();
		
		assertThat(var.name()).isEqualTo("name");
		assertThat(var.type()).isEqualTo(stringType);
		assertThat(var.modifiers()).isEmpty();
		assertThat(var.isField()).isFalse();
		assertThat(var.isParameter()).isFalse();
	}
	
	@Test
	void testFieldVariable() {
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		VariableInfo var = VariableInfo.Builder.named("count")
			.type(intType)
			.modifiers(Set.of(Modifier.PRIVATE, Modifier.FINAL))
			.field()
			.build();
		
		assertThat(var.isField()).isTrue();
		assertThat(var.isPrivate()).isTrue();
		assertThat(var.isFinal()).isTrue();
		assertThat(var.isStatic()).isFalse();
	}
	
	@Test
	void testParameterVariable() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		VariableInfo var = VariableInfo.Builder.named("arg")
			.type(stringType)
			.parameter()
			.build();
		
		assertThat(var.isParameter()).isTrue();
		assertThat(var.isField()).isFalse();
	}
	
	@Test
	void testStaticField() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		VariableInfo var = VariableInfo.Builder.named("CONSTANT")
			.type(stringType)
			.modifiers(Set.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL))
			.field()
			.build();
		
		assertThat(var.isPublic()).isTrue();
		assertThat(var.isStatic()).isTrue();
		assertThat(var.isFinal()).isTrue();
		assertThat(var.isPrivate()).isFalse();
	}
	
	@Test
	void testHasType_string() {
		TypeInfo listType = TypeInfo.Builder.of("java.util.List").build();
		VariableInfo var = VariableInfo.Builder.named("items")
			.type(listType)
			.build();
		
		assertThat(var.hasType("java.util.List")).isTrue();
		assertThat(var.hasType("java.util.Set")).isFalse();
	}
	
	@Test
	void testHasType_class() {
		TypeInfo stringType = TypeInfo.Builder.of(String.class).build();
		VariableInfo var = VariableInfo.Builder.named("text")
			.type(stringType)
			.build();
		
		assertThat(var.hasType(String.class)).isTrue();
		assertThat(var.hasType(Integer.class)).isFalse();
	}
	
	@Test
	void testHasModifier() {
		VariableInfo var = VariableInfo.Builder.named("field")
			.type(TypeInfo.Builder.of("int").primitive().build())
			.modifiers(Set.of(Modifier.PRIVATE, Modifier.FINAL))
			.build();
		
		assertThat(var.hasModifier(Modifier.PRIVATE)).isTrue();
		assertThat(var.hasModifier(Modifier.FINAL)).isTrue();
		assertThat(var.hasModifier(Modifier.STATIC)).isFalse();
	}
	
	@Test
	void testValidation_nullName() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		assertThatThrownBy(() -> new VariableInfo(null, stringType, Set.of(), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Variable name cannot be null");
	}
	
	@Test
	void testValidation_emptyName() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		assertThatThrownBy(() -> new VariableInfo("", stringType, Set.of(), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Variable name cannot be empty");
	}
	
	@Test
	void testValidation_nullType() {
		assertThatThrownBy(() -> new VariableInfo("name", null, Set.of(), false, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Variable type cannot be null");
	}
}
