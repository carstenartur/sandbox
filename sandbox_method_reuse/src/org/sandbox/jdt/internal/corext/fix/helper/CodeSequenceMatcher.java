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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
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
		
		// Match each statement pair
		for (int i = 0; i < targetSequence.size(); i++) {
			Statement targetStmt = targetSequence.get(i);
			Statement candidateStmt = candidateSequence.get(i);
			
			if (!matchStatement(targetStmt, candidateStmt, mapping)) {
				return null;
			}
		}
		
		return mapping;
	}
	
	/**
	 * Match two statements with variable mapping
	 */
	private static boolean matchStatement(Statement target, Statement candidate, VariableMapping mapping) {
		if (target == null || candidate == null) {
			return false;
		}
		
		// Must be same statement type
		if (target.getNodeType() != candidate.getNodeType()) {
			return false;
		}
		
		// Use custom AST matcher that tracks variable mappings
		VariableMappingMatcher matcher = new VariableMappingMatcher(mapping);
		return target.subtreeMatch(matcher, candidate);
	}
	
	/**
	 * Custom AST Matcher that tracks variable name mappings
	 */
	private static class VariableMappingMatcher extends ASTMatcher {
		private final VariableMapping mapping;
		
		public VariableMappingMatcher(VariableMapping mapping) {
			this.mapping = mapping;
		}
		
		@Override
		public boolean match(SimpleName node, Object other) {
			if (!(other instanceof SimpleName)) {
				return false;
			}
			
			SimpleName otherName = (SimpleName) other;
			String targetName = node.getIdentifier();
			String candidateName = otherName.getIdentifier();
			
			// Check if this is a consistent mapping
			return mapping.addMapping(targetName, candidateName);
		}
	}
}

/**
 * Represents a one-to-one mapping between variable names in the target code and
 * variable names in the candidate code.
 * <p>
 * This helper is used by {@link CodeSequenceMatcher} (via
 * {@code VariableMappingMatcher}) to normalize variable names when comparing
 * AST subtrees. It maintains a bidirectional mapping (target → candidate and
 * candidate → target) and enforces consistency: once a name pair is mapped,
 * subsequent mappings for either name must match the original pair or the
 * mapping is considered invalid.
 */
class VariableMapping {
	// Maps from target variable name to candidate variable name
	private final Map<String, String> targetToCandidate = new HashMap<>();
	// Maps from candidate variable name to target variable name (for reverse lookup)
	private final Map<String, String> candidateToTarget = new HashMap<>();
	
	/**
	 * Add or verify a mapping between target and candidate variable names
	 * 
	 * @param targetName Variable name in target code
	 * @param candidateName Variable name in candidate code
	 * @return true if mapping is consistent, false if conflict
	 */
	public boolean addMapping(String targetName, String candidateName) {
		// Check if we already have a mapping for this target name
		if (targetToCandidate.containsKey(targetName)) {
			// Verify consistency: same target name must always map to same candidate name
			return targetToCandidate.get(targetName).equals(candidateName);
		}
		
		// Check if candidate name is already mapped to a different target name
		if (candidateToTarget.containsKey(candidateName)) {
			// Verify consistency: same candidate name must always map to same target name
			return candidateToTarget.get(candidateName).equals(targetName);
		}
		
		// New mapping - add it
		targetToCandidate.put(targetName, candidateName);
		candidateToTarget.put(candidateName, targetName);
		return true;
	}
	
	/**
	 * Get the candidate name mapped to a target name
	 */
	public String getCandidateName(String targetName) {
		return targetToCandidate.get(targetName);
	}
	
	/**
	 * Check if mapping is valid (at least one mapping exists)
	 */
	public boolean isValid() {
		return !targetToCandidate.isEmpty();
	}
	
	/**
	 * Get all target to candidate mappings
	 */
	public Map<String, String> getMappings() {
		return new HashMap<>(targetToCandidate);
	}
}
