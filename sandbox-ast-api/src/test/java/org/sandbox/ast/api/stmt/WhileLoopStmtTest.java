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

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.TypeInfo;

class WhileLoopStmtTest {
	
	private final TypeInfo booleanType = TypeInfo.Builder.of("boolean").primitive().build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr condition = SimpleNameExpr.of("running");
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.condition()).contains(condition);
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testHasCondition() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("hasNext")
				.type(booleanType)
				.build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.hasCondition()).isTrue();
	}
	
	@Test
	void testHasBody() {
		EnhancedForStmt body = EnhancedForStmt.builder().build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.body(body)
				.build();
		
		assertThat(stmt.hasBody()).isTrue();
	}
	
	@Test
	void testConditionHasType() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("flag")
				.type(booleanType)
				.build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.conditionHasType("boolean")).isTrue();
		assertThat(stmt.conditionHasType("int")).isFalse();
	}
	
	@Test
	void testHasBooleanTypedCondition() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("flag")
				.type(booleanType)
				.build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.hasBooleanTypedCondition()).isTrue();
	}
	
	@Test
	@SuppressWarnings("deprecation")
	void testHasConstantCondition_deprecated() {
		// Note: hasConstantCondition() is deprecated and doesn't actually check for constants,
		// only for boolean type. This test remains for backward compatibility.
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("true")
				.type(booleanType)
				.build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.hasConstantCondition()).isTrue();
	}
	
	@Test
	void testEmptyStmt() {
		WhileLoopStmt stmt = WhileLoopStmt.builder().build();
		
		assertThat(stmt.hasCondition()).isFalse();
		assertThat(stmt.hasBody()).isFalse();
	}
	
	@Test
	void testNullHandling() {
		WhileLoopStmt stmt = new WhileLoopStmt(null, null);
		
		assertThat(stmt.condition()).isEmpty();
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testFullyPopulated() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("active")
				.type(booleanType)
				.build();
		EnhancedForStmt body = EnhancedForStmt.builder().build();
		
		WhileLoopStmt stmt = WhileLoopStmt.builder()
				.condition(condition)
				.body(body)
				.build();
		
		assertThat(stmt.hasCondition()).isTrue();
		assertThat(stmt.hasBody()).isTrue();
		assertThat(stmt.conditionHasType("boolean")).isTrue();
	}
	
	@Test
	void testAsWhileLoop() {
		WhileLoopStmt stmt = WhileLoopStmt.builder().build();
		
		assertThat(stmt.asWhileLoop()).contains(stmt);
		assertThat(stmt.isWhileLoop()).isTrue();
	}
	
	@Test
	void testNestedInEnhancedFor() {
		SimpleNameExpr condition = SimpleNameExpr.of("flag");
		WhileLoopStmt whileStmt = WhileLoopStmt.builder()
				.condition(condition)
				.build();
		
		EnhancedForStmt enhancedFor = EnhancedForStmt.builder()
				.body(whileStmt)
				.build();
		
		assertThat(enhancedFor.body()).contains(whileStmt);
		assertThat(enhancedFor.body().flatMap(ASTStmt::asWhileLoop)).contains(whileStmt);
	}
}
