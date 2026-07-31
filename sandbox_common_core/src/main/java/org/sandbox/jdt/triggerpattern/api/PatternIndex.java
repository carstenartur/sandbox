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
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
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
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.triggerpattern.internal.FqnAwarePlaceholderAstMatcher;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;
import org.sandbox.jdt.triggerpattern.internal.TypeDeclarationHeaderMatcher;
import org.sandbox.jdt.triggerpattern.internal.TypeDeclarationPatternParser;

/** Indexes transformation rules by pattern kind for one-pass AST matching. */
public final class PatternIndex {

	private final Map<PatternKind, List<IndexEntry>> rulesByKind;
	private final PatternParser parser;
	private final TypeDeclarationPatternParser typeParser;
	private boolean caseInsensitive;

	public PatternIndex(List<TransformationRule> rules) {
		this.parser= new PatternParser();
		this.typeParser= new TypeDeclarationPatternParser();
		this.rulesByKind= buildIndex(rules);
	}

	public void setCaseInsensitive(boolean caseInsensitive) {
		this.caseInsensitive= caseInsensitive;
	}

	private Map<PatternKind, List<IndexEntry>> buildIndex(List<TransformationRule> rules) {
		Map<PatternKind, List<IndexEntry>> index= new EnumMap<>(PatternKind.class);
		for (TransformationRule rule : rules) {
			Pattern sourcePattern= rule.sourcePattern();
			ASTNode patternNode= sourcePattern.getKind() == PatternKind.TYPE_DECLARATION
					? typeParser.parse(sourcePattern.getValue()) : parser.parse(sourcePattern);
			if (patternNode == null) {
				continue;
			}
			PatternKind kind= sourcePattern.getKind();
			index.computeIfAbsent(kind, ignored -> new ArrayList<>())
					.add(new IndexEntry(rule, sourcePattern, patternNode));
		}
		return index;
	}

	public int size() {
		return rulesByKind.values().stream().mapToInt(List::size).sum();
	}

	public int kindCount() {
		return rulesByKind.size();
	}

	public List<TransformationRule> getRulesForKind(PatternKind kind) {
		List<IndexEntry> entries= rulesByKind.getOrDefault(kind, Collections.emptyList());
		return entries.stream().map(IndexEntry::rule).toList();
	}

	public Map<TransformationRule, List<Match>> findAllMatches(CompilationUnit cu) {
		if (cu == null || rulesByKind.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<TransformationRule, List<Match>> results= new java.util.LinkedHashMap<>();
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				checkNodeAgainstIndex(node, results);
			}
		});
		return results;
	}

	private void checkNodeAgainstIndex(ASTNode node, Map<TransformationRule, List<Match>> results) {
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
		if (node instanceof AbstractTypeDeclaration) {
			matchAgainstKind(node, PatternKind.TYPE_DECLARATION, results);
		}
		if (node instanceof VariableDeclarationStatement) {
			matchAgainstKind(node, PatternKind.DECLARATION, results);
		}
		if (node instanceof Block block) {
			matchAgainstKind(node, PatternKind.BLOCK, results);
			matchStatementSequences(block, results);
		}
	}

	private void matchAgainstKind(ASTNode node, PatternKind kind,
			Map<TransformationRule, List<Match>> results) {
		List<IndexEntry> entries= rulesByKind.get(kind);
		if (entries == null || entries.isEmpty()) {
			return;
		}
		for (IndexEntry entry : entries) {
			PlaceholderAstMatcher matcher= kind == PatternKind.TYPE_DECLARATION
					? new TypeDeclarationHeaderMatcher() : new FqnAwarePlaceholderAstMatcher();
			matcher.setCaseInsensitive(caseInsensitive);
			if (entry.patternNode().subtreeMatch(matcher, node)) {
				Match match= new Match(node, matcher.getBindings(),
						node.getStartPosition(), node.getLength());
				results.computeIfAbsent(entry.rule(), ignored -> new ArrayList<>()).add(match);
			}
		}
	}

	private void matchStatementSequences(Block block,
			Map<TransformationRule, List<Match>> results) {
		List<IndexEntry> entries= rulesByKind.get(PatternKind.STATEMENT_SEQUENCE);
		if (entries == null || entries.isEmpty()) {
			return;
		}
		@SuppressWarnings("unchecked")
		List<Statement> statements= block.statements();
		for (IndexEntry entry : entries) {
			ASTNode patternNode= entry.patternNode();
			if (!(patternNode instanceof Block patternBlock)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			List<Statement> patternStatements= patternBlock.statements();
			int patternSize= patternStatements.size();
			if (patternSize == 0 || patternSize > statements.size()) {
				continue;
			}
			for (int start= 0; start <= statements.size() - patternSize; start++) {
				Block syntheticBlock= block.getAST().newBlock();
				for (int index= 0; index < patternSize; index++) {
					syntheticBlock.statements().add(ASTNode.copySubtree(block.getAST(),
							statements.get(start + index)));
				}
				PlaceholderAstMatcher matcher= new FqnAwarePlaceholderAstMatcher();
				matcher.setCaseInsensitive(caseInsensitive);
				if (patternBlock.subtreeMatch(matcher, syntheticBlock)) {
					int offset= statements.get(start).getStartPosition();
					Statement last= statements.get(start + patternSize - 1);
					int length= last.getStartPosition() + last.getLength() - offset;
					Match match= new Match(block, matcher.getBindings(), offset, length);
					results.computeIfAbsent(entry.rule(), ignored -> new ArrayList<>()).add(match);
				}
			}
		}
	}

	private record IndexEntry(TransformationRule rule, Pattern sourcePattern, ASTNode patternNode) {
	}
}
