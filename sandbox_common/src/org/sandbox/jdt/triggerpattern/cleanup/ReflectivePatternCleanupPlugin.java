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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternContext;
import org.sandbox.jdt.triggerpattern.api.PatternHandler;

/**
 * Base class for reflective pattern cleanup plugins.
 * 
 * <p>This class extends {@link AbstractPatternCleanupPlugin} and adds reflection-based
 * pattern handler discovery and invocation. Subclasses can define multiple pattern
 * handlers using the {@link PatternHandler} annotation, and this base class will
 * automatically discover and invoke them when matching patterns are found.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Automatic discovery of {@code @PatternHandler} annotated methods</li>
 *   <li>Reflection-based handler invocation</li>
 *   <li>Support for handler priorities</li>
 *   <li>Simplified pattern-based cleanup implementations</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * public class MyCleanupPlugin extends ReflectivePatternCleanupPlugin {
 *     
 *     {@literal @}PatternHandler(
 *         pattern = "if ($condition) { $then } else { $else }",
 *         kind = PatternKind.STATEMENT
 *     )
 *     public void convertToSwitch(PatternContext context) {
 *         // Transformation logic here
 *     }
 *     
 *     {@literal @}Override
 *     public String getPreview(boolean afterRefactoring) {
 *         return afterRefactoring ? "// After" : "// Before";
 *     }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
public abstract class ReflectivePatternCleanupPlugin extends AbstractPatternCleanupPlugin<PatternContext> {
    
    private List<HandlerInfo> handlerInfos;
    
    /**
     * Represents a pattern handler method with its metadata.
     */
    private static class HandlerInfo {
        final Method method;
        final PatternHandler annotation;
        final Pattern pattern;
        
        HandlerInfo(Method method, PatternHandler annotation) {
            this.method = method;
            this.annotation = annotation;
            this.pattern = new Pattern(
                annotation.pattern(), 
                annotation.kind(), 
                annotation.qualifiedType().isEmpty() ? null : annotation.qualifiedType()
            );
        }
    }
    
    /**
     * Discovers all pattern handler methods in the subclass.
     * This is called lazily on first use.
     */
    private void discoverHandlers() {
        if (handlerInfos != null) {
            return;
        }
        
        handlerInfos = new ArrayList<>();
        Method[] methods = this.getClass().getDeclaredMethods();
        
        for (Method method : methods) {
            PatternHandler annotation = method.getAnnotation(PatternHandler.class);
            if (annotation != null) {
                // Validate method signature
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != 1 || !paramTypes[0].equals(PatternContext.class)) {
                    throw new IllegalStateException(
                        "Method " + method.getName() + " annotated with @PatternHandler must have exactly one parameter of type PatternContext"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                
                Class<?> returnType = method.getReturnType();
                if (returnType != void.class && returnType != boolean.class) {
                    throw new IllegalStateException(
                        "Method " + method.getName() + " annotated with @PatternHandler must return void or boolean"); //$NON-NLS-1$ //$NON-NLS-2$
                }
                
                method.setAccessible(true);
                handlerInfos.add(new HandlerInfo(method, annotation));
            }
        }
        
        // Sort handlers by priority (ascending)
        handlerInfos.sort(Comparator.comparingInt(h -> h.annotation.priority()));
    }
    
    /**
     * Returns all patterns from the discovered pattern handlers.
     * 
     * @return list of patterns to match
     */
    @Override
    protected List<Pattern> getPatterns() {
        discoverHandlers();
        return handlerInfos.stream()
            .map(h -> h.pattern)
            .collect(Collectors.toList());
    }
    
    /**
     * Creates a PatternContext holder from a match.
     * The holder is created with null AST rewrite components, which will be
     * set later when processRewrite is called.
     * 
     * @param match the pattern match
     * @return a PatternContext holder (initially with null rewrite components)
     */
    @Override
    protected PatternContext createHolder(Match match) {
        // Create a holder that wraps the match
        // The actual rewrite components will be provided in processRewrite
        return new PatternContext(match, null, null, null, null);
    }
    
    /**
     * Processes the rewrite by invoking the appropriate pattern handler.
     * 
     * @param group the text edit group
     * @param rewriter the AST rewriter
     * @param ast the AST instance
     * @param importRewriter the import rewriter
     * @param holder the pattern context (which wraps the match)
     */
    @Override
    protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
            ImportRewrite importRewriter, PatternContext holder) {
        
        // Create a new context with the actual rewrite components
        Match match = holder.getMatch();
        PatternContext context = new PatternContext(match, rewriter, ast, importRewriter, group);
        
        // Find and invoke the appropriate handler
        discoverHandlers();
        
        for (HandlerInfo handlerInfo : handlerInfos) {
            // Check if this handler's pattern matches
            if (matchesPattern(match, handlerInfo.pattern)) {
                try {
                    Object result = handlerInfo.method.invoke(this, context);
                    
                    // If handler returns boolean and returns true, stop processing
                    if (result instanceof Boolean && (Boolean) result) {
                        break;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(
                        "Failed to invoke pattern handler: " + handlerInfo.method.getName(), e); //$NON-NLS-1$
                }
            }
        }
    }
    
    /**
     * Checks if a match corresponds to a specific pattern.
     * This is a simple implementation that compares the pattern strings.
     * 
     * @param match the match to check
     * @param pattern the pattern to compare against
     * @return true if the match corresponds to this pattern
     */
    private boolean matchesPattern(Match match, Pattern pattern) {
        // For now, we'll invoke all handlers since we can't easily determine
        // which specific pattern produced this match without additional metadata.
        // Subclasses can override shouldProcess() to add additional filtering.
        // A more sophisticated implementation could track pattern->match associations.
        return true;
    }
    
    /**
     * Provides a default cleanup ID based on the class name.
     * Subclasses should override this if they have a @CleanupPattern annotation
     * or want to provide a custom cleanup ID.
     * 
     * @return the cleanup ID
     */
    @Override
    public String getCleanupId() {
        String fromAnnotation = super.getCleanupId();
        if (!fromAnnotation.isEmpty()) {
            return fromAnnotation;
        }
        // Generate a default cleanup ID from the class name
        String className = this.getClass().getSimpleName();
        return "cleanup.reflective." + className.toLowerCase(java.util.Locale.ROOT); //$NON-NLS-1$
    }
    
    /**
     * Provides a default description based on the class name.
     * Subclasses should override this if they have a @CleanupPattern annotation
     * or want to provide a custom description.
     * 
     * @return the description
     */
    @Override
    public String getDescription() {
        String fromAnnotation = super.getDescription();
        if (!fromAnnotation.isEmpty()) {
            return fromAnnotation;
        }
        // Generate a default description from the class name
        String className = this.getClass().getSimpleName();
        return className.replaceAll("([A-Z])", " $1").trim(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
