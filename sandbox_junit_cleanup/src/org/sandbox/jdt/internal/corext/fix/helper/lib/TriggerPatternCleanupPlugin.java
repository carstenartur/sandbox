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
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Base class for JUnit cleanup plugins that use TriggerPattern for pattern matching.
 * 
 * <p>This provides a declarative approach to defining what AST patterns to match,
 * replacing the imperative HelperVisitor-based approach.</p>
 * 
 * <p>Subclasses only need to:</p>
 * <ol>
 * <li>Override {@link #getPatterns()} to define patterns to match</li>
 * <li>Override {@link #processMatch(Match, JUnitCleanUpFixCore, Set, ReferenceHolder)} to handle matches (optional)</li>
 * <li>Override {@link #process2Rewrite(...)} for the actual AST transformation</li>
 * </ol>
 */
public abstract class TriggerPatternCleanupPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {
    
    private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
    
    /**
     * Returns the patterns to match in the compilation unit.
     * 
     * @return list of patterns to match
     */
    protected abstract List<Pattern> getPatterns();
    
    /**
     * Creates a JunitHolder from a Match.
     * Subclasses can override to customize holder creation.
     * 
     * @param match the matched pattern
     * @return a JunitHolder containing match information
     */
    protected JunitHolder createHolder(Match match) {
        JunitHolder holder = new JunitHolder();
        holder.minv = match.getMatchedNode();
        holder.bindings = match.getBindings();
        return holder;
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
        dataHolder.put(dataHolder.size(), holder);
        operations.add(fixcore.rewrite(dataHolder));
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
                
                nodesprocessed.add(node);
                
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
