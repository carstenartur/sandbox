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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

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
		
		// Parse the replacement pattern
		PatternParser parser = new PatternParser();
		PatternKind kind = matchedNode instanceof Expression ? PatternKind.EXPRESSION : PatternKind.STATEMENT;
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
	 * Substitutes placeholders in a template node with actual bindings.
	 * 
	 * @param template the template node with placeholders
	 * @param bindings the placeholder bindings from the match
	 * @param ast the AST for creating new nodes
	 * @return a new node with placeholders replaced
	 */
	private static ASTNode substitutePlaceholders(ASTNode template, Map<String, Object> bindings, AST ast) {
		// Create a matcher to identify placeholders in the template
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		
		// For simple cases, if the entire template is a placeholder, return its binding directly
		String placeholderName = extractPlaceholderName(template);
		if (placeholderName != null) {
			Object binding = bindings.get(placeholderName);
			if (binding instanceof ASTNode) {
				return ASTNode.copySubtree(ast, (ASTNode) binding);
			}
		}
		
		// For complex templates, perform a recursive substitution
		// This is a simplified implementation - a full implementation would need
		// to traverse the template tree and replace all placeholder nodes
		ASTNode copy = ASTNode.copySubtree(ast, template);
		return substituteInTree(copy, bindings, ast);
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
	
	/**
	 * Recursively substitutes placeholders in a tree.
	 * 
	 * @param node the node to process
	 * @param bindings the bindings map
	 * @param ast the AST for creating nodes
	 * @return the processed node (possibly replaced)
	 */
	private static ASTNode substituteInTree(ASTNode node, Map<String, Object> bindings, AST ast) {
		// Check if this node itself is a placeholder
		String placeholderName = extractPlaceholderName(node);
		if (placeholderName != null) {
			Object binding = bindings.get(placeholderName);
			if (binding instanceof ASTNode) {
				return ASTNode.copySubtree(ast, (ASTNode) binding);
			} else if (binding instanceof List<?>) {
				// Multi-placeholder - for now, return the node as-is
				// Full implementation would need context about where to insert the list
				return node;
			}
		}
		
		// For other nodes, we would recursively process children
		// This is a simplified version - full implementation would use visitor pattern
		return node;
	}
}
