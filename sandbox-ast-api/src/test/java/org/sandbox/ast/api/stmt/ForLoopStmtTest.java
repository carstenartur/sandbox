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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.TypeInfo;

class ForLoopStmtTest {
	
	private final TypeInfo booleanType = TypeInfo.Builder.of("boolean").primitive().build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr init = SimpleNameExpr.of("i");
		SimpleNameExpr condition = SimpleNameExpr.of("i < 10");
		SimpleNameExpr updater = SimpleNameExpr.of("i++");
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.addInitializer(init)
				.condition(condition)
				.addUpdater(updater)
				.build();
		
		assertThat(stmt.initializers()).hasSize(1);
		assertThat(stmt.condition()).contains(condition);
		assertThat(stmt.updaters()).hasSize(1);
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testMultipleInitializers() {
		SimpleNameExpr init1 = SimpleNameExpr.of("i");
		SimpleNameExpr init2 = SimpleNameExpr.of("j");
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.addInitializer(init1)
				.addInitializer(init2)
				.build();
		
		assertThat(stmt.hasInitializers()).isTrue();
		assertThat(stmt.initializers()).hasSize(2);
		assertThat(stmt.initializerCount()).isEqualTo(2);
		assertThat(stmt.initializer(0)).contains(init1);
		assertThat(stmt.initializer(1)).contains(init2);
	}
	
	@Test
	void testMultipleUpdaters() {
		SimpleNameExpr updater1 = SimpleNameExpr.of("i++");
		SimpleNameExpr updater2 = SimpleNameExpr.of("j--");
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.addUpdater(updater1)
				.addUpdater(updater2)
				.build();
		
		assertThat(stmt.hasUpdaters()).isTrue();
		assertThat(stmt.updaters()).hasSize(2);
		assertThat(stmt.updaterCount()).isEqualTo(2);
		assertThat(stmt.updater(0)).contains(updater1);
		assertThat(stmt.updater(1)).contains(updater2);
	}
	
	@Test
	void testInitializersAsList() {
		SimpleNameExpr init1 = SimpleNameExpr.of("i");
		SimpleNameExpr init2 = SimpleNameExpr.of("j");
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.initializers(List.of(init1, init2))
				.build();
		
		assertThat(stmt.initializers()).containsExactly(init1, init2);
	}
	
	@Test
	void testUpdatersAsList() {
		SimpleNameExpr updater1 = SimpleNameExpr.of("i++");
		SimpleNameExpr updater2 = SimpleNameExpr.of("j--");
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.updaters(List.of(updater1, updater2))
				.build();
		
		assertThat(stmt.updaters()).containsExactly(updater1, updater2);
	}
	
	@Test
	void testHasCondition() {
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("i < 10")
				.type(booleanType)
				.build();
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.condition(condition)
				.build();
		
		assertThat(stmt.hasCondition()).isTrue();
	}
	
	@Test
	void testHasBody() {
		WhileLoopStmt body = WhileLoopStmt.builder().build();
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.body(body)
				.build();
		
		assertThat(stmt.hasBody()).isTrue();
	}
	
	@Test
	void testInfiniteLoop() {
		ForLoopStmt stmt = ForLoopStmt.builder().build();
		
		assertThat(stmt.isInfiniteLoop()).isTrue();
		
		ForLoopStmt withCondition = ForLoopStmt.builder()
				.condition(SimpleNameExpr.of("true"))
				.build();
		
		assertThat(withCondition.isInfiniteLoop()).isFalse();
	}
	
	@Test
	void testIndexOutOfBounds() {
		ForLoopStmt stmt = ForLoopStmt.builder()
				.addInitializer(SimpleNameExpr.of("i"))
				.addUpdater(SimpleNameExpr.of("i++"))
				.build();
		
		assertThat(stmt.initializer(-1)).isEmpty();
		assertThat(stmt.initializer(1)).isEmpty();
		assertThat(stmt.updater(-1)).isEmpty();
		assertThat(stmt.updater(1)).isEmpty();
	}
	
	@Test
	void testEmptyStmt() {
		ForLoopStmt stmt = ForLoopStmt.builder().build();
		
		assertThat(stmt.hasInitializers()).isFalse();
		assertThat(stmt.hasCondition()).isFalse();
		assertThat(stmt.hasUpdaters()).isFalse();
		assertThat(stmt.hasBody()).isFalse();
		assertThat(stmt.isInfiniteLoop()).isTrue();
	}
	
	@Test
	void testNullHandling() {
		ForLoopStmt stmt = new ForLoopStmt(null, null, null, null);
		
		assertThat(stmt.initializers()).isEmpty();
		assertThat(stmt.condition()).isEmpty();
		assertThat(stmt.updaters()).isEmpty();
		assertThat(stmt.body()).isEmpty();
	}
	
	@Test
	void testFullyPopulated() {
		SimpleNameExpr init = SimpleNameExpr.of("i");
		SimpleNameExpr condition = SimpleNameExpr.builder()
				.identifier("i < 10")
				.type(booleanType)
				.build();
		SimpleNameExpr updater = SimpleNameExpr.of("i++");
		EnhancedForStmt body = EnhancedForStmt.builder().build();
		
		ForLoopStmt stmt = ForLoopStmt.builder()
				.addInitializer(init)
				.condition(condition)
				.addUpdater(updater)
				.body(body)
				.build();
		
		assertThat(stmt.hasInitializers()).isTrue();
		assertThat(stmt.hasCondition()).isTrue();
		assertThat(stmt.hasUpdaters()).isTrue();
		assertThat(stmt.hasBody()).isTrue();
		assertThat(stmt.isInfiniteLoop()).isFalse();
	}
	
	@Test
	void testAsForLoop() {
		ForLoopStmt stmt = ForLoopStmt.builder().build();
		
		assertThat(stmt.asForLoop()).contains(stmt);
		assertThat(stmt.isForLoop()).isTrue();
	}
}
