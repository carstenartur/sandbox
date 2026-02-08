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

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * Abstract base class for plugins that migrate @Rule annotated fields.
 * 
 * <p>Subclasses need to implement:</p>
 * <ul>
 *   <li>{@link #getRuleType()} - The JUnit 4 rule type (e.g., "org.junit.rules.TestName")</li>
 *   <li>{@link #process2Rewrite} - The transformation logic</li>
 *   <li>{@link #getPreview(boolean)} - Preview for UI</li>
 * </ul>
 */
public abstract class AbstractRuleFieldPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * Returns the fully qualified name of the rule type to migrate.
	 * @return e.g., "org.junit.rules.TestName"
	 */
	protected abstract String getRuleType();
	
	/**
	 * Whether to also check for @ClassRule annotation.
	 * Default is false (only @Rule).
	 */
	protected boolean includeClassRule() {
		return false;
	}

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		
		// Find @Rule fields
		HelperVisitor.forField()
			.withAnnotation(ORG_JUNIT_RULE)
			.ofType(getRuleType())
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> 
				processRuleField(fixcore, operations, (FieldDeclaration) visited, aholder));
		
		// Optionally find @ClassRule fields
		if (includeClassRule()) {
			HelperVisitor.forField()
				.withAnnotation(ORG_JUNIT_CLASS_RULE)
				.ofType(getRuleType())
				.in(compilationUnit)
				.excluding(nodesprocessed)
				.processEach(dataHolder, (visited, aholder) -> 
					processRuleField(fixcore, operations, (FieldDeclaration) visited, aholder));
		}
	}

	/**
	 * Processes a rule field. Default implementation creates a JunitHolder and adds a rewrite operation.
	 * Subclasses can override for custom validation.
	 */
	protected boolean processRuleField(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, FieldDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) node.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return true; // Continue processing
		}
		
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding != null && getRuleType().equals(binding.getQualifiedName())) {
			JunitHolder mh = new JunitHolder();
			mh.setMinv(node);
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		return true; // Continue processing
	}
}
