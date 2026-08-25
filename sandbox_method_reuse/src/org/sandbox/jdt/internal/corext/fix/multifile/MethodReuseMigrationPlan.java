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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.InputDescriptor;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.OutputDescriptor;
import org.sandbox.jdt.internal.corext.fix.multifile.MethodReuseSemanticSupport.SequenceDescriptor;

/** Immutable plan for extracting repeated statement sequences into shared methods. */
public record MethodReuseMigrationPlan(SelectedCompilationUnitPlan selectedScope,
		List<MethodReuseSequenceCandidate> candidates) {

	/** Defensively copies all immutable plan data. */
	public MethodReuseMigrationPlan {
		selectedScope= Objects.requireNonNull(selectedScope);
		candidates= List.copyOf(candidates);
	}

	/** Returns whether a compilation unit belongs to this cleanup run. */
	public boolean contains(ICompilationUnit unit) {
		return selectedScope.contains(unit);
	}

	/** Re-resolves and adds the local operations for the supplied compilation unit. */
	public void addOperationsFor(ICompilationUnit unit, CompilationUnit root,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		String handle= unit.getPrimary().getHandleIdentifier();
		for (MethodReuseSequenceCandidate candidate : candidates) {
			boolean participates= candidate.targetCompilationUnitHandle().equals(handle)
					|| candidate.occurrences().stream()
							.anyMatch(occurrence -> handle.equals(occurrence.compilationUnitHandle()));
			if (!participates) {
				continue;
			}
			ResolvedUnitCandidate resolved= resolve(unit.getPrimary(), root, candidate);
			for (ResolvedOccurrence occurrence : resolved.localOccurrences()) {
				for (Statement statement : occurrence.statements()) {
					nodesProcessed.add(statement);
				}
			}
			operations.add(new MethodReuseExtractionOperation(resolved));
		}
	}

	private static ResolvedUnitCandidate resolve(ICompilationUnit unit, CompilationUnit root,
			MethodReuseSequenceCandidate candidate) throws CoreException {
		List<ResolvedOccurrence> localOccurrences= new ArrayList<>();
		for (MethodReuseSequenceOccurrence occurrence : candidate.occurrences()) {
			if (!unit.getHandleIdentifier().equals(occurrence.compilationUnitHandle())) {
				continue;
			}
			localOccurrences.add(resolveOccurrence(unit, root, occurrence, candidate));
		}

		ICompilationUnit targetUnit= unit;
		CompilationUnit targetRoot= root;
		if (!candidate.targetCompilationUnitHandle().equals(unit.getHandleIdentifier())) {
			IJavaElement targetElement= JavaCore.create(candidate.targetCompilationUnitHandle());
			if (!(targetElement instanceof ICompilationUnit resolvedTarget) || !resolvedTarget.exists()) {
				throw stale(candidate, "target compilation unit is unavailable"); //$NON-NLS-1$
			}
			targetUnit= resolvedTarget.getPrimary();
			targetRoot= MethodReuseSemanticSupport.parse(targetUnit);
		}
		ResolvedOccurrence canonical= resolveOccurrence(targetUnit, targetRoot,
				candidate.canonicalOccurrence(), candidate);
		TypeDeclaration targetType= MethodReuseSemanticSupport.findType(targetRoot,
				candidate.targetTypeBindingKey());
		if (targetType == null || !canonical.descriptor().targetTypeEligible()
				|| !candidate.targetTypeQualifiedName()
						.equals(canonical.descriptor().declaringTypeQualifiedName())
				|| !MethodReuseSemanticSupport.methodNameAvailable(targetType,
						candidate.generatedMethodName())) {
			throw stale(candidate, "target type or generated method name changed"); //$NON-NLS-1$
		}

		boolean ownsGeneratedMethod=
				candidate.targetCompilationUnitHandle().equals(unit.getHandleIdentifier());
		return new ResolvedUnitCandidate(candidate, List.copyOf(localOccurrences),
				ownsGeneratedMethod ? canonical : null, ownsGeneratedMethod ? targetType : null);
	}

	private static ResolvedOccurrence resolveOccurrence(ICompilationUnit unit, CompilationUnit root,
			MethodReuseSequenceOccurrence occurrence, MethodReuseSequenceCandidate candidate)
			throws CoreException {
		MethodDeclaration method= MethodReuseSemanticSupport.findMethod(root,
				occurrence.methodBindingKey());
		SequenceDescriptor descriptor= MethodReuseSemanticSupport.describeSequence(unit, root, method,
				occurrence.startStatementIndex(), candidate.statementCount());
		if (descriptor == null || !candidate.expectedFingerprint().equals(descriptor.fingerprint())
				|| !candidate.inputTypeSignature().equals(descriptor.inputTypeSignature())
				|| !candidate.outputTypeKey().equals(descriptor.outputTypeKey())) {
			throw stale(candidate, "one repeated sequence changed or is no longer safe"); //$NON-NLS-1$
		}
		List<Statement> statements= MethodReuseSemanticSupport.statementWindow(method,
				occurrence.startStatementIndex(), candidate.statementCount());
		if (statements.size() != candidate.statementCount()) {
			throw stale(candidate, "one repeated sequence range is unavailable"); //$NON-NLS-1$
		}
		return new ResolvedOccurrence(descriptor, method, statements);
	}

	private static CoreException stale(MethodReuseSequenceCandidate candidate, String detail) {
		String message= "The coordinated method-reuse plan is stale for " + candidate.candidateId() //$NON-NLS-1$
				+ ": " + detail + ". No partial extraction was produced."; //$NON-NLS-1$ //$NON-NLS-2$
		return new CoreException(new Status(IStatus.ERROR, "sandbox_method_reuse", message)); //$NON-NLS-1$
	}

	record ResolvedOccurrence(SequenceDescriptor descriptor, MethodDeclaration method,
			List<Statement> statements) {
		ResolvedOccurrence {
			statements= List.copyOf(statements);
		}
	}

	record ResolvedUnitCandidate(MethodReuseSequenceCandidate candidate,
			List<ResolvedOccurrence> localOccurrences, ResolvedOccurrence canonicalOccurrence,
			TypeDeclaration targetType) {
		ResolvedUnitCandidate {
			localOccurrences= List.copyOf(localOccurrences);
		}
	}
}

/** One immutable occurrence identified without retaining AST nodes. */
record MethodReuseSequenceOccurrence(String compilationUnitHandle, String methodBindingKey,
		int startStatementIndex) {
	MethodReuseSequenceOccurrence {
		Objects.requireNonNull(compilationUnitHandle);
		Objects.requireNonNull(methodBindingKey);
		if (startStatementIndex < 0) {
			throw new IllegalArgumentException();
		}
	}
}

/** One immutable extraction candidate and all non-overlapping occurrences. */
record MethodReuseSequenceCandidate(String candidateId, String targetCompilationUnitHandle,
		String targetTypeBindingKey, String targetTypeQualifiedName, String generatedMethodName,
		int statementCount, String expectedFingerprint, String inputTypeSignature,
		String outputTypeKey, MethodReuseSequenceOccurrence canonicalOccurrence,
		List<MethodReuseSequenceOccurrence> occurrences) {

	MethodReuseSequenceCandidate {
		Objects.requireNonNull(candidateId);
		Objects.requireNonNull(targetCompilationUnitHandle);
		Objects.requireNonNull(targetTypeBindingKey);
		Objects.requireNonNull(targetTypeQualifiedName);
		Objects.requireNonNull(generatedMethodName);
		Objects.requireNonNull(expectedFingerprint);
		Objects.requireNonNull(inputTypeSignature);
		outputTypeKey= outputTypeKey == null ? "" : outputTypeKey; //$NON-NLS-1$
		Objects.requireNonNull(canonicalOccurrence);
		occurrences= List.copyOf(occurrences);
		if (statementCount < 2 || occurrences.size() < 2
				|| !occurrences.contains(canonicalOccurrence)) {
			throw new IllegalArgumentException();
		}
	}
}

/** One file-local operation for a coordinated sequence-extraction candidate. */
final class MethodReuseExtractionOperation extends CompilationUnitRewriteOperation {

	private final MethodReuseMigrationPlan.ResolvedUnitCandidate resolved;

	MethodReuseExtractionOperation(MethodReuseMigrationPlan.ResolvedUnitCandidate resolved) {
		this.resolved= resolved;
	}

	@Override
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
			throws CoreException {
		MethodReuseSequenceCandidate candidate= resolved.candidate();
		AST ast= cuRewrite.getRoot().getAST();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		TextEditGroup group= createTextEditGroup(
				"Extract repeated sequence into " + candidate.targetTypeQualifiedName() + '.' //$NON-NLS-1$
						+ candidate.generatedMethodName(), cuRewrite);

		for (MethodReuseMigrationPlan.ResolvedOccurrence occurrence : resolved.localOccurrences()) {
			replaceOccurrence(ast, rewrite, occurrence, candidate, group);
		}
		if (resolved.canonicalOccurrence() != null && resolved.targetType() != null) {
			insertGeneratedMethod(ast, rewrite, cuRewrite.getImportRewrite(),
					resolved.canonicalOccurrence(), resolved.targetType(), candidate, group);
		}
	}

	private static void replaceOccurrence(AST ast, ASTRewrite rewrite,
			MethodReuseMigrationPlan.ResolvedOccurrence occurrence,
			MethodReuseSequenceCandidate candidate, TextEditGroup group) {
		List<Statement> statements= occurrence.statements();
		Statement first= statements.get(0);
		ListRewrite listRewrite= rewrite.getListRewrite(first.getParent(),
				Block.STATEMENTS_PROPERTY);
		MethodInvocation invocation= createInvocation(ast, occurrence.descriptor(), candidate);
		Statement replacement;
		OutputDescriptor output= occurrence.descriptor().output();
		if (output == null) {
			replacement= ast.newExpressionStatement(invocation);
		} else {
			Statement declarationStatement=
					statements.get(output.declarationStatementIndexInSequence());
			VariableDeclarationStatement original=
					(VariableDeclarationStatement) declarationStatement;
			VariableDeclarationStatement copied=
					(VariableDeclarationStatement) ASTNode.copySubtree(ast, original);
			VariableDeclarationFragment fragment=
					(VariableDeclarationFragment) copied.fragments().get(0);
			fragment.setInitializer(invocation);
			replacement= copied;
		}
		listRewrite.replace(first, replacement, group);
		for (int index= 1; index < statements.size(); index++) {
			listRewrite.remove(statements.get(index), group);
		}
	}

	private static MethodInvocation createInvocation(AST ast, SequenceDescriptor descriptor,
			MethodReuseSequenceCandidate candidate) {
		MethodInvocation invocation= ast.newMethodInvocation();
		if (!candidate.targetTypeBindingKey().equals(descriptor.declaringTypeBindingKey())) {
			invocation.setExpression(ast.newName(candidate.targetTypeQualifiedName()));
		}
		invocation.setName(ast.newSimpleName(candidate.generatedMethodName()));
		for (InputDescriptor input : descriptor.inputs()) {
			invocation.arguments().add(ast.newSimpleName(input.sourceName()));
		}
		return invocation;
	}

	@SuppressWarnings("unchecked")
	private static void insertGeneratedMethod(AST ast, ASTRewrite rewrite,
			ImportRewrite imports, MethodReuseMigrationPlan.ResolvedOccurrence canonical,
			TypeDeclaration targetType, MethodReuseSequenceCandidate candidate,
			TextEditGroup group) {
		SequenceDescriptor descriptor= canonical.descriptor();
		MethodDeclaration generated= ast.newMethodDeclaration();
		generated.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
		generated.setName(ast.newSimpleName(candidate.generatedMethodName()));
		ImportRewriteContext importContext= new ContextSensitiveImportRewriteContext(
				(CompilationUnit) targetType.getRoot(), targetType.getStartPosition(), imports);
		OutputDescriptor output= descriptor.output();
		if (output == null) {
			generated.setReturnType2(ast.newPrimitiveType(PrimitiveType.VOID));
		} else {
			generated.setReturnType2(importType(output.typeBinding(), ast, imports, importContext,
					TypeLocation.RETURN_TYPE));
		}
		for (InputDescriptor input : descriptor.inputs()) {
			SingleVariableDeclaration parameter= ast.newSingleVariableDeclaration();
			parameter.setName(ast.newSimpleName(input.sourceName()));
			parameter.setType(importType(input.typeBinding(), ast, imports, importContext,
					TypeLocation.PARAMETER));
			generated.parameters().add(parameter);
		}

		Block body= ast.newBlock();
		for (Statement statement : canonical.statements()) {
			body.statements().add(ASTNode.copySubtree(ast, statement));
		}
		if (output != null) {
			ReturnStatement returned= ast.newReturnStatement();
			returned.setExpression(ast.newSimpleName(output.sourceName()));
			body.statements().add(returned);
		}
		generated.setBody(body);
		ListRewrite declarations= rewrite.getListRewrite(targetType,
				TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		declarations.insertLast(generated, group);
	}

	private static Type importType(ITypeBinding binding, AST ast, ImportRewrite imports,
			ImportRewriteContext context, TypeLocation location) {
		return imports.addImport(binding, ast, context, location);
	}
}
