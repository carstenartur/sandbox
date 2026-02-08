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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternContext;
import org.sandbox.jdt.triggerpattern.api.PatternHandler;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Abstract base class for cleanup plugins that use reflective pattern handlers.
 * 
 * <p>This class enables a declarative style for defining cleanup transformations
 * by scanning methods annotated with {@link PatternHandler} and automatically
 * invoking them when patterns match.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>
 * public class MyCleanupPlugin extends ReflectivePatternCleanupPlugin {
 *     {@code @PatternHandler("if ($expr instanceof $type)")}
 *     public void handleInstanceOf(PatternContext context) {
 *         // transformation logic using context
 *     }
 *     
 *     {@code @Override}
 *     public String getPreview(boolean afterRefactoring) {
 *         return afterRefactoring ? "// transformed" : "// original";
 *     }
 * }
 * </pre>
 * 
 * <p>Methods annotated with {@code @PatternHandler} must:</p>
 * <ul>
 *   <li>Have {@code public} visibility</li>
 *   <li>Return {@code void}</li>
 *   <li>Take a single {@link PatternContext} parameter</li>
 * </ul>
 * 
 * @since 1.3.0
 */
public abstract class ReflectivePatternCleanupPlugin extends AbstractPatternCleanupPlugin<PatternContext> {
	
	// Maps from Match to the handler method that should process it
	private final Map<Match, Method> matchToHandler = new IdentityHashMap<>();
	
	// Cache of discovered handler methods
	private List<HandlerInfo> handlers;
	
	// We need our own engine instance since AbstractPatternCleanupPlugin's ENGINE is private
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	/**
	 * Information about a discovered pattern handler method.
	 */
	private static class HandlerInfo {
		final Method method;
		final Pattern pattern;
		
		HandlerInfo(Method method, Pattern pattern) {
			this.method = method;
			this.pattern = pattern;
		}
	}
	
	/**
	 * Discovers all {@code @PatternHandler} annotated methods in this class.
	 * 
	 * @return list of handler information
	 */
	private List<HandlerInfo> discoverHandlers() {
		if (handlers != null) {
			return handlers;
		}
		
		List<HandlerInfo> result = new ArrayList<>();
		Class<?> clazz = getClass();
		
		// Scan all methods in the class hierarchy
		while (clazz != null && clazz != Object.class) {
			for (Method method : clazz.getDeclaredMethods()) {
				PatternHandler annotation = method.getAnnotation(PatternHandler.class);
				if (annotation != null) {
					// Validate method signature
					validateHandlerMethod(method);
					
					// Create pattern from annotation
					String qualifiedType = annotation.qualifiedType().isEmpty() ? null : annotation.qualifiedType();
					Pattern pattern = new Pattern(annotation.value(), annotation.kind(), qualifiedType);
					
					// Make method accessible
					method.setAccessible(true);
					
					result.add(new HandlerInfo(method, pattern));
				}
			}
			clazz = clazz.getSuperclass();
		}
		
		handlers = result;
		return result;
	}
	
	/**
	 * Validates that a handler method has the correct signature.
	 * 
	 * @param method the method to validate
	 * @throws IllegalArgumentException if the method signature is invalid
	 */
	private void validateHandlerMethod(Method method) {
		// Check return type is void
		if (method.getReturnType() != void.class) {
			throw new IllegalArgumentException(
				"@PatternHandler method " + method.getName() +  //$NON-NLS-1$
				" must return void, but returns " + method.getReturnType().getName()); //$NON-NLS-1$
		}
		
		// Check parameter count and type
		Class<?>[] paramTypes = method.getParameterTypes();
		if (paramTypes.length != 1 || paramTypes[0] != PatternContext.class) {
			throw new IllegalArgumentException(
				"@PatternHandler method " + method.getName() +  //$NON-NLS-1$
				" must take a single PatternContext parameter"); //$NON-NLS-1$
		}
	}
	
	/**
	 * Returns the patterns from all {@code @PatternHandler} annotated methods.
	 * 
	 * @return list of patterns to match
	 */
	@Override
	protected List<Pattern> getPatterns() {
		List<HandlerInfo> handlerInfos = discoverHandlers();
		List<Pattern> patterns = new ArrayList<>();
		for (HandlerInfo info : handlerInfos) {
			patterns.add(info.pattern);
		}
		return patterns;
	}
	
	/**
	 * Finds all matches and maps them to their handler methods.
	 * 
	 * <p>Note: We override this method to use our own {@link TriggerPatternEngine} instance
	 * since the one in {@link AbstractPatternCleanupPlugin} is private.</p>
	 * 
	 * @param compilationUnit the compilation unit to search
	 * @param patterns the patterns to match
	 * @return list of all matches
	 */
	@Override
	protected List<Match> findAllMatches(CompilationUnit compilationUnit, List<Pattern> patterns) {
		List<Match> allMatches = new ArrayList<>();
		List<HandlerInfo> handlerInfos = discoverHandlers();
		
		// Clear previous mappings
		matchToHandler.clear();
		
		// For each handler, find matches and record which handler should process them
		for (HandlerInfo info : handlerInfos) {
			List<Match> matches = engine.findMatches(compilationUnit, info.pattern);
			for (Match match : matches) {
				matchToHandler.put(match, info.method);
				allMatches.add(match);
			}
		}
		
		return allMatches;
	}
	
	/**
	 * Creates a minimal holder for a match.
	 * 
	 * <p>We can't create a full PatternContext here because ASTRewrite, AST, etc. aren't
	 * available yet. We store the match for later retrieval in processRewrite.</p>
	 * 
	 * @param match the pattern match
	 * @return null (actual context created in processRewrite)
	 */
	@Override
	protected PatternContext createHolder(Match match) {
		// Store match for later processing
		// We return null because PatternContext requires ASTRewrite which isn't available yet
		pendingMatches.add(match);
		return null;
	}
	
	// Store matches that need processing
	// Note: Not thread-safe. This is acceptable because Eclipse cleanup operations
	// are single-threaded per compilation unit. The framework expects:
	// 1. findAllMatches() called once to discover matches
	// 2. createHolder() called once per match 
	// 3. processRewrite() called once with all context
	private final List<Match> pendingMatches = new ArrayList<>();
	
	/**
	 * Processes rewrites by invoking appropriate handler methods for all pending matches.
	 * 
	 * <p>This method is called by the cleanup framework with the full AST rewriting context.
	 * It creates a {@link PatternContext} for each pending match and invokes the registered handler.</p>
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewriter the import rewriter
	 * @param holder the pattern context (not used, will be null)
	 */
	@Override
	protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, PatternContext holder) {
		
		// Process all pending matches
		for (Match match : pendingMatches) {
			// Look up the handler for this match
			Method handler = matchToHandler.get(match);
			if (handler == null) {
				continue; // Skip if no handler found
			}
			
			// Create the context with full rewriting capabilities
			PatternContext context = new PatternContext(match, rewriter, ast, importRewriter, group);
			
			// Invoke the handler
			try {
				handler.invoke(this, context);
			} catch (Exception e) {
				throw new RuntimeException(
					"Failed to invoke pattern handler " + handler.getName() +  //$NON-NLS-1$
					" for match at offset " + match.getOffset(), e); //$NON-NLS-1$
			}
		}
		
		// Clear pending matches after processing
		pendingMatches.clear();
	}
	
	/**
	 * Gets a preview of the code transformation.
	 * 
	 * <p>Subclasses must implement this to provide example code snippets
	 * showing the "before" and "after" states of the transformation.</p>
	 * 
	 * @param afterRefactoring if true, return the "after" preview; otherwise the "before" preview
	 * @return a code snippet showing the transformation
	 */
	@Override
	public abstract String getPreview(boolean afterRefactoring);
}
