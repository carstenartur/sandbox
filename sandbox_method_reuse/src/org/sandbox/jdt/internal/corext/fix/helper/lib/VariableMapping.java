package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.HashMap;
import java.util.Map;

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
public class VariableMapping {
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