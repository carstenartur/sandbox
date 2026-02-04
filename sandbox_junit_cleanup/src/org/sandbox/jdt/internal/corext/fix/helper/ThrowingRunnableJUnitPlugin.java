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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2026 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Migrates JUnit 4 ThrowingRunnable to JUnit 5 Executable.
 * 
 * <p>Transforms:</p>
 * <ul>
 *   <li>org.junit.function.ThrowingRunnable → org.junit.jupiter.api.function.Executable</li>
 *   <li>ThrowingRunnable.run() → Executable.execute()</li>
 * </ul>
 * 
 * <p>Handles:</p>
 * <ul>
 *   <li>Variable declarations with ThrowingRunnable type</li>
 *   <li>Method parameters with ThrowingRunnable type</li>
 *   <li>Generic type parameters: AtomicReference&lt;ThrowingRunnable&gt;</li>
 *   <li>Method invocations: throwingRunnable.run()</li>
 *   <li>Import statements</li>
 * </ul>
 */
public class ThrowingRunnableJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String THROWING_RUNNABLE_SIMPLE = "ThrowingRunnable";
	private static final String EXECUTABLE_SIMPLE = "Executable";
	private static final String RUN_METHOD = "run";
	private static final String EXECUTE_METHOD = "execute";

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		Set<ASTNode> found = new HashSet<>();
		
		// Visit the compilation unit to find ThrowingRunnable usages
		compilationUnit.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(ImportDeclaration node) {
				String importName = node.getName().getFullyQualifiedName();
				if (ORG_JUNIT_FUNCTION_THROWING_RUNNABLE.equals(importName)) {
					if (!nodesprocessed.contains(node)) {
						found.add(node);
						addStandardRewriteOperation(fixcore, operations, node, dataHolder);
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(SimpleType node) {
				// Check if this is a ThrowingRunnable type reference
				ITypeBinding binding = node.resolveBinding();
				if (binding != null && ORG_JUNIT_FUNCTION_THROWING_RUNNABLE.equals(binding.getQualifiedName())) {
					ASTNode parent = node.getParent();
					// Collect the parent declaration node (variable, parameter, etc.)
					ASTNode declarationNode = findDeclarationNode(parent);
					if (declarationNode != null && !nodesprocessed.contains(declarationNode) && !found.contains(declarationNode)) {
						found.add(declarationNode);
						addStandardRewriteOperation(fixcore, operations, declarationNode, dataHolder);
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(MethodInvocation node) {
				// Check if this is a .run() call on a ThrowingRunnable
				if (RUN_METHOD.equals(node.getName().getIdentifier())) {
					Expression expression = node.getExpression();
					if (expression != null) {
						ITypeBinding typeBinding = expression.resolveTypeBinding();
						if (typeBinding != null && ORG_JUNIT_FUNCTION_THROWING_RUNNABLE.equals(typeBinding.getQualifiedName())) {
							if (!nodesprocessed.contains(node) && !found.contains(node)) {
								found.add(node);
								addStandardRewriteOperation(fixcore, operations, node, dataHolder);
							}
						}
					}
				}
				return true;
			}
		});
		
		nodesprocessed.addAll(found);
	}
	
	/**
	 * Finds the declaration node for a type reference.
	 * Walks up the AST to find the variable declaration, parameter, field, etc.
	 */
	private ASTNode findDeclarationNode(ASTNode node) {
		while (node != null) {
			if (node instanceof VariableDeclarationStatement ||
				node instanceof SingleVariableDeclaration ||
				node instanceof VariableDeclarationFragment) {
				return node;
			}
			// For parameterized types, go up one more level
			if (node instanceof ParameterizedType) {
				node = node.getParent();
				continue;
			}
			break;
		}
		return null;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		
		ASTNode node = junitHolder.minv;
		
		if (node instanceof ImportDeclaration) {
			processImportDeclaration(importRewriter, (ImportDeclaration) node);
		} else if (node instanceof MethodInvocation) {
			processMethodInvocation(group, rewriter, ast, (MethodInvocation) node);
		} else if (node instanceof VariableDeclarationStatement) {
			processVariableDeclaration(group, rewriter, ast, importRewriter, (VariableDeclarationStatement) node);
		} else if (node instanceof SingleVariableDeclaration) {
			processSingleVariableDeclaration(group, rewriter, ast, importRewriter, (SingleVariableDeclaration) node);
		} else if (node instanceof VariableDeclarationFragment) {
			// Handle field declarations
			processVariableDeclarationFragment(group, rewriter, ast, importRewriter, (VariableDeclarationFragment) node);
		}
	}
	
	/**
	 * Processes import declarations, replacing ThrowingRunnable import with Executable import.
	 */
	private void processImportDeclaration(ImportRewrite importRewriter, ImportDeclaration importDecl) {
		importRewriter.removeImport(ORG_JUNIT_FUNCTION_THROWING_RUNNABLE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_FUNCTION_EXECUTABLE);
	}
	
	/**
	 * Processes method invocations, replacing .run() with .execute().
	 */
	private void processMethodInvocation(TextEditGroup group, ASTRewrite rewriter, AST ast, MethodInvocation node) {
		if (RUN_METHOD.equals(node.getName().getIdentifier())) {
			SimpleName newName = ast.newSimpleName(EXECUTE_METHOD);
			ASTNodes.replaceButKeepComment(rewriter, node.getName(), newName, group);
		}
	}
	
	/**
	 * Processes variable declarations, replacing ThrowingRunnable type with Executable.
	 */
	private void processVariableDeclaration(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, VariableDeclarationStatement node) {
		Type type = node.getType();
		Type newType = createExecutableType(ast, type);
		if (newType != null) {
			ASTNodes.replaceButKeepComment(rewriter, type, newType, group);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_FUNCTION_EXECUTABLE);
		}
	}
	
	/**
	 * Processes single variable declarations (method parameters), replacing ThrowingRunnable with Executable.
	 */
	private void processSingleVariableDeclaration(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, SingleVariableDeclaration node) {
		Type type = node.getType();
		Type newType = createExecutableType(ast, type);
		if (newType != null) {
			ASTNodes.replaceButKeepComment(rewriter, type, newType, group);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_FUNCTION_EXECUTABLE);
		}
	}
	
	/**
	 * Processes variable declaration fragments (field declarations).
	 */
	private void processVariableDeclarationFragment(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, VariableDeclarationFragment node) {
		// The parent should be a FieldDeclaration or VariableDeclarationStatement
		ASTNode parent = node.getParent();
		if (parent instanceof org.eclipse.jdt.core.dom.FieldDeclaration) {
			org.eclipse.jdt.core.dom.FieldDeclaration fieldDecl = (org.eclipse.jdt.core.dom.FieldDeclaration) parent;
			Type type = fieldDecl.getType();
			Type newType = createExecutableType(ast, type);
			if (newType != null) {
				ASTNodes.replaceButKeepComment(rewriter, type, newType, group);
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_FUNCTION_EXECUTABLE);
			}
		}
	}
	
	/**
	 * Creates a new Executable type, handling both simple and parameterized types.
	 */
	private Type createExecutableType(AST ast, Type originalType) {
		if (originalType instanceof SimpleType) {
			SimpleType simpleType = (SimpleType) originalType;
			if (THROWING_RUNNABLE_SIMPLE.equals(simpleType.getName().getFullyQualifiedName())) {
				return ast.newSimpleType(ast.newName(EXECUTABLE_SIMPLE));
			}
		} else if (originalType instanceof ParameterizedType) {
			// Handle generic types like AtomicReference<ThrowingRunnable>
			ParameterizedType paramType = (ParameterizedType) originalType;
			Type baseType = paramType.getType();
			
			// Check if any type argument is ThrowingRunnable
			boolean hasThrowingRunnable = false;
			for (Object arg : paramType.typeArguments()) {
				if (arg instanceof SimpleType) {
					SimpleType argType = (SimpleType) arg;
					if (THROWING_RUNNABLE_SIMPLE.equals(argType.getName().getFullyQualifiedName())) {
						hasThrowingRunnable = true;
						break;
					}
				}
			}
			
			if (hasThrowingRunnable) {
				// Create new parameterized type with Executable instead
				ParameterizedType newParamType = ast.newParameterizedType((Type) ASTNode.copySubtree(ast, baseType));
				for (Object arg : paramType.typeArguments()) {
					if (arg instanceof SimpleType) {
						SimpleType argType = (SimpleType) arg;
						if (THROWING_RUNNABLE_SIMPLE.equals(argType.getName().getFullyQualifiedName())) {
							newParamType.typeArguments().add(ast.newSimpleType(ast.newName(EXECUTABLE_SIMPLE)));
						} else {
							newParamType.typeArguments().add(ASTNode.copySubtree(ast, argType));
						}
					} else {
						newParamType.typeArguments().add(ASTNode.copySubtree(ast, (ASTNode) arg));
					}
				}
				return newParamType;
			}
		}
		return null;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.function.Executable;
					
					Executable runnable = () -> {};
					runnable.execute();
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.function.ThrowingRunnable;
				
				ThrowingRunnable runnable = () -> {};
				runnable.run();
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "ThrowingRunnable"; //$NON-NLS-1$
	}
}
