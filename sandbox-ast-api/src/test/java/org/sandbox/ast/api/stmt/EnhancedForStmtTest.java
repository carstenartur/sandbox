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
package org.sandbox.ast.api.stmt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.Modifier;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;

class EnhancedForStmtTest {
	
	private final TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
	private final TypeInfo listType = TypeInfo.Builder.of("java.util.List")
			.addTypeArgument(stringType)
			.build();
	
	@Test
	void testBasicConstruction() {
		VariableInfo param = VariableInfo.Builder.named("item")
				.type(stringType)
				.modifiers(Set.of())
				.build();
		SimpleNameExpr iterable = SimpleNameExpr.of("items");
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.parameter(param)
				.iterable(iterable)
				.build();
		
		assertThat(stmt.parameter()).contains(param);
		assertThat(stmt.iterable()).contains(iterable);
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testHasParameter() {
		VariableInfo param = VariableInfo.Builder.named("item")
				.type(stringType)
				.modifiers(Set.of())
				.build();
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.parameter(param)
				.build();
		
		assertThat(stmt.hasParameter()).isTrue();
		assertThat(stmt.parameterName()).contains("item");
	}
	
	@Test
	void testHasIterable() {
		SimpleNameExpr iterable = SimpleNameExpr.of("items");
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.iterable(iterable)
				.build();
		
		assertThat(stmt.hasIterable()).isTrue();
	}
	
	@Test
	void testHasBody() {
		WhileLoopStmt body = WhileLoopStmt.builder().build();
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.body(body)
				.build();
		
		assertThat(stmt.hasBody()).isTrue();
	}
	
	@Test
	void testIterableHasType() {
		SimpleNameExpr iterable = SimpleNameExpr.builder()
				.identifier("items")
				.type(listType)
				.build();
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.iterable(iterable)
				.build();
		
		assertThat(stmt.iterableHasType("java.util.List")).isTrue();
		assertThat(stmt.iterableHasType("java.util.Set")).isFalse();
	}
	
	@Test
	void testParameterHasType() {
		VariableInfo param = VariableInfo.Builder.named("item")
				.type(stringType)
				.modifiers(Set.of())
				.build();
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.parameter(param)
				.build();
		
		assertThat(stmt.parameterHasType("java.lang.String")).isTrue();
		assertThat(stmt.parameterHasType("java.lang.Integer")).isFalse();
	}
	
	@Test
	void testEmptyStmt() {
		EnhancedForStmt stmt = EnhancedForStmt.builder().build();
		
		assertThat(stmt.hasParameter()).isFalse();
		assertThat(stmt.hasIterable()).isFalse();
		assertThat(stmt.hasBody()).isFalse();
		assertThat(stmt.parameterName()).isEmpty();
	}
	
	@Test
	void testNullHandling() {
		EnhancedForStmt stmt = new EnhancedForStmt(null, null, null);
		
		assertThat(stmt.parameter()).isEmpty();
		assertThat(stmt.iterable()).isEmpty();
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testFullyPopulated() {
		VariableInfo param = VariableInfo.Builder.named("item")
				.type(stringType)
				.modifiers(Set.of(Modifier.FINAL))
				.build();
		SimpleNameExpr iterable = SimpleNameExpr.builder()
				.identifier("items")
				.type(listType)
				.build();
		WhileLoopStmt body = WhileLoopStmt.builder().build();
		
		EnhancedForStmt stmt = EnhancedForStmt.builder()
				.parameter(param)
				.iterable(iterable)
				.body(body)
				.build();
		
		assertThat(stmt.hasParameter()).isTrue();
		assertThat(stmt.hasIterable()).isTrue();
		assertThat(stmt.hasBody()).isTrue();
		assertThat(stmt.parameterName()).contains("item");
		assertThat(stmt.iterableHasType("java.util.List")).isTrue();
		assertThat(stmt.parameterHasType("java.lang.String")).isTrue();
	}
	
	@Test
	void testAsEnhancedFor() {
		EnhancedForStmt stmt = EnhancedForStmt.builder().build();
		
		assertThat(stmt.asEnhancedFor()).contains(stmt);
		assertThat(stmt.isEnhancedFor()).isTrue();
	}
}
