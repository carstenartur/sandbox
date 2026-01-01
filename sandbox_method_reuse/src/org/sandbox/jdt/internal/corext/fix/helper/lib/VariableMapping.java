package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.Expression;

/**
 * Represents a one-to-one mapping between variable names in the target code and
 * variable names or expressions in the candidate code.
 * <p>
 * This helper is used by {@link CodeSequenceMatcher} (via
 * {@code VariableMappingMatcher}) to normalize variable names when comparing
 * AST subtrees. It maintains a bidirectional mapping (target → candidate and
 * candidate → target) and enforces consistency: once a name pair is mapped,
 * subsequent mappings for either name must match the original pair or the
 * mapping is considered invalid.
 * <p>
 * This class also supports mapping variable names to complex expressions (not just
 * simple names), which is useful for cases where a parameter is replaced by a
 * method call or other expression.
 */
public class VariableMapping {
	// Maps from target variable name to candidate variable name
	private final Map<String, String> targetToCandidate = new HashMap<>();
	// Maps from candidate variable name to target variable name (for reverse lookup)
	private final Map<String, String> candidateToTarget = new HashMap<>();
	// Maps from target variable name to candidate expression (for complex expressions)
	private final Map<String, Expression> targetToExpression = new HashMap<>();
	
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
	 * Add a mapping from a target variable name to a candidate expression
	 * Used when the candidate uses a complex expression (like a method call) 
	 * where the target uses a simple variable
	 * 
	 * @param targetName Variable name in target code
	 * @param candidateExpr Expression in candidate code
	 */
	public void addExpressionMapping(String targetName, Expression candidateExpr) {
		targetToExpression.put(targetName, candidateExpr);
	}
	
	/**
	 * Get the candidate name mapped to a target name
	 */
	public String getCandidateName(String targetName) {
		return targetToCandidate.get(targetName);
	}
	
	/**
	 * Get the candidate expression mapped to a target name
	 */
	public Expression getCandidateExpression(String targetName) {
		return targetToExpression.get(targetName);
	}
	
	/**
	 * Check if a target name has an expression mapping (not just a name mapping)
	 */
	public boolean hasExpressionMapping(String targetName) {
		return targetToExpression.containsKey(targetName);
	}
	
	/**
	 * Check if mapping is valid (at least one mapping exists)
	 */
	public boolean isValid() {
		return !targetToCandidate.isEmpty() || !targetToExpression.isEmpty();
	}
	
	/**
	 * Get all target to candidate mappings
	 */
	public Map<String, String> getMappings() {
		return new HashMap<>(targetToCandidate);
	}
	
	/**
	 * Get all target to expression mappings
	 */
	public Map<String, Expression> getExpressionMappings() {
		return new HashMap<>(targetToExpression);
	}
}