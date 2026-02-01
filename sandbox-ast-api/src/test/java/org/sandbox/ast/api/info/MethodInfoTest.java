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
 * Tests for MethodInfo.
 */
class MethodInfoTest {
	
	@Test
	void testSimpleMethod() {
		TypeInfo returnType = TypeInfo.Builder.of("void").build();
		MethodInfo method = MethodInfo.Builder.named("toString")
			.returnType(returnType)
			.build();
		
		assertThat(method.name()).isEqualTo("toString");
		assertThat(method.returnType()).isEqualTo(returnType);
		assertThat(method.parameters()).isEmpty();
	}
	
	@Test
	void testMethodWithParameters() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		ParameterInfo param1 = ParameterInfo.of("text", stringType);
		ParameterInfo param2 = ParameterInfo.of("count", intType);
		
		MethodInfo method = MethodInfo.Builder.named("repeat")
			.returnType(stringType)
			.addParameter(param1)
			.addParameter(param2)
			.build();
		
		assertThat(method.parameters()).hasSize(2);
		assertThat(method.parameters().get(0)).isEqualTo(param1);
		assertThat(method.parameters().get(1)).isEqualTo(param2);
	}
	
	@Test
	void testIsMathMax() {
		TypeInfo mathType = TypeInfo.Builder.of("java.lang.Math").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("max")
			.declaringType(mathType)
			.returnType(intType)
			.addParameter(ParameterInfo.of("a", intType))
			.addParameter(ParameterInfo.of("b", intType))
			.build();
		
		assertThat(method.isMathMax()).isTrue();
		assertThat(method.isMathMin()).isFalse();
	}
	
	@Test
	void testIsMathMin() {
		TypeInfo mathType = TypeInfo.Builder.of("java.lang.Math").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("min")
			.declaringType(mathType)
			.returnType(intType)
			.addParameter(ParameterInfo.of("a", intType))
			.addParameter(ParameterInfo.of("b", intType))
			.build();
		
		assertThat(method.isMathMin()).isTrue();
		assertThat(method.isMathMax()).isFalse();
	}
	
	@Test
	void testIsListAdd() {
		TypeInfo listType = TypeInfo.Builder.of("java.util.ArrayList").build();
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo boolType = TypeInfo.Builder.of("boolean").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("add")
			.declaringType(listType)
			.returnType(boolType)
			.addParameter(ParameterInfo.of("element", stringType))
			.build();
		
		assertThat(method.isListAdd()).isTrue();
	}
	
	@Test
	void testIsListGet() {
		TypeInfo listType = TypeInfo.Builder.of("java.util.List").build();
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("get")
			.declaringType(listType)
			.returnType(stringType)
			.addParameter(ParameterInfo.of("index", intType))
			.build();
		
		assertThat(method.isListGet()).isTrue();
	}
	
	@Test
	void testIsCollectionStream() {
		TypeInfo listType = TypeInfo.Builder.of("java.util.ArrayList").build();
		TypeInfo streamType = TypeInfo.Builder.of("java.util.stream.Stream").build();
		
		MethodInfo method = MethodInfo.Builder.named("stream")
			.declaringType(listType)
			.returnType(streamType)
			.build();
		
		assertThat(method.isCollectionStream()).isTrue();
	}
	
	@Test
	void testHasSignature() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("substring")
			.returnType(stringType)
			.addParameter(ParameterInfo.of("start", intType))
			.addParameter(ParameterInfo.of("end", intType))
			.build();
		
		assertThat(method.hasSignature("substring", "int", "int")).isTrue();
		assertThat(method.hasSignature("substring", "int")).isFalse();
		assertThat(method.hasSignature("substring", "int", "int", "int")).isFalse();
		assertThat(method.hasSignature("substr", "int", "int")).isFalse();
	}
	
	@Test
	void testModifiers() {
		TypeInfo voidType = TypeInfo.Builder.of("void").build();
		
		MethodInfo method = MethodInfo.Builder.named("main")
			.returnType(voidType)
			.modifiers(Set.of(Modifier.PUBLIC, Modifier.STATIC))
			.build();
		
		assertThat(method.isPublic()).isTrue();
		assertThat(method.isStatic()).isTrue();
		assertThat(method.isPrivate()).isFalse();
		assertThat(method.isFinal()).isFalse();
		assertThat(method.isAbstract()).isFalse();
	}
	
	@Test
	void testSignature() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("substring")
			.returnType(stringType)
			.addParameter(ParameterInfo.of("start", intType))
			.addParameter(ParameterInfo.of("end", intType))
			.build();
		
		assertThat(method.signature()).isEqualTo("substring(int, int)");
	}
	
	@Test
	void testEquals() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method1 = MethodInfo.Builder.named("substring")
			.returnType(stringType)
			.addParameter(ParameterInfo.of("start", intType))
			.build();
		
		MethodInfo method2 = MethodInfo.Builder.named("substring")
			.returnType(stringType)
			.addParameter(ParameterInfo.of("start", intType))
			.build();
		
		assertThat(method1).isEqualTo(method2);
	}
	
	@Test
	void testValidation_nullName() {
		TypeInfo voidType = TypeInfo.Builder.of("void").build();
		assertThatThrownBy(() -> new MethodInfo(null, null, voidType, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method name cannot be null");
	}
	
	@Test
	void testValidation_emptyName() {
		TypeInfo voidType = TypeInfo.Builder.of("void").build();
		assertThatThrownBy(() -> new MethodInfo("", null, voidType, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method name cannot be empty");
	}
	
	@Test
	void testValidation_nullReturnType() {
		assertThatThrownBy(() -> new MethodInfo("method", null, null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Return type cannot be null");
	}
}
