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
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.Signature;

import org.sandbox.jdt.container.api.ClosedSourceContainerPlan;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.LosslessParameterBridgePlan;

/** Resolves stable plan handles into source-local workbench labels, kept out of exported diagnostics. */
public final class ContainerSignaturePreview {
	private ContainerSignaturePreview() {
	}

	public static List<String> describe(ClosedSourceContainerPlan plan) throws CoreException {
		List<String> details= new ArrayList<>();
		details.add(plan.compatibility().summary());
		if (plan instanceof ClosedSourceReturnMigrationPlan returned) {
			var method= method(returned.methodHandle());
			details.add("Return: " + label(method) + " — " + transition(method.getReturnType())); //$NON-NLS-1$ //$NON-NLS-2$
			for (var use : returned.consumers()) {
				if (use.functionTarget().isPresent()) {
					details.add("Functional target in " + caller(use.unitHandle(), use.functionTarget().get().initializer().start())); //$NON-NLS-1$
				}
				details.add("Result caller: " + caller(use.unitHandle(), use.call().start())); //$NON-NLS-1$
			}
		} else if (plan instanceof LosslessParameterBridgePlan bridge) {
			var parameter= bridge.migration().parameterPlans().getFirst();
			var method= method(parameter.methodJavaElementHandle());
			details.add("Deprecated overload: " + label(method)); //$NON-NLS-1$
			details.add("New overload: " + method.getDeclaringType().getFullyQualifiedName('.') + "." + method.getElementName() //$NON-NLS-1$ //$NON-NLS-2$
					+ "(List<" + type(Signature.getElementType(method.getParameterTypes()[0])) + ">)"); //$NON-NLS-1$ //$NON-NLS-2$
			parameters(details, bridge.migration());
		} else if (plan instanceof ClosedSourceParameterMigrationPlan parameters) {
			parameters(details, parameters);
		}
		return List.copyOf(details);
	}

	private static void parameters(List<String> details, ClosedSourceParameterMigrationPlan plan) throws CoreException {
		for (var member : plan.parameterPlans()) {
			var method= method(member.methodJavaElementHandle());
			int at= member.parameterIndex();
			String prefix= plan.parameterPlans().size() == 1 ? "Parameter: " : "Override family member: "; //$NON-NLS-1$ //$NON-NLS-2$
			details.add(prefix + label(method) + ", parameter " + (at + 1) + " — " + transition(method.getParameterTypes()[at])); //$NON-NLS-1$ //$NON-NLS-2$
			if (!member.ownedFieldBindingKey().isEmpty()) {
				for (var edit : member.edits()) {
					if (edit.kind() == EditKind.CHANGE_OWNED_FIELD
							&& method.getCompilationUnit().getElementAt(edit.sourceStart()) instanceof IField field) {
						details.add("Owned field: " + field.getDeclaringType().getFullyQualifiedName('.') + "." + field.getElementName() //$NON-NLS-1$ //$NON-NLS-2$
								+ " — " + transition(field.getTypeSignature()) + ". The constructor stores the same list."); //$NON-NLS-1$ //$NON-NLS-2$
					} else if (edit.kind() == EditKind.REPLACE_FIELD_LENGTH) {
						details.add("Stored length becomes size() in " + caller(member.compilationUnitHandle(), edit.sourceStart())); //$NON-NLS-1$
					}
				}
			}
		}
		for (var transfer : plan.callerPlan().argumentTransfers()) {
			details.add("Caller: " + caller(plan.callerPlan().compilationUnitHandle(), transfer.sourceStart()) //$NON-NLS-1$
					+ " → " + label(method(transfer.methodJavaElementHandle()))); //$NON-NLS-1$
		}
	}

	private static IMethod method(String handle) throws CoreException {
		if (JavaCore.create(handle) instanceof IMethod method && method.exists()) {
			return method;
		}
		throw new CoreException(Status.error("A signature declaration disappeared before preview.")); //$NON-NLS-1$
	}

	private static String transition(String signature) {
		return type(signature) + " → List<" + type(Signature.getElementType(signature)) + ">"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String type(String signature) {
		return Signature.getSimpleName(Signature.toString(signature));
	}

	private static String label(IMethod method) {
		List<String> arguments= new ArrayList<>();
		for (String argument : method.getParameterTypes()) {
			arguments.add(type(argument));
		}
		return method.getDeclaringType().getFullyQualifiedName('.') + "." + method.getElementName() //$NON-NLS-1$
				+ "(" + String.join(", ", arguments) + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static String caller(String handle, int offset) throws CoreException {
		if (JavaCore.create(handle) instanceof ICompilationUnit unit && unit.exists()) {
			IJavaElement member= unit.getElementAt(offset);
			if (member != null && member.getAncestor(IJavaElement.METHOD) instanceof IMethod method) {
				return label(method) + " (" + unit.getElementName() + ", source offset " + offset + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			return unit.getElementName() + ", source offset " + offset; //$NON-NLS-1$
		}
		throw new CoreException(Status.error("A caller disappeared before preview.")); //$NON-NLS-1$
	}
}
