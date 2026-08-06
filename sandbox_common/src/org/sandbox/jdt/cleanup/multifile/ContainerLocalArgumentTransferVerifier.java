/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;

import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;

/** Revalidates unchanged caller arguments against their exact planned method target. */
final class ContainerLocalArgumentTransferVerifier {

	private static final String PLUGIN_ID= "sandbox_common"; //$NON-NLS-1$

	private ContainerLocalArgumentTransferVerifier() {
	}

	static void verify(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerLocalRewritePlan plan) throws CoreException {
		if (plan.argumentTransfers().isEmpty()) {
			return;
		}
		Map<SourceRange, ArgumentTransfer> expected=
				HashMap.newHashMap(plan.argumentTransfers().size());
		for (ArgumentTransfer transfer : plan.argumentTransfers()) {
			expected.put(new SourceRange(
					transfer.sourceStart(), transfer.sourceLength()), transfer);
		}
		Set<SourceRange> observed= HashSet.newHashSet(expected.size());
		CoreException[] failure= { null };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (failure[0] != null) {
					return false;
				}
				SourceRange range= new SourceRange(
						name.getStartPosition(), name.getLength());
				ArgumentTransfer transfer= expected.get(range);
				if (transfer == null) {
					return true;
				}
				if (!plannedVariable(name, plan.bindingKey())
						|| !(name.getParent() instanceof MethodInvocation invocation)) {
					failure[0]= stale(unit,
							"planned argument transfer no longer contains the local value"); //$NON-NLS-1$
					return false;
				}
				int argumentIndex= invocation.arguments().indexOf(name);
				if (argumentIndex != transfer.parameterIndex()
						|| !transfer.methodJavaElementHandle().equals(
								methodHandle(invocation.resolveMethodBinding()))) {
					failure[0]= stale(unit, "argument-transfer target changed"); //$NON-NLS-1$
					return false;
				}
				if (!observed.add(range)) {
					failure[0]= stale(unit,
							"argument-transfer source range resolves more than once"); //$NON-NLS-1$
					return false;
				}
				return true;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
		if (!observed.equals(expected.keySet())) {
			throw stale(unit, "argument-transfer occurrence set changed"); //$NON-NLS-1$
		}
	}

	private static boolean plannedVariable(SimpleName name, String bindingKey) {
		return name.resolveBinding() instanceof IVariableBinding binding
				&& bindingKey.equals(binding.getVariableDeclaration().getKey());
	}

	private static String methodHandle(IMethodBinding binding) {
		IJavaElement element= binding == null
				? null : binding.getMethodDeclaration().getJavaElement();
		return element == null ? "" : element.getHandleIdentifier(); //$NON-NLS-1$
	}

	private static CoreException stale(ICompilationUnit unit, String detail) {
		return new CoreException(new Status(
				IStatus.ERROR,
				PLUGIN_ID,
				"Container rewrite plan is stale for " //$NON-NLS-1$
						+ unit.getElementName() + ": " + detail)); //$NON-NLS-1$
	}

	private record SourceRange(int start, int length) {
	}
}
