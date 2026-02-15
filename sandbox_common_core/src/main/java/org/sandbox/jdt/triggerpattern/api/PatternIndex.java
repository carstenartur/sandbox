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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Indexes transformation rules by their {@link PatternKind} for efficient
 * batch matching against a compilation unit.
 *
 * <p>Instead of traversing the AST once per rule (O(N*M) where N is the number
 * of AST nodes and M is the number of rules), a {@code PatternIndex} groups
 * rules by their pattern kind and traverses the AST only once. Each visited
 * node is checked against only the rules of the matching kind.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * PatternIndex index = new PatternIndex(hintFile.getRules());
 * Map&lt;TransformationRule, List&lt;Match&gt;&gt; results = index.findAllMatches(cu);
 * </pre>
 *
 * @since 1.3.3
 */
public final class PatternIndex {

	private final Map<PatternKind, List<IndexEntry>> rulesByKind;
	private final PatternParser parser;

	/**
	 * Creates a new pattern index from a list of transformation rules.
	 *
	 * <p>Pre-parses all source patterns and groups them by kind for efficient
	 * batch matching.</p>
	 *
	 * @param rules the rules to index
	 */
	public PatternIndex(List<TransformationRule> rules) {
		this.parser = new PatternParser();
		this.rulesByKind = buildIndex(rules);
	}

	private Map<PatternKind, List<IndexEntry>> buildIndex(List<TransformationRule> rules) {
		Map<PatternKind, List<IndexEntry>> index = new EnumMap<>(PatternKind.class);

		for (TransformationRule rule : rules) {
			Pattern sourcePattern = rule.sourcePattern();
			ASTNode patternNode = parser.parse(sourcePattern);
			if (patternNode == null) {
				continue;
			}

			PatternKind kind = sourcePattern.getKind();
			index.computeIfAbsent(kind, k -> new ArrayList<>())
					.add(new IndexEntry(rule, sourcePattern, patternNode));
		}

		return index;
	}

	/**
	 * Returns the number of indexed rules.
	 *
	 * @return the total number of rules in the index
	 */
	public int size() {
		return rulesByKind.values().stream().mapToInt(List::size).sum();
	}

	/**
	 * Returns the number of distinct pattern kinds in the index.
	 *
	 * @return the number of distinct pattern kinds
	 */
	public int kindCount() {
		return rulesByKind.size();
	}

	/**
	 * Returns the rules for a specific pattern kind.
	 *
	 * @param kind the pattern kind
	 * @return unmodifiable list of transformation rules for the given kind
	 */
	public List<TransformationRule> getRulesForKind(PatternKind kind) {
		List<IndexEntry> entries = rulesByKind.getOrDefault(kind, Collections.emptyList());
		return entries.stream().map(IndexEntry::rule).toList();
	}

	/**
	 * Finds all matches for all indexed rules in a single AST traversal.
	 *
	 * <p>This is significantly more efficient than calling
	 * {@link TriggerPatternEngine#findMatches(CompilationUnit, Pattern)} once per
	 * rule, because the AST is traversed only once.</p>
	 *
	 * @param cu the compilation unit to search
	 * @return map from each rule that had matches to its list of matches
	 */
	public Map<TransformationRule, List<Match>> findAllMatches(CompilationUnit cu) {
		if (cu == null || rulesByKind.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<TransformationRule, List<Match>> results = new java.util.LinkedHashMap<>();

		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				checkNodeAgainstIndex(node, results);
			}
		});

		return results;
	}

	/**
	 * Checks a single AST node against all applicable indexed patterns.
	 */
	private void checkNodeAgainstIndex(ASTNode node, Map<TransformationRule, List<Match>> results) {
		// Determine which pattern kinds could match this node type
		if (node instanceof Expression) {
			matchAgainstKind(node, PatternKind.EXPRESSION, results);
		}
		if (node instanceof MethodInvocation) {
			matchAgainstKind(node, PatternKind.METHOD_CALL, results);
		}
		if (node instanceof ClassInstanceCreation) {
			matchAgainstKind(node, PatternKind.CONSTRUCTOR, results);
		}
		if (node instanceof Statement) {
			matchAgainstKind(node, PatternKind.STATEMENT, results);
		}
		if (node instanceof Annotation) {
			matchAgainstKind(node, PatternKind.ANNOTATION, results);
		}
		if (node instanceof ImportDeclaration) {
			matchAgainstKind(node, PatternKind.IMPORT, results);
		}
		if (node instanceof FieldDeclaration) {
			matchAgainstKind(node, PatternKind.FIELD, results);
		}
		if (node instanceof MethodDeclaration) {
			matchAgainstKind(node, PatternKind.METHOD_DECLARATION, results);
		}
		if (node instanceof Block block) {
			matchAgainstKind(node, PatternKind.BLOCK, results);
			matchStatementSequences(block, results);
		}
	}

	/**
	 * Attempts to match a node against all rules of the given pattern kind.
	 */
	private void matchAgainstKind(ASTNode node, PatternKind kind,
			Map<TransformationRule, List<Match>> results) {
		List<IndexEntry> entries = rulesByKind.get(kind);
		if (entries == null || entries.isEmpty()) {
			return;
		}

		for (IndexEntry entry : entries) {
			PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
			if (entry.patternNode().subtreeMatch(matcher, node)) {
				Match match = new Match(node, matcher.getBindings(),
						node.getStartPosition(), node.getLength());
				results.computeIfAbsent(entry.rule(), r -> new ArrayList<>()).add(match);
			}
		}
	}

	/**
	 * Attempts statement sequence matching for all STATEMENT_SEQUENCE rules.
	 */
	private void matchStatementSequences(Block block,
			Map<TransformationRule, List<Match>> results) {
		List<IndexEntry> entries = rulesByKind.get(PatternKind.STATEMENT_SEQUENCE);
		if (entries == null || entries.isEmpty()) {
			return;
		}

		@SuppressWarnings("unchecked")
		List<Statement> statements = block.statements();

		for (IndexEntry entry : entries) {
			ASTNode patternNode = entry.patternNode();
			if (!(patternNode instanceof Block patternBlock)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			List<Statement> patternStatements = patternBlock.statements();
			int patternSize = patternStatements.size();
			if (patternSize == 0 || patternSize > statements.size()) {
				continue;
			}

			// Sliding window
			for (int i = 0; i <= statements.size() - patternSize; i++) {
				boolean allMatch = true;
				PlaceholderAstMatcher combinedMatcher = new PlaceholderAstMatcher();
				for (int j = 0; j < patternSize; j++) {
					PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
					if (!patternStatements.get(j).subtreeMatch(matcher, statements.get(i + j))) {
						allMatch = false;
						break;
					}
					combinedMatcher.mergeBindings(matcher);
				}
				if (allMatch) {
					Statement first = statements.get(i);
					Statement last = statements.get(i + patternSize - 1);
					int offset = first.getStartPosition();
					int length = (last.getStartPosition() + last.getLength()) - offset;
					Match match = new Match(first, combinedMatcher.getBindings(), offset, length);
					results.computeIfAbsent(entry.rule(), r -> new ArrayList<>()).add(match);
				}
			}
		}
	}

	/**
	 * An entry in the pattern index, containing a rule and its pre-parsed pattern node.
	 */
	private record IndexEntry(TransformationRule rule, Pattern sourcePattern, ASTNode patternNode) {
	}
}
