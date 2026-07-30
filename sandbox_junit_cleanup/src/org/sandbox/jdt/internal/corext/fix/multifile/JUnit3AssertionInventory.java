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
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

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
		String[] rejection= new String[1];
		List<InvocationMigration> invocations= new ArrayList<>();
		declaration.accept(new ASTVisitor() {
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
					if (CUSTOM_EXECUTION_METHODS.contains(name) || ASSERTION_METHODS.contains(name)) {
						rejection[0]= "A potentially JUnit-related invocation has no resolved method binding."; //$NON-NLS-1$
					}
					return rejection[0] == null;
				}
				ITypeBinding declaringClass= binding.getDeclaringClass();
				String owner= declaringClass == null ? "" : declaringClass.getErasure().getQualifiedName(); //$NON-NLS-1$
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
}
