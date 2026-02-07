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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Helper class that provides TriggerPattern cleanup functionality via composition.
 * This is the delegate used by cleanup plugins that need to maintain their own inheritance hierarchy
 * (e.g., JUnit plugins extending AbstractTool) but want to use TriggerPattern features.
 * 
 * <p>For cleanup plugins that don't have inheritance constraints, consider using
 * {@link AbstractPatternCleanupPlugin} directly via inheritance instead.</p>
 * 
 * @since 1.3.0
 */
public class PatternCleanupHelper {
    
    private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();
    
    private final Class<?> pluginClass;
    
    /**
     * Creates a helper for the given plugin class.
     * 
     * @param pluginClass the plugin class (used to read @CleanupPattern annotation)
     */
    public PatternCleanupHelper(Class<?> pluginClass) {
        this.pluginClass = pluginClass;
    }
    
    /**
     * Returns the Pattern extracted from the @CleanupPattern annotation.
     * 
     * @return the pattern for matching, or null if no @CleanupPattern annotation is present
     */
    public Pattern getPattern() {
        CleanupPattern annotation = pluginClass.getAnnotation(CleanupPattern.class);
        if (annotation == null) {
            return null;
        }
        String qualifiedType = annotation.qualifiedType().isEmpty() ? null : annotation.qualifiedType();
        return new Pattern(annotation.value(), annotation.kind(), qualifiedType);
    }
    
    /**
     * Returns the patterns to match in the compilation unit.
     * Default implementation returns a single pattern from @CleanupPattern annotation.
     * 
     * @return list of patterns to match, or empty list if no pattern is configured
     */
    public List<Pattern> getPatterns() {
        Pattern pattern = getPattern();
        if (pattern != null) {
            return List.of(pattern);
        }
        return List.of();
    }
    
    /**
     * Returns the cleanup ID from the @CleanupPattern annotation.
     * 
     * @return the cleanup ID, or empty string if annotation is not present or cleanupId is not set
     */
    public String getCleanupId() {
        CleanupPattern annotation = pluginClass.getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.cleanupId() : ""; //$NON-NLS-1$
    }
    
    /**
     * Returns the description from the @CleanupPattern annotation.
     * 
     * @return the description, or empty string if annotation is not present or description is not set
     */
    public String getDescription() {
        CleanupPattern annotation = pluginClass.getAnnotation(CleanupPattern.class);
        return annotation != null ? annotation.description() : ""; //$NON-NLS-1$
    }
    
    /**
     * Finds all matches using TriggerPatternEngine.
     * 
     * @param compilationUnit the compilation unit to search
     * @param patterns the patterns to match
     * @return list of all matches
     */
    public List<Match> findAllMatches(org.eclipse.jdt.core.dom.CompilationUnit compilationUnit, List<Pattern> patterns) {
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
    public boolean validateQualifiedType(ASTNode node, String qualifiedType) {
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
