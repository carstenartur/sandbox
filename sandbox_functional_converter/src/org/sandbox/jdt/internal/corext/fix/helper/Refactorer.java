/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;

public class Refactorer {
	private final EnhancedForStatement forLoop;
	private final ASTRewrite rewrite;
	private final PreconditionsChecker preconditions;
	private final TextEditGroup group;

	public Refactorer(EnhancedForStatement forLoop, ASTRewrite rewrite, PreconditionsChecker preconditions,
			TextEditGroup group) {
		this.forLoop = forLoop;
		this.rewrite = rewrite;
		this.preconditions = preconditions;
		//forLoop.getAST();
		this.group = group;
	}

	/** Checks if the loop can be refactored to a stream operation. */
	public boolean isRefactorable() {
		return preconditions.isSafeToRefactor() 
//				&& preconditions.iteratesOverIterable()
				;
	}

	/**
	 * Performs the refactoring of the loop into a stream operation. Uses
	 * StreamPipelineBuilder for all conversions.
	 */
	public void refactor() {
		refactorWithBuilder();
	}

	/**
	 * Refactors the loop using the StreamPipelineBuilder approach.
	 */
	private void refactorWithBuilder() {
		StreamPipelineBuilder builder = new StreamPipelineBuilder(forLoop, preconditions);

		if (!builder.analyze()) {
			return; // Cannot convert
		}

		MethodInvocation pipeline = builder.buildPipeline();
		if (pipeline == null) {
			return; // Failed to build pipeline
		}

		Statement replacement = builder.wrapPipeline(pipeline);
		if (replacement != null) {
			ASTNodes.replaceButKeepComment(rewrite, forLoop, replacement, group);
		}
	}
}
