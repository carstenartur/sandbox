/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.sandbox.jdt.triggerpattern.api.Match;

/** Substitutes Java identifier tokens once, preserving literals and expression grouping. */
public final class BindingSubstitution {

	private BindingSubstitution() {
	}

	private record ExpressionRange(int start, int end) {
	}

	public static String substitute(String pattern, Match match) {
		if (pattern == null || match.getBindings().isEmpty()) {
			return pattern;
		}
		IScanner scanner = ToolFactory.createScanner(false, false, false, JavaCore.VERSION_25);
		scanner.setSource(pattern.toCharArray());
		StringBuilder result = new StringBuilder();
		List<ExpressionRange> groupedExpressions = new ArrayList<>();
		int copiedUntil = 0;
		try {
			for (int token = scanner.getNextToken(); token != ITerminalSymbols.TokenNameEOF; token = scanner.getNextToken()) {
				int start = scanner.getCurrentTokenStartPosition();
				if (token != ITerminalSymbols.TokenNameIdentifier || start < copiedUntil) {
					continue;
				}
				String name = new String(scanner.getCurrentTokenSource(), Charset.defaultCharset());
				Object value = match.getBindings().get(name);
				if (value == null) {
					continue;
				}
				int end = scanner.getCurrentTokenEndPosition() + 1;
				StringBuilder replacement = new StringBuilder();
				int replacementOffset = result.length() + start - copiedUntil;
				if (value instanceof List<?> nodes) {
					if (pattern.startsWith(".length", end) && (end + 7 == pattern.length() //$NON-NLS-1$
							|| !Character.isJavaIdentifierPart(pattern.charAt(end + 7)))) {
						replacement.append(nodes.size());
						end += 7;
					} else if (end < pattern.length() && pattern.charAt(end) == '[') {
						int closing = pattern.indexOf(']', end);
						if (closing < 0) {
							continue;
						}
						try {
							int index = Integer.parseInt(pattern.substring(end + 1, closing));
							if (index < 0) {
								index += nodes.size();
							}
							if (index < 0 || index >= nodes.size()) {
								continue;
							}
							appendValue(replacement, nodes.get(index), groupedExpressions, replacementOffset);
							end = closing + 1;
						} catch (NumberFormatException invalidIndex) {
							continue;
						}
					} else {
						boolean statements = nodes.stream().allMatch(Statement.class::isInstance);
						for (Object node : nodes) {
							if (!replacement.isEmpty()) {
								replacement.append(statements ? "\n" : ", "); //$NON-NLS-1$ //$NON-NLS-2$
							}
							appendValue(replacement, node, groupedExpressions, replacementOffset);
						}
						if (statements) {
							int semicolon = end;
							while (semicolon < pattern.length() && Character.isWhitespace(pattern.charAt(semicolon))) {
								semicolon++;
							}
							if (semicolon < pattern.length() && pattern.charAt(semicolon) == ';') {
								end = semicolon + 1;
							}
						}
					}
				} else {
					appendValue(replacement, value, groupedExpressions, replacementOffset);
				}
				result.append(pattern, copiedUntil, start).append(replacement);
				copiedUntil = end;
			}
		} catch (InvalidInputException invalidJava) {
			throw new IllegalArgumentException("Replacement is not lexically valid Java", invalidJava); //$NON-NLS-1$
		}
		return groupExpressionBindings(result.append(pattern, copiedUntil, pattern.length()).toString(),
				groupedExpressions, match);
	}

	private static void appendValue(StringBuilder target, Object value, List<ExpressionRange> groupedExpressions,
			int offset) {
		String text = value instanceof ASTNode node ? node.toString().stripTrailing() : String.valueOf(value);
		if (value instanceof Expression expression && requiresGrouping(expression)) {
			groupedExpressions.add(new ExpressionRange(offset + target.length(), offset + target.length() + text.length()));
		}
		target.append(text);
	}

	private static String groupExpressionBindings(String source, List<ExpressionRange> groupedExpressions, Match match) {
		if (groupedExpressions.isEmpty()) {
			return source;
		}
		// Parse the substituted text before grouping: a parenthesized assignment or
		// update is not a legal StatementExpression. Exact source ranges distinguish
		// a whole statement/for-update from a binding nested inside that expression.
		Set<ExpressionRange> statementExpressions = new HashSet<>();
		var destination = match.getMatchedNode().getLocationInParent();
		boolean statementExpressionDestination = destination == ExpressionStatement.EXPRESSION_PROPERTY
				|| destination == ForStatement.INITIALIZERS_PROPERTY || destination == ForStatement.UPDATERS_PROPERTY
				|| destination == LambdaExpression.BODY_PROPERTY;
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_EXPRESSION);
		parser.setSource(source.toCharArray());
		var options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
		parser.setCompilerOptions(options);
		ASTNode context = parser.createAST(null);
		if (!(context instanceof Expression) || !hasCompleteSourceRange(context, source)
				|| (context.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			// Lambdas are expressions, not standalone statements: statement recovery
			// can drop their bodies. Use statements only when the whole template
			// cannot be parsed as one complete expression.
			parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setKind(ASTParser.K_STATEMENTS);
			parser.setSource(source.toCharArray());
			parser.setCompilerOptions(options);
			parser.setStatementsRecovery(true);
			context = parser.createAST(null);
		}
		context.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				// A bare expression needs its original AST destination; parser recovery
				// alone must not turn a nested replacement into a statement expression.
				if (statementExpressionDestination && node instanceof Expression expression
						&& hasCompleteSourceRange(expression, source)) {
					addStatementExpression(expression);
				}
			}

			@Override
			public boolean visit(ExpressionStatement node) {
				int end = node.getStartPosition() + node.getLength();
				if (end > 0 && end <= source.length() && source.charAt(end - 1) == ';') {
					addStatementExpression(node.getExpression());
				}
				return true;
			}

			@Override
			public boolean visit(ForStatement node) {
				for (Object initializer : node.initializers()) {
					addStatementExpression((Expression) initializer);
				}
				for (Object updater : node.updaters()) {
					addStatementExpression((Expression) updater);
				}
				return true;
			}

			@Override
			public boolean visit(LambdaExpression node) {
				if (node.getBody() instanceof Expression body) {
					addStatementExpression(body);
				}
				return true;
			}

			private void addStatementExpression(Expression expression) {
				if (expression instanceof Assignment || expression instanceof PostfixExpression
						|| expression instanceof PrefixExpression prefix
								&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
										|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT)) {
					statementExpressions.add(new ExpressionRange(expression.getStartPosition(),
							expression.getStartPosition() + expression.getLength()));
				}
			}
		});
		StringBuilder result = new StringBuilder();
		int copiedUntil = 0;
		for (ExpressionRange expression : groupedExpressions) {
			result.append(source, copiedUntil, expression.start());
			if (statementExpressions.contains(expression)) {
				result.append(source, expression.start(), expression.end());
			} else {
				result.append('(').append(source, expression.start(), expression.end()).append(')');
			}
			copiedUntil = expression.end();
		}
		return result.append(source, copiedUntil, source.length()).toString();
	}

	private static boolean hasCompleteSourceRange(ASTNode node, String source) {
		int start = node.getStartPosition();
		int end = start + node.getLength();
		return start >= 0 && end <= source.length() && isJavaTrivia(source.substring(0, start))
				&& isJavaTrivia(source.substring(end));
	}

	private static boolean isJavaTrivia(String text) {
		IScanner scanner = ToolFactory.createScanner(false, false, false, JavaCore.VERSION_25);
		scanner.setSource(text.toCharArray());
		try {
			return scanner.getNextToken() == ITerminalSymbols.TokenNameEOF;
		} catch (InvalidInputException invalidJava) {
			return false;
		}
	}

	private static boolean requiresGrouping(Expression expression) {
		return expression instanceof InfixExpression || expression instanceof ConditionalExpression
				|| expression instanceof Assignment || expression instanceof CastExpression
				|| expression instanceof InstanceofExpression || expression instanceof LambdaExpression
				|| expression instanceof PatternInstanceofExpression
				|| expression instanceof PrefixExpression || expression instanceof PostfixExpression
				|| expression instanceof MethodReference || expression instanceof SwitchExpression;
	}
}
