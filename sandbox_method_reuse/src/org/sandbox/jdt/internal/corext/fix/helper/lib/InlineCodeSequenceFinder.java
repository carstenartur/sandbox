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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Inline Code Sequence Finder - Searches for inline code sequences in method bodies
 * 
 * This class traverses the AST to find code sequences within method bodies that
 * match the body of a target method and could be replaced by a method call.
 */
public class InlineCodeSequenceFinder {
	
	/**
	 * Result class containing information about a found inline code sequence
	 */
	public static class InlineSequenceMatch {
		private final MethodDeclaration containingMethod;
		private final List<Statement> matchingStatements;
		private final VariableMapping variableMapping;
		
		public InlineSequenceMatch(MethodDeclaration containingMethod, List<Statement> matchingStatements, VariableMapping variableMapping) {
			this.containingMethod = containingMethod;
			this.matchingStatements = matchingStatements;
			this.variableMapping = variableMapping;
		}
		
		public MethodDeclaration getContainingMethod() {
			return containingMethod;
		}
		
		public List<Statement> getMatchingStatements() {
			return matchingStatements;
		}
		
		public VariableMapping getVariableMapping() {
			return variableMapping;
		}
	}
	
	/**
	 * Find inline code sequences in the compilation unit that match the target method body
	 * 
	 * @param cu The compilation unit to search
	 * @param targetMethod The method whose body we're looking for inline
	 * @return List of matches found
	 */
	public static List<InlineSequenceMatch> findInlineSequences(CompilationUnit cu, MethodDeclaration targetMethod) {
		if (cu == null || targetMethod == null || targetMethod.getBody() == null) {
			return new ArrayList<>();
		}
		
		List<InlineSequenceMatch> matches = new ArrayList<>();
		Block targetBody = targetMethod.getBody();
		List<Statement> targetStatements = getStatements(targetBody);
		
		if (targetStatements.isEmpty()) {
			return matches;
		}
		
		// Visit all methods in the compilation unit
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				// Don't search in the target method itself
				if (node == targetMethod) {
					return false;
				}
				
				// Search for matching sequences in this method
				searchInMethod(node, targetStatements, matches);
				return true;
			}
		});
		
		return matches;
	}
	
	/**
	 * Search for matching code sequences within a single method
	 */
	private static void searchInMethod(MethodDeclaration method, List<Statement> targetStatements, List<InlineSequenceMatch> matches) {
		if (method.getBody() == null) {
			return;
		}
		
		List<Statement> methodStatements = getStatements(method.getBody());
		int targetLength = targetStatements.size();
		
		// Try to find the target sequence at each position
		for (int i = 0; i <= methodStatements.size() - targetLength; i++) {
			List<Statement> candidateSequence = methodStatements.subList(i, i + targetLength);
			
			// Try to match this sequence with the target
			VariableMapping mapping = CodeSequenceMatcher.matchSequence(targetStatements, candidateSequence);
			
			if (mapping != null && mapping.isValid()) {
				// Found a match!
				matches.add(new InlineSequenceMatch(method, new ArrayList<>(candidateSequence), mapping));
			}
		}
	}
	
	/**
	 * Extract statements from a block (helper method)
	 */
	@SuppressWarnings("unchecked")
	private static List<Statement> getStatements(Block block) {
		return block.statements();
	}
}
