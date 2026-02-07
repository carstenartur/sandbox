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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.Modifier;
import org.sandbox.ast.api.info.ParameterInfo;
import org.sandbox.ast.api.info.TypeInfo;

class MethodInvocationExprTest {
	
	private final TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
	private final TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
	private final TypeInfo listType = TypeInfo.Builder.of("java.util.List")
			.addTypeArgument(stringType)
			.build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr receiver = SimpleNameExpr.of("myList");
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.receiver(receiver)
				.type(intType)
				.build();
		
		assertThat(expr.receiver()).contains(receiver);
		assertThat(expr.type()).contains(intType);
		assertThat(expr.arguments()).isEmpty();
		assertThat(expr.method()).isEmpty();
	}
	
	@Test
	void testWithArguments() {
		SimpleNameExpr arg1 = SimpleNameExpr.of("arg1");
		SimpleNameExpr arg2 = SimpleNameExpr.of("arg2");
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.addArgument(arg1)
				.addArgument(arg2)
				.build();
		
		assertThat(expr.arguments()).hasSize(2);
		assertThat(expr.arguments()).containsExactly(arg1, arg2);
		assertThat(expr.argumentCount()).isEqualTo(2);
	}
	
	@Test
	void testWithMethodInfo() {
		MethodInfo method = MethodInfo.Builder.named("substring")
				.returnType(stringType)
				.addParameter(ParameterInfo.of("start", intType))
				.addParameter(ParameterInfo.of("end", intType))
				.modifiers(Set.of(Modifier.PUBLIC))
				.build();
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.method(method)
				.build();
		
		assertThat(expr.method()).contains(method);
		assertThat(expr.methodName()).contains("substring");
	}
	
	@Test
	void testMethodCallChecks() {
		MethodInfo method = MethodInfo.Builder.named("add")
				.returnType(TypeInfo.Builder.of("boolean").primitive().build())
				.declaringType(listType)
				.addParameter(ParameterInfo.of("element", stringType))
				.build();
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.method(method)
				.build();
		
		assertThat(expr.isMethodCall("add", 1)).isTrue();
		assertThat(expr.isMethodCall("add", 2)).isFalse();
		assertThat(expr.isMethodCall("java.util.List", "add")).isTrue();
		assertThat(expr.isMethodCall("java.util.Set", "add")).isFalse();
	}
	
	@Test
	void testStaticMethodCheck() {
		MethodInfo staticMethod = MethodInfo.Builder.named("max")
				.returnType(intType)
				.declaringType(TypeInfo.Builder.of("java.lang.Math").build())
				.modifiers(Set.of(Modifier.STATIC))
				.build();
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.method(staticMethod)
				.build();
		
		assertThat(expr.isStatic()).isTrue();
	}
	
	@Test
	void testChainedMethodCall() {
		MethodInvocationExpr innerCall = MethodInvocationExpr.builder()
				.method(MethodInfo.Builder.named("trim").returnType(stringType).build())
				.build();
		
		MethodInvocationExpr outerCall = MethodInvocationExpr.builder()
				.receiver(innerCall)
				.method(MethodInfo.Builder.named("toLowerCase").returnType(stringType).build())
				.build();
		
		assertThat(outerCall.isChained()).isTrue();
		assertThat(innerCall.isChained()).isFalse();
	}
	
	@Test
	void testReceiverTypeCheck() {
		SimpleNameExpr receiver = SimpleNameExpr.builder()
				.identifier("myString")
				.type(stringType)
				.build();
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.receiver(receiver)
				.build();
		
		assertThat(expr.receiverHasType("java.lang.String")).isTrue();
		assertThat(expr.receiverHasType("java.util.List")).isFalse();
	}
	
	@Test
	void testArgumentAccess() {
		SimpleNameExpr arg1 = SimpleNameExpr.of("arg1");
		SimpleNameExpr arg2 = SimpleNameExpr.of("arg2");
		
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.arguments(List.of(arg1, arg2))
				.build();
		
		assertThat(expr.argument(0)).contains(arg1);
		assertThat(expr.argument(1)).contains(arg2);
		assertThat(expr.argument(2)).isEmpty();
		assertThat(expr.argument(-1)).isEmpty();
	}
	
	@Test
	void testHasReceiver() {
		MethodInvocationExpr withReceiver = MethodInvocationExpr.builder()
				.receiver(SimpleNameExpr.of("obj"))
				.build();
		
		MethodInvocationExpr withoutReceiver = MethodInvocationExpr.builder()
				.build();
		
		assertThat(withReceiver.hasReceiver()).isTrue();
		assertThat(withoutReceiver.hasReceiver()).isFalse();
	}
	
	@Test
	void testASTExprInterfaceMethods() {
		MethodInvocationExpr expr = MethodInvocationExpr.builder()
				.type(stringType)
				.build();
		
		assertThat(expr.type()).contains(stringType);
		assertThat(expr.hasType("java.lang.String")).isTrue();
		assertThat(expr.hasType(String.class)).isTrue();
		assertThat(expr.isMethodInvocation()).isTrue();
		assertThat(expr.isSimpleName()).isFalse();
		assertThat(expr.asMethodInvocation()).contains(expr);
		assertThat(expr.asSimpleName()).isEmpty();
	}
}
