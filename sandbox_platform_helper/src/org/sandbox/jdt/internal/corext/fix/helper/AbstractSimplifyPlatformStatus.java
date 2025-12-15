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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.SimplifyPlatformStatusFixCore;

/**
 * @param <T> Type found in Visitor
 */
public abstract class AbstractSimplifyPlatformStatus<T extends ASTNode> {
	String methodName;
	String expectedStatusLiteral;

	public AbstractSimplifyPlatformStatus(String methodName, String expectedStatusLiteral) {
		this.methodName= methodName;
		this.expectedStatusLiteral= expectedStatusLiteral;
	}

	/**
	 * Adds an import to the class. This method should be used for every class
	 * reference added to the generated code.
	 *
	 * @param typeName  a fully qualified name of a type
	 * @param cuRewrite CompilationUnitRewrite
	 * @param ast       AST
	 * @return simple name of a class if the import was added and fully qualified
	 *         name if there was a conflict
	 */
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName= cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	public abstract String getPreview(boolean afterRefactoring);

	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> dataholder= new ReferenceHolder<>();
			HelperVisitor.callClassInstanceCreationVisitor(Status.class, compilationUnit, dataholder, nodesprocessed, (visited, holder) -> {
				if (nodesprocessed.contains(visited) || (visited.arguments().size() != 5)) {
					return false;
				}
				/**
				 * Expected pattern: new Status(severity, pluginId, IStatus.OK, message, throwable)
				 * Where:
				 *   - severity is IStatus.INFO, IStatus.WARNING, or IStatus.ERROR
				 *   - pluginId is a String
				 *   - code is IStatus.OK (mandatory for this transformation)
				 *   - message is a String
				 *   - throwable is a Throwable or null
				 *
				 * Transforms to: Status.info(message, throwable) / Status.warning(message, throwable) / Status.error(message, throwable)
				 */
				List<Expression> arguments= visited.arguments();
				
				// Safely check if argument at index 2 (code) is IStatus.OK
				Expression codeArg= arguments.get(2);
				if (!(codeArg instanceof QualifiedName)) {
					return false;
				}
				QualifiedName codeQualifiedName= (QualifiedName) codeArg;
				if (!"IStatus.OK".equals(codeQualifiedName.toString())) { //$NON-NLS-1$
					return false;
				}
				
				// Safely check if argument at index 0 (severity) matches expected status literal
				Expression severityArg= arguments.get(0);
				if (!(severityArg instanceof QualifiedName)) {
					return false;
				}
				QualifiedName severityQualifiedName= (QualifiedName) severityArg;
				if (expectedStatusLiteral.equals(severityQualifiedName.toString())) {
					operations.add(fixcore.rewrite(visited, holder));
					nodesprocessed.add(visited);
					return false;
				}
				return true;
			});
		} catch (Exception e) {
			throw new CoreException(Status.error("Problem in find", e)); //$NON-NLS-1$
		}
	}

	public void rewrite(SimplifyPlatformStatusFixCore upp, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> holder) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();

		/**
		 * Create call to Status.warning(), Status.error(), or Status.info()
		 */
		MethodInvocation staticCall= ast.newMethodInvocation();
		staticCall.setExpression(ASTNodeFactory.newName(ast, Status.class.getSimpleName()));
		staticCall.setName(ast.newSimpleName(methodName));
		
		List<ASTNode> arguments= visited.arguments();
		List<ASTNode> staticCallArguments= staticCall.arguments();
		
		// Add message argument (always at position 3 for 5-argument constructor)
		int messagePosition= 3;
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(messagePosition))));
		
		// Add throwable argument if present (at position 4) and not null
		if (arguments.size() == 5) {
			ASTNode throwableArg= arguments.get(4);
			ASTNode codeArg= arguments.get(2);
			if (!throwableArg.toString().equals("null") && codeArg.toString().equals("IStatus.OK")) { //$NON-NLS-1$ //$NON-NLS-2$
				staticCallArguments.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(throwableArg)));
			}
		}
		
		ASTNodes.replaceButKeepComment(rewrite, visited, staticCall, group);
		remover.registerRemovedNode(visited);
		remover.applyRemoves(importRewrite);
	}
}
