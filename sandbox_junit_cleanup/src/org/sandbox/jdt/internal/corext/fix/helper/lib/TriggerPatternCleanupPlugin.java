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
import org.eclipse.jdt.core.dom.MarkerAnnotation;
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
import org.sandbox.jdt.triggerpattern.cleanup.AbstractPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.cleanup.PatternCleanupHelper;

/**
 * Abstract base class that connects TriggerPattern with the JUnit Cleanup framework.
 * Reduces boilerplate from ~80 lines to ~20 lines per plugin.
 * 
 * <p>This class bridges the generic TriggerPattern framework (in sandbox_common) with 
 * JUnit-specific cleanup infrastructure, using composition to delegate pattern matching
 * logic to {@link PatternCleanupHelper}.</p>
 * 
 * <p>Subclasses must:</p>
 * <ol>
 *   <li>Add {@link CleanupPattern} annotation to the class</li>
 *   <li>Implement {@link #createHolder(Match)} to create the JunitHolder</li>
 *   <li>Implement {@link #process2Rewrite} for the AST transformation (or use @RewriteRule)</li>
 *   <li>Implement {@link #getPreview(boolean)} for UI preview</li>
 * </ol>
 * 
 * <p>Alternative approach: Override {@link #getPatterns()} instead of using annotation.</p>
 * 
 * @since 1.3.0
 */
public abstract class TriggerPatternCleanupPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {
    
    private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
    private final PatternCleanupHelper helper = new PatternCleanupHelper(this.getClass());
    
    /**
     * Returns the Pattern extracted from the @CleanupPattern annotation.
     * Subclasses can override {@link #getPatterns()} instead if they need multiple patterns.
     * 
     * <p>Delegates to {@link PatternCleanupHelper#getPattern()}.</p>
     * 
     * @return the pattern for matching, or null if no @CleanupPattern annotation is present
     */
    public Pattern getPattern() {
        return helper.getPattern();
    }
    
    /**
     * Returns the patterns to match in the compilation unit.
     * Default implementation returns a single pattern from @CleanupPattern annotation.
     * Subclasses can override to provide multiple patterns.
     * 
     * <p>Delegates to {@link PatternCleanupHelper#getPatterns()} by default.</p>
     * 
     * @return list of patterns to match
     * @throws IllegalStateException if neither @CleanupPattern annotation is present nor getPatterns() is overridden
     */
    protected List<Pattern> getPatterns() {
        List<Pattern> patterns = helper.getPatterns();
        if (patterns.isEmpty()) {
            throw new IllegalStateException(
                "Plugin " + getClass().getSimpleName() + 
                " must either be annotated with @CleanupPattern or override getPatterns() method to define patterns");
        }
        return patterns;
    }
    
    /**
     * Returns the cleanup ID from the @CleanupPattern annotation.
     * 
     * <p>Delegates to {@link PatternCleanupHelper#getCleanupId()}.</p>
     * 
     * @return the cleanup ID, or empty string if annotation is not present or cleanupId is not set
     */
    public String getCleanupId() {
        return helper.getCleanupId();
    }
    
    /**
     * Returns the description from the @CleanupPattern annotation.
     * 
     * <p>Delegates to {@link PatternCleanupHelper#getDescription()}.</p>
     * 
     * @return the description, or empty string if annotation is not present or description is not set
     */
    public String getDescription() {
        return helper.getDescription();
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
     * <p>Delegates to {@link PatternCleanupHelper#validateQualifiedType(ASTNode, String)}.</p>
     * 
     * @param node the AST node to validate
     * @param qualifiedType the expected fully qualified type name
     * @return true if types match, false otherwise
     */
    protected boolean validateQualifiedType(ASTNode node, String qualifiedType) {
        return helper.validateQualifiedType(node, qualifiedType);
    }
    
    /**
     * Helper delegate for rewrite rule processing.
     * Provides access to AbstractPatternCleanupPlugin's consolidated rewrite logic.
     */
    private final RewriteRuleDelegate rewriteRuleDelegate = new RewriteRuleDelegate();
    
    /**
     * Provides default implementation of process2Rewrite using @RewriteRule annotation.
     * Subclasses can override this method if they need custom rewrite logic,
     * or they can use @RewriteRule for pattern-based transformations.
     * 
     * <p><b>Supported patterns:</b></p>
     * <ul>
     *   <li>ANNOTATION patterns: MarkerAnnotation, SingleMemberAnnotation</li>
     *   <li>EXPRESSION patterns: Any expression replacement (delegates to FixUtilities.rewriteFix)</li>
     *   <li>METHOD_CALL patterns: Method invocation replacement (delegates to FixUtilities.rewriteFix)</li>
     *   <li>CONSTRUCTOR patterns: Constructor invocation replacement (delegates to FixUtilities.rewriteFix)</li>
     *   <li>STATEMENT patterns: Statement replacement (delegates to FixUtilities.rewriteFix)</li>
     *   <li>FIELD patterns: Field declaration replacement (delegates to FixUtilities.rewriteFix)</li>
     * </ul>
     * 
     * <p><b>Limitations for ANNOTATION patterns:</b></p>
     * <ul>
     *   <li>NormalAnnotation with named parameters like {@code @Ignore(value="reason")} is not supported.</li>
     * </ul>
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
        
        // Delegate to the consolidated rewrite logic in AbstractPatternCleanupPlugin
        // JunitHolder now implements MatchHolder, enabling type-safe delegation
        rewriteRuleDelegate.doRewrite(group, rewriter, ast, importRewriter, junitHolder);
    }
    
    /**
     * Inner class that provides access to AbstractPatternCleanupPlugin's processRewriteWithRule.
     * Uses composition to leverage the consolidated rewrite logic without requiring
     * TriggerPatternCleanupPlugin to extend AbstractPatternCleanupPlugin (which would
     * break the existing AbstractTool inheritance).
     */
    private class RewriteRuleDelegate extends AbstractPatternCleanupPlugin<JunitHolder> {
        
        /**
         * Public method to invoke the protected processRewriteWithRule.
         * This is needed because the outer class cannot directly call the protected method.
         */
        public void doRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
                ImportRewrite importRewriter, JunitHolder holder) {
            processRewriteWithRule(group, rewriter, ast, importRewriter, holder);
        }
        
        @Override
        protected JunitHolder createHolder(Match match) {
            // Not used - delegation only for processRewriteWithRule
            return null;
        }
        
        @Override
        protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
                ImportRewrite importRewriter, JunitHolder holder) {
            // Not used - we call processRewriteWithRule directly
        }
        
        @Override
        public String getPreview(boolean afterRefactoring) {
            // Delegate to the outer class's getPreview method
            return TriggerPatternCleanupPlugin.this.getPreview(afterRefactoring);
        }
        
        /**
         * Override to get the RewriteRule annotation from the outer plugin class
         * rather than this delegate class.
         */
        @Override
        protected RewriteRule getRewriteRule() {
            return TriggerPatternCleanupPlugin.this.getClass().getAnnotation(RewriteRule.class);
          
        @SuppressWarnings("unused") // Intended for future multi-placeholder pattern support
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
