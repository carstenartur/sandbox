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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.Modifier;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;

class SimpleNameExprTest {
	
	private final TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
	private final TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr expr = SimpleNameExpr.of("myVariable");
		
		assertThat(expr.identifier()).isEqualTo("myVariable");
		assertThat(expr.variableBinding()).isEmpty();
		assertThat(expr.methodBinding()).isEmpty();
		assertThat(expr.typeBinding()).isEmpty();
		assertThat(expr.type()).isEmpty();
	}
	
	@Test
	void testWithVariableBinding() {
		VariableInfo var = VariableInfo.Builder.named("myString")
				.type(stringType)
				.modifiers(Set.of(Modifier.PRIVATE, Modifier.FINAL))
				.build();
		
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("myString")
				.variableBinding(var)
				.type(stringType)
				.build();
		
		assertThat(expr.resolveVariable()).contains(var);
		assertThat(expr.isVariable()).isTrue();
		assertThat(expr.isMethod()).isFalse();
		assertThat(expr.isType()).isFalse();
	}
	
	@Test
	void testWithMethodBinding() {
		MethodInfo method = MethodInfo.Builder.named("toString")
				.returnType(stringType)
				.modifiers(Set.of(Modifier.PUBLIC))
				.build();
		
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("toString")
				.methodBinding(method)
				.build();
		
		assertThat(expr.resolveMethod()).contains(method);
		assertThat(expr.isMethod()).isTrue();
		assertThat(expr.isVariable()).isFalse();
	}
	
	@Test
	void testWithTypeBinding() {
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("String")
				.typeBinding(stringType)
				.build();
		
		assertThat(expr.resolveType()).contains(stringType);
		assertThat(expr.isType()).isTrue();
		assertThat(expr.isVariable()).isFalse();
	}
	
	@Test
	void testVariableModifierChecks() {
		VariableInfo finalVar = VariableInfo.Builder.named("CONSTANT")
				.type(stringType)
				.modifiers(Set.of(Modifier.FINAL, Modifier.STATIC))
				.build();
		
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("CONSTANT")
				.variableBinding(finalVar)
				.build();
		
		assertThat(expr.isFinalVariable()).isTrue();
		assertThat(expr.isStaticVariable()).isTrue();
	}
	
	@Test
	void testFieldAndParameterChecks() {
		VariableInfo field = VariableInfo.Builder.named("field")
				.type(intType)
				.field()
				.build();
		
		VariableInfo param = VariableInfo.Builder.named("param")
				.type(intType)
				.parameter()
				.build();
		
		SimpleNameExpr fieldExpr = SimpleNameExpr.builder()
				.identifier("field")
				.variableBinding(field)
				.build();
		
		SimpleNameExpr paramExpr = SimpleNameExpr.builder()
				.identifier("param")
				.variableBinding(param)
				.build();
		
		assertThat(fieldExpr.isField()).isTrue();
		assertThat(fieldExpr.isParameter()).isFalse();
		assertThat(paramExpr.isField()).isFalse();
		assertThat(paramExpr.isParameter()).isTrue();
	}
	
	@Test
	void testVariableTypeCheck() {
		VariableInfo var = VariableInfo.Builder.named("myList")
				.type(TypeInfo.Builder.of("java.util.List").build())
				.build();
		
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("myList")
				.variableBinding(var)
				.build();
		
		assertThat(expr.variableHasType("java.util.List")).isTrue();
		assertThat(expr.variableHasType("java.util.Set")).isFalse();
	}
	
	@Test
	void testNullIdentifierThrows() {
		assertThatThrownBy(() -> SimpleNameExpr.of(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Identifier cannot be null");
	}
	
	@Test
	void testEmptyIdentifierThrows() {
		assertThatThrownBy(() -> SimpleNameExpr.of(""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Identifier cannot be empty");
	}
	
	@Test
	void testASTExprInterfaceMethods() {
		SimpleNameExpr expr = SimpleNameExpr.builder()
				.identifier("value")
				.type(intType)
				.build();
		
		assertThat(expr.type()).contains(intType);
		assertThat(expr.hasType("int")).isTrue();
		assertThat(expr.isSimpleName()).isTrue();
		assertThat(expr.isMethodInvocation()).isFalse();
		assertThat(expr.asSimpleName()).contains(expr);
		assertThat(expr.asMethodInvocation()).isEmpty();
	}
}
