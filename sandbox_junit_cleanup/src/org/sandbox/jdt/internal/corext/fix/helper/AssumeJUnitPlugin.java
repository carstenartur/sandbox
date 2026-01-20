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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
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
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Migrates JUnit 4 Assume calls to JUnit 5 Assumptions.
 */
public class AssumeJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	// Assume-specific method sets (different from assertion methods)
	private static final Set<String> MULTI_PARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull", "assumeThat");
	private static final Set<String> ONEPARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull");
	private static final Set<String> ALL_ASSUMPTION_METHODS = Stream.of(MULTI_PARAM_ASSUMPTIONS, ONEPARAM_ASSUMPTIONS)
			.flatMap(Set::stream).collect(Collectors.toSet());

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		ALL_ASSUMPTION_METHODS.forEach(assertionmethod -> {
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSUME, assertionmethod, compilationUnit, dataHolder,
					nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		ALL_ASSUMPTION_METHODS.forEach(assertionmethod -> {
			HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME + "." + assertionmethod, compilationUnit,
					dataHolder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
	}
	
	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (junitHolder.minv instanceof MethodInvocation) {
			MethodInvocation minv= junitHolder.getMethodInvocation();
			if ("assumeThat".equals(minv.getName().getIdentifier()) && isJUnitAssume(minv)) {
				// Check if this is using Hamcrest matchers
				if (usesHamcrestMatcher(minv)) {
					// Use Hamcrest's MatcherAssume for Hamcrest matchers
					importRewriter.addStaticImport("org.hamcrest.junit.MatcherAssume", "assumeThat", true);
				} else {
					// Use JUnit Jupiter's Assumptions for non-Hamcrest assumeThat
					importRewriter.addStaticImport("org.junit.jupiter.api.Assumptions", "assumeThat", true);
				}
				importRewriter.removeStaticImport("org.junit.Assume.assumeThat");
				MethodInvocation newAssumeThatCall = ast.newMethodInvocation();
				newAssumeThatCall.setName(ast.newSimpleName("assumeThat"));
				for (Object arg : minv.arguments()) {
					newAssumeThatCall.arguments().add(rewriter.createCopyTarget((ASTNode) arg));
				}
				ASTNodes.replaceButKeepComment(rewriter,minv, newAssumeThatCall, group);
			} else {
				// For assumeTrue, assumeFalse, assumeNotNull - use JUnit 5 Assumptions
				reorderParameters(minv, rewriter, group, ONEPARAM_ASSUMPTIONS, MULTI_PARAM_ASSUMPTIONS);
				SimpleName newQualifier= ast.newSimpleName(ASSUMPTIONS);
				Expression expression= minv.getExpression();
				if (expression != null) {
					ASTNodes.replaceButKeepComment(rewriter, expression, newQualifier, group);
				}
				// Add import for Assumptions class (needed for qualified method calls)
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS);
			}
		} else {
			changeImportDeclaration(junitHolder.getImportDeclaration(), importRewriter, group);
		}
	}

	/**
	 * Checks if the assumeThat method belongs to org.junit.Assume.
	 * 
	 * @param node the method invocation to check
	 * @return true if the method is from org.junit.Assume
	 */
	private boolean isJUnitAssume(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		return binding != null && ORG_JUNIT_ASSUME.equals(binding.getDeclaringClass().getQualifiedName());
	}

	/**
	 * Checks if assumeThat is being used with Hamcrest matchers.
	 * Hamcrest's assumeThat has a Matcher parameter, identified by checking if any parameter
	 * implements org.hamcrest.Matcher interface.
	 * 
	 * @param minv the method invocation to check
	 * @return true if using Hamcrest matchers, false otherwise
	 */
	private boolean usesHamcrestMatcher(MethodInvocation minv) {
		if (minv.arguments().isEmpty()) {
			return false;
		}
		
		// Check each argument to see if it's a Hamcrest Matcher
		for (Object arg : minv.arguments()) {
			if (arg instanceof Expression) {
				Expression expr = (Expression) arg;
				org.eclipse.jdt.core.dom.ITypeBinding typeBinding = expr.resolveTypeBinding();
				if (typeBinding != null && implementsHamcrestMatcher(typeBinding)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Recursively checks if a type binding implements org.hamcrest.Matcher interface.
	 * 
	 * @param typeBinding the type binding to check
	 * @return true if the type implements Matcher
	 */
	private boolean implementsHamcrestMatcher(org.eclipse.jdt.core.dom.ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return false;
		}
		
		// Check if the type itself is Matcher
		String qualifiedName = typeBinding.getErasure().getQualifiedName();
		if ("org.hamcrest.Matcher".equals(qualifiedName)) {
			return true;
		}
		
		// Check interfaces
		for (org.eclipse.jdt.core.dom.ITypeBinding interfaceBinding : typeBinding.getInterfaces()) {
			if (implementsHamcrestMatcher(interfaceBinding)) {
				return true;
			}
		}
		
		// Check superclass
		org.eclipse.jdt.core.dom.ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null && implementsHamcrestMatcher(superclass)) {
			return true;
		}
		
		return false;
	}

	/**
	 * Changes import declarations for JUnit 4 Assume to JUnit 5 Assumptions.
	 * 
	 * Note: This method now only removes the old import. The new imports (either
	 * org.junit.jupiter.api.Assumptions or org.hamcrest.junit.MatcherAssume) are
	 * added in process2Rewrite based on the actual usage context.
	 * 
	 * @param node the import declaration to change
	 * @param importRewriter the import rewriter to use
	 * @param group text edit group (unused - import rewrites are tracked separately)
	 */
	public void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, TextEditGroup group) {
		String importName = node.getName().getFullyQualifiedName();
		
		// Remove the JUnit 4 Assume import
		if (importName.equals(ORG_JUNIT_ASSUME)) {
			importRewriter.removeImport(ORG_JUNIT_ASSUME);
			// Note: We do NOT add the replacement import here.
			// The appropriate import (Assumptions or MatcherAssume) will be added
			// by process2Rewrite based on the actual method usage.
		} else if (node.isStatic() && importName.startsWith(ORG_JUNIT_ASSUME + ".")) {
			// Handle static imports like: import static org.junit.Assume.assumeThat
			String methodName = importName.substring((ORG_JUNIT_ASSUME + ".").length());
			importRewriter.removeStaticImport(ORG_JUNIT_ASSUME + "." + methodName);
			// Note: We do NOT add the replacement import here.
			// The appropriate import will be added by process2Rewrite.
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assumptions.assumeNotNull(object,"failuremessage");
					Assumptions.assertTrue(condition,"failuremessage");
					"""; //$NON-NLS-1$
		}
		return """
				Assume.assumeNotNull("failuremessage", object);
				Assume.assertTrue("failuremessage",condition);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Assume"; //$NON-NLS-1$
	}
}
