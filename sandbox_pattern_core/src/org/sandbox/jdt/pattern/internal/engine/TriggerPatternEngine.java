/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.internal.engine;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.pattern.api.Match;
import org.sandbox.jdt.pattern.api.Pattern;
import org.sandbox.jdt.pattern.api.PatternKind;
import org.sandbox.jdt.pattern.internal.matcher.PlaceholderAstMatcher;
import org.sandbox.jdt.pattern.internal.parser.PatternParser;

/**
 * Engine for finding pattern matches in Java compilation units.
 * <p>
 * This engine parses patterns and traverses AST nodes to find all matches.
 * Each match includes the matched node, placeholder bindings, and source position.
 * </p>
 */
public class TriggerPatternEngine {

private final PatternParser parser;

/**
 * Creates a new trigger pattern engine.
 */
public TriggerPatternEngine() {
this.parser = new PatternParser();
}

/**
 * Finds all matches of the given pattern in the compilation unit.
 * 
 * @param cu the compilation unit to search
 * @param pattern the pattern to match
 * @return list of matches (empty if no matches found)
 */
public List<Match> findMatches(CompilationUnit cu, Pattern pattern) {
// Parse the pattern into an AST node
ASTNode patternNode = parser.parse(pattern);
if (patternNode == null) {
return List.of(); // Invalid pattern
}

List<Match> matches = new ArrayList<>();

// Create a visitor to traverse the compilation unit
cu.accept(new ASTVisitor() {
@Override
public boolean preVisit2(ASTNode node) {
// Only check nodes of the appropriate type
if (pattern.getKind() == PatternKind.EXPRESSION && node instanceof Expression) {
tryMatch(node, patternNode, matches);
} else if (pattern.getKind() == PatternKind.STATEMENT && node instanceof Statement) {
tryMatch(node, patternNode, matches);
}
return true; // Continue visiting children
}
});

return matches;
}

/**
 * Attempts to match a single node against the pattern.
 * 
 * @param candidateNode the node from source code
 * @param patternNode the pattern AST node
 * @param matches the list to add matches to
 */
private void tryMatch(ASTNode candidateNode, ASTNode patternNode, List<Match> matches) {
PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();

if (matcher.safeMatch(patternNode, candidateNode)) {
// Found a match - create Match object with bindings and position
int offset = candidateNode.getStartPosition();
int length = candidateNode.getLength();

Match match = new Match(candidateNode, matcher.getBindings(), offset, length);
matches.add(match);
}
}
}
