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
package org.sandbox.ast.api.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.sandbox.ast.api.core.ASTWrapper;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.expr.SimpleNameExpr;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.stmt.EnhancedForStmt;
import org.sandbox.ast.api.stmt.IfStmt;
import org.sandbox.ast.api.visitor.FluentVisitor;

/**
 * Practical examples demonstrating FluentVisitor usage patterns.
 * Shows how to use the visitor API for common AST analysis tasks.
 */
public class FluentVisitorExamples {

/**
 * Example 1: Find all method invocations named "add".
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of method names found
 */
public static List<String> findAddMethodCalls(List<ASTWrapper> nodes) {
List<String> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation()
.when(mi -> mi.methodName().equals(Optional.of("add")))
.then(mi -> results.add("Found add() call"))
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 2: Find all static method calls.
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of static method calls
 */
public static List<MethodInvocationExpr> findStaticMethodCalls(List<ASTWrapper> nodes) {
List<MethodInvocationExpr> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation()
.filter(MethodInvocationExpr::isStatic)
.then(results::add)
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 3: Find all string concatenations.
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of string concatenation expressions
 */
public static List<InfixExpr> findStringConcatenations(List<ASTWrapper> nodes) {
List<InfixExpr> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onInfix()
.filter(InfixExpr::isStringConcatenation)
.then(results::add)
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 4: Find all variable names starting with "temp".
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of temporary variable names
 */
public static List<String> findTemporaryVariables(List<ASTWrapper> nodes) {
List<String> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName()
.when(sn -> sn.identifier().startsWith("temp"))
.when(SimpleNameExpr::isVariable)
.then(sn -> results.add(sn.identifier()))
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 5: Find all enhanced for loops iterating over lists.
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of enhanced for statements
 */
public static List<EnhancedForStmt> findListIterations(List<ASTWrapper> nodes) {
List<EnhancedForStmt> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onEnhancedFor()
.when(ef -> ef.iterable()
.map(expr -> expr.hasType("java.util.List"))
.orElse(false))
.then(results::add)
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 6: Find all if statements with simple boolean conditions.
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of if statements
 */
public static List<IfStmt> findSimpleBooleanIfs(List<ASTWrapper> nodes) {
List<IfStmt> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onIfStatement()
.when(is -> is.condition().isSimpleName())
.then(results::add)
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 7: Combine multiple visitors using andThen.
 * 
 * @param nodes list of AST nodes to analyze
 * @return combined analysis results
 */
public static String analyzeCodePatterns(List<ASTWrapper> nodes) {
List<String> methodCalls = new ArrayList<>();
List<String> controlFlow = new ArrayList<>();

FluentVisitor methodVisitor = FluentVisitor.builder()
.onMethodInvocation(mi -> methodCalls.add("method"))
.build();

FluentVisitor controlFlowVisitor = FluentVisitor.builder()
.onIfStatement(is -> controlFlow.add("if"))
.onEnhancedFor(ef -> controlFlow.add("for"))
.onWhileLoop(wl -> controlFlow.add("while"))
.build();

FluentVisitor combined = methodVisitor.andThen(controlFlowVisitor);
combined.visitAll(nodes);

return String.format("Method calls: %d, Control flow: %d", 
methodCalls.size(), controlFlow.size());
}

/**
 * Example 8: Complex multi-condition visitor.
 * Finds method calls to List.add() with at least one argument.
 * 
 * @param nodes list of AST nodes to analyze
 * @return list of matching method invocations
 */
public static List<MethodInvocationExpr> findListAddWithArguments(List<ASTWrapper> nodes) {
List<MethodInvocationExpr> results = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onMethodInvocation()
.when(mi -> mi.methodName().equals(Optional.of("add")))
.when(mi -> mi.argumentCount() > 0)
.when(mi -> mi.method()
.map(MethodInfo::isListAdd)
.orElse(false))
.then(results::add)
.build();

visitor.visitAll(nodes);
return results;
}

/**
 * Example 9: Multiple handlers for the same node type.
 * Demonstrates counting both getters and setters.
 * 
 * @param nodes list of AST nodes to analyze
 * @return formatted count string
 */
public static String countGettersAndSetters(List<ASTWrapper> nodes) {
List<String> getters = new ArrayList<>();
List<String> setters = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onSimpleName()
.when(sn -> sn.identifier().startsWith("get"))
.then(sn -> getters.add(sn.identifier()))
.onSimpleName()
.when(sn -> sn.identifier().startsWith("set"))
.then(sn -> setters.add(sn.identifier()))
.build();

visitor.visitAll(nodes);
return String.format("Getters: %d, Setters: %d", getters.size(), setters.size());
}

/**
 * Example 10: Using onAny to count all nodes.
 * 
 * @param nodes list of AST nodes to analyze
 * @return total node count
 */
public static int countAllNodes(List<ASTWrapper> nodes) {
List<ASTWrapper> allNodes = new ArrayList<>();

FluentVisitor visitor = FluentVisitor.builder()
.onAny(allNodes::add)
.build();

visitor.visitAll(nodes);
return allNodes.size();
}
}
