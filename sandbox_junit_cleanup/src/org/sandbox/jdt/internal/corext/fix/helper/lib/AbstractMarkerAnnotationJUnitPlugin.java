/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * Abstract base class for JUnit plugins that perform simple marker annotation replacement.
 * This class consolidates the common patterns found in annotation-based cleanup plugins such as
 * BeforeJUnitPlugin, AfterJUnitPlugin, BeforeClassJUnitPlugin, AfterClassJUnitPlugin, and TestJUnitPlugin.
 * 
 * Subclasses only need to provide:
 * - The source annotation (JUnit 4) fully qualified name
 * - The target annotation (JUnit 5) simple name  
 * - The target annotation (JUnit 5) fully qualified name for import
 * - Preview strings and toString
 */
public abstract class AbstractMarkerAnnotationJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * @return The fully qualified name of the JUnit 4 source annotation (e.g., "org.junit.Before")
	 */
	protected abstract String getSourceAnnotation();
	
	/**
	 * @return The simple name of the JUnit 5 target annotation (e.g., "BeforeEach")
	 */
	protected abstract String getTargetAnnotationName();
	
	/**
	 * @return The fully qualified name of the JUnit 5 target annotation for import (e.g., "org.junit.jupiter.api.BeforeEach")
	 */
	protected abstract String getTargetAnnotationImport();

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callMarkerAnnotationVisitor(getSourceAnnotation(), compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	/**
	 * Processes a found marker annotation node and adds a rewrite operation.
	 * 
	 * @param fixcore the cleanup fix core
	 * @param operations the set of operations to add to
	 * @param node the found marker annotation
	 * @param dataHolder the reference holder for data
	 * @return false to continue visiting
	 */
	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, MarkerAnnotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	@Override
	public void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv= junitHolder.getAnnotation();
		MarkerAnnotation newAnnotation= ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(getTargetAnnotationName()));
		ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
		importRewriter.removeImport(getSourceAnnotation());
		importRewriter.addImport(getTargetAnnotationImport());
	}
}
