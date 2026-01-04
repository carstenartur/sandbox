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
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Engine for finding pattern matches in Java code.
 * 
 * <p>This engine traverses a compilation unit and identifies all occurrences
 * that match a given pattern. It supports both expression and statement patterns
 * with placeholder binding.</p>
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
		
		ASTNode patternNode = parser.parse(pattern);
		if (patternNode == null) {
			return List.of();
		}
		
		List<Match> matches = new ArrayList<>();
		
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				// Only check nodes of compatible types
				if (pattern.getKind() == PatternKind.EXPRESSION && node instanceof Expression) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.STATEMENT && node instanceof Statement) {
					checkMatch(node, patternNode, matches);
				}
			}
		});
		
		return matches;
	}
	
	/**
	 * Finds all matches of the given pattern in the compilation unit.
	 * 
	 * @param icu the ICompilationUnit to search
	 * @param pattern the pattern to match
	 * @return a list of all matches (may be empty)
	 * @throws JavaModelException if the compilation unit cannot be parsed
	 */
	public List<Match> findMatches(ICompilationUnit icu, Pattern pattern) throws JavaModelException {
		if (icu == null || pattern == null) {
			return List.of();
		}
		
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(icu);
		astParser.setResolveBindings(false);
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		
		return findMatches(cu, pattern);
	}
	
	/**
	 * Checks if a candidate node matches the pattern node and adds a Match if it does.
	 */
	private void checkMatch(ASTNode candidate, ASTNode patternNode, List<Match> matches) {
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		
		if (patternNode.subtreeMatch(matcher, candidate)) {
			// We have a match! Create a Match object with bindings and position
			int offset = candidate.getStartPosition();
			int length = candidate.getLength();
			
			Match match = new Match(candidate, matcher.getBindings(), offset, length);
			matches.add(match);
		}
	}
}
