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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractMethodMigrationPlugin;

/**
 * Migrates JUnit 4 Assume calls to JUnit 5 Assumptions.
 * 
 * <p>
 * Special handling:
 * </p>
 * <ul>
 * <li>assumeThat with Hamcrest →
 * org.hamcrest.junit.MatcherAssume.assumeThat</li>
 * <li>assumeThat without Hamcrest →
 * org.junit.jupiter.api.Assumptions.assumeThat</li>
 * <li>Other assumptions → JUnit 5 Assumptions with parameter reordering</li>
 * </ul>
 */
public class AssumeJUnitPlugin extends AbstractMethodMigrationPlugin {

	// Assume-specific method sets (different from assertion methods)
	private static final String METHOD_ASSUME_NOT_NULL = "assumeNotNull"; //$NON-NLS-1$
	private static final Set<String> MULTI_PARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeThat");
	private static final Set<String> ONEPARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse");
	/*
	 * Keep assumeNotNull visible to the visitor so its legacy imports can be
	 * preserved, but never rewrite the invocation: Jupiter has no equivalent
	 * method and JUnit 4 evaluates every vararg before checking for null.
	 */
	private static final Set<String> ALL_ASSUMPTION_METHODS = Stream.of(
			MULTI_PARAM_ASSUMPTIONS, ONEPARAM_ASSUMPTIONS, Set.of(METHOD_ASSUME_NOT_NULL))
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

		String methodName = minv.getName().getIdentifier();
		if (METHOD_ASSUME_NOT_NULL.equals(methodName) && isJUnitAssume(minv)) {
			// There is no Jupiter Assumptions.assumeNotNull. Keep the proven
			// JUnit 4 call intact instead of emitting uncompilable code or
			// changing eager vararg evaluation into short-circuit semantics.
			return;
		}

		if (METHOD_ASSUME_THAT.equals(methodName) && isJUnitAssume(minv)) {
			// Special handling for assumeThat - check if using Hamcrest matchers
			if (usesHamcrestMatcher(minv)) {
				// Use Hamcrest's MatcherAssume for Hamcrest matchers
				importRewriter.addStaticImport(ORG_HAMCREST_JUNIT_MATCHER_ASSUME, METHOD_ASSUME_THAT, true);
			} else {
				// Use JUnit Jupiter's Assumptions for non-Hamcrest assumeThat
				importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS, METHOD_ASSUME_THAT, true);
			}
			importRewriter.removeStaticImport(ORG_JUNIT_ASSUME + "." + METHOD_ASSUME_THAT);
			MethodInvocation newAssumeThatCall = ast.newMethodInvocation();
			newAssumeThatCall.setName(ast.newSimpleName(METHOD_ASSUME_THAT));
			for (Object arg : minv.arguments()) {
				newAssumeThatCall.arguments().add(rewriter.createCopyTarget((org.eclipse.jdt.core.dom.ASTNode) arg));
			}
			ASTNodes.replaceButKeepComment(rewriter, minv, newAssumeThatCall, group);
		} else {
			// For assumeTrue and assumeFalse use the ordinary Jupiter migration.
			super.processMethodInvocation(group, rewriter, ast, importRewriter, minv);
			if (minv.getExpression() == null && containsLegacyAssumeNotNull(minv)) {
				// A retained JUnit 4 wildcard import may still be needed by
				// assumeNotNull. Add a specific Jupiter import only in that
				// mixed case; otherwise the ordinary import rewrite remains
				// unchanged.
				importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS, methodName, false);
			} else if (minv.getExpression() != null) {
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS);
			}
		}
	}

	@Override
	protected void processImportDeclaration(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, ImportDeclaration importDecl) {

		String importName = importDecl.getName().getFullyQualifiedName();
		boolean keepLegacyAssume = containsLegacyAssumeNotNull(importDecl);

		// Special handling for org.junit.Assume imports when using Hamcrest
		if (importDecl.isStatic()) {
			// Handle static imports
			if (importDecl.isOnDemand()) {
				// Wildcard import: import static org.junit.Assume.*
				if (ORG_JUNIT_ASSUME.equals(importName) && !keepLegacyAssume) {
					importRewriter.removeStaticImport(importName + ".*");
					importRewriter.addStaticImport(getTargetClass(), "*", false);
				}
			} else {
				// Specific static import: import static org.junit.Assume.assumeThat
				if (importName.startsWith(ORG_JUNIT_ASSUME + ".")) {
					String methodName = importName.substring(ORG_JUNIT_ASSUME.length() + 1);
					if (METHOD_ASSUME_NOT_NULL.equals(methodName)) {
						return;
					}
					// Remove the JUnit 4 static import - the method handler will add the correct
					// one
					importRewriter.removeStaticImport(importName);
					// For assumeThat, the processMethodInvocation will add the correct import
					// (Hamcrest or JUnit 5)
					// For other methods, add JUnit 5 static import
					if (!METHOD_ASSUME_THAT.equals(methodName)) {
						importRewriter.addStaticImport(getTargetClass(), methodName, false);
					}
				}
			}
		} else {
			// Handle regular imports: import org.junit.Assume
			if (ORG_JUNIT_ASSUME.equals(importName) && !keepLegacyAssume) {
				importRewriter.removeImport(ORG_JUNIT_ASSUME);
				// Target imports are added by the invocation that actually needs
				// them; Hamcrest-only migrations do not need Assumptions.
			}
		}
	}

	private boolean containsLegacyAssumeNotNull(ASTNode node) {
		boolean[] found = { false };
		node.getRoot().accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (METHOD_ASSUME_NOT_NULL.equals(invocation.getName().getIdentifier())
						&& isJUnitAssume(invocation)) {
					found[0] = true;
					return false;
				}
				return !found[0];
			}
		});
		return found[0];
	}

	@Override
	protected void reorderMessageParameter(TextEditGroup group, ASTRewrite rewriter,
			MethodInvocation methodInvocation) {
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
	 * Checks if assumeThat is being used with Hamcrest matchers. Hamcrest's
	 * assumeThat has a Matcher parameter, identified by checking if any parameter
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
	 * Recursively checks if a type binding implements org.hamcrest.Matcher
	 * interface.
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
			if (ORG_HAMCREST_MATCHER.equals(qualifiedName)) {
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
					Assumptions.assumeTrue(condition, "failuremessage");
					// Assume.assumeNotNull(...) stays on JUnit 4 for manual migration.
					"""; //$NON-NLS-1$
		}
		return """
				Assume.assumeTrue("failuremessage", condition);
				Assume.assumeNotNull(object);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Assume"; //$NON-NLS-1$
	}
}
