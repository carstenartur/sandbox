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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * Abstract base class for plugins that process @Test annotation parameters.
 * Used for @Test(timeout=...) and @Test(expected=...) migrations.
 * 
 * <p>Subclasses need to implement:</p>
 * <ul>
 *   <li>{@link #getParameterName()} - The parameter name (e.g., "timeout" or "expected")</li>
 *   <li>{@link #process2Rewrite} - The transformation logic</li>
 *   <li>{@link #getPreview(boolean)} - Preview for UI</li>
 * </ul>
 */
public abstract class AbstractTestAnnotationParameterPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * Returns the parameter name to look for in @Test annotation.
	 * @return e.g., "timeout" or "expected"
	 */
	protected abstract String getParameterName();
	
	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		HelperVisitor.forAnnotation(ORG_JUNIT_TEST)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof NormalAnnotation) {
					return processTestAnnotation(fixcore, operations, (NormalAnnotation) visited, aholder);
				}
				return true;
			});
	}

	private boolean processTestAnnotation(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, NormalAnnotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		MemberValuePair targetPair = null;
		
		@SuppressWarnings("unchecked")
		List<MemberValuePair> values = node.values();
		for (MemberValuePair pair : values) {
			if (getParameterName().equals(pair.getName().getIdentifier())) {
				targetPair = pair;
				break;
			}
		}
		
		if (targetPair != null && validateParameter(targetPair)) {
			JunitHolder mh = new JunitHolder();
			mh.setMinv(node);
			mh.setAdditionalInfo(targetPair);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		
		return true;
	}
	
	/**
	 * Validates the parameter value. Subclasses can override for specific validation.
	 * Default implementation returns true (accept all values).
	 */
	protected boolean validateParameter(MemberValuePair pair) {
		return true;
	}
}
