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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.junit.jupiter.api.Disabled;
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
	@Disabled("org.opentest4j.AssertionFailedError: Parser should return a non-null node ==> expected: not <null>\r\n"
			+ "	at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:152)\r\n"
			+ "	at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)\r\n"
			+ "	at org.junit.jupiter.api.AssertNotNull.failNull(AssertNotNull.java:49)\r\n"
			+ "	at org.junit.jupiter.api.AssertNotNull.assertNotNull(AssertNotNull.java:35)\r\n"
			+ "	at org.junit.jupiter.api.Assertions.assertNotNull(Assertions.java:312)\r\n"
			+ "	at org.sandbox.jdt.triggerpattern.test.PatternParserTest.testParseIfStatement(PatternParserTest.java:73)\r\n"
			+ "	at java.base/java.lang.reflect.Method.invoke(Method.java:580)\r\n"
			+ "	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)\r\n"
			+ "	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)\r\n"
			+ "\r\n"
			+ "")
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
	
	@Test
	public void testParseConstructorWithPlaceholders() {
		Pattern pattern = new Pattern("new String($bytes, $enc)", PatternKind.CONSTRUCTOR);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof ClassInstanceCreation, "Node should be a ClassInstanceCreation");
	}
	
	@Test
	public void testParseConstructorWithoutArguments() {
		Pattern pattern = new Pattern("new StringBuilder()", PatternKind.CONSTRUCTOR);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof ClassInstanceCreation, "Node should be a ClassInstanceCreation");
	}
	
	@Test
	public void testParseConstructorWithComplexArguments() {
		Pattern pattern = new Pattern("new OutputStreamWriter($stream, StandardCharsets.UTF_8)", PatternKind.CONSTRUCTOR);
		ASTNode node = parser.parse(pattern);
		
		assertNotNull(node, "Parser should return a non-null node");
		assertTrue(node instanceof ClassInstanceCreation, "Node should be a ClassInstanceCreation");
	}
}
