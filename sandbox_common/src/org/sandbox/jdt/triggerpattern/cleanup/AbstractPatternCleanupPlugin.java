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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Abstract base class that connects TriggerPattern with the Eclipse Cleanup framework.
 * Provides generic pattern matching and rewriting capabilities that can be used by
 * any cleanup implementation, not just JUnit cleanups.
 * 
 * <p>This class extracts the generic TriggerPattern integration logic from the JUnit-specific
 * {@code TriggerPatternCleanupPlugin}. It handles:</p>
 * <ul>
 *   <li>Pattern matching using {@link TriggerPatternEngine}</li>
 *   <li>{@link CleanupPattern} annotation processing</li>
 *   <li>{@link RewriteRule} annotation processing for declarative transformations</li>
 *   <li>Import management (add/remove imports and static imports)</li>
 *   <li>Qualified type validation</li>
 * </ul>
 * 
 * <p><b>Type Parameters:</b></p>
 * <ul>
 *   <li>{@code <H>} - The holder type used to store match information (e.g., JunitHolder)</li>
 * </ul>
 * 
 * <p><b>Subclasses must implement:</b></p>
 * <ol>
 *   <li>{@link #createHolder(Match)} - Create holder from match</li>
 *   <li>{@link #processRewrite(TextEditGroup, ASTRewrite, AST, ImportRewrite, Object)} - Apply AST transformations</li>
 *   <li>{@link #getPreview(boolean)} - Provide UI preview</li>
 * </ol>
 * 
 * <p><b>Optional overrides:</b></p>
 * <ul>
 *   <li>{@link #getPatterns()} - For multiple patterns (instead of single {@link CleanupPattern} annotation)</li>
 *   <li>{@link #shouldProcess(Match, Pattern)} - For additional match validation</li>
 *   <li>{@link #processMatch(Match, Object, Object)} - For custom match processing</li>
 * </ul>
 * 
 * @param <H> the holder type for storing match information
 * @since 1.3.0
 */
public abstract class AbstractPatternCleanupPlugin<H> {
    
    private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
    
    /**
     * Returns the Pattern extracted from the @CleanupPattern annotation.
     * Subclasses can override {@link #getPatterns()} instead if they need multiple patterns.
     * 
     * <p>Note: This method returns {@code null} when no annotation is present, which signals
     * that the subclass should override {@link #getPatterns()} instead. In contrast,
     * {@link #getCleanupId()} and {@link #getDescription()} return empty strings as safe defaults.</p>
     * 
     * @return the pattern for matching, or null if no @CleanupPattern annotation is present
     */
    public Pattern getPattern() {
        CleanupPattern annotation = this.getClass().getAnnotation(CleanupPattern.class);
        if (annotation == null) {
            return null; // Subclass uses getPatterns() instead
        }
        String qualifiedType = annotation.qualifiedType().isEmpty() ? null : annotation.qualifiedType();
        return new Pattern(annotation.value(), annotation.kind(), qualifiedType);
    }
    
    /**
     * Returns the patterns to match in the compilation unit.
     * Default implementation returns a single pattern from @CleanupPattern annotation.
     * Subclasses can override to provide multiple patterns.
     * 
     * @return list of patterns to match
     * @throws IllegalStateException if neither @CleanupPattern annotation is present nor getPatterns() is overridden
     */
    protected List<Pattern> getPatterns() {
        Pattern pattern = getPattern();
        if (pattern != null) {
            return List.of(pattern);
        }
        throw new IllegalStateException(
            "Plugin " + getClass().getSimpleName() + 
            " must either be annotated with @CleanupPattern or override getPatterns() method to define patterns");
    }
    
    /**
     * Returns the cleanup ID from the @CleanupPattern annotation.
     * 
     * @return the cleanup ID, or empty string if annotation is not present or cleanupId is not set
     */
    public String getCleanupId() {
        CleanupPattern annotation = this.getClass().getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.cleanupId() : "";
    }
    
    /**
     * Returns the description from the @CleanupPattern annotation.
     * 
     * @return the description, or empty string if annotation is not present or description is not set
     */
    public String getDescription() {
        CleanupPattern annotation = this.getClass().getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.description() : "";
    }
    
    /**
     * Creates a holder from a Match.
     * Subclasses must implement this to convert pattern matches to their specific holder type.
     * 
     * @param match the matched pattern
     * @return a holder containing match information, or null to skip this match
     */
    protected abstract H createHolder(Match match);
    
    /**
     * Subclasses can override to add additional validation.
     * Default implementation returns true (process all matches).
     * 
     * @param match the pattern match
     * @param pattern the pattern that was matched
     * @return true if this match should be processed
     */
    protected boolean shouldProcess(Match match, Pattern pattern) {
        return true;
    }
    
    /**
     * Called for each match. Default implementation creates a holder and processes it.
     * Subclasses can override for custom processing logic.
     * 
     * @param match the matched pattern
     * @param fixcore the cleanup fix core (implementation-specific type)
     * @param operations operations collection (implementation-specific type)
     * @return true to stop processing more matches, false to continue
     */
    protected boolean processMatch(Match match, Object fixcore, Object operations) {
        H holder = createHolder(match);
        if (holder != null) {
            // Subclasses that override this method handle their own operation creation
            // This default implementation is a hook for subclasses
        }
        return false;
    }
    
    /**
     * Finds all matches using TriggerPatternEngine.
     * This is the generic pattern matching logic extracted from the JUnit-specific implementation.
     * 
     * @param compilationUnit the compilation unit to search
     * @param patterns the patterns to match
     * @return list of all matches
     */
    protected List<Match> findAllMatches(org.eclipse.jdt.core.dom.CompilationUnit compilationUnit, List<Pattern> patterns) {
        java.util.List<Match> allMatches = new java.util.ArrayList<>();
        for (Pattern pattern : patterns) {
            List<Match> matches = ENGINE.findMatches(compilationUnit, pattern);
            allMatches.addAll(matches);
        }
        return allMatches;
    }
    
    /**
     * Validates that the node's type binding matches the expected qualified type.
     * 
     * @param node the AST node to validate
     * @param qualifiedType the expected fully qualified type name
     * @return true if types match, false otherwise
     */
    protected boolean validateQualifiedType(ASTNode node, String qualifiedType) {
        if (node instanceof Annotation) {
            Annotation annotation = (Annotation) node;
            ITypeBinding binding = annotation.resolveTypeBinding();
            if (binding != null) {
                return qualifiedType.equals(binding.getQualifiedName());
            }
            // Fallback: check simple name
            return annotation.getTypeName().getFullyQualifiedName().equals(
                    qualifiedType.substring(qualifiedType.lastIndexOf('.') + 1));
        }
        // Add more type checks as needed
        return true;
    }
    
    // Regex pattern for parsing replacement patterns (compiled once for performance)
    // Supports:
    // - @AnnotationName or @AnnotationName($placeholder) or @AnnotationName($placeholder$)
    // - MethodName.method($args) or MethodName.method($args$)
    private static final java.util.regex.Pattern REPLACEMENT_PATTERN = 
        java.util.regex.Pattern.compile("@?([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)(?:\\((.*)\\))?");
    
    /**
     * Processes AST rewrite operations.
     * Subclasses must implement this to apply their specific transformations.
     * 
     * <p>Alternatively, subclasses can use the {@link RewriteRule} annotation for simple
     * annotation replacements, and this method can use the default implementation provided
     * by calling {@link #processRewriteWithRule(TextEditGroup, ASTRewrite, AST, ImportRewrite, Object)}.</p>
     * 
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param holder the holder containing transformation information
     */
    protected abstract void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, H holder);
    
    /**
     * Provides default implementation of processRewrite using @RewriteRule annotation.
     * This method can be called by subclasses that want to use declarative @RewriteRule
     * for simple annotation replacements.
     * 
     * <p><b>Limitations:</b> This default implementation only supports:
     * <ul>
     *   <li>MarkerAnnotation (no parameters): {@code @BeforeEach}</li>
     *   <li>SingleMemberAnnotation (single value): {@code @Disabled($value)}</li>
     * </ul>
     * NormalAnnotation with named parameters like {@code @Ignore(value="reason")} is not supported.
     * </p>
     * 
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param holder the holder containing transformation information (must provide getAnnotation() and getBindingAsExpression() methods)
     */
    protected void processRewriteWithRule(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, Object holder) {
        
        RewriteRule rewriteRule = this.getClass().getAnnotation(RewriteRule.class);
        if (rewriteRule == null) {
            throw new UnsupportedOperationException(
                "Plugin " + getClass().getSimpleName() + 
                " must be annotated with @RewriteRule because it does not override processRewrite()");
        }
        
        // Process the replacement pattern
        String replaceWith = rewriteRule.replaceWith();
        
        // Use reflection to get annotation and bindings from holder
        Annotation oldAnnotation = getAnnotationFromHolder(holder);
        
        // Parse the replacement pattern to extract annotation name and placeholders
        AnnotationReplacementInfo replacementInfo = parseReplacementPattern(replaceWith);
        
        // Create the new annotation based on whether placeholders are present
        Annotation newAnnotation;
        if (replacementInfo.hasPlaceholders()) {
            // Create SingleMemberAnnotation with the placeholder value
            SingleMemberAnnotation singleMemberAnnotation = ast.newSingleMemberAnnotation();
            singleMemberAnnotation.setTypeName(ast.newSimpleName(replacementInfo.annotationName));
            
            // Get the placeholder value from bindings
            String placeholder = replacementInfo.placeholderName;
            Expression value = getBindingAsExpressionFromHolder(holder, "$" + placeholder);
            
            // Fallback: if no binding is found, reuse the value from existing annotation
            if (value == null && oldAnnotation instanceof SingleMemberAnnotation) {
                value = ((SingleMemberAnnotation) oldAnnotation).getValue();
            }
            
            if (value != null) {
                singleMemberAnnotation.setValue(ASTNodes.createMoveTarget(rewriter, value));
            }
            
            newAnnotation = singleMemberAnnotation;
        } else {
            // Create MarkerAnnotation (no parameters)
            MarkerAnnotation markerAnnotation = ast.newMarkerAnnotation();
            markerAnnotation.setTypeName(ast.newSimpleName(replacementInfo.annotationName));
            newAnnotation = markerAnnotation;
        }
        
        // Replace the old annotation with the new one
        ASTNodes.replaceButKeepComment(rewriter, oldAnnotation, newAnnotation, group);
        
        // Handle imports
        for (String importToRemove : rewriteRule.removeImports()) {
            importRewriter.removeImport(importToRemove);
        }
        for (String importToAdd : rewriteRule.addImports()) {
            importRewriter.addImport(importToAdd);
        }
        for (String staticImportToRemove : rewriteRule.removeStaticImports()) {
            importRewriter.removeStaticImport(staticImportToRemove);
        }
        for (String staticImportToAdd : rewriteRule.addStaticImports()) {
            addStaticImport(importRewriter, staticImportToAdd);
        }
    }
    
    /**
     * Adds a static import to the compilation unit.
     * Parses the import string and handles both specific method imports and wildcard imports.
     * 
     * @param importRewriter the import rewriter
     * @param staticImport the fully qualified static import (e.g., "org.junit.Assert.assertEquals" or "org.junit.Assert.*")
     */
    protected void addStaticImport(ImportRewrite importRewriter, String staticImport) {
        // Parse static import: "org.junit.Assert.assertEquals" -> class="org.junit.Assert", method="assertEquals"
        int lastDot = staticImport.lastIndexOf('.');
        if (lastDot > 0) {
            String className = staticImport.substring(0, lastDot);
            String methodName = staticImport.substring(lastDot + 1);
            // Handle wildcard imports (*)
            if ("*".equals(methodName)) {
                importRewriter.addStaticImport(className, "*", false);
            } else {
                importRewriter.addStaticImport(className, methodName, false);
            }
        }
    }
    
    /**
     * Gets the annotation from holder using reflection.
     * Subclasses can override if they have a type-safe way to access the annotation.
     * 
     * @param holder the holder object
     * @return the annotation
     */
    @SuppressWarnings("unused")
    protected Annotation getAnnotationFromHolder(Object holder) {
        try {
            java.lang.reflect.Method method = holder.getClass().getMethod("getAnnotation");
            return (Annotation) method.invoke(holder);
        } catch (Exception e) {
            throw new RuntimeException("Holder must provide getAnnotation() method", e);
        }
    }
    
    /**
     * Gets a binding as expression from holder using reflection.
     * Subclasses can override if they have a type-safe way to access bindings.
     * 
     * @param holder the holder object
     * @param placeholder the placeholder name
     * @return the expression binding, or null if not found
     */
    @SuppressWarnings("unused")
    protected Expression getBindingAsExpressionFromHolder(Object holder, String placeholder) {
        try {
            java.lang.reflect.Method method = holder.getClass().getMethod("getBindingAsExpression", String.class);
            return (Expression) method.invoke(holder, placeholder);
        } catch (Exception e) {
            return null; // Binding not found is not an error
        }
    }
    
    /**
     * Parses a replacement pattern to extract annotation name and placeholder information.
     * 
     * <p><b>Pattern format:</b></p>
     * <ul>
     *   <li>{@code @AnnotationName} - marker annotation</li>
     *   <li>{@code @AnnotationName($placeholder)} - single value annotation</li>
     *   <li>{@code @AnnotationName($placeholder$)} - annotation with multi-placeholder</li>
     *   <li>{@code ClassName.method($args$)} - method call with multi-placeholder</li>
     * </ul>
     * 
     * <p><b>Note:</b> Now supports both simple and qualified names (e.g., "Assertions.assertEquals").</p>
     * 
     * @param pattern the replacement pattern (e.g., "@BeforeEach", "@Disabled($value)", "Assertions.assertEquals($args$)")
     * @return parsed annotation replacement information
     */
    private AnnotationReplacementInfo parseReplacementPattern(String pattern) {
        java.util.regex.Matcher matcher = REPLACEMENT_PATTERN.matcher(pattern.trim());
        
        if (matcher.matches()) {
            String name = matcher.group(1);
            String placeholderPart = matcher.group(2); // null if no parentheses
            return new AnnotationReplacementInfo(name, placeholderPart);
        }
        
        throw new IllegalArgumentException("Invalid replacement pattern: " + pattern);
    }
    
    /**
     * Holds parsed information about an annotation replacement.
     */
    protected static class AnnotationReplacementInfo {
        final String annotationName;
        final String placeholderName; // null if no placeholder, or could be "$args$" for multi-placeholders
        
        AnnotationReplacementInfo(String annotationName, String placeholderName) {
            this.annotationName = annotationName;
            this.placeholderName = placeholderName;
        }
        
        boolean hasPlaceholders() {
            return placeholderName != null && !placeholderName.isEmpty();
        }
        
        boolean isMultiPlaceholder() {
            return placeholderName != null && placeholderName.startsWith("$") && placeholderName.endsWith("$"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
    
    /**
     * Replaces a marker annotation with a new one and updates imports.
     * This is a common operation for simple annotation migrations.
     * 
     * <p>This helper method is useful for plugins that need to override {@code processRewrite()}
     * for custom logic but still want to leverage a standardized approach for simple
     * marker annotation replacements.</p>
     * 
     * <p><b>Example usage:</b></p>
     * <pre>
     * // Replace @BeforeClass with @BeforeAll
     * replaceMarkerAnnotation(
     *     group, rewriter, ast, importRewriter,
     *     oldAnnotation,
     *     "BeforeAll",
     *     "org.junit.BeforeClass",
     *     "org.junit.jupiter.api.BeforeAll"
     * );
     * </pre>
     * 
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param oldAnnotation the annotation to replace
     * @param newAnnotationName the simple name of the new annotation (e.g., "BeforeEach")
     * @param removeImport the fully qualified import to remove (e.g., "org.junit.Before")
     * @param addImport the fully qualified import to add (e.g., "org.junit.jupiter.api.BeforeEach")
     */
    protected void replaceMarkerAnnotation(
            TextEditGroup group, 
            ASTRewrite rewriter, 
            AST ast,
            ImportRewrite importRewriter, 
            Annotation oldAnnotation,
            String newAnnotationName, 
            String removeImport, 
            String addImport) {
        
        MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
        newAnnotation.setTypeName(ast.newSimpleName(newAnnotationName));
        ASTNodes.replaceButKeepComment(rewriter, oldAnnotation, newAnnotation, group);
        importRewriter.removeImport(removeImport);
        importRewriter.addImport(addImport);
    }
    
    /**
     * Gets a preview of the code before or after refactoring.
     * Used to display examples in the Eclipse cleanup preferences UI.
     * 
     * @param afterRefactoring if true, returns the "after" preview; if false, returns the "before" preview
     * @return a code snippet showing the transformation (formatted as Java source code)
     */
    public abstract String getPreview(boolean afterRefactoring);
}
