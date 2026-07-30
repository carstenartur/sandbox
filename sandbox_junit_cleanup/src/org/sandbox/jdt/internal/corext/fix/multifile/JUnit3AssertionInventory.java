/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.ASSERTION_METHODS;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_ASSERT;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport.JUNIT3_TEST_CASE;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;

import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SemanticSupport;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.InvocationKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.InvocationMigration;

/** Collects exact assertion call sites and rejects custom JUnit 3 API usage. */
final class JUnit3AssertionInventory {

	private static final Set<String> CUSTOM_EXECUTION_METHODS= Set.of(
			"suite", "runTest", "runBare", "createResult", "countTestCases", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"getName", "setName", "run"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	record Result(String rejectionReason, List<InvocationMigration> invocations) {
		Result {
			invocations= List.copyOf(invocations);
		}

		boolean supported() {
			return rejectionReason == null;
		}
	}

	private JUnit3AssertionInventory() {
	}

	static Result analyze(TypeDeclaration declaration) {
		return analyze(declaration, removableJdtCoreSuiteKeys(declaration));
	}

	/**
	 * Analyzes assertions while skipping method bodies whose complete semantics were
	 * separately validated and whose declarations are removed by the same atomic plan.
	 */
	static Result analyze(TypeDeclaration declaration, Set<String> ignoredMethodBindingKeys) {
		Set<String> ignored= ignoredMethodBindingKeys == null ? Set.of() : Set.copyOf(ignoredMethodBindingKeys);
		boolean jdtCoreHarness= declaration != null
				&& JdtCoreHarnessScopeDetector.inheritsJdtCoreHarness(declaration.resolveBinding());
		String[] rejection= new String[1];
		List<InvocationMigration> invocations= new ArrayList<>();
		declaration.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding binding= node.resolveBinding();
				String key= binding == null ? null : binding.getMethodDeclaration().getKey();
				return key == null || !ignored.contains(key);
			}

			@Override
			public boolean visit(SuperMethodInvocation node) {
				rejection[0]= "Explicit superclass method calls can change lifecycle semantics under Jupiter."; //$NON-NLS-1$
				return false;
			}

			@Override
			public boolean visit(MethodInvocation node) {
				if (rejection[0] != null) {
					return false;
				}
				IMethodBinding binding= node.resolveMethodBinding();
				String name= node.getName().getIdentifier();
				if (binding == null) {
					if (CUSTOM_EXECUTION_METHODS.contains(name) || ASSERTION_METHODS.contains(name)
							|| jdtCoreHarness && "buildTestSuite".equals(name)) { //$NON-NLS-1$
						rejection[0]= "A potentially JUnit-related invocation has no resolved method binding."; //$NON-NLS-1$
					}
					return rejection[0] == null;
				}
				ITypeBinding declaringClass= binding.getDeclaringClass();
				String owner= declaringClass == null ? "" : declaringClass.getErasure().getQualifiedName(); //$NON-NLS-1$
				if (jdtCoreHarness && "buildTestSuite".equals(name) //$NON-NLS-1$
						&& !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(owner)) {
					rejection[0]= "The removable local JDT Core suite delegates to an external harness owner."; //$NON-NLS-1$
					return false;
				}
				if (!JUNIT3_TEST_CASE.equals(owner) && !JUNIT3_ASSERT.equals(owner)) {
					return true;
				}
				if (!ASSERTION_METHODS.contains(name)) {
					rejection[0]= "The hierarchy calls inherited JUnit 3 API " + owner + "." + name + "()."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					return false;
				}
				String key= binding.getMethodDeclaration().getKey();
				if (key == null) {
					rejection[0]= "A JUnit 3 assertion call has no stable method binding key."; //$NON-NLS-1$
					return false;
				}
				InvocationKind kind= JUnit3SemanticSupport.hasLeadingMessage(node, binding)
						? InvocationKind.MESSAGE_FIRST
						: InvocationKind.QUALIFY;
				invocations.add(new InvocationMigration(key, node.getStartPosition(), node.getLength(), kind));
				return true;
			}
		});
		return new Result(rejection[0], invocations);
	}

	private static Set<String> removableJdtCoreSuiteKeys(TypeDeclaration declaration) {
		ITypeBinding owner= declaration == null ? null : declaration.resolveBinding();
		if (!JdtCoreHarnessScopeDetector.inheritsJdtCoreHarness(owner)) {
			return Set.of();
		}
		Set<String> result= new LinkedHashSet<>();
		for (MethodDeclaration method : declaration.getMethods()) {
			String key= exactRemovableJdtCoreSuiteKey(method, owner);
			if (key != null) {
				result.add(key);
			}
		}
		return Set.copyOf(result);
	}

	private static String exactRemovableJdtCoreSuiteKey(MethodDeclaration method, ITypeBinding owner) {
		if (!"suite".equals(method.getName().getIdentifier()) //$NON-NLS-1$
				|| !Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
				|| !method.parameters().isEmpty() || method.getBody() == null
				|| method.getBody().statements().size() != 1) {
			return null;
		}
		IMethodBinding binding= method.resolveBinding();
		ITypeBinding returnType= binding == null ? null : binding.getReturnType();
		if (returnType == null || !"junit.framework.Test".equals(returnType.getErasure().getQualifiedName())) { //$NON-NLS-1$
			return null;
		}
		Object statement= method.getBody().statements().get(0);
		if (!(statement instanceof ReturnStatement returnStatement)
				|| !(returnStatement.getExpression() instanceof MethodInvocation invocation)
				|| !"buildTestSuite".equals(invocation.getName().getIdentifier()) //$NON-NLS-1$
				|| invocation.arguments().size() != 1
				|| !(invocation.arguments().get(0) instanceof TypeLiteral literal)) {
			return null;
		}
		IMethodBinding invoked= invocation.resolveMethodBinding();
		ITypeBinding invokedOwner= invoked == null ? null : invoked.getDeclaringClass();
		ITypeBinding selected= literal.getType().resolveBinding();
		if (invokedOwner == null || selected == null
				|| !JdtCoreHarnessPlanner.JDT_CORE_TEST_CASE.equals(
						invokedOwner.getErasure().getQualifiedName())
				|| !JUnitMigrationPlan.typeKey(owner).equals(JUnitMigrationPlan.typeKey(selected))) {
			return null;
		}
		return binding.getMethodDeclaration().getKey();
	}
}
