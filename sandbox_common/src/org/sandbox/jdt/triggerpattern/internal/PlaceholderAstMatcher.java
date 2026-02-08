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
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;

/**
 * An AST matcher that supports placeholder matching with multi-placeholder and type constraint support.
 * 
 * <p>Placeholders are identified by a {@code $} prefix in SimpleName nodes.
 * When a placeholder is encountered:</p>
 * <ul>
 *   <li>If it's the first occurrence, the placeholder is bound to the corresponding node</li>
 *   <li>If it's a subsequent occurrence, the node must match the previously bound node</li>
 *   <li>Multi-placeholders (ending with $) match zero or more nodes and are stored as lists</li>
 *   <li>Type constraints (e.g., $x:StringLiteral) validate the matched node's type</li>
 * </ul>
 * 
 * <p>Example: In pattern {@code "$x + $x"}, both occurrences of {@code $x} must match
 * the same expression.</p>
 * 
 * @since 1.2.2
 */
public class PlaceholderAstMatcher extends ASTMatcher {
	
	private final Map<String, Object> bindings = new HashMap<>();  // Object can be ASTNode or List<ASTNode>
	private final ASTMatcher reusableMatcher = new ASTMatcher();
	
	/**
	 * Creates a new placeholder matcher.
	 */
	public PlaceholderAstMatcher() {
		super();
	}
	
	/**
	 * Returns the placeholder bindings.
	 * 
	 * @return a map of placeholder names to bound AST nodes or lists of AST nodes
	 */
	public Map<String, Object> getBindings() {
		return new HashMap<>(bindings);
	}
	
	/**
	 * Clears all placeholder bindings.
	 */
	public void clearBindings() {
		bindings.clear();
	}
	
	/**
	 * Detects if a placeholder name represents a multi-placeholder (e.g., $args$).
	 * 
	 * @param name the placeholder name
	 * @return true if this is a multi-placeholder
	 */
	private boolean isMultiPlaceholder(String name) {
		return name.startsWith("$") && name.endsWith("$") && name.length() > 2; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Parses placeholder information from a placeholder name.
	 * Supports syntax: $name, $name$, $name:Type, $name$:Type
	 * 
	 * @param placeholderName the placeholder name (e.g., "$x", "$args$", "$msg:StringLiteral")
	 * @return parsed placeholder information
	 */
	private PlaceholderInfo parsePlaceholder(String placeholderName) {
		String name = placeholderName;
		String typeConstraint = null;
		
		// Check for type constraint (e.g., $x:StringLiteral)
		int colonIndex = name.indexOf(':');
		if (colonIndex > 0) {
			typeConstraint = name.substring(colonIndex + 1);
			name = name.substring(0, colonIndex);
		}
		
		boolean isMulti = isMultiPlaceholder(name);
		return new PlaceholderInfo(name, typeConstraint, isMulti);
	}
	
	/**
	 * Validates that a node matches the specified type constraint.
	 * 
	 * @param node the AST node to validate
	 * @param typeConstraint the type constraint (e.g., "StringLiteral"), null means any type
	 * @return true if the node matches the constraint
	 */
	private boolean matchesTypeConstraint(ASTNode node, String typeConstraint) {
		if (typeConstraint == null) {
			return true;
		}
		
		return switch (typeConstraint) {
			case "StringLiteral" -> node instanceof StringLiteral; //$NON-NLS-1$
			case "NumberLiteral" -> node instanceof NumberLiteral; //$NON-NLS-1$
			case "TypeLiteral" -> node instanceof TypeLiteral; //$NON-NLS-1$
			case "SimpleName" -> node instanceof SimpleName; //$NON-NLS-1$
			case "MethodInvocation" -> node instanceof MethodInvocation; //$NON-NLS-1$
			case "Expression" -> node instanceof Expression; //$NON-NLS-1$
			case "Statement" -> node instanceof Statement; //$NON-NLS-1$
			default -> node.getClass().getSimpleName().equals(typeConstraint);
		};
	}
	
	@Override
	public boolean match(SimpleName patternNode, Object other) {
		if (!(other instanceof ASTNode)) {
			return false;
		}
		
		String name = patternNode.getIdentifier();
		
		// Check if this is a placeholder (starts with $)
		if (name != null && name.startsWith("$")) { //$NON-NLS-1$
			ASTNode otherNode = (ASTNode) other;
			
			// Parse placeholder info (handles type constraints)
			PlaceholderInfo placeholderInfo = parsePlaceholder(name);
			
			// Validate type constraint if specified
			if (!matchesTypeConstraint(otherNode, placeholderInfo.typeConstraint())) {
				return false;
			}
			
			// Use the cleaned placeholder name (without type constraint) for binding
			String placeholderName = placeholderInfo.name();
			
			// Check if this placeholder has been bound before
			if (bindings.containsKey(placeholderName)) {
				// Placeholder already bound - must match the previously bound node
				Object boundValue = bindings.get(placeholderName);
				if (boundValue instanceof ASTNode) {
					ASTNode boundNode = (ASTNode) boundValue;
					return boundNode.subtreeMatch(reusableMatcher, otherNode);
				}
				// If it's a list binding, that's an error - shouldn't happen for SimpleName
				return false;
			}
			// First occurrence - bind the placeholder to this node
			bindings.put(placeholderName, otherNode);
			return true;
		}
		
		// Not a placeholder - use default matching
		return super.match(patternNode, other);
	}
	
	/**
	 * Matches marker annotations (e.g., @Before, @After).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(MarkerAnnotation patternNode, Object other) {
		if (!(other instanceof MarkerAnnotation)) {
			return false;
		}
		MarkerAnnotation otherAnnotation = (MarkerAnnotation) other;
		
		// Match annotation name
		return patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName());
	}
	
	/**
	 * Matches single member annotations (e.g., {@code @SuppressWarnings("unchecked")}).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(SingleMemberAnnotation patternNode, Object other) {
		if (!(other instanceof SingleMemberAnnotation)) {
			return false;
		}
		SingleMemberAnnotation otherAnnotation = (SingleMemberAnnotation) other;
		
		// Match annotation name
		if (!patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName())) {
			return false;
		}
		
		// Match the value with placeholder support
		return safeSubtreeMatch(patternNode.getValue(), otherAnnotation.getValue());
	}
	
	/**
	 * Matches normal annotations (e.g., @Test(expected=Exception.class, timeout=1000)).
	 * 
	 * @param patternNode the pattern annotation
	 * @param other the candidate node
	 * @return {@code true} if the annotations match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(NormalAnnotation patternNode, Object other) {
		if (!(other instanceof NormalAnnotation)) {
			return false;
		}
		NormalAnnotation otherAnnotation = (NormalAnnotation) other;
		
		// Match annotation name
		if (!patternNode.getTypeName().getFullyQualifiedName()
				.equals(otherAnnotation.getTypeName().getFullyQualifiedName())) {
			return false;
		}
		
		// Match member-value pairs with placeholder support
		@SuppressWarnings("unchecked")
		List<MemberValuePair> patternPairs = patternNode.values();
		@SuppressWarnings("unchecked")
		List<MemberValuePair> otherPairs = otherAnnotation.values();
		
		// Must have same number of pairs
		if (patternPairs.size() != otherPairs.size()) {
			return false;
		}
		
		// Create a map for O(n) lookup instead of O(n²)
		Map<String, MemberValuePair> otherPairMap = new HashMap<>();
		for (MemberValuePair otherPair : otherPairs) {
			otherPairMap.put(otherPair.getName().getIdentifier(), otherPair);
		}
		
		// Match each pattern pair with corresponding pair in other annotation
		// (annotation pairs can be in any order)
		for (MemberValuePair patternPair : patternPairs) {
			String patternName = patternPair.getName().getIdentifier();
			
			// Find corresponding pair in other annotation
			MemberValuePair matchingOtherPair = otherPairMap.get(patternName);
			
			// If no matching pair found, annotations don't match
			if (matchingOtherPair == null) {
				return false;
			}
			
			// Values must match (with placeholder support)
			if (!safeSubtreeMatch(patternPair.getValue(), matchingOtherPair.getValue())) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Matches field declarations with support for annotations and placeholders.
	 * 
	 * @param patternNode the pattern field declaration
	 * @param other the candidate node
	 * @return {@code true} if the fields match
	 * @since 1.2.3
	 */
	@Override
	public boolean match(FieldDeclaration patternNode, Object other) {
		if (!(other instanceof FieldDeclaration)) {
			return false;
		}
		FieldDeclaration otherField = (FieldDeclaration) other;
		
		// Match modifiers (including annotations)
		@SuppressWarnings("unchecked")
		List<IExtendedModifier> patternModifiers = patternNode.modifiers();
		@SuppressWarnings("unchecked")
		List<IExtendedModifier> otherModifiers = otherField.modifiers();
		
		// Match each modifier/annotation in the pattern
		for (IExtendedModifier patternMod : patternModifiers) {
			if (patternMod.isAnnotation()) {
				// Find matching annotation in other field
				boolean found = false;
				for (IExtendedModifier otherMod : otherModifiers) {
					if (otherMod.isAnnotation()) {
						if (safeSubtreeMatch((ASTNode) patternMod, (ASTNode) otherMod)) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					return false;
				}
			} else if (patternMod.isModifier()) {
				// Check if other has the same modifier
				boolean found = false;
				for (IExtendedModifier otherMod : otherModifiers) {
					if (otherMod.isModifier()) {
						if (safeSubtreeMatch((ASTNode) patternMod, (ASTNode) otherMod)) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					return false;
				}
			}
		}
		
		// Match type
		if (!safeSubtreeMatch(patternNode.getType(), otherField.getType())) {
			return false;
		}
		
		// Match fragments (variable names) - need special handling for placeholders
		@SuppressWarnings("unchecked")
		List<Object> patternFragments = patternNode.fragments();
		@SuppressWarnings("unchecked")
		List<Object> otherFragments = otherField.fragments();
		
		if (patternFragments.size() != otherFragments.size()) {
			return false;
		}
		
		// For each fragment, we only need to match the variable name (not the initializer)
		// because the pattern might have placeholder names like $name
		for (int i = 0; i < patternFragments.size(); i++) {
			org.eclipse.jdt.core.dom.VariableDeclarationFragment patternFrag = 
					(org.eclipse.jdt.core.dom.VariableDeclarationFragment) patternFragments.get(i);
			org.eclipse.jdt.core.dom.VariableDeclarationFragment otherFrag = 
					(org.eclipse.jdt.core.dom.VariableDeclarationFragment) otherFragments.get(i);
			
			// Match the variable name (this handles placeholders via SimpleName matching)
			if (!safeSubtreeMatch(patternFrag.getName(), otherFrag.getName())) {
				return false;
			}
			
			// Only check initializers if pattern has one
			if (patternFrag.getInitializer() != null) {
				if (!safeSubtreeMatch(patternFrag.getInitializer(), otherFrag.getInitializer())) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Helper method to perform subtree matching using this matcher.
	 */
	private boolean safeSubtreeMatch(ASTNode node1, ASTNode node2) {
		if (node1 == null) {
			return node2 == null;
		}
		return node1.subtreeMatch(this, node2);
	}
	
	/**
	 * Matches method invocations with support for multi-placeholder arguments.
	 * 
	 * @param patternNode the pattern method invocation
	 * @param other the candidate node
	 * @return {@code true} if the method invocations match
	 * @since 1.3.1
	 */
	@Override
	public boolean match(MethodInvocation patternNode, Object other) {
		if (!(other instanceof MethodInvocation)) {
			return false;
		}
		MethodInvocation otherInvocation = (MethodInvocation) other;
		
		// Match method name
		if (!safeSubtreeMatch(patternNode.getName(), otherInvocation.getName())) {
			return false;
		}
		
		// Match expression (receiver)
		if (!safeSubtreeMatch(patternNode.getExpression(), otherInvocation.getExpression())) {
			return false;
		}
		
		// Match type arguments if present
		@SuppressWarnings("unchecked")
		List<org.eclipse.jdt.core.dom.Type> patternTypeArgs = patternNode.typeArguments();
		@SuppressWarnings("unchecked")
		List<org.eclipse.jdt.core.dom.Type> otherTypeArgs = otherInvocation.typeArguments();
		
		if (patternTypeArgs.size() != otherTypeArgs.size()) {
			return false;
		}
		
		for (int i = 0; i < patternTypeArgs.size(); i++) {
			if (!safeSubtreeMatch(patternTypeArgs.get(i), otherTypeArgs.get(i))) {
				return false;
			}
		}
		
		// Match arguments with multi-placeholder support
		@SuppressWarnings("unchecked")
		List<Expression> patternArgs = patternNode.arguments();
		@SuppressWarnings("unchecked")
		List<Expression> otherArgs = otherInvocation.arguments();
		
		return matchArgumentsWithMultiPlaceholders(patternArgs, otherArgs);
	}
	
	/**
	 * Matches argument lists with support for multi-placeholders.
	 * 
	 * @param patternArgs the pattern arguments
	 * @param otherArgs the candidate arguments
	 * @return true if arguments match (considering multi-placeholders)
	 */
	private boolean matchArgumentsWithMultiPlaceholders(List<Expression> patternArgs, List<Expression> otherArgs) {
		// Check if pattern has a single multi-placeholder argument
		if (patternArgs.size() == 1 && patternArgs.get(0) instanceof SimpleName) {
			SimpleName patternArg = (SimpleName) patternArgs.get(0);
			String name = patternArg.getIdentifier();
			
			if (name != null && name.startsWith("$")) { //$NON-NLS-1$
				PlaceholderInfo info = parsePlaceholder(name);
				
				if (info.isMulti()) {
					// Multi-placeholder: bind to list of all arguments
					String placeholderName = info.name();
					
					// Validate type constraints for all arguments if specified
					if (info.typeConstraint() != null) {
						for (Expression arg : otherArgs) {
							if (!matchesTypeConstraint(arg, info.typeConstraint())) {
								return false;
							}
						}
					}
					
					// Check if already bound
					if (bindings.containsKey(placeholderName)) {
						Object boundValue = bindings.get(placeholderName);
						if (boundValue instanceof List<?>) {
							@SuppressWarnings("unchecked")
							List<ASTNode> boundList = (List<ASTNode>) boundValue;
							if (boundList.size() != otherArgs.size()) {
								return false;
							}
							for (int i = 0; i < boundList.size(); i++) {
								if (!boundList.get(i).subtreeMatch(reusableMatcher, otherArgs.get(i))) {
									return false;
								}
							}
							return true;
						}
						return false;
					}
					// First occurrence - bind to list
					bindings.put(placeholderName, new ArrayList<>(otherArgs));
					return true;
				}
			}
		}
		
		// Standard matching: same number of arguments, each matching
		if (patternArgs.size() != otherArgs.size()) {
			return false;
		}
		
		for (int i = 0; i < patternArgs.size(); i++) {
			if (!safeSubtreeMatch(patternArgs.get(i), otherArgs.get(i))) {
				return false;
			}
		}
		
		return true;
	}
}
