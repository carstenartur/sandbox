/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;

/**
 * Tests for {@link PatternParser}.
 */
public class PatternParserTest {
	
	private final PatternParser parser = new PatternParser();
	
	@Test
	public void testParseSimpleExpression() {
		Pattern pattern = new Pattern("$x + 1", PatternKind.EXPRESSION);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof Expression, "Node should be an Expression");
		assertTrue(node instanceof InfixExpression, "Node should be an InfixExpression");
	}
	
	@Test
	public void testParseMethodInvocation() {
		Pattern pattern = new Pattern("$obj.toString()", PatternKind.EXPRESSION);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof MethodInvocation, "Node should be a MethodInvocation");
	}
	
	@Test
	public void testParsePlaceholderExpression() {
		Pattern pattern = new Pattern("$x", PatternKind.EXPRESSION);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof SimpleName, "Node should be a SimpleName");
		SimpleName name = (SimpleName) node;
		assertTrue(name.getIdentifier().equals("$x"), "Identifier should be $x");
	}
	
	@Test
	public void testParseIfStatement() {
		Pattern pattern = new Pattern("if ($cond) $then;", PatternKind.STATEMENT);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof Statement, "Node should be a Statement");
		assertTrue(node instanceof IfStatement, "Node should be an IfStatement");
	}
	
	@Test
	public void testParseReturnStatement() {
		Pattern pattern = new Pattern("return $x;", PatternKind.STATEMENT);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof Statement, "Node should be a Statement");
	}
}
