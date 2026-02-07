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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;

/**
 * Utilities for creating declarative fix templates.
 * 
 * <p>This class provides methods to create fixes using pattern-based
 * replacement templates, similar to NetBeans' JavaFixUtilities.</p>
 * 
 * @since 1.2.2
 */
public final class FixUtilities {
	
	private FixUtilities() {
		// Utility class, no instances
	}
	
	/**
	 * Creates a declarative rewrite fix from a replacement template.
	 * 
	 * <p>Placeholders from the match are substituted into the replacement template.
	 * The matched node is replaced with the result.</p>
	 * 
	 * <p>Example:</p>
	 * <pre>
	 * Match pattern:    "$x + 1"
	 * Replacement:      "++$x"
	 * For code "count + 1", produces "++count"
	 * </pre>
	 * 
	 * <p>Supports both single placeholders ($x) and multi-placeholders ($args$).
	 * The replacement pattern is parsed using the same syntax as trigger patterns.</p>
	 * 
	 * @param ctx the hint context containing the match and rewrite
	 * @param replacementPattern the replacement pattern with placeholders
	 * @throws IllegalArgumentException if the replacement pattern cannot be parsed
	 */
	public static void rewriteFix(HintContext ctx, String replacementPattern) {
		if (ctx == null || replacementPattern == null) {
			throw new IllegalArgumentException("Context and replacement pattern must not be null"); //$NON-NLS-1$
		}
		
		Match match = ctx.getMatch();
		ASTRewrite rewrite = ctx.getASTRewrite();
		ASTNode matchedNode = match.getMatchedNode();
		
		// Determine pattern kind from matched node type
		PatternKind kind = determinePatternKind(matchedNode);
		
		// Parse the replacement pattern
		PatternParser parser = new PatternParser();
		Pattern pattern = new Pattern(replacementPattern, kind, null, null);
		ASTNode replacementNode = parser.parse(pattern);
		
		if (replacementNode == null) {
			throw new IllegalArgumentException("Could not parse replacement pattern: " + replacementPattern); //$NON-NLS-1$
		}
		
		// Substitute placeholders with actual bindings
		ASTNode substituted = substitutePlaceholders(replacementNode, match.getBindings(), rewrite.getAST());
		
		// Replace the matched node with the substituted replacement
		rewrite.replace(matchedNode, substituted, null);
	}
	
	/**
	 * Determines the PatternKind from a matched node type.
	 * 
	 * @param node the matched node
	 * @return the appropriate PatternKind
	 */
	public static PatternKind determinePatternKindFromNode(ASTNode node) {
		return determinePatternKind(node);
	}
	
	/**
	 * Determines the PatternKind from a matched node type.
	 * 
	 * @param node the matched node
	 * @return the appropriate PatternKind
	 */
	private static PatternKind determinePatternKind(ASTNode node) {
		// Check ClassInstanceCreation before Expression since it extends Expression
		if (node instanceof ClassInstanceCreation) {
			return PatternKind.CONSTRUCTOR;
		} else if (node instanceof Annotation) {
			return PatternKind.ANNOTATION;
		} else if (node instanceof MethodInvocation) {
			return PatternKind.METHOD_CALL;
		} else if (node instanceof ImportDeclaration) {
			return PatternKind.IMPORT;
		} else if (node instanceof FieldDeclaration) {
			return PatternKind.FIELD;
		} else if (node instanceof Expression) {
			return PatternKind.EXPRESSION;
		}
		return PatternKind.STATEMENT;
	}
	
	/**
	 * Substitutes placeholders in a template node with actual bindings.
	 * 
	 * @param template the template node with placeholders
	 * @param bindings the placeholder bindings from the match
	 * @param ast the AST for creating new nodes
	 * @return a new node with placeholders replaced
	 */
	private static ASTNode substitutePlaceholders(ASTNode template, Map<String, Object> bindings, AST ast) {
		// For simple cases, if the entire template is a placeholder, return its binding directly
		String placeholderName = extractPlaceholderName(template);
		if (placeholderName != null) {
			Object binding = bindings.get(placeholderName);
			if (binding instanceof ASTNode) {
				return ASTNode.copySubtree(ast, (ASTNode) binding);
			}
		}
		
		// For complex templates, perform a recursive substitution
		ASTNode copy = ASTNode.copySubtree(ast, template);
		
		// Build a map of placeholder SimpleNames to their replacements
		Map<SimpleName, ASTNode> replacements = new HashMap<>();
		
		copy.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				String name = node.getIdentifier();
				if (name.startsWith("$")) { //$NON-NLS-1$
					Object binding = bindings.get(name);
					if (binding instanceof ASTNode) {
						replacements.put(node, (ASTNode) binding);
					}
					// Multi-placeholders are handled separately as they need list context
				}
				return true;
			}
		});
		
		// Apply replacements using structural replace
		for (Map.Entry<SimpleName, ASTNode> entry : replacements.entrySet()) {
			SimpleName placeholder = entry.getKey();
			ASTNode replacement = entry.getValue();
			ASTNode parent = placeholder.getParent();
			
			if (parent != null) {
				StructuralPropertyDescriptor location = placeholder.getLocationInParent();
				if (location.isChildProperty()) {
					parent.setStructuralProperty(location, ASTNode.copySubtree(ast, replacement));
				}
			}
		}
		
		return copy;
	}
	
	/**
	 * Extracts the placeholder name from a node if it is a simple placeholder.
	 * 
	 * @param node the node to check
	 * @return the placeholder name (e.g., "$x"), or null if not a placeholder
	 */
	private static String extractPlaceholderName(ASTNode node) {
		// SimpleName nodes with names starting with $ are placeholders
		if (node.getNodeType() == ASTNode.SIMPLE_NAME) {
			String name = node.toString();
			if (name.startsWith("$")) { //$NON-NLS-1$
				return name;
			}
		}
		return null;
	}
}
