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
//import java.util.function.BiPredicate;

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
import org.sandbox.jdt.internal.corext.util.ASTRewriteUtils;

/**
 * @param <T> Type found in Visitor
 */
public abstract class AbstractSimplifyPlatformStatus<T extends ASTNode> {
	String methodname;
	String istatus;

	public AbstractSimplifyPlatformStatus(String methodname, String istatus) {
		this.methodname= methodname;
		this.istatus= istatus;
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
	 * @deprecated Use {@link ASTRewriteUtils#addImport(String, CompilationUnitRewrite, AST)} instead.
	 */
	@Deprecated
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		return ASTRewriteUtils.addImport(typeName, cuRewrite, ast);
	}

	public abstract String getPreview(boolean afterRefactoring);

	public void find(SimplifyPlatformStatusFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> dataholder= new ReferenceHolder<>();
			HelperVisitor.callClassInstanceCreationVisitor(Status.class, compilationUnit, dataholder, nodesprocessed, (visited, holder) -> {
				if (nodesprocessed.contains(visited) || 
//						(visited.arguments().size() != 3)&&
//						(visited.arguments().size() != 4)&&
						(visited.arguments().size() != 5)
						) {
					return false;
				}
				/**
				 * new Status(INFO, callerClass, OK, message, null);
				 * new Status(WARNING, callerClass, OK, message, null);
				 * new Status(WARNING, callerClass, OK, message, exception);
				 * new Status(ERROR, callerClass, OK, message, null);
				 * new Status(ERROR, callerClass, OK, message, exception);
				 *
				 *
				 * IStatus status = new Status(IStatus.WARNING, "plugin id", IStatus.OK, "important message", e);
				 * IStatus status = new Status(IStatus.WARNING, "plugin id", "important message", null);
				 * IStatus status = new Status(IStatus.WARNING, "plugin id", "important message");
				 */
				List<Expression> arguments= visited.arguments();
				QualifiedName argstring3 = (QualifiedName) arguments.get(2);
				if (!"IStatus.OK".equals(argstring3.toString())) { //$NON-NLS-1$
					return false;
				}
//				QualifiedName argstring5 = (QualifiedName) arguments.get(4);
				QualifiedName argstring1 = (QualifiedName) arguments.get(0);
//				String mybinding= argstring1.getFullyQualifiedName();
				if (istatus.equals(argstring1.toString())) {
					operations.add(fixcore.rewrite(visited,holder));
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
		 * Add call to Status.warning(),Status.error() and Status.info()
		 */
		MethodInvocation staticCall= ast.newMethodInvocation();
		staticCall.setExpression(ASTNodeFactory.newName(ast, Status.class.getSimpleName()));
		staticCall.setName(ast.newSimpleName(methodname));
		List<ASTNode> arguments= visited.arguments();
		List<ASTNode> staticCallArguments= staticCall.arguments();
//		int positionmessage= arguments.size() == 5 ? 3 : 2;
		int positionmessage= 3;
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(positionmessage))));
		ASTNode node2= arguments.get(2);
		switch (arguments.size()) {
		/**
		 * new Status(IStatus.WARNING, JavaManipulation.ID_PLUGIN, IJavaStatusConstants.INTERNAL_ERROR, message, error)
		 */
		case 5:
			ASTNode node4= arguments.get(4);
			if (!node4.toString().equals("null") && node2.toString().equals("IStatus.OK")) { //$NON-NLS-1$ //$NON-NLS-2$
				staticCallArguments.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(node4)));
			}
			break;
		case 4:
//			return;
//			ASTNode node= arguments.get(3);
//			if (!node.toString().equals("null")) { //$NON-NLS-1$
//				staticCallArguments.add(ASTNodes.createMoveTarget(rewrite, ASTNodes.getUnparenthesedExpression(node)));
//			}
			break;
		case 3:
//			return;
		default:
			break;
		}
		ASTNodes.replaceButKeepComment(rewrite, visited, staticCall, group);
//		QualifiedName stat= (QualifiedName) arguments.get(0);
//		importRemover.removeImport(IStatus.class.getCanonicalName());
		remover.registerRemovedNode(visited);
		remover.applyRemoves(importRewrite);
	}
}
