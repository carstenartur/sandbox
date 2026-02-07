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

import org.sandbox.ast.api.expr.ASTExpr;
import org.sandbox.ast.api.expr.CastExpr;
import org.sandbox.ast.api.expr.InfixExpr;
import org.sandbox.ast.api.expr.MethodInvocationExpr;
import org.sandbox.ast.api.info.MethodInfo;

/**
 * Examples demonstrating the value of the fluent AST API.
 * 
 * These examples show how the new API simplifies common AST manipulation patterns
 * that would otherwise require verbose instanceof checks and casting.
 */
public class FluentAPIExamples {
	
	/**
	 * Example 1: Find all List.add() method calls in an expression tree.
	 * 
	 * Old style would require:
	 * - Multiple instanceof checks
	 * - Explicit casting
	 * - Nested if statements
	 * - Manual null checks
	 * 
	 * New style uses fluent API with Optional chaining.
	 */
	public static List<MethodInvocationExpr> findListAddCalls(List<ASTExpr> expressions) {
		List<MethodInvocationExpr> results = new ArrayList<>();
		
		for (ASTExpr expr : expressions) {
			expr.asMethodInvocation()
					.filter(mi -> mi.method().map(MethodInfo::isListAdd).orElse(false))
					.ifPresent(results::add);
		}
		
		return results;
	}
	
	/**
	 * Example 2: Find all casts to String.
	 */
	public static List<CastExpr> findStringCasts(List<ASTExpr> expressions) {
		List<CastExpr> results = new ArrayList<>();
		
		for (ASTExpr expr : expressions) {
			expr.asCast()
					.filter(cast -> cast.castsTo(String.class))
					.ifPresent(results::add);
		}
		
		return results;
	}
	
	/**
	 * Example 3: Find all string concatenations.
	 */
	public static List<InfixExpr> findStringConcatenations(List<ASTExpr> expressions) {
		List<InfixExpr> results = new ArrayList<>();
		
		for (ASTExpr expr : expressions) {
			expr.asInfix()
					.filter(InfixExpr::isStringConcatenation)
					.ifPresent(results::add);
		}
		
		return results;
	}
	
	/**
	 * Example 4: Find all static method calls.
	 */
	public static List<MethodInvocationExpr> findStaticMethodCalls(List<ASTExpr> expressions) {
		List<MethodInvocationExpr> results = new ArrayList<>();
		
		for (ASTExpr expr : expressions) {
			expr.asMethodInvocation()
					.filter(MethodInvocationExpr::isStatic)
					.ifPresent(results::add);
		}
		
		return results;
	}
}
