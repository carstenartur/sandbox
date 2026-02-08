/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.api;

import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;

/**
 * Context object providing access to pattern match information and AST rewriting capabilities.
 * 
 * <p>This class is passed to methods annotated with {@link PatternHandler} and provides
 * everything needed to inspect the matched code and perform transformations.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Access to the matched AST node</li>
 *   <li>Pattern bindings (placeholders like {@code $condition}, {@code $body})</li>
 *   <li>AST rewriting infrastructure</li>
 *   <li>Import management</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * {@literal @}PatternHandler(pattern = "if ($condition) { $body }")
 * public void handleIf(PatternContext context) {
 *     ASTNode condition = context.getBinding("$condition");
 *     ASTNode body = context.getBinding("$body");
 *     
 *     // Transform the code
 *     AST ast = context.getAST();
 *     ASTRewrite rewriter = context.getRewriter();
 *     // ... perform transformation ...
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
public class PatternContext {
    
    private final Match match;
    private final ASTRewrite rewriter;
    private final AST ast;
    private final ImportRewrite importRewriter;
    private final TextEditGroup group;
    
    /**
     * Creates a new pattern context.
     * 
     * @param match the pattern match containing the matched node and bindings
     * @param rewriter the AST rewriter for making code changes
     * @param ast the AST instance for creating new nodes
     * @param importRewriter the import rewriter for managing imports
     * @param group the text edit group for tracking changes
     */
    public PatternContext(Match match, ASTRewrite rewriter, AST ast, 
            ImportRewrite importRewriter, TextEditGroup group) {
        this.match = match;
        this.rewriter = rewriter;
        this.ast = ast;
        this.importRewriter = importRewriter;
        this.group = group;
    }
    
    /**
     * Returns the pattern match information.
     * 
     * @return the match object
     */
    public Match getMatch() {
        return match;
    }
    
    /**
     * Returns the AST node that matched the pattern.
     * 
     * @return the matched node
     */
    public ASTNode getMatchedNode() {
        return match.getMatchedNode();
    }
    
    /**
     * Returns all pattern bindings (placeholders and their matched values).
     * 
     * <p>For example, if the pattern is {@code "if ($condition) { $body }"},
     * the bindings map will contain entries for {@code "$condition"} and {@code "$body"}.</p>
     * 
     * <p><b>Note:</b> Bindings can be either single {@link ASTNode} instances or 
     * {@link java.util.List List&lt;ASTNode&gt;} for multi-placeholders (e.g., {@code $args$}).
     * Use {@link #getBinding(String)} or {@link #getBindingAsExpression(String)} for type-safe
     * access to single-node bindings.</p>
     * 
     * @return map of placeholder names to matched values (ASTNode or List&lt;ASTNode&gt;)
     */
    public Map<String, Object> getBindings() {
        return match.getBindings();
    }
    
    /**
     * Returns the matched value for a specific placeholder.
     * 
     * @param placeholder the placeholder name (e.g., "$condition")
     * @return the matched AST node, or null if not found
     */
    public ASTNode getBinding(String placeholder) {
        Object binding = match.getBindings().get(placeholder);
        return binding instanceof ASTNode ? (ASTNode) binding : null;
    }
    
    /**
     * Returns the matched value as an Expression.
     * 
     * @param placeholder the placeholder name
     * @return the matched expression, or null if not found or not an expression
     */
    public Expression getBindingAsExpression(String placeholder) {
        ASTNode node = getBinding(placeholder);
        return node instanceof Expression ? (Expression) node : null;
    }
    
    /**
     * Returns the matched value as a list of ASTNodes for multi-placeholders.
     * 
     * <p>Multi-placeholders (e.g., {@code $args$}) can match multiple nodes and
     * are stored as lists in the bindings map.</p>
     * 
     * @param placeholder the placeholder name (e.g., "$args$")
     * @return the list of matched nodes, or null if not found or not a list
     */
    @SuppressWarnings("unchecked")
    public java.util.List<ASTNode> getBindingAsList(String placeholder) {
        Object binding = match.getBindings().get(placeholder);
        if (binding instanceof java.util.List) {
            return (java.util.List<ASTNode>) binding;
        }
        return null;
    }
    
    /**
     * Returns the AST rewriter for making code modifications.
     * 
     * @return the AST rewriter
     */
    public ASTRewrite getRewriter() {
        return rewriter;
    }
    
    /**
     * Returns the AST instance for creating new nodes.
     * 
     * @return the AST
     */
    public AST getAST() {
        return ast;
    }
    
    /**
     * Returns the import rewriter for managing imports.
     * 
     * @return the import rewriter
     */
    public ImportRewrite getImportRewriter() {
        return importRewriter;
    }
    
    /**
     * Returns the text edit group for tracking changes.
     * 
     * @return the text edit group
     */
    public TextEditGroup getGroup() {
        return group;
    }
    
    /**
     * Adds an import to the compilation unit.
     * 
     * @param qualifiedName the fully qualified type name to import
     */
    public void addImport(String qualifiedName) {
        importRewriter.addImport(qualifiedName);
    }
    
    /**
     * Removes an import from the compilation unit.
     * 
     * @param qualifiedName the fully qualified type name to remove
     */
    public void removeImport(String qualifiedName) {
        importRewriter.removeImport(qualifiedName);
    }
    
    /**
     * Adds a static import to the compilation unit.
     * 
     * @param qualifiedName the fully qualified static import (e.g., "org.junit.Assert.assertEquals")
     */
    public void addStaticImport(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            String className = qualifiedName.substring(0, lastDot);
            String memberName = qualifiedName.substring(lastDot + 1);
            if ("*".equals(memberName)) { //$NON-NLS-1$
                importRewriter.addStaticImport(className, "*", false); //$NON-NLS-1$
            } else {
                importRewriter.addStaticImport(className, memberName, false);
            }
        }
    }
    
    /**
     * Removes a static import from the compilation unit.
     * 
     * @param qualifiedName the fully qualified static import to remove
     */
    public void removeStaticImport(String qualifiedName) {
        importRewriter.removeStaticImport(qualifiedName);
    }
}
