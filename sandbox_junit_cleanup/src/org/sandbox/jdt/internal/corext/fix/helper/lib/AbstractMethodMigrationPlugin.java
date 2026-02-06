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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 * Abstract base class for plugins that migrate method calls from one class to another.
 * Used for Assert → Assertions and Assume → Assumptions migrations.
 * 
 * <p>Subclasses need to implement:</p>
 * <ul>
 *   <li>{@link #getSourceClass()} - The JUnit 4 class (e.g., "org.junit.Assert")</li>
 *   <li>{@link #getTargetClass()} - The JUnit 5 class (e.g., "org.junit.jupiter.api.Assertions")</li>
 *   <li>{@link #getTargetSimpleName()} - Simple name of target class (e.g., "Assertions")</li>
 *   <li>{@link #getMethodNames()} - Set of method names to migrate</li>
 *   <li>{@link #getPreview(boolean)} - Preview for UI</li>
 * </ul>
 * 
 * <p>The base class handles:</p>
 * <ul>
 *   <li>Finding method calls and static imports</li>
 *   <li>Replacing class references in qualified calls</li>
 *   <li>Updating imports</li>
 *   <li>Parameter reordering (message to last position)</li>
 * </ul>
 */
public abstract class AbstractMethodMigrationPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	/**
	 * Returns the fully qualified name of the source class (JUnit 4).
	 * @return e.g., "org.junit.Assert"
	 */
	protected abstract String getSourceClass();
	
	/**
	 * Returns the fully qualified name of the target class (JUnit 5).
	 * @return e.g., "org.junit.jupiter.api.Assertions"
	 */
	protected abstract String getTargetClass();
	
	/**
	 * Returns the simple name of the target class.
	 * @return e.g., "Assertions"
	 */
	protected abstract String getTargetSimpleName();
	
	/**
	 * Returns the set of method names to migrate.
	 * @return e.g., Set.of("assertEquals", "assertTrue", "assertFalse", ...)
	 */
	protected abstract Set<String> getMethodNames();
	
	/**
	 * Returns methods that require parameter reordering (message moves to last).
	 * Default implementation returns all methods.
	 * @return set of method names that need parameter reordering
	 */
	protected Set<String> getMethodsRequiringReorder() {
		return getMethodNames();
	}

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = ReferenceHolder.createIndexed();
		HelperVisitor.forMethodCalls(getSourceClass(), getMethodNames())
			.andStaticImports()
			.andImportsOf(getSourceClass())
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		ASTNode node = junitHolder.minv;
		
		if (node instanceof MethodInvocation) {
			processMethodInvocation(group, rewriter, ast, importRewriter, (MethodInvocation) node);
		} else if (node instanceof ImportDeclaration) {
			processImportDeclaration(group, rewriter, ast, importRewriter, 
				(ImportDeclaration) node);
		}
	}

	/**
	 * Processes a method invocation, updating the class reference and reordering parameters if needed.
	 */
	protected void processMethodInvocation(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, MethodInvocation methodInvocation) {
		
		String methodName = methodInvocation.getName().getIdentifier();
		
		// Update qualified expression if present (e.g., Assert.assertEquals → Assertions.assertEquals)
		Expression expression = methodInvocation.getExpression();
		if (expression instanceof SimpleName) {
			SimpleName simpleName = (SimpleName) expression;
			String sourceSimpleName = getSourceClass().substring(getSourceClass().lastIndexOf('.') + 1);
			if (sourceSimpleName.equals(simpleName.getIdentifier())) {
				SimpleName newName = ast.newSimpleName(getTargetSimpleName());
				ASTNodes.replaceButKeepComment(rewriter, simpleName, newName, group);
				importRewriter.addImport(getTargetClass());
				importRewriter.removeImport(getSourceClass());
			}
		}
		
		// Reorder parameters if needed (message moves to last position in JUnit 5)
		if (getMethodsRequiringReorder().contains(methodName)) {
			reorderMessageParameter(group, rewriter, methodInvocation);
		}
	}

	/**
	 * Processes an import declaration, replacing the source import with target import.
	 * 
	 * <p>Handles both specific and wildcard imports:</p>
	 * <ul>
	 *   <li>Wildcard: import static org.junit.Assert.* → import static org.junit.jupiter.api.Assertions.*</li>
	 *   <li>Specific: import static org.junit.Assert.assertEquals → import static org.junit.jupiter.api.Assertions.assertEquals</li>
	 * </ul>
	 */
	protected void processImportDeclaration(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, ImportDeclaration importDecl) {
		
		String importName = importDecl.getName().getFullyQualifiedName();
		
		if (importDecl.isStatic()) {
			// Handle static imports
			if (importDecl.isOnDemand()) {
				// Wildcard import: import static org.junit.Assert.*
				// Note: importName is "org.junit.Assert" (without .*)
				if (getSourceClass().equals(importName)) {
					importRewriter.removeStaticImport(importName + ".*");
					importRewriter.addStaticImport(getTargetClass(), "*", false);
				}
			} else {
				// Specific import: import static org.junit.Assert.assertEquals
				// Note: importName is "org.junit.Assert.assertEquals"
				if (importName.startsWith(getSourceClass() + ".")) {
					String methodName = importName.substring(getSourceClass().length() + 1);
					importRewriter.removeStaticImport(importName);
					importRewriter.addStaticImport(getTargetClass(), methodName, false);
				}
			}
		} else {
			// Handle regular imports
			if (getSourceClass().equals(importName)) {
				importRewriter.removeImport(getSourceClass());
				importRewriter.addImport(getTargetClass());
			}
		}
	}

	/**
	 * Reorders parameters so that the message parameter moves to the last position.
	 * JUnit 4: assertEquals(String message, expected, actual)
	 * JUnit 5: assertEquals(expected, actual, String message)
	 * 
	 * Delegates to existing {@link #reorderParameters} method.
	 */
	protected void reorderMessageParameter(TextEditGroup group, ASTRewrite rewriter,
			MethodInvocation methodInvocation) {
		// Delegate to existing reorderParameters method in AbstractTool
		// which handles the parameter reordering logic
		reorderParameters(methodInvocation, rewriter, group, ONEPARAM_ASSERTIONS, TWOPARAM_ASSERTIONS);
	}
}
