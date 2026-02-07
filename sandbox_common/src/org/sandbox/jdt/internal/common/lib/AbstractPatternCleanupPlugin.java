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
package org.sandbox.jdt.internal.common.lib;

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
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Generic base class that connects TriggerPattern with any cleanup framework.
 * This class is framework-agnostic and can be used by any cleanup plugin, not just JUnit.
 * 
 * <p>It provides utilities for extracting {@link Pattern}s from {@link CleanupPattern}
 * annotations and for driving the {@link TriggerPatternEngine}, but it does not
 * define any framework-specific lifecycle methods.
 * 
 * <p>Typical usage:</p>
 * <ul>
 *   <li>Annotate a concrete cleanup class with {@link CleanupPattern}, or</li>
 *   <li>Override {@link #getPatterns()} to supply one or more patterns programmatically.</li>
 * </ul>
 * 
 * <p>Framework-specific integration (for example, mapping matches to UI previews or
 * test framework hooks such as JUnit's {@code AbstractTool}) should be implemented
 * in adapter or subclass layers that extend or use this utility class. Those adapter
 * classes are responsible for defining and implementing their own lifecycle methods.</p>
 * 
 * @param <T> Type of holder used by the cleanup framework
 * @since 1.3.0
 */
public abstract class AbstractPatternCleanupPlugin<T> {
    
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
            "Plugin " + getClass().getSimpleName() +  //$NON-NLS-1$
            " must either be annotated with @CleanupPattern or override getPatterns() method to define patterns"); //$NON-NLS-1$
    }
    
    /**
     * Returns the cleanup ID from the @CleanupPattern annotation.
     * 
     * @return the cleanup ID, or empty string if annotation is not present or cleanupId is not set
     */
    public String getCleanupId() {
        CleanupPattern annotation = this.getClass().getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.cleanupId() : ""; //$NON-NLS-1$
    }
    
    /**
     * Returns the description from the @CleanupPattern annotation.
     * 
     * @return the description, or empty string if annotation is not present or description is not set
     */
    public String getDescription() {
        CleanupPattern annotation = this.getClass().getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.description() : ""; //$NON-NLS-1$
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
    
    /**
     * Provides access to the TriggerPatternEngine for subclasses.
     * 
     * @return the pattern matching engine
     */
    protected TriggerPatternEngine getEngine() {
        return ENGINE;
    }
    
    // Regex pattern for parsing replacement patterns (compiled once for performance)
    // Supports:
    // - @AnnotationName or @AnnotationName($placeholder) or @AnnotationName($placeholder$)
    // - MethodName.method($args) or MethodName.method($args$)
    private static final java.util.regex.Pattern REPLACEMENT_PATTERN = 
        java.util.regex.Pattern.compile("@?([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)(?:\\((.*)\\))?"); //$NON-NLS-1$
    
    /**
     * Provides default implementation using @RewriteRule annotation.
     * Subclasses can override this method if they need custom rewrite logic.
     * 
     * <p><b>Limitations:</b> This default implementation only supports:
     * <ul>
     *   <li>MarkerAnnotation (no parameters): {@code @BeforeEach}</li>
     *   <li>SingleMemberAnnotation (single value): {@code @Disabled($value)}</li>
     * </ul>
     * NormalAnnotation with named parameters like {@code @Ignore(value="reason")} is not supported.
     * Plugins that need such transformations must override this method.
     * 
     * @param pluginClass the class to check for @RewriteRule annotation
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param holder the holder containing information for the transformation
     * @param oldAnnotation the annotation to be replaced
     */
    protected void applyRewriteRule(Class<?> pluginClass, TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, T holder, Annotation oldAnnotation) {
        
        RewriteRule rewriteRule = pluginClass.getAnnotation(RewriteRule.class);
        if (rewriteRule == null) {
            throw new UnsupportedOperationException(
                "Plugin " + pluginClass.getSimpleName() +  //$NON-NLS-1$
                " must be annotated with @RewriteRule or override the rewrite method"); //$NON-NLS-1$
        }
        
        // Process the replacement pattern
        String replaceWith = rewriteRule.replaceWith();
        
        // Parse the replacement pattern to extract annotation name and placeholders
        AnnotationReplacementInfo replacementInfo = parseReplacementPattern(replaceWith);
        
        // Create the new annotation based on whether placeholders are present
        Annotation newAnnotation;
        if (replacementInfo.hasPlaceholders()) {
            // Create SingleMemberAnnotation with the placeholder value
            SingleMemberAnnotation singleMemberAnnotation = ast.newSingleMemberAnnotation();
            singleMemberAnnotation.setTypeName(ast.newSimpleName(replacementInfo.annotationName));
            
            // Get the placeholder value from the holder
            String placeholder = replacementInfo.placeholderName;
            Expression value = getBindingFromHolder(holder, "$" + placeholder); //$NON-NLS-1$
            
            /*
             * Fallback: if no binding is found for the placeholder, reuse the value from the
             * existing annotation when it is a SingleMemberAnnotation.
             */
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
            // Parse static import: "org.junit.Assert.assertEquals" -> class="org.junit.Assert", method="assertEquals"
            int lastDot = staticImportToAdd.lastIndexOf('.');
            if (lastDot > 0) {
                String className = staticImportToAdd.substring(0, lastDot);
                String methodName = staticImportToAdd.substring(lastDot + 1);
                // Handle wildcard imports (*)
                if ("*".equals(methodName)) { //$NON-NLS-1$
                    importRewriter.addStaticImport(className, "*", false); //$NON-NLS-1$
                } else {
                    importRewriter.addStaticImport(className, methodName, false);
                }
            }
        }
    }
    
    /**
     * Extracts a binding from the holder. Subclasses must implement this to provide
     * access to bindings stored in their specific holder type.
     * 
     * @param holder the holder containing bindings
     * @param key the binding key (e.g., "$value")
     * @return the expression bound to the key, or null if not found
     */
    protected abstract Expression getBindingFromHolder(T holder, String key);
    
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
     * @param pattern the replacement pattern
     * @return parsed annotation replacement information
     */
    private AnnotationReplacementInfo parseReplacementPattern(String pattern) {
        java.util.regex.Matcher matcher = REPLACEMENT_PATTERN.matcher(pattern.trim());
        
        if (matcher.matches()) {
            String name = matcher.group(1);
            String placeholderPart = matcher.group(2); // null if no parentheses
            return new AnnotationReplacementInfo(name, placeholderPart);
        }
        
        throw new IllegalArgumentException("Invalid replacement pattern: " + pattern); //$NON-NLS-1$
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
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param oldAnnotation the annotation to replace
     * @param newAnnotationName the simple name of the new annotation
     * @param removeImport the fully qualified import to remove
     * @param addImport the fully qualified import to add
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
}
