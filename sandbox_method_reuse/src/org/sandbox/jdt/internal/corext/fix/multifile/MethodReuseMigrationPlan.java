/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.MethodDescriptor;

/** Immutable plan for exact duplicate-method delegation across source files. */
public record MethodReuseMigrationPlan(SelectedCompilationUnitPlan selectedScope,
		List<MethodReuseCandidate> candidates) {

	/** Defensively copies all retained semantic descriptors. */
	public MethodReuseMigrationPlan {
		selectedScope= Objects.requireNonNull(selectedScope);
		candidates= List.copyOf(candidates);
	}

	/** Returns whether the compilation unit participates in this cleanup run. */
	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	/** Resolves current AST nodes and adds only edits owned by the supplied unit. */
	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed) throws CoreException {
		String handle= unit.getPrimary().getHandleIdentifier();
		for (MethodReuseCandidate candidate : candidates) {
			if (!handle.equals(candidate.duplicateCompilationUnitHandle())) {
				continue;
			}
			ResolvedCandidate resolved= resolve(unit.getPrimary(), root, candidate);
			if (nodesProcessed.add(resolved.duplicateMethod().getBody())) {
				operations.add(new MethodReuseDelegationOperation(resolved));
			}
		}
	}

	private static ResolvedCandidate resolve(ICompilationUnit duplicateUnit, CompilationUnit duplicateRoot,
			MethodReuseCandidate candidate) throws CoreException {
		MethodDeclaration duplicateMethod= MethodReuseSemanticSupport.findMethod(duplicateRoot,
				candidate.duplicateMethodBindingKey());
		MethodDescriptor duplicateDescriptor= MethodReuseSemanticSupport.describe(duplicateUnit, duplicateRoot,
				duplicateMethod);
		verifyDescriptor(candidate, duplicateDescriptor, false);

		IJavaElement targetElement= JavaCore.create(candidate.targetCompilationUnitHandle());
		if (!(targetElement instanceof ICompilationUnit targetUnit) || !targetUnit.exists()) {
			throw stale(candidate, "target compilation unit is unavailable"); //$NON-NLS-1$
		}
		CompilationUnit targetRoot= MethodReuseSemanticSupport.parse(targetUnit.getPrimary());
		MethodDeclaration targetMethod= MethodReuseSemanticSupport.findMethod(targetRoot,
				candidate.targetMethodBindingKey());
		MethodDescriptor targetDescriptor= MethodReuseSemanticSupport.describe(targetUnit.getPrimary(), targetRoot,
				targetMethod);
		verifyDescriptor(candidate, targetDescriptor, true);
		if (!targetDescriptor.targetEligible()) {
			throw stale(candidate, "target method is no longer safely accessible"); //$NON-NLS-1$
		}

		List<String> parameterNames= new ArrayList<>();
		for (Object parameter : duplicateMethod.parameters()) {
			if (!(parameter instanceof SingleVariableDeclaration declaration)) {
				throw stale(candidate, "duplicate parameter list changed"); //$NON-NLS-1$
			}
			parameterNames.add(declaration.getName().getIdentifier());
		}
		return new ResolvedCandidate(candidate, duplicateMethod, List.copyOf(parameterNames));
	}

	private static void verifyDescriptor(MethodReuseCandidate candidate, MethodDescriptor descriptor,
			boolean target) throws CoreException {
		if (descriptor == null) {
			throw stale(candidate, (target ? "target" : "duplicate") + " method is no longer eligible"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		String expectedMethodKey= target ? candidate.targetMethodBindingKey()
				: candidate.duplicateMethodBindingKey();
		String expectedUnitHandle= target ? candidate.targetCompilationUnitHandle()
				: candidate.duplicateCompilationUnitHandle();
		if (!expectedMethodKey.equals(descriptor.methodBindingKey())
				|| !expectedUnitHandle.equals(descriptor.compilationUnitHandle())
				|| !candidate.signatureKey().equals(descriptor.signatureKey())
				|| !candidate.expectedFingerprint().equals(descriptor.fingerprint())) {
			throw stale(candidate, (target ? "target" : "duplicate") + " semantic identity changed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (target && (!candidate.targetTypeQualifiedName().equals(descriptor.declaringTypeQualifiedName())
				|| !candidate.targetMethodName().equals(descriptor.methodName()))) {
			throw stale(candidate, "target source identity changed"); //$NON-NLS-1$
		}
	}

	private static CoreException stale(MethodReuseCandidate candidate, String detail) {
		String message= "The coordinated method-reuse plan is stale for " + candidate.candidateId() //$NON-NLS-1$
				+ ": " + detail + ". No partial delegation was produced."; //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_method_reuse", message)); //$NON-NLS-1$
	}

	record ResolvedCandidate(MethodReuseCandidate candidate, MethodDeclaration duplicateMethod,
			List<String> parameterNames) {
	}
}

/** Semantic plan entry. It retains identifiers and fingerprints, never AST nodes. */
record MethodReuseCandidate(String candidateId, String targetCompilationUnitHandle,
		String targetMethodBindingKey, String targetTypeQualifiedName, String targetMethodName,
		String duplicateCompilationUnitHandle, String duplicateMethodBindingKey, String signatureKey,
		String expectedFingerprint) {

	MethodReuseCandidate {
		Objects.requireNonNull(candidateId);
		Objects.requireNonNull(targetCompilationUnitHandle);
		Objects.requireNonNull(targetMethodBindingKey);
		Objects.requireNonNull(targetTypeQualifiedName);
		Objects.requireNonNull(targetMethodName);
		Objects.requireNonNull(duplicateCompilationUnitHandle);
		Objects.requireNonNull(duplicateMethodBindingKey);
		Objects.requireNonNull(signatureKey);
		Objects.requireNonNull(expectedFingerprint);
	}
}

/** One file-local edit emitted after every planned identity was re-resolved. */
final class MethodReuseDelegationOperation extends CompilationUnitRewriteOperation {

	private final MethodReuseMigrationPlan.ResolvedCandidate resolved;

	MethodReuseDelegationOperation(MethodReuseMigrationPlan.ResolvedCandidate resolved) {
		this.resolved= resolved;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		MethodReuseCandidate candidate= resolved.candidate();
		MethodDeclaration duplicateMethod= resolved.duplicateMethod();
		AST ast= cuRewrite.getRoot().getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		String importedType= cuRewrite.getImportRewrite().addImport(candidate.targetTypeQualifiedName());
		invocation.setExpression(ast.newName(importedType));
		invocation.setName(ast.newSimpleName(candidate.targetMethodName()));
		for (String parameterName : resolved.parameterNames()) {
			invocation.arguments().add(ast.newSimpleName(parameterName));
		}

		ReturnStatement replacementReturn= ast.newReturnStatement();
		replacementReturn.setExpression(invocation);
		Block replacementBody= ast.newBlock();
		replacementBody.statements().add(replacementReturn);
		TextEditGroup group= createTextEditGroup(
				"Reuse " + candidate.targetTypeQualifiedName() + '.' + candidate.targetMethodName(), cuRewrite); //$NON-NLS-1$
		cuRewrite.getASTRewrite().replace(duplicateMethod.getBody(), replacementBody, group);
	}
}
