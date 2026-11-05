/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Comment;
import org.sandbox.jdt.triggerpattern.api.GuardContext;

/** Common checks for text replacements subsequently applied through JDT. */
final class HintRewriteSafety {

	private HintRewriteSafety() {
	}

	/** JDT's default rewrite range can extend to adjacent comments. */
	static boolean hasCommentInRewriteRange(GuardContext context) {
		ASTNode node= context.getMatchedNode();
		if (node == null || context.getCompilationUnit() == null
				|| context.getCompilationUnit().getCommentList() == null
				|| node.getRoot() != context.getCompilationUnit()) {
			return true;
		}
		int start= context.getCompilationUnit().getExtendedStartPosition(node);
		int length= context.getCompilationUnit().getExtendedLength(node);
		if (start < 0 || length < 0 || start > node.getStartPosition()
				|| (long) start + length < (long) node.getStartPosition() + node.getLength()) {
			return true;
		}
		long end= (long) start + length;
		for (Object value : context.getCompilationUnit().getCommentList()) {
			Comment comment= (Comment) value;
			int commentStart= comment.getStartPosition();
			if (commentStart < end && (long) commentStart + comment.getLength() > start) {
				return true;
			}
		}
		return false;
	}
}
