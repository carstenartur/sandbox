/*******************************************************************************
 * Copyright (c) 2021, 2025 Carsten Hammer.
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

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.util.NamingUtils;

/**
 * Abstract base class for JUnit migration tools.
 * Provides common functionality for transforming JUnit 3/4 tests to JUnit 5.
 * Delegates to specialized helper classes for most operations.
 * 
 * @param <T> Type found in Visitor
 */
public abstract class AbstractTool<T> {

	/**
	 * Gets all variable names used in the scope of the given AST node.
	 * 
	 * @param node the AST node to analyze
	 * @return collection of variable names used in the node's scope
	 */
	public static Collection<String> getUsedVariableNames(ASTNode node) {
		CompilationUnit root = (CompilationUnit) node.getRoot();
		return new ScopeAnalyzer(root).getUsedVariableNames(node.getStartPosition(), node.getLength());
	}

	/**
	 * Extracts the class name from a field declaration's initializer.
	 * Delegates to {@link NamingUtils#extractClassNameFromField(FieldDeclaration)}.
	 * 
	 * @param field the field declaration to extract from
	 * @return the class name, or null if not found
	 */
	public String extractClassNameFromField(FieldDeclaration field) {
		return NamingUtils.extractClassNameFromField(field);
	}

	/**
	 * Finds JUnit migration opportunities in the compilation unit.
	 * Implementations should scan for patterns that need to be migrated from JUnit 3/4 to JUnit 5.
	 * 
	 * @param fixcore the JUnit cleanup fix core
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect rewrite operations
	 * @param nodesprocessed set of already processed AST nodes to avoid duplicates
	 */
	public abstract void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed);

	/**
	 * Gets a preview of the code before or after refactoring.
	 * Used to display examples in the Eclipse cleanup preferences UI.
	 * 
	 * @param afterRefactoring if true, returns the "after" preview; if false, returns the "before" preview
	 * @return a code snippet showing the transformation (formatted as Java source code)
	 */
	public abstract String getPreview(boolean afterRefactoring);

	/**
	 * Checks if a field is annotated with the specified annotation.
	 * 
	 * @param field the field declaration to check
	 * @param annotationClass the fully qualified annotation class name
	 * @return true if the field has the annotation
	 */
	protected boolean isFieldAnnotatedWith(FieldDeclaration field, String annotationClass) {
		return AnnotationUtils.isFieldAnnotatedWith(field, annotationClass);
	}

	/**
	 * Modifies a class that extends ExternalResource to use JUnit 5 extensions instead.
	 * Delegates to {@link ExternalResourceRefactorer#modifyExternalResourceClass}.
	 * 
	 * @param node the type declaration to modify
	 * @param field the field declaration with ExternalResource
	 * @param fieldStatic whether the field is static (affects callback type)
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	protected void modifyExternalResourceClass(TypeDeclaration node, FieldDeclaration field, boolean fieldStatic,
			ASTRewrite rewriter, AST ast, TextEditGroup group, ImportRewrite importRewriter) {
		ExternalResourceRefactorer.modifyExternalResourceClass(node, field, fieldStatic, rewriter, ast, group,
				importRewriter);
	}


	/**
	 * Processes a JUnit migration by applying the necessary AST rewrites.
	 * Implementations should transform the matched pattern into JUnit 5 compatible code.
	 * 
	 * @param group the text edit group for tracking changes
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewriter the import rewriter
	 * @param junitHolder the holder containing JUnit migration information
	 */
	protected abstract void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder);

	/**
	 * Refactors an anonymous ExternalResource class to implement JUnit 5 callback interfaces.
	 * Delegates to {@link ExternalResourceRefactorer#refactorAnonymousClassToImplementCallbacks}.
	 * 
	 * @param anonymousClass the anonymous class declaration to refactor
	 * @param fieldDeclaration the field containing the anonymous class
	 * @param fieldStatic whether the field is static
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param group the text edit group
	 * @param importRewriter the import rewriter
	 */
	protected void refactorAnonymousClassToImplementCallbacks(AnonymousClassDeclaration anonymousClass,
			FieldDeclaration fieldDeclaration, boolean fieldStatic, ASTRewrite rewriter, AST ast, TextEditGroup group,
			ImportRewrite importRewriter) {
		ExternalResourceRefactorer.refactorAnonymousClassToImplementCallbacks(anonymousClass, fieldDeclaration,
				fieldStatic, rewriter, ast, group, importRewriter);
	}

	/**
	 * Refactors TestName field usage in a class and all its subclasses.
	 * Delegates to {@link TestNameRefactorer#refactorTestnameInClassAndSubclasses}.
	 * 
	 * @param group the text edit group
	 * @param rewriter the AST rewriter
	 * @param ast the AST instance
	 * @param importRewrite the import rewriter
	 * @param node the TestName field declaration to replace
	 */
	protected void refactorTestnameInClassAndSubclasses(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewrite, FieldDeclaration node) {
		TestNameRefactorer.refactorTestnameInClassAndSubclasses(group, rewriter, ast, importRewrite, node);
	}

	/**
	 * Reorders parameters in a method invocation to match JUnit 5 assertion parameter order.
	 * Delegates to {@link AssertionRefactorer#reorderParameters}.
	 * 
	 * @param node the method invocation to reorder
	 * @param rewriter the AST rewriter
	 * @param group the text edit group
	 * @param oneparam assertion methods with one value parameter
	 * @param twoparam assertion methods with two value parameters
	 */
	public void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group, Set<String> oneparam,
			Set<String> twoparam) {
		AssertionRefactorer.reorderParameters(node, rewriter, group, oneparam, twoparam);
	}

	/**
	 * Standard helper for processing found nodes in the common pattern.
	 * Creates a JunitHolder, stores the node, adds it to the data holder,
	 * and creates a rewrite operation.
	 * 
	 * @param fixcore the cleanup fix core
	 * @param operations the set of operations to add to
	 * @param node the AST node that was found
	 * @param dataHolder the reference holder for storing data
	 * @return true to continue processing other nodes (fluent API semantics)
	 */
	protected boolean addStandardRewriteOperation(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh = new JunitHolder();
		mh.setMinv(node);
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return true;
	}

	/**
	 * Handles import declaration changes for migrating JUnit 4 to JUnit 5.
	 * Delegates to {@link ImportHelper#changeImportDeclaration}.
	 * 
	 * @param node the import declaration to change
	 * @param importRewriter the import rewriter to use
	 * @param sourceClass the JUnit 4 fully qualified class name
	 * @param targetClass the JUnit 5 fully qualified class name
	 */
	protected void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, String sourceClass,
			String targetClass) {
		ImportHelper.changeImportDeclaration(node, importRewriter, sourceClass, targetClass);
	}

	/**
	 * Applies the JUnit migration rewrite to the compilation unit.
	 * Delegates to the abstract process2Rewrite method for actual transformation.
	 * 
	 * @param upp the JUnit cleanup fix core
	 * @param hit the reference holder containing migration information
	 * @param cuRewrite the compilation unit rewrite
	 * @param group the text edit group
	 */
	public void rewrite(JUnitCleanUpFixCore upp, ReferenceHolder<Integer, JunitHolder> hit,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter = cuRewrite.getImportRewrite();
		JunitHolder junitHolder = hit.get(hit.size() - 1);
		process2Rewrite(group, rewriter, ast, importRewriter, junitHolder);
		hit.remove(hit.size() - 1);
	}
}
