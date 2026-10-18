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
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.triggerpattern.internal.FqnAwarePlaceholderAstMatcher;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;
import org.sandbox.jdt.triggerpattern.api.GuardExpression.TruthValue;

/**
 * Engine for finding pattern matches in Java code.
 * 
 * <p>This engine traverses a compilation unit and identifies all occurrences
 * that match a given pattern. It supports expression, statement, annotation,
 * method call, import, field, constructor, and method declaration patterns
 * with placeholder binding.</p>
 * 
 * <p>Single-pattern and batch matching share {@link PatternIndex}, including
 * FQN resolution, original source-node bindings and statement-sequence ranges.</p>
 * <p><b>Supported Pattern Kinds:</b></p>
 * <ul>
 *   <li>{@link PatternKind#EXPRESSION} - Expressions like {@code $x + 1}</li>
 *   <li>{@link PatternKind#STATEMENT} - Statements like {@code return $x;}</li>
 *   <li>{@link PatternKind#ANNOTATION} - Annotations like {@code @Before}</li>
 *   <li>{@link PatternKind#METHOD_CALL} - Method invocations like {@code assertEquals($a, $b)}</li>
 *   <li>{@link PatternKind#IMPORT} - Import declarations</li>
 *   <li>{@link PatternKind#FIELD} - Field declarations</li>
 *   <li>{@link PatternKind#CONSTRUCTOR} - Constructor invocations like {@code new String($bytes)}</li>
 *   <li>{@link PatternKind#METHOD_DECLARATION} - Method declarations like {@code void dispose()}</li>
 *   <li>{@link PatternKind#TYPE_DECLARATION} - Type declaration headers</li>
 *   <li>{@link PatternKind#DECLARATION} - Local variable declarations</li>
 *   <li>{@link PatternKind#BLOCK} - Entire blocks with variadic statement placeholders</li>
 *   <li>{@link PatternKind#STATEMENT_SEQUENCE} - Consecutive original statements within a block</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public class TriggerPatternEngine {
	
	private final PatternParser parser = new PatternParser();
	
	/**
	 * Finds all matches of the given pattern in the compilation unit.
	 * 
	 * @param cu the compilation unit to search
	 * @param pattern the pattern to match
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatches(CompilationUnit cu, Pattern pattern) {
		if (cu == null || pattern == null) {
			return List.of();
		}
		
		TransformationRule rule = TransformationRule.of(pattern, List.of());
		return new PatternIndex(List.of(rule)).findAllMatches(cu).getOrDefault(rule, List.of());
	}

	/**
	 * Finds matches eligible for invoking an automatic rewrite handler. Structural
	 * candidates with unresolved concrete API identities remain available through
	 * {@link #findMatches(CompilationUnit, Pattern)} for diagnostics.
	 */
	public List<Match> findExecutableMatches(CompilationUnit cu, Pattern pattern) {
		return findMatches(cu, pattern).stream()
				.filter(match -> match.getUnknownSemanticRequirements().isEmpty()).toList();
	}
	
	/**
	 * Finds all matches of the given pattern in the compilation unit.
	 * 
	 * <p>Binding resolution is enabled for concrete API identities, type constraints
	 * and override detection via {@link IMethodBinding}.</p>
	 * 
	 * @param icu the ICompilationUnit to search
	 * @param pattern the pattern to match
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatches(ICompilationUnit icu, Pattern pattern) {
		if (icu == null || pattern == null) {
			return List.of();
		}
		
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(icu);
		astParser.setResolveBindings(true);
		astParser.setProject(icu.getJavaProject());
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		
		return findMatches(cu, pattern);
	}
	
	/**
	 * Finds all AST nodes of the given types in the compilation unit.
	 * 
	 * <p>This method is used for @TriggerTreeKind hints that match based on
	 * AST node types rather than patterns.</p>
	 * 
	 * @param cu the compilation unit to search
	 * @param nodeTypes array of AST node type constants (e.g., ASTNode.METHOD_DECLARATION)
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatchesByNodeType(CompilationUnit cu, int... nodeTypes) {
		if (cu == null || nodeTypes == null || nodeTypes.length == 0) {
			return List.of();
		}
		
		List<Match> matches = new ArrayList<>();
		
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				int nodeType = node.getNodeType();
				for (int targetType : nodeTypes) {
					if (nodeType == targetType) {
						createMatchWithAutoBindings(node, matches);
						break;
					}
				}
			}
		});
		
		return matches;
	}
	
	/**
	 * Finds all AST nodes of the given types in the compilation unit.
	 * 
	 * <p>This method is used for @TriggerTreeKind hints that match based on
	 * AST node types rather than patterns.</p>
	 * 
	 * @param icu the ICompilationUnit to search
	 * @param nodeTypes array of AST node type constants (e.g., ASTNode.METHOD_DECLARATION)
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatchesByNodeType(ICompilationUnit icu, int... nodeTypes) {
		if (icu == null || nodeTypes == null || nodeTypes.length == 0) {
			return List.of();
		}
		
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(icu);
		astParser.setResolveBindings(false);
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		
		return findMatchesByNodeType(cu, nodeTypes);
	}
	
	/**
	 * Finds all matches of the given pattern in the compilation unit, applying
	 * body constraints to filter the results.
	 * 
	 * <p>This method extends {@link #findMatches(CompilationUnit, Pattern)} by also
	 * checking the body of matched METHOD_DECLARATION nodes. The body constraint
	 * specifies a pattern that must (or must not, when negated) be present in the
	 * method body.</p>
	 * 
	 * @param cu the compilation unit to search
	 * @param pattern the pattern to match
	 * @param bodyConstraintPattern the pattern to look for in the method body
	 * @param bodyConstraintKind the kind of the body constraint pattern
	 * @param negate if {@code true}, the constraint triggers when the pattern is NOT found
	 * @return a list of all matches that satisfy both the pattern and body constraint (may be empty)
	 * @since 1.2.6
	 */
	public List<Match> findMatchesWithConstraints(CompilationUnit cu, Pattern pattern,
			String bodyConstraintPattern, PatternKind bodyConstraintKind, boolean negate) {
		if (cu == null || pattern == null || bodyConstraintPattern == null) {
			return List.of();
		}

		List<Match> signatureMatches = findExecutableMatches(cu, pattern);

		if (pattern.getKind() != PatternKind.METHOD_DECLARATION || bodyConstraintPattern.isEmpty()) {
			return signatureMatches;
		}

		Pattern bodyPattern = Pattern.of(bodyConstraintPattern, bodyConstraintKind);
		ASTNode bodyPatternNode = parser.parse(bodyPattern);
		if (bodyPatternNode == null) {
			return List.of();
		}

		List<Match> result = new ArrayList<>();
		for (Match match : signatureMatches) {
			ASTNode matchedNode = match.getMatchedNode();
			if (matchedNode instanceof MethodDeclaration methodDecl) {
				Block body = methodDecl.getBody();
				if (body != null) {
					TruthValue found = containsPattern(body, bodyPatternNode);
					// negate=true means trigger when pattern NOT found; negate=false means trigger when found
					if (found != TruthValue.UNKNOWN && (found == TruthValue.MATCH) != negate) {
						result.add(match);
					}
				} else if (negate) {
					// Methods without a body (e.g., abstract/interface methods) trivially lack the pattern,
					// so they satisfy a negated body constraint and are included in the results.
					result.add(match);
				}
			}
		}
		return result;
	}

	/**
	 * Checks if a method body contains a match for the given pattern.
	 * 
	 * <p>Searches the entire body subtree, including nested blocks and
	 * control flow structures.</p>
	 * 
	 * @param body the method body to search
	 * @param bodyPatternNode the pattern AST node to look for
	 * @return whether presence or absence is proven, or unknown when a necessary
	 *         API identity cannot be resolved
	 */
	private TruthValue containsPattern(Block body, ASTNode bodyPatternNode) {
		TruthValue[] found = {TruthValue.NO_MATCH};
		body.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				SemanticRewritePlanContext.checkCanceled();
				if (found[0] == TruthValue.MATCH) {
					return false;
				}
				// Try matching against statements
				if (node instanceof Statement || node instanceof Expression) {
					PlaceholderAstMatcher matcher = new FqnAwarePlaceholderAstMatcher();
					boolean matches = bodyPatternNode.subtreeMatch(matcher, node);
					if (matches && matcher.getUnknownSemanticRequirements().isEmpty()) {
						found[0] = TruthValue.MATCH;
					} else if (!matcher.getUnknownSemanticRequirements().isEmpty()) {
						found[0] = TruthValue.UNKNOWN;
					}
				}
				return found[0] != TruthValue.MATCH;
			}
		});
		return found[0];
	}

	/**
	 * Creates a Match with auto-bindings for a matched node.
	 * 
	 * @param node the matched node
	 * @param matches the list to add the match to
	 */
	private void createMatchWithAutoBindings(ASTNode node, List<Match> matches) {
		createMatchWithAutoBindings(node, new HashMap<>(), matches);
	}
	
	/**
	 * Creates a Match with auto-bindings for a matched node.
	 * 
	 * @param node the matched node
	 * @param bindings existing bindings (e.g., from pattern matching)
	 * @param matches the list to add the match to
	 */
	private void createMatchWithAutoBindings(ASTNode node, Map<String, Object> bindings, List<Match> matches) {
		matches.add(PatternMatchSupport.createMatch(node, bindings,
				node.getStartPosition(), node.getLength(), List.of()));
	}
}
