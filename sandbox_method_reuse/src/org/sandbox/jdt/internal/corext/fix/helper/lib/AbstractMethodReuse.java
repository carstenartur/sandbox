package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
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
import org.sandbox.jdt.internal.corext.fix.MethodReuseFixCore;

public abstract class AbstractMethodReuse<T extends ASTNode> {

	public void find(MethodReuseFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed)
			throws CoreException {
		try {
			ReferenceHolder<ASTNode, Object> dataholder = new ReferenceHolder<>();
			HelperVisitor.callMethodDeclarationVisitor(compilationUnit, dataholder, nodesprocessed,
					(visited, holder) -> {
						if (nodesprocessed.contains(visited)) {
							return false;
						}
						operations.add(fixcore.rewrite(visited, holder));
						nodesprocessed.add(visited);
						return false;
					});
		} catch (Exception e) {
			throw new CoreException(Status.error("Problem in find", e)); //$NON-NLS-1$
		}
	}

	public void rewrite(MethodReuseFixCore upp, final MethodDeclaration visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> holder) {
//		ASTRewrite rewrite= cuRewrite.getASTRewrite();
//		AST ast= cuRewrite.getRoot().getAST();
//		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
//		ImportRemover remover= cuRewrite.getImportRemover();

//		ASTNodes.replaceButKeepComment(rewrite, visited, staticCall, group);
//		remover.registerRemovedNode(visited);
//		remover.applyRemoves(importRewrite);
	}

	public abstract String getPreview(boolean afterRefactoring);
}
