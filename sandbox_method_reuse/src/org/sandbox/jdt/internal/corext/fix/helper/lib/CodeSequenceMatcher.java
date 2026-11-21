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

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Code Sequence Matcher - Matches AST subtrees with variable normalization
 * 
 * This class compares code sequences to determine if they are structurally equivalent,
 * even when different variable names are used. It creates a mapping between variables
 * in the target and candidate sequences.
 */
public class CodeSequenceMatcher {
	
	/**
	 * Try to match a candidate sequence against a target sequence
	 * 
	 * @param targetSequence The target statement sequence (from the method being searched for)
	 * @param candidateSequence The candidate statement sequence (from inline code)
	 * @return VariableMapping if match successful, null otherwise
	 */
	public static VariableMapping matchSequence(List<Statement> targetSequence, List<Statement> candidateSequence) {
		if (targetSequence == null || candidateSequence == null) {
			return null;
		}
		
		if (targetSequence.size() != candidateSequence.size()) {
			return null;
		}
		
		VariableMapping mapping = new VariableMapping();
		VariableMappingMatcher matcher = new VariableMappingMatcher(mapping);
		
		// Match each statement pair
		for (int i = 0; i < targetSequence.size(); i++) {
			Statement targetStmt = targetSequence.get(i);
			Statement candidateStmt = candidateSequence.get(i);
			
			if (!matchStatement(targetStmt, candidateStmt, matcher)) {
				return null;
			}
		}
		
		return mapping;
	}
	
	/**
	 * Match two statements with variable mapping
	 */
	private static boolean matchStatement(Statement target, Statement candidate, VariableMappingMatcher matcher) {
		if (target == null || candidate == null) {
			return false;
		}
		
		// Special case: return statement can match variable declaration
		// This allows matching code like "return x + y;" with "int result = x + y;"
		if (target instanceof org.eclipse.jdt.core.dom.ReturnStatement && 
			candidate instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement) {
			
			org.eclipse.jdt.core.dom.ReturnStatement returnStmt = (org.eclipse.jdt.core.dom.ReturnStatement) target;
			org.eclipse.jdt.core.dom.VariableDeclarationStatement varDecl = (org.eclipse.jdt.core.dom.VariableDeclarationStatement) candidate;
			
			// Return statement must have an expression
			if (returnStmt.getExpression() == null) {
				return false;
			}
			
			// Variable declaration must have exactly one fragment with an initializer
			if (varDecl.fragments().size() != 1) {
				return false;
			}
			
			// Defensive check: ensure fragment list is not empty before accessing index 0
			if (varDecl.fragments().isEmpty()) {
				return false;
			}
			
			org.eclipse.jdt.core.dom.VariableDeclarationFragment fragment = 
				(org.eclipse.jdt.core.dom.VariableDeclarationFragment) varDecl.fragments().get(0);
			
			if (fragment.getInitializer() == null) {
				return false;
			}
			
			// Match the return expression against the initializer expression
			return returnStmt.getExpression().subtreeMatch(matcher, fragment.getInitializer());
		}
		
		// Must be same statement type for normal matching
		if (target.getNodeType() != candidate.getNodeType()) {
			return false;
		}
		
		// Use custom AST matcher that tracks variable mappings
		return target.subtreeMatch(matcher, candidate);
	}
	
	/**
	 * Custom AST Matcher that tracks variable name mappings
	 */
	private static class VariableMappingMatcher extends ASTMatcher {
		private final VariableMapping mapping;
		private final Map<String, String> bindings = new HashMap<>();
		
		public VariableMappingMatcher(VariableMapping mapping) {
			this.mapping = mapping;
		}
		
		@Override
		public boolean match(SimpleName node, Object other) {
			IBinding target = node.resolveBinding();
			if (target == null || target.isRecovered()) {
				return false;
			}
			if (other instanceof SimpleName name) {
				IBinding candidate = name.resolveBinding();
				if (candidate == null || candidate.isRecovered() || node.isDeclaration() != name.isDeclaration()) {
					return false;
				}
				if (target instanceof IVariableBinding variable && !variable.isField()
						&& candidate instanceof IVariableBinding otherVariable && !otherVariable.isField()) {
					if (!variable.getType().isEqualTo(otherVariable.getType())) {
						return false;
					}
					String previous = bindings.putIfAbsent(variable.getKey(), otherVariable.getKey());
					return (previous == null || previous.equals(otherVariable.getKey()))
							&& mapping.addMapping(node.getIdentifier(), name.getIdentifier());
				}
				// Method names, types and fields denote fixed symbols, never placeholders.
				return target.isEqualTo(candidate);
			}
			// Constants may be evaluated once at the call site. Arbitrary calls may not:
			// parameter substitution otherwise changes evaluation count, order and exceptions.
			if (target instanceof IVariableBinding variable && variable.isParameter()
					&& !node.isDeclaration() && other instanceof Expression expression
					&& expression.resolveConstantExpressionValue() != null
					&& variable.getType().isEqualTo(expression.resolveTypeBinding())) {
				return mapping.addExpressionMapping(node.getIdentifier(), expression);
			}
			return false;
		}
	}
}
