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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Refines line-based diff hunks into statement/expression-level
 * {@link CodeChangePair}s.
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>Parse both sides (before/after) of the file as {@link CompilationUnit}s.</li>
 *   <li>For each hunk, map changed lines to AST nodes.</li>
 *   <li>Extract the smallest enclosing statements/expressions.</li>
 *   <li>Pair them up (before ↔ after) to produce {@link CodeChangePair}s.</li>
 * </ol>
 *
 * @since 1.2.6
 */
public class DiffHunkRefiner {

	/**
	 * Refines a file diff into a list of code change pairs at the
	 * statement/expression level.
	 *
	 * @param diff the file diff with full before/after content and hunks
	 * @return a list of refined code change pairs
	 */
	public List<CodeChangePair> refineToStatements(FileDiff diff) {
		if (diff.contentBefore() == null || diff.contentAfter() == null) {
			return List.of();
		}

		if (!isJavaFile(diff.filePath())) {
			return List.of();
		}

		CompilationUnit cuBefore = parseCompilationUnit(diff.contentBefore());
		CompilationUnit cuAfter = parseCompilationUnit(diff.contentAfter());

		if (cuBefore == null || cuAfter == null) {
			return List.of();
		}

		List<CodeChangePair> result = new ArrayList<>();

		for (DiffHunk hunk : diff.hunks()) {
			List<CodeChangePair> pairs = refineHunk(
					diff.filePath(), hunk, cuBefore, cuAfter,
					diff.contentBefore(), diff.contentAfter());
			result.addAll(pairs);
		}

		return result;
	}

	private List<CodeChangePair> refineHunk(String filePath, DiffHunk hunk,
			CompilationUnit cuBefore, CompilationUnit cuAfter,
			String contentBefore, String contentAfter) {

		// Collect lines changed in the before and after
		Set<Integer> beforeLines = computeChangedLines(
				hunk.beforeStartLine(), hunk.beforeLineCount());
		Set<Integer> afterLines = computeChangedLines(
				hunk.afterStartLine(), hunk.afterLineCount());

		// Find AST nodes that overlap the changed lines
		List<ASTNode> beforeNodes = findNodesForLines(cuBefore, beforeLines);
		List<ASTNode> afterNodes = findNodesForLines(cuAfter, afterLines);

		List<CodeChangePair> pairs = new ArrayList<>();

		// Pair up statements by position
		int count = Math.min(beforeNodes.size(), afterNodes.size());
		for (int i = 0; i < count; i++) {
			ASTNode beforeNode = beforeNodes.get(i);
			ASTNode afterNode = afterNodes.get(i);

			String beforeSnippet = extractSource(contentBefore, beforeNode);
			String afterSnippet = extractSource(contentAfter, afterNode);

			if (beforeSnippet.equals(afterSnippet)) {
				continue; // skip unchanged
			}

			PatternKind kind = inferKind(beforeNode);
			int lineNumber = cuBefore.getLineNumber(beforeNode.getStartPosition());

			pairs.add(new CodeChangePair(filePath, lineNumber,
					beforeSnippet, afterSnippet,
					beforeNode, afterNode, kind));
		}

		return pairs;
	}

	private Set<Integer> computeChangedLines(int startLine, int lineCount) {
		Set<Integer> lines = new HashSet<>();
		for (int i = 0; i < lineCount; i++) {
			lines.add(startLine + i);
		}
		return lines;
	}

	private List<ASTNode> findNodesForLines(CompilationUnit cu, Set<Integer> lines) {
		List<ASTNode> matchingNodes = new ArrayList<>();
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (node instanceof Statement) {
					int startLine = cu.getLineNumber(node.getStartPosition());
					int endLine = cu.getLineNumber(
							node.getStartPosition() + node.getLength() - 1);
					for (int line = startLine; line <= endLine; line++) {
						if (lines.contains(line)) {
							matchingNodes.add(node);
							break;
						}
					}
				}
			}
		});

		// Deduplicate: keep only the smallest enclosing statement per position
		return deduplicateNodes(matchingNodes);
	}

	private List<ASTNode> deduplicateNodes(List<ASTNode> nodes) {
		// If a node contains another node in the list, keep only the child
		List<ASTNode> result = new ArrayList<>();
		for (ASTNode node : nodes) {
			boolean isParent = nodes.stream()
					.anyMatch(other -> other != node && isAncestor(node, other));
			if (!isParent) {
				// Avoid adding the same node twice
				boolean alreadyAdded = result.stream()
						.anyMatch(r -> r.getStartPosition() == node.getStartPosition()
								&& r.getLength() == node.getLength());
				if (!alreadyAdded) {
					result.add(node);
				}
			}
		}
		return result;
	}

	private boolean isAncestor(ASTNode ancestor, ASTNode descendant) {
		int aStart = ancestor.getStartPosition();
		int aEnd = aStart + ancestor.getLength();
		int dStart = descendant.getStartPosition();
		int dEnd = dStart + descendant.getLength();
		return aStart <= dStart && aEnd >= dEnd && (aStart != dStart || aEnd != dEnd);
	}

	private String extractSource(String source, ASTNode node) {
		int start = node.getStartPosition();
		int end = start + node.getLength();
		if (start >= 0 && end <= source.length()) {
			return source.substring(start, end).trim();
		}
		return node.toString().trim();
	}

	private PatternKind inferKind(ASTNode node) {
		if (node instanceof ExpressionStatement es) {
			if (es.getExpression() instanceof MethodInvocation) {
				return PatternKind.METHOD_CALL;
			}
			if (es.getExpression() instanceof ClassInstanceCreation) {
				return PatternKind.CONSTRUCTOR;
			}
			return PatternKind.EXPRESSION;
		}
		return PatternKind.STATEMENT;
	}

	private boolean isJavaFile(String filePath) {
		return filePath != null && filePath.endsWith(".java"); //$NON-NLS-1$
	}

	private static CompilationUnit parseCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_COMPLIANCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		ASTNode node = parser.createAST(null);
		if (node instanceof CompilationUnit cu) {
			return cu;
		}
		return null;
	}
}
