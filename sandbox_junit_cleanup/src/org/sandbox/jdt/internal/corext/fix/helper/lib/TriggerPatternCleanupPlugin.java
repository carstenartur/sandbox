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
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Abstract base class that connects TriggerPattern with the Cleanup framework.
 * Reduces boilerplate from ~80 lines to ~20 lines per plugin.
 * 
 * <p>Subclasses must:</p>
 * <ol>
 *   <li>Add {@link CleanupPattern} annotation to the class</li>
 *   <li>Implement {@link #createHolder(Match)} to create the JunitHolder</li>
 *   <li>Implement {@link #process2Rewrite} for the AST transformation</li>
 *   <li>Implement {@link #getPreview(boolean)} for UI preview</li>
 * </ol>
 * 
 * <p>Alternative approach: Override {@link #getPatterns()} instead of using annotation.</p>
 * 
 * @since 1.3.0
 */
public abstract class TriggerPatternCleanupPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {
    
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
     * Creates a JunitHolder from a Match.
     * Default implementation stores the matched node and bindings.
     * Subclasses can override to customize holder creation.
     * 
     * @param match the matched pattern
     * @return a JunitHolder containing match information, or null to skip this match
     */
    protected JunitHolder createHolder(Match match) {
        JunitHolder holder = new JunitHolder();
        holder.setMinv(match.getMatchedNode());
        holder.setBindings(match.getBindings());
        return holder;
    }
    
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
     * Called for each match. Default implementation adds a rewrite operation.
     * Subclasses can override for custom processing.
     * 
     * @param match the matched pattern
     * @param fixcore the cleanup fix core
     * @param operations set to add operations to
     * @param dataHolder the reference holder
     * @return true to stop processing more matches, false to continue
     */
    protected boolean processMatch(Match match, JUnitCleanUpFixCore fixcore,
            Set<CompilationUnitRewriteOperationWithSourceRange> operations,
            ReferenceHolder<Integer, JunitHolder> dataHolder) {
        JunitHolder holder = createHolder(match);
        if (holder != null) {
            dataHolder.put(dataHolder.size(), holder);
            operations.add(fixcore.rewrite(dataHolder));
        }
        return false;
    }
    
    @Override
    public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
            Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
        
        ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
        
        for (Pattern pattern : getPatterns()) {
            List<Match> matches = ENGINE.findMatches(compilationUnit, pattern);
            
            for (Match match : matches) {
                ASTNode node = match.getMatchedNode();
                
                // Skip already processed nodes
                if (nodesprocessed.contains(node)) {
                    continue;
                }
                
                // Validate qualified type if specified
                if (pattern.getQualifiedType() != null) {
                    if (!validateQualifiedType(node, pattern.getQualifiedType())) {
                        continue;
                    }
                }
                
                // Mark node as processed once it passes basic type validation
                // so it is not re-evaluated in subsequent find() calls, even if
                // shouldProcess() decides to skip it.
                nodesprocessed.add(node);
                
                // Allow subclasses to add additional validation
                if (!shouldProcess(match, pattern)) {
                    continue;
                }
                
                boolean stop = processMatch(match, fixcore, operations, dataHolder);
                if (stop) {
                    return;
                }
            }
        }
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
     * Provides default implementation of process2Rewrite using @RewriteRule annotation.
     * Subclasses can override this method if they need custom rewrite logic,
     * or they can use @RewriteRule for simple annotation replacements.
     * 
     * <p><b>Limitations:</b> This default implementation only supports:
     * <ul>
     *   <li>MarkerAnnotation (no parameters): {@code @BeforeEach}</li>
     *   <li>SingleMemberAnnotation (single value): {@code @Disabled($value)}</li>
     * </ul>
     * NormalAnnotation with named parameters like {@code @Ignore(value="reason")} is not supported.
     * Plugins that need such transformations must override this method.
     * 
     * @param group the text edit group for tracking changes
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param junitHolder the holder containing JUnit migration information
     */
    @Override
    protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, JunitHolder junitHolder) {
        
        RewriteRule rewriteRule = this.getClass().getAnnotation(RewriteRule.class);
        if (rewriteRule == null) {
            throw new UnsupportedOperationException(
                "Plugin " + getClass().getSimpleName() + 
                " must be annotated with @RewriteRule because it does not override process2Rewrite()");
        }
        
        // Process the replacement pattern
        String replaceWith = rewriteRule.replaceWith();
        Annotation oldAnnotation = junitHolder.getAnnotation();
        
        // Parse the replacement pattern to extract annotation name and placeholders
        AnnotationReplacementInfo replacementInfo = parseReplacementPattern(replaceWith);
        
        // Create the new annotation based on whether placeholders are present
        Annotation newAnnotation;
        if (replacementInfo.hasPlaceholders()) {
            // Create SingleMemberAnnotation with the placeholder value
            SingleMemberAnnotation singleMemberAnnotation = ast.newSingleMemberAnnotation();
            singleMemberAnnotation.setTypeName(ast.newSimpleName(replacementInfo.annotationName));
            
            // Get the placeholder value from bindings
            // TriggerPattern stores placeholders with $ prefix in the bindings map
            String placeholder = replacementInfo.placeholderName;
            Expression value = junitHolder.getBindingAsExpression("$" + placeholder);
            
            /*
             * Fallback: if no binding is found for the placeholder, reuse the value from the
             * existing annotation when it is a SingleMemberAnnotation.
             *
             * This is a defensive, last-resort mechanism to preserve the original annotation
             * value so that the cleanup does not silently drop semantics.
             *
             * NOTE / TODO:
             * - In normal operation, placeholder lookup via junitHolder.getBindingAsExpression(...)
             *   should succeed and this block should not be relied upon.
             * - If placeholder names or bindings are misconfigured, this fallback can mask the
             *   underlying bug by making the transformation appear to succeed.
             * - Once placeholder lookup is reliable, consider removing this fallback (or replacing
             *   it with a more visible failure mechanism) so that binding errors surface during
             *   development and testing.
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
                if ("*".equals(methodName)) {
                    importRewriter.addStaticImport(className, "*", false);
                } else {
                    importRewriter.addStaticImport(className, methodName, false);
                }
            }
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
    private static class AnnotationReplacementInfo {
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
     * This is a common operation for simple annotation migrations like
     * {@code @Before → @BeforeEach}, {@code @After → @AfterEach}, etc.
     * 
     * <p>This helper method is useful for plugins that need to override {@code process2Rewrite()}
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
}
