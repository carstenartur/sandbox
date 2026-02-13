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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.SimplifyPlatformStatusFixCore;

/**
 * Simplifies MultiStatus creation patterns to use IStatus.OK as the status code.
 * 
 * Transforms:
 * <pre>
 * MultiStatus status = new MultiStatus(pluginId, someCode, message, exception);
 * </pre>
 * 
 * To:
 * <pre>
 * MultiStatus status = new MultiStatus(pluginId, IStatus.OK, message, exception);
 * </pre>
 * 
 * Note: Unlike Status, MultiStatus doesn't have factory methods like error() or warning().
 * This transformation normalizes the status code to IStatus.OK, which is the recommended
 * practice when the overall status is determined by child statuses.
 */
public class MultiStatusSimplifyPlatformStatus extends AbstractSimplifyPlatformStatus<ClassInstanceCreation> {

	private static final String ISTATUS_OK = "IStatus.OK"; //$NON-NLS-1$
	private static final String ISTATUS_SIMPLE_NAME = "IStatus"; //$NON-NLS-1$
	private static final String OK_SIMPLE_NAME = "OK"; //$NON-NLS-1$

	public MultiStatusSimplifyPlatformStatus() {
		super("", ""); // MultiStatus doesn't use factory method names //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "MultiStatus status = new MultiStatus(pluginId, IStatus.OK, \"message\", null);\n"; //$NON-NLS-1$
		}
		return "MultiStatus status = new MultiStatus(pluginId, someCode, \"message\", null);\n"; //$NON-NLS-1$
	}

	@Override
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) throws CoreException {
		find(fixcore, compilationUnit, operations, nodesprocessed, false);
	}

	@Override
	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean preservePluginId) throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> dataholder= ReferenceHolder.createForNodes();
			HelperVisitorFactory.forClassInstanceCreation(MultiStatus.class)
				.in(compilationUnit)
				.excluding(nodesprocessed)
				.processEach(dataholder, (visited, holder) -> {
					if (nodesprocessed.contains(visited)) {
						return false;
					}
					
					// MultiStatus has a 4-argument constructor:
					// MultiStatus(String pluginId, int code, String message, Throwable exception)
					if (visited.arguments().size() != 4) {
						return false;
					}
					
					List<Expression> arguments= visited.arguments();
					
					// Check if argument at index 1 (code) is NOT already IStatus.OK
					Expression codeArg= arguments.get(1);
					if (codeArg instanceof QualifiedName) {
						QualifiedName codeQualifiedName= (QualifiedName) codeArg;
						if (ISTATUS_OK.equals(codeQualifiedName.toString())) {
							// Already using IStatus.OK, no transformation needed
							return false;
						}
					}
					
					// Found a MultiStatus that could be simplified to use IStatus.OK
					operations.add(fixcore.rewrite(visited, holder, preservePluginId));
					nodesprocessed.add(visited);
					return false;
				});
		} catch (Exception e) {
			throw new CoreException(Status.error("Problem in find MultiStatus", e)); //$NON-NLS-1$
		}
	}

	@Override
	public void rewrite(SimplifyPlatformStatusFixCore upp, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> holder) {
		rewrite(upp, visited, cuRewrite, group, holder, false);
	}

	@Override
	public void rewrite(SimplifyPlatformStatusFixCore upp, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> holder,
			boolean preservePluginId) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		// Create a new MultiStatus construction with IStatus.OK as the code parameter
		ClassInstanceCreation newMultiStatus= ast.newClassInstanceCreation();
		Name multiStatusName= addImport(MultiStatus.class.getName(), cuRewrite, ast);
		newMultiStatus.setType(ast.newSimpleType(multiStatusName));
		
		// Add import for IStatus to support IStatus.OK reference
		addImport(org.eclipse.core.runtime.IStatus.class.getName(), cuRewrite, ast);
		
		List<Expression> arguments= visited.arguments();
		List<Expression> newArguments= newMultiStatus.arguments();
		
		// Copy pluginId (argument 0)
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(0))));
		
		// Replace code with IStatus.OK
		QualifiedName okConstant= ast.newQualifiedName(
				ast.newSimpleName(ISTATUS_SIMPLE_NAME),
				ast.newSimpleName(OK_SIMPLE_NAME));
		newArguments.add(okConstant);
		
		// Copy message (argument 2)
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(2))));
		
		// Copy exception (argument 3)
		newArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(3))));
		
		ASTNodes.replaceButKeepComment(rewrite, visited, newMultiStatus, group);
		remover.registerRemovedNode(visited);
		// Note: Do NOT call remover.applyRemoves(importRewrite) here
		// The transformation still uses MultiStatus and IStatus classes,
		// so the imports should be preserved.
		// ImportRewrite will automatically manage imports without explicit applyRemoves.
	}
}
