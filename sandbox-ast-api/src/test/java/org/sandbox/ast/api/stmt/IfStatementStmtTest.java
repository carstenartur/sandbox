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

class IfStatementStmtTest {
	
	private final TypeInfo booleanType = TypeInfo.Builder.of("boolean").primitive().build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr condition = SimpleNameExpr.of("flag");
		WhileLoopStmt thenStmt = WhileLoopStmt.builder().build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition)
				.thenStatement(thenStmt)
				.build();
		
		assertThat(stmt.condition()).contains(condition);
		assertThat(stmt.thenStatement()).contains(thenStmt);
		assertThat(stmt.elseStatement()).isEmpty();
	}
	
	@Test
	void testHasCondition() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("isValid")
				.type(booleanType)
				.build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.hasCondition()).isTrue();
	}
	
	@Test
	void testHasThenStatement() {
		WhileLoopStmt thenStmt = WhileLoopStmt.builder().build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.thenStatement(thenStmt)
				.build();
		
		assertThat(stmt.hasThenStatement()).isTrue();
	}
	
	@Test
	void testHasElseStatement() {
		EnhancedForStmt elseStmt = EnhancedForStmt.builder().build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.elseStatement(elseStmt)
				.build();
		
		assertThat(stmt.hasElseStatement()).isTrue();
	}
	
	@Test
	void testConditionHasType() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("enabled")
				.type(booleanType)
				.build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.conditionHasType("boolean")).isTrue();
		assertThat(stmt.conditionHasType("int")).isFalse();
	}
	
	@Test
	void testElseIf() {
		SimpleNameExpr condition1 = SimpleNameExpr.of("x > 0");
		SimpleNameExpr condition2 = SimpleNameExpr.of("x < 0");
		WhileLoopStmt thenStmt1 = WhileLoopStmt.builder().build();
		WhileLoopStmt thenStmt2 = WhileLoopStmt.builder().build();
		
		IfStatementStmt elseIfStmt = IfStatementStmt.builder()
				.condition(condition2)
				.thenStatement(thenStmt2)
				.build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition1)
				.thenStatement(thenStmt1)
				.elseStatement(elseIfStmt)
				.build();
		
		assertThat(stmt.hasElseIf()).isTrue();
		assertThat(stmt.elseIf()).contains(elseIfStmt);
	}
	
	@Test
	void testElseIfChain() {
		SimpleNameExpr condition1 = SimpleNameExpr.of("x == 1");
		SimpleNameExpr condition2 = SimpleNameExpr.of("x == 2");
		SimpleNameExpr condition3 = SimpleNameExpr.of("x == 3");
		
		IfStatementStmt elseIf2 = IfStatementStmt.builder()
				.condition(condition3)
				.build();
		
		IfStatementStmt elseIf1 = IfStatementStmt.builder()
				.condition(condition2)
				.elseStatement(elseIf2)
				.build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition1)
				.elseStatement(elseIf1)
				.build();
		
		assertThat(stmt.hasElseIf()).isTrue();
		assertThat(stmt.elseIf()).contains(elseIf1);
		assertThat(stmt.elseIf().flatMap(IfStatementStmt::elseIf)).contains(elseIf2);
	}
	
	@Test
	void testNoElseIf() {
		WhileLoopStmt elseStmt = WhileLoopStmt.builder().build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.elseStatement(elseStmt)
				.build();
		
		assertThat(stmt.hasElseIf()).isFalse();
		assertThat(stmt.elseIf()).isEmpty();
	}
	
	@Test
	void testEmptyStmt() {
		IfStatementStmt stmt = IfStatementStmt.builder().build();
		
		assertThat(stmt.hasCondition()).isFalse();
		assertThat(stmt.hasThenStatement()).isFalse();
		assertThat(stmt.hasElseStatement()).isFalse();
		assertThat(stmt.hasElseIf()).isFalse();
	}
	
	@Test
	void testNullHandling() {
		IfStatementStmt stmt = new IfStatementStmt(null, null, null);
		
		assertThat(stmt.condition()).isEmpty();
		assertThat(stmt.thenStatement()).isEmpty();
		assertThat(stmt.elseStatement()).isEmpty();
	}
	
	@Test
	void testFullyPopulated() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("isActive")
				.type(booleanType)
				.build();
		WhileLoopStmt thenStmt = WhileLoopStmt.builder().build();
		EnhancedForStmt elseStmt = EnhancedForStmt.builder().build();
		
		IfStatementStmt stmt = IfStatementStmt.builder()
				.condition(condition)
				.thenStatement(thenStmt)
				.elseStatement(elseStmt)
				.build();
		
		assertThat(stmt.hasCondition()).isTrue();
		assertThat(stmt.hasThenStatement()).isTrue();
		assertThat(stmt.hasElseStatement()).isTrue();
		assertThat(stmt.conditionHasType("boolean")).isTrue();
	}
	
	@Test
	void testAsIfStatement() {
		IfStatementStmt stmt = IfStatementStmt.builder().build();
		
		assertThat(stmt.asIfStatement()).contains(stmt);
		assertThat(stmt.isIfStatement()).isTrue();
	}
}
