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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintRegistry.HintDescriptor;

/**
 * Quick Assist processor for trigger pattern hints.
 * 
 * <p>This processor finds matching patterns at the cursor location and invokes
 * registered hint methods to provide completion proposals.</p>
 * 
 * @since 1.2.2
 */
public class TriggerPatternQuickAssistProcessor implements IQuickAssistProcessor {
	
	private static final HintRegistry REGISTRY = new HintRegistry();
	private final TriggerPatternEngine engine = new TriggerPatternEngine();
	
	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException {
		// Quick check - we could optimize this by caching patterns
		return !REGISTRY.getHints().isEmpty();
	}
	
	@Override
	public IJavaCompletionProposal[] getAssists(IInvocationContext context, IProblemLocation[] locations)
			throws CoreException {
		
		ICompilationUnit icu = context.getCompilationUnit();
		if (icu == null) {
			return null;
		}
		
		// Parse the compilation unit
		CompilationUnit cu = getCompilationUnit(icu);
		if (cu == null) {
			return null;
		}
		
		// Get the AST node at the cursor position
		int offset = context.getSelectionOffset();
		ASTNode coveringNode = getCoveringNode(cu, offset);
		if (coveringNode == null) {
			return null;
		}
		
		List<IJavaCompletionProposal> proposals = new ArrayList<>();
		
		// Check each registered hint
		for (HintDescriptor hint : REGISTRY.getHints()) {
			if (!hint.isEnabledByDefault()) {
				continue;
			}
			
			// Find matches for this pattern near the cursor
			List<Match> matches = engine.findMatches(cu, hint.getPattern());
			
			// Check if any match contains the cursor position
			for (Match match : matches) {
				if (containsOffset(match, offset)) {
					// Create hint context
					ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
					HintContext hintContext = new HintContext(cu, icu, match, rewrite);
					
					try {
						// Invoke the hint method
						Object result = hint.invoke(hintContext);
						
						// Convert result to proposals
						if (result instanceof IJavaCompletionProposal) {
							proposals.add((IJavaCompletionProposal) result);
						} else if (result instanceof List) {
							@SuppressWarnings("unchecked")
							List<IJavaCompletionProposal> list = (List<IJavaCompletionProposal>) result;
							proposals.addAll(list);
						}
					} catch (Exception e) {
						// Log error but continue with other hints
						ILog log = Platform.getLog(TriggerPatternQuickAssistProcessor.class);
						log.log(Status.error("Error invoking hint method", e)); //$NON-NLS-1$
					}
				}
			}
		}
		
		return proposals.isEmpty() ? null : proposals.toArray(new IJavaCompletionProposal[0]);
	}
	
	/**
	 * Parses the compilation unit.
	 */
	private CompilationUnit getCompilationUnit(ICompilationUnit icu) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(icu);
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
	
	/**
	 * Gets the AST node covering the given offset.
	 */
	private ASTNode getCoveringNode(CompilationUnit cu, int offset) {
		// Find the smallest node that covers the offset
		class NodeFinder extends org.eclipse.jdt.core.dom.ASTVisitor {
			ASTNode result = null;
			int targetOffset;
			
			NodeFinder(int offset) {
				this.targetOffset = offset;
			}
			
			@Override
			public void preVisit(ASTNode node) {
				int start = node.getStartPosition();
				int end = start + node.getLength();
				
				if (start <= targetOffset && targetOffset <= end) {
					// This node covers the offset
					if (result == null || node.getLength() < result.getLength()) {
						result = node;
					}
				}
			}
		}
		
		NodeFinder finder = new NodeFinder(offset);
		cu.accept(finder);
		return finder.result;
	}
	
	/**
	 * Checks if a match contains the given offset.
	 */
	private boolean containsOffset(Match match, int offset) {
		int start = match.getOffset();
		int end = start + match.getLength();
		return start <= offset && offset <= end;
	}
}
