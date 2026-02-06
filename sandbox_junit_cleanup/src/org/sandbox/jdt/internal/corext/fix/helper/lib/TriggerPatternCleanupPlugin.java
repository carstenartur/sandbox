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
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.common.lib.AbstractPatternCleanupPlugin;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;

/**
 * JUnit-specific extension that connects TriggerPattern with the JUnit Cleanup framework.
 * Reduces boilerplate from ~80 lines to ~20 lines per plugin.
 * 
 * <p>Subclasses must:</p>
 * <ol>
 *   <li>Add {@link CleanupPattern} annotation to the class</li>
 *   <li>Implement {@link #createHolder(Match)} to create the JunitHolder</li>
 *   <li>Implement {@link #process2Rewrite} for the AST transformation (or use {@link RewriteRule})</li>
 *   <li>Implement {@link #getPreview(boolean)} for UI preview</li>
 * </ol>
 * 
 * <p>Alternative approach: Override {@link #getPatterns()} instead of using annotation.</p>
 * 
 * @since 1.3.0
 */
public abstract class TriggerPatternCleanupPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {
    
    /** Access to the pattern matching engine */
    private static final AbstractPatternCleanupPlugin<JunitHolder> PATTERN_HELPER = new AbstractPatternCleanupPlugin<JunitHolder>() {
        @Override
        protected Expression getBindingFromHolder(JunitHolder holder, String key) {
            return holder.getBindingAsExpression(key);
        }
    };
    
    /**
     * Returns the Pattern extracted from the @CleanupPattern annotation.
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
     * Creates a JunitHolder from a Match.
     * Default implementation stores the matched node and bindings.
     * Subclasses can override to customize holder creation.
     * 
     * @param match the matched pattern
     * @return a JunitHolder containing match information, or null to skip this match
     */
    protected JunitHolder createHolder(Match match) {
        JunitHolder holder = new JunitHolder();
        holder.minv = match.getMatchedNode();
        holder.bindings = match.getBindings();
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
            List<Match> matches = PATTERN_HELPER.getEngine().findMatches(compilationUnit, pattern);
            
            for (Match match : matches) {
                ASTNode node = match.getMatchedNode();
                
                // Skip already processed nodes
                if (nodesprocessed.contains(node)) {
                    continue;
                }
                
                // Validate qualified type if specified
                if (pattern.getQualifiedType() != null) {
                    if (!PATTERN_HELPER.validateQualifiedType(node, pattern.getQualifiedType())) {
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
     * Provides default implementation of process2Rewrite using @RewriteRule annotation.
     * Subclasses can override this method if they need custom rewrite logic.
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
        
        Annotation oldAnnotation = junitHolder.getAnnotation();
        
        // Delegate the actual rewrite to the helper, passing our class for annotation lookup
        PATTERN_HELPER.applyRewriteRule(this.getClass(), group, rewriter, ast, importRewriter, junitHolder, oldAnnotation);
    }
    
    /**
     * Replaces a marker annotation with a new one and updates imports.
     * Delegates to the base class implementation.
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
        
        PATTERN_HELPER.replaceMarkerAnnotation(group, rewriter, ast, importRewriter, oldAnnotation, 
                newAnnotationName, removeImport, addImport);
    }
}
