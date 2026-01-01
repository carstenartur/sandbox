package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;

public abstract class AbstractMethodReuse<T extends ASTNode> {

	/**
	 * Find inline code sequences that can be replaced with method calls
	 * 
	 * @param fixcore the cleanup fix core
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect rewrite operations
	 * @param nodesprocessed set of already processed AST nodes to avoid duplicates
	 */
	public abstract void find(MethodReuseCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed);

	/**
	 * Rewrite the AST to replace inline code with method calls
	 * 
	 * @param fixcore the cleanup fix core
	 * @param holder reference holder containing the data needed for rewriting
	 * @param cuRewrite the compilation unit rewrite
	 * @param group the text edit group
	 * @throws CoreException if rewriting fails
	 */
	public abstract void rewrite(MethodReuseCleanUpFixCore fixcore, ReferenceHolder<?, ?> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException;

	/**
	 * Gets a preview of the code before or after refactoring.
	 * Used to display examples in the Eclipse cleanup preferences UI.
	 * 
	 * @param afterRefactoring if true, returns the "after" preview; if false, returns the "before" preview
	 * @return a code snippet showing the transformation (formatted as Java source code)
	 */
	public abstract String getPreview(boolean afterRefactoring);
}
