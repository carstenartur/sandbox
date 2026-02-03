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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodMigrationPlugin;

/**
 * Migrates JUnit 4 Assume calls to JUnit 5 Assumptions.
 * 
 * <p>Special handling:</p>
 * <ul>
 *   <li>assumeThat with Hamcrest → org.hamcrest.junit.MatcherAssume.assumeThat</li>
 *   <li>assumeThat without Hamcrest → org.junit.jupiter.api.Assumptions.assumeThat</li>
 *   <li>Other assumptions → JUnit 5 Assumptions with parameter reordering</li>
 * </ul>
 */
public class AssumeJUnitPlugin extends AbstractMethodMigrationPlugin {

	// Assume-specific method sets (different from assertion methods)
	private static final Set<String> MULTI_PARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull", "assumeThat");
	private static final Set<String> ONEPARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull");
	private static final Set<String> ALL_ASSUMPTION_METHODS = Stream.of(MULTI_PARAM_ASSUMPTIONS, ONEPARAM_ASSUMPTIONS)
			.flatMap(Set::stream).collect(Collectors.toSet());

	@Override
	protected String getSourceClass() {
		return ORG_JUNIT_ASSUME;
	}

	@Override
	protected String getTargetClass() {
		return ORG_JUNIT_JUPITER_API_ASSUMPTIONS;
	}

	@Override
	protected String getTargetSimpleName() {
		return ASSUMPTIONS;
	}

	@Override
	protected Set<String> getMethodNames() {
		return ALL_ASSUMPTION_METHODS;
	}

	@Override
	protected Set<String> getMethodsRequiringReorder() {
		return ONEPARAM_ASSUMPTIONS;
	}

	@Override
	protected void processMethodInvocation(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, MethodInvocation minv) {
		
		if ("assumeThat".equals(minv.getName().getIdentifier()) && isJUnitAssume(minv)) {
			// Special handling for assumeThat - check if using Hamcrest matchers
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
				newAssumeThatCall.arguments().add(rewriter.createCopyTarget((org.eclipse.jdt.core.dom.ASTNode) arg));
			}
			ASTNodes.replaceButKeepComment(rewriter, minv, newAssumeThatCall, group);
		} else {
			// For assumeTrue, assumeFalse, assumeNotNull - use base class behavior
			super.processMethodInvocation(group, rewriter, ast, importRewriter, minv);
			// Add import for Assumptions class (needed for qualified method calls)
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS);
		}
	}

	@Override
	protected void processImportDeclaration(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, ImportDeclaration importDecl) {
		
		String importName = importDecl.getName().getFullyQualifiedName();
		
		// Special handling for org.junit.Assume imports when using Hamcrest
		if (importDecl.isStatic()) {
			// Handle static imports
			if (importDecl.isOnDemand()) {
				// Wildcard import: import static org.junit.Assume.*
				if (ORG_JUNIT_ASSUME.equals(importName)) {
					importRewriter.removeStaticImport(importName + ".*");
					importRewriter.addStaticImport(getTargetClass(), "*", false);
				}
			} else {
				// Specific static import: import static org.junit.Assume.assumeThat
				if (importName.startsWith(ORG_JUNIT_ASSUME + ".")) {
					String methodName = importName.substring(ORG_JUNIT_ASSUME.length() + 1);
					// Remove the JUnit 4 static import - the method handler will add the correct one
					importRewriter.removeStaticImport(importName);
					// For assumeThat, the processMethodInvocation will add the correct import (Hamcrest or JUnit 5)
					// For other methods, add JUnit 5 static import
					if (!"assumeThat".equals(methodName)) {
						importRewriter.addStaticImport(getTargetClass(), methodName, false);
					}
				}
			}
		} else {
			// Handle regular imports: import org.junit.Assume
			if (ORG_JUNIT_ASSUME.equals(importName)) {
				// Always remove the JUnit 4 import
				importRewriter.removeImport(ORG_JUNIT_ASSUME);
				// Only add JUnit 5 Assumptions import if needed (will be added by processMethodInvocation for non-Hamcrest methods)
				// Don't unconditionally add it here, as Hamcrest-only usage doesn't need it
			}
		}
	}

	@Override
	protected void reorderMessageParameter(TextEditGroup group, ASTRewrite rewriter, MethodInvocation methodInvocation) {
		// Use specific parameter sets for assumptions
		reorderParameters(methodInvocation, rewriter, group, ONEPARAM_ASSUMPTIONS, MULTI_PARAM_ASSUMPTIONS);
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
				ITypeBinding typeBinding = expr.resolveTypeBinding();
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
	private boolean implementsHamcrestMatcher(ITypeBinding typeBinding) {
		if (typeBinding == null) {
			return false;
		}
		
		// Check if the type itself is Matcher
		ITypeBinding erasure = typeBinding.getErasure();
		if (erasure != null) {
			String qualifiedName = erasure.getQualifiedName();
			if ("org.hamcrest.Matcher".equals(qualifiedName)) {
				return true;
			}
		}
		
		// Check interfaces
		for (ITypeBinding interfaceBinding : typeBinding.getInterfaces()) {
			if (implementsHamcrestMatcher(interfaceBinding)) {
				return true;
			}
		}
		
		// Check superclass
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null && implementsHamcrestMatcher(superclass)) {
			return true;
		}
		
		return false;
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
