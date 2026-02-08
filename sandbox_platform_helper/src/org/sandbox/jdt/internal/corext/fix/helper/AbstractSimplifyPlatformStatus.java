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
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
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
		find(fixcore, compilationUnit, operations, nodesprocessed, false);
	}

	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean preservePluginId) throws CoreException {
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
					operations.add(fixcore.rewrite(visited, holder, preservePluginId));
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
		rewrite(upp, visited, cuRewrite, group, holder, false);
	}

	public void rewrite(SimplifyPlatformStatusFixCore upp, final ClassInstanceCreation visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> holder,
			boolean preservePluginId) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRemover remover= cuRewrite.getImportRemover();

		/**
		 * Create call to Status.warning(), Status.error(), or Status.info()
		 */
		// Add imports in alphabetical order to match expected test output
		// IStatus comes before Status alphabetically
		addImport("org.eclipse.core.runtime.IStatus", cuRewrite, ast);
		
		MethodInvocation staticCall= ast.newMethodInvocation();
		Name statusName= addImport(Status.class.getName(), cuRewrite, ast);
		staticCall.setExpression(statusName);
		staticCall.setName(ast.newSimpleName(methodName));
		
		List<ASTNode> arguments= visited.arguments();
		List<ASTNode> staticCallArguments= staticCall.arguments();
		
		// Note: preservePluginId parameter is not used for Status factory methods
		// because the Eclipse Platform Status.error(), Status.warning(), and Status.info()
		// methods only accept (message, exception) parameters, not plugin ID.
		
		// Add message argument (always at position 3 for 5-argument constructor)
		int messagePosition= 3;
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(messagePosition))));
		
		// Add throwable argument if present (at position 4) and not null
		ASTNode throwableArg= arguments.get(4);
		// Add the exception parameter if it's not a null literal
		// The code argument (position 2) was already validated as IStatus.OK in the find() method
		if (!(throwableArg instanceof NullLiteral)) {
			staticCallArguments.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(throwableArg)));
		}
		
		ASTNodes.replaceButKeepComment(rewrite, visited, staticCall, group);
		remover.registerRemovedNode(visited);
		// Note: Do NOT call remover.applyRemoves(importRewrite) here
		// ImportRewrite automatically manages import removal/addition throughout the transformation.
		// The Status import is explicitly added via addImport() above, and IStatus is preserved from the variable declaration.
	}
}
