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
package org.sandbox.ast.api.visitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.core.ASTWrapper;
import org.sandbox.ast.api.expr.FieldAccessExpr;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.InfixOperator;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.TypeInfo;
import org.sandbox.ast.api.info.VariableInfo;
import org.sandbox.ast.api.stmt.EnhancedForStmt;
import org.sandbox.ast.api.stmt.ForLoopStmt;
import org.sandbox.ast.api.stmt.IfStmt;
import org.sandbox.ast.api.stmt.WhileLoopStmt;

class FluentVisitorTest {

private final TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
private final TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();

@Test
void testBasicMethodInvocationVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation(mi -> visited.add("method"))
.build();

MethodInvocationExpr expr = MethodInvocationExpr.builder().build();
visitor.visit(expr);

assertThat(visited).containsExactly("method");
}

@Test
void testBasicSimpleNameVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName(sn -> visited.add(sn.identifier()))
.build();

SimpleNameExpr expr = SimpleNameExpr.of("testName");
visitor.visit(expr);

assertThat(visited).containsExactly("testName");
}

@Test
void testMultipleHandlers() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation(mi -> visited.add("method"))
.onSimpleName(sn -> visited.add("name"))
.build();

MethodInvocationExpr methodExpr = MethodInvocationExpr.builder().build();
SimpleNameExpr nameExpr = SimpleNameExpr.of("test");

visitor.visit(methodExpr);
visitor.visit(nameExpr);

assertThat(visited).containsExactly("method", "name");
}

@Test
void testConditionalHandlerWithWhen() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName()
.when(sn -> sn.identifier().startsWith("test"))
.then(sn -> visited.add(sn.identifier()))
.build();

SimpleNameExpr matchingName = SimpleNameExpr.of("testMethod");
SimpleNameExpr nonMatchingName = SimpleNameExpr.of("otherMethod");

visitor.visit(matchingName);
visitor.visit(nonMatchingName);

assertThat(visited).containsExactly("testMethod");
}

@Test
void testConditionalHandlerWithFilter() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation()
.filter(mi -> mi.methodName().equals(Optional.of("add")))
.then(mi -> visited.add("add-call"))
.build();

MethodInfo addMethod = MethodInfo.Builder.named("add")
.returnType(TypeInfo.Builder.of("boolean").primitive().build())
.build();
MethodInfo getMethod = MethodInfo.Builder.named("get")
.returnType(TypeInfo.Builder.of("java.lang.Object").build())
.build();

MethodInvocationExpr addExpr = MethodInvocationExpr.builder().method(addMethod).build();
MethodInvocationExpr getExpr = MethodInvocationExpr.builder().method(getMethod).build();

visitor.visit(addExpr);
visitor.visit(getExpr);

assertThat(visited).containsExactly("add-call");
}

@Test
void testMultipleConditions() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName()
.when(sn -> sn.identifier().startsWith("test"))
.when(sn -> sn.identifier().length() > 6)
.then(sn -> visited.add(sn.identifier()))
.build();

SimpleNameExpr shortTest = SimpleNameExpr.of("test");  // matches first, not second
SimpleNameExpr longTest = SimpleNameExpr.of("testMethod");  // matches both
SimpleNameExpr other = SimpleNameExpr.of("otherLongName");  // matches neither

visitor.visit(shortTest);
visitor.visit(longTest);
visitor.visit(other);

assertThat(visited).containsExactly("testMethod");
}

@Test
void testFieldAccessVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onFieldAccess(fa -> visited.add(fa.fieldName()))
.build();

FieldAccessExpr expr = FieldAccessExpr.builder()
.receiver(SimpleNameExpr.of("obj"))
.fieldName("myField")
.build();

visitor.visit(expr);

assertThat(visited).containsExactly("myField");
}

@Test
void testInfixVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onInfix(ie -> visited.add(ie.operator().symbol()))
.build();

InfixExpr expr = InfixExpr.builder()
.leftOperand(SimpleNameExpr.of("a"))
.rightOperand(SimpleNameExpr.of("b"))
.operator(InfixOperator.PLUS)
.build();

visitor.visit(expr);

assertThat(visited).containsExactly("+");
}

@Test
void testInfixConditionalVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onInfix()
.filter(InfixExpr::isArithmetic)
.then(ie -> visited.add("arithmetic"))
.build();

InfixExpr arithmetic = InfixExpr.builder()
.leftOperand(SimpleNameExpr.of("a"))
.rightOperand(SimpleNameExpr.of("b"))
.operator(InfixOperator.PLUS)
.build();

InfixExpr logical = InfixExpr.builder()
.leftOperand(SimpleNameExpr.of("a"))
.rightOperand(SimpleNameExpr.of("b"))
.operator(InfixOperator.CONDITIONAL_AND)
.build();

visitor.visit(arithmetic);
visitor.visit(logical);

assertThat(visited).containsExactly("arithmetic");
}

@Test
void testEnhancedForVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onEnhancedFor(ef -> visited.add("enhanced-for"))
.build();

EnhancedForStmt stmt = EnhancedForStmt.builder()
.parameter(VariableInfo.Builder.named("item").type(stringType).build())
.build();

visitor.visit(stmt);

assertThat(visited).containsExactly("enhanced-for");
}

@Test
void testWhileLoopVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onWhileLoop(wl -> visited.add("while"))
.build();

WhileLoopStmt stmt = WhileLoopStmt.builder()
.condition(SimpleNameExpr.of("condition"))
.build();

visitor.visit(stmt);

assertThat(visited).containsExactly("while");
}

@Test
void testForLoopVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onForLoop(fl -> visited.add("for"))
.build();

ForLoopStmt stmt = ForLoopStmt.builder().build();

visitor.visit(stmt);

assertThat(visited).containsExactly("for");
}

@Test
void testIfStatementVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onIfStatement(is -> visited.add("if"))
.build();

IfStmt stmt = IfStmt.builder()
.condition(SimpleNameExpr.of("condition"))
.build();

visitor.visit(stmt);

assertThat(visited).containsExactly("if");
}

@Test
void testOnExpressionVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onExpression(expr -> visited.add("expr"))
.build();

visitor.visit(SimpleNameExpr.of("test"));
visitor.visit(MethodInvocationExpr.builder().build());

assertThat(visited).containsExactly("expr", "expr");
}

@Test
void testOnStatementVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onStatement(stmt -> visited.add("stmt"))
.build();

visitor.visit(IfStmt.builder().condition(SimpleNameExpr.of("c")).build());
visitor.visit(WhileLoopStmt.builder().condition(SimpleNameExpr.of("c")).build());

assertThat(visited).containsExactly("stmt", "stmt");
}

@Test
void testOnAnyVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onAny(node -> visited.add("any"))
.build();

visitor.visit(SimpleNameExpr.of("test"));
visitor.visit(IfStmt.builder().condition(SimpleNameExpr.of("c")).build());

assertThat(visited).containsExactly("any", "any");
}

@Test
void testVisitAll() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName(sn -> visited.add(sn.identifier()))
.build();

List<ASTWrapper> nodes = List.of(
SimpleNameExpr.of("first"),
SimpleNameExpr.of("second"),
SimpleNameExpr.of("third")
);

visitor.visitAll(nodes);

assertThat(visited).containsExactly("first", "second", "third");
}

@Test
void testVisitAllWithNull() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName(sn -> visited.add(sn.identifier()))
.build();

visitor.visitAll(null);

assertThat(visited).isEmpty();
}

@Test
void testAndThen() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor1 = FluentVisitor.builder()
.onSimpleName(sn -> visited.add("v1:" + sn.identifier()))
.build();

FluentVisitor visitor2 = FluentVisitor.builder()
.onSimpleName(sn -> visited.add("v2:" + sn.identifier()))
.build();

FluentVisitor combined = visitor1.andThen(visitor2);

combined.visit(SimpleNameExpr.of("test"));

assertThat(visited).containsExactly("v1:test", "v2:test");
}

@Test
void testComplexVisitor() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation()
.filter(mi -> mi.hasReceiver())
.then(mi -> visited.add("chained-call"))
.onSimpleName()
.when(sn -> sn.identifier().startsWith("get"))
.then(sn -> visited.add("getter:" + sn.identifier()))
.onFieldAccess(fa -> visited.add("field:" + fa.fieldName()))
.onInfix()
.filter(InfixExpr::isStringConcatenation)
.then(ie -> visited.add("string-concat"))
.build();

// Create test nodes
MethodInvocationExpr chainedCall = MethodInvocationExpr.builder()
.receiver(SimpleNameExpr.of("obj"))
.build();

SimpleNameExpr getter = SimpleNameExpr.of("getName");
SimpleNameExpr setter = SimpleNameExpr.of("setName");

FieldAccessExpr field = FieldAccessExpr.builder()
.receiver(SimpleNameExpr.of("obj"))
.fieldName("value")
.build();

InfixExpr stringConcat = InfixExpr.builder()
.leftOperand(SimpleNameExpr.builder()
.identifier("a")
.type(stringType)
.build())
.rightOperand(SimpleNameExpr.builder()
.identifier("b")
.type(stringType)
.build())
.operator(InfixOperator.PLUS)
.type(stringType)
.build();

InfixExpr numeric = InfixExpr.builder()
.leftOperand(SimpleNameExpr.builder()
.identifier("a")
.type(intType)
.build())
.rightOperand(SimpleNameExpr.builder()
.identifier("b")
.type(intType)
.build())
.operator(InfixOperator.PLUS)
.type(intType)
.build();

// Visit all nodes
visitor.visit(chainedCall);
visitor.visit(getter);
visitor.visit(setter);
visitor.visit(field);
visitor.visit(stringConcat);
visitor.visit(numeric);

assertThat(visited).containsExactly(
"chained-call",
"getter:getName",
"field:value",
"string-concat"
);
}

@Test
void testVisitNullNode() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onAny(node -> visited.add("any"))
.build();

visitor.visit(null);

assertThat(visited).isEmpty();
}

@Test
void testEmptyVisitor() {
FluentVisitor visitor = FluentVisitor.builder().build();

// Should not throw
visitor.visit(SimpleNameExpr.of("test"));
}

@Test
void testMixedExpressionsAndStatements() {
List<String> visited = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onExpression(expr -> visited.add("expr"))
.onStatement(stmt -> visited.add("stmt"))
.build();

visitor.visit(SimpleNameExpr.of("test"));
visitor.visit(IfStmt.builder().condition(SimpleNameExpr.of("c")).build());
visitor.visit(MethodInvocationExpr.builder().build());
visitor.visit(WhileLoopStmt.builder().condition(SimpleNameExpr.of("c")).build());

assertThat(visited).containsExactly("expr", "stmt", "expr", "stmt");
}
}
