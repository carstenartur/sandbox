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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
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
					// Store the SimpleType node directly for replacement
					if (!nodesprocessed.contains(node) && !found.contains(node)) {
						found.add(node);
						addStandardRewriteOperation(fixcore, operations, node, dataHolder);
					}
				}
				return true;
			}
			
			@Override
			public boolean visit(ParameterizedType node) {
				// Check if this parameterized type contains ThrowingRunnable
				// e.g., AtomicReference<ThrowingRunnable>
				if (containsThrowingRunnable(node)) {
					if (!nodesprocessed.contains(node) && !found.contains(node)) {
						found.add(node);
						addStandardRewriteOperation(fixcore, operations, node, dataHolder);
					}
				}
				// Don't visit children - we handle the whole parameterized type
				return false;
			}
			
			@Override
			public boolean visit(MethodInvocation node) {
				// Check if this is a .run() call on a ThrowingRunnable
				if (RUN_METHOD.equals(node.getName().getIdentifier()) && node.arguments().isEmpty()) {
					boolean isThrowingRunnableRun = false;
					
					// First, try to check via method binding's declaring class
					org.eclipse.jdt.core.dom.IMethodBinding methodBinding = node.resolveMethodBinding();
					if (methodBinding != null) {
						ITypeBinding declaringClass = methodBinding.getDeclaringClass();
						if (declaringClass != null && ORG_JUNIT_FUNCTION_THROWING_RUNNABLE.equals(declaringClass.getQualifiedName())) {
							isThrowingRunnableRun = true;
						}
					}
					
					// Fallback: check the receiver expression type (handles generic type arguments)
					if (!isThrowingRunnableRun && node.getExpression() != null) {
						ITypeBinding receiverType = node.getExpression().resolveTypeBinding();
						if (receiverType != null && ORG_JUNIT_FUNCTION_THROWING_RUNNABLE.equals(receiverType.getQualifiedName())) {
							isThrowingRunnableRun = true;
						}
					}
					
					if (isThrowingRunnableRun) {
						if (!nodesprocessed.contains(node) && !found.contains(node)) {
							found.add(node);
							addStandardRewriteOperation(fixcore, operations, node, dataHolder);
						}
					}
				}
				return true;
			}
		});
		
		nodesprocessed.addAll(found);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		
		ASTNode node = junitHolder.minv;
		
		if (node instanceof ImportDeclaration) {
			processImportDeclaration(importRewriter, (ImportDeclaration) node);
		} else if (node instanceof MethodInvocation) {
			processMethodInvocation(group, rewriter, ast, (MethodInvocation) node);
		} else if (node instanceof SimpleType) {
			processSimpleType(group, rewriter, ast, importRewriter, (SimpleType) node);
		} else if (node instanceof ParameterizedType) {
			processParameterizedType(group, rewriter, ast, importRewriter, (ParameterizedType) node);
		}
	}
	
	/**
	 * Processes import declarations, replacing ThrowingRunnable import with Executable import.
	 */
	private void processImportDeclaration(ImportRewrite importRewriter, ImportDeclaration importDecl) {
		ensureImports(importRewriter);
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
	 * Processes simple type nodes, replacing ThrowingRunnable with Executable.
	 */
	private void processSimpleType(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, SimpleType node) {
		if (THROWING_RUNNABLE_SIMPLE.equals(node.getName().getFullyQualifiedName())) {
			SimpleType newType = ast.newSimpleType(ast.newName(EXECUTABLE_SIMPLE));
			ASTNodes.replaceButKeepComment(rewriter, node, newType, group);
			ensureImports(importRewriter);
		}
	}
	
	/**
	 * Processes parameterized type nodes, replacing ThrowingRunnable in type arguments with Executable.
	 * e.g., AtomicReference&lt;ThrowingRunnable&gt; -&gt; AtomicReference&lt;Executable&gt;
	 */
	private void processParameterizedType(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, ParameterizedType node) {
		Type newType = createExecutableType(ast, node);
		if (newType != null) {
			ASTNodes.replaceButKeepComment(rewriter, node, newType, group);
			ensureImports(importRewriter);
		}
	}
	
	/**
	 * Ensures the correct imports are present (removes old, adds new).
	 * ImportRewrite handles deduplication automatically.
	 */
	private void ensureImports(ImportRewrite importRewriter) {
		importRewriter.removeImport(ORG_JUNIT_FUNCTION_THROWING_RUNNABLE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_FUNCTION_EXECUTABLE);
	}
	
	/**
	 * Creates a new Executable type, handling both simple and parameterized types.
	 * Recursively processes nested parameterized types.
	 */
	private Type createExecutableType(AST ast, Type originalType) {
		if (originalType instanceof SimpleType) {
			SimpleType simpleType = (SimpleType) originalType;
			if (THROWING_RUNNABLE_SIMPLE.equals(simpleType.getName().getFullyQualifiedName())) {
				return ast.newSimpleType(ast.newName(EXECUTABLE_SIMPLE));
			}
		} else if (originalType instanceof ParameterizedType) {
			// Handle generic types like AtomicReference<ThrowingRunnable>
			// or nested types like Map<String, AtomicReference<ThrowingRunnable>>
			ParameterizedType paramType = (ParameterizedType) originalType;
			Type baseType = paramType.getType();
			
			// Check if any type argument needs transformation (recursive check)
			boolean needsTransformation = false;
			for (Object arg : paramType.typeArguments()) {
				if (containsThrowingRunnable((Type) arg)) {
					needsTransformation = true;
					break;
				}
			}
			
			if (needsTransformation) {
				// Create new parameterized type with transformed arguments
				ParameterizedType newParamType = ast.newParameterizedType((Type) ASTNode.copySubtree(ast, baseType));
				for (Object arg : paramType.typeArguments()) {
					Type argType = (Type) arg;
					Type transformedArg = createExecutableType(ast, argType);
					if (transformedArg != null) {
						// Argument was transformed
						newParamType.typeArguments().add(transformedArg);
					} else {
						// Argument doesn't need transformation, copy as-is
						newParamType.typeArguments().add(ASTNode.copySubtree(ast, argType));
					}
				}
				return newParamType;
			}
		}
		return null;
	}
	
	/**
	 * Checks if a type contains ThrowingRunnable anywhere in its structure.
	 */
	private boolean containsThrowingRunnable(Type type) {
		if (type instanceof SimpleType) {
			SimpleType simpleType = (SimpleType) type;
			return THROWING_RUNNABLE_SIMPLE.equals(simpleType.getName().getFullyQualifiedName());
		} else if (type instanceof ParameterizedType) {
			ParameterizedType paramType = (ParameterizedType) type;
			for (Object arg : paramType.typeArguments()) {
				if (containsThrowingRunnable((Type) arg)) {
					return true;
				}
			}
		}
		return false;
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
