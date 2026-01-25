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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
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
            // Create a separate dataHolder for each match to avoid index mismatches
            // JUnitCleanUpFixCore.rewrite() uses hit.get(0) for source range computation
            // while AbstractTool.rewrite() uses hit.get(hit.size()-1) for processing.
            // With a single-item holder, both indices point to the same item.
            ReferenceHolder<Integer, JunitHolder> perMatchDataHolder = new ReferenceHolder<>();
            perMatchDataHolder.put(0, holder);
            operations.add(fixcore.rewrite(perMatchDataHolder));
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
}
