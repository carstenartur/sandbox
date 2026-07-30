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
package org.sandbox.jdt.triggerpattern.cleanup;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.HintPlanRequirement;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlanContext;
import org.sandbox.jdt.triggerpattern.internal.GuardRegistry;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Adds semantic-plan authorization and exact coverage checks around the existing
 * {@link HintFileFixCore} rewrite backend.
 */
public final class PlanAwareHintFileFixCore {

	private static final Pattern ANNOTATION_NAME= Pattern.compile(
			"@([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)"); //$NON-NLS-1$

	private PlanAwareHintFileFixCore() {
	}

	/**
	 * Adds authorized operations and returns the exact semantic targets covered by
	 * at least one rewriting rule.
	 */
	public static Set<NodeKey> findOperationsFromContent(CompilationUnit compilationUnit,
			String hintFileContent, SemanticRewritePlan plan, Map<String, String> compilerOptions,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesProcessed)
			throws CoreException {
		GuardRegistry.getInstance();
		String requiredPlan= requiredPlan(hintFileContent);
		if (plan == null || plan.rolesByNode().isEmpty()) {
			throw failure("Hint program requires semantic plan " + requiredPlan //$NON-NLS-1$
					+ " but no non-empty authorization plan was supplied", null); //$NON-NLS-1$
		}
		if (!plan.satisfiesContract(requiredPlan)) {
			throw failure("Hint program requires semantic plan " + requiredPlan //$NON-NLS-1$
					+ " but received contract " + plan.contractId(), null); //$NON-NLS-1$
		}
		if (hintFileContent.contains("$widestType")) { //$NON-NLS-1$
			throw failure("Plan-aware hint programs may not use analysis-dependent $widestType replacements", null); //$NON-NLS-1$
		}
		HintFile hintFile= parse(hintFileContent);
		BatchTransformationProcessor processor= new BatchTransformationProcessor(hintFile);
		List<TransformationResult> authorized= processor.process(compilationUnit, compilerOptions, plan).stream()
				.filter(TransformationResult::hasReplacement)
				.toList();

		Set<NodeKey> covered= new LinkedHashSet<>();
		for (TransformationResult result : authorized) {
			ASTNode matched= result.match().getMatchedNode();
			NodeKey key= NodeKey.from(matched);
			if (matched == null || key == null) {
				throw failure("A plan-aware hint result has no stable semantic AST target", null); //$NON-NLS-1$
			}
			covered.add(key);
			nodesProcessed.add(matched);
		}

		Set<CompilationUnitRewriteOperation> delegated= new LinkedHashSet<>();
		try (SemanticRewritePlanContext.Scope ignored=
				SemanticRewritePlanContext.install(plan, compilerOptions)) {
			HintFileFixCore.findOperationsFromContent(compilationUnit, hintFileContent, delegated);
		}
		if (delegated.size() != authorized.size()) {
			throw failure("The existing hint backend produced " + delegated.size() //$NON-NLS-1$
					+ " operations for " + authorized.size() + " authorized replacements", null); //$NON-NLS-1$ //$NON-NLS-2$
		}

		Iterator<TransformationResult> resultIterator= authorized.iterator();
		Iterator<CompilationUnitRewriteOperation> operationIterator= delegated.iterator();
		while (resultIterator.hasNext() && operationIterator.hasNext()) {
			TransformationResult result= resultIterator.next();
			CompilationUnitRewriteOperation delegate= operationIterator.next();
			if (result.match().getMatchedNode() instanceof MethodDeclaration) {
				operations.add(new PlannedMethodAnnotationOperation(result));
			} else {
				operations.add(new DelegatingHintOperation(delegate));
			}
		}
		return Set.copyOf(covered);
	}

	private static String requiredPlan(String content) throws CoreException {
		try {
			return HintPlanRequirement.fromContent(content)
					.orElseThrow(() -> new IllegalArgumentException(
							"A plan-aware hint program must declare <!requires-plan: ...>")); //$NON-NLS-1$
		} catch (IllegalArgumentException e) {
			throw failure("Invalid plan-aware hint contract: " + e.getMessage(), e); //$NON-NLS-1$
		}
	}

	private static HintFile parse(String content) throws CoreException {
		try {
			return new HintFileParser().parse(content);
		} catch (HintFileParser.HintParseException e) {
			throw failure("Cannot parse trusted plan-aware hint program", e); //$NON-NLS-1$
		}
	}

	private static CoreException failure(String message, Throwable cause) {
		return new CoreException(new Status(IStatus.ERROR, "sandbox_common", message, cause)); //$NON-NLS-1$
	}

	private static Set<String> annotationNames(String source) {
		Set<String> names= new LinkedHashSet<>();
		if (source == null) {
			return names;
		}
		Matcher matcher= ANNOTATION_NAME.matcher(source);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}

	private static String simpleName(String qualifiedName) {
		int separator= qualifiedName.lastIndexOf('.');
		return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
	}

	/**
	 * Applies marker annotations declared by an authorized method-declaration hint
	 * using real AST nodes and {@link ImportRewrite} rather than text placeholders.
	 */
	private static final class PlannedMethodAnnotationOperation
			extends CompilationUnitRewriteOperationWithSourceRange {
		private final TransformationResult result;

		PlannedMethodAnnotationOperation(TransformationResult result) {
			this.result= result;
		}

		@Override
		public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
				throws CoreException {
			if (!(result.match().getMatchedNode() instanceof MethodDeclaration method)) {
				throw failure("The planned method annotation target is missing", null); //$NON-NLS-1$
			}
			Set<String> annotationsToAdd= annotationNames(result.replacement());
			if (result.rule() != null) {
				annotationsToAdd.removeAll(annotationNames(result.rule().sourcePattern().getValue()));
			}
			if (annotationsToAdd.isEmpty()) {
				throw failure("The planned method hint does not add an annotation", null); //$NON-NLS-1$
			}

			Set<String> existing= new LinkedHashSet<>();
			for (Object modifier : method.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					ITypeBinding binding= annotation.resolveTypeBinding();
					String name= binding == null ? annotation.getTypeName().getFullyQualifiedName()
							: binding.getQualifiedName();
					existing.add(simpleName(name));
				}
			}

			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			ImportRewrite imports= cuRewrite.getImportRewrite();
			ListRewrite modifiers= rewrite.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
			TextEditGroup group= createTextEditGroup(
					result.description() == null ? "Apply planned method annotation" : result.description(), //$NON-NLS-1$
					cuRewrite);
			for (String annotationName : annotationsToAdd) {
				String simple= simpleName(annotationName);
				if (existing.contains(simple)) {
					continue;
				}
				String importedName= annotationName.indexOf('.') >= 0
						? imports.addImport(annotationName) : annotationName;
				MarkerAnnotation annotation= ast.newMarkerAnnotation();
				annotation.setTypeName(ast.newName(importedName));
				modifiers.insertFirst(annotation, group);
				existing.add(simple);
			}
		}
	}

	/** Preserves the source-range behavior expected by coordinated cleanups. */
	private static final class DelegatingHintOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final CompilationUnitRewriteOperation delegate;

		DelegatingHintOperation(CompilationUnitRewriteOperation delegate) {
			this.delegate= delegate;
		}

		@Override
		public void rewriteASTInternal(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel)
				throws CoreException {
			delegate.rewriteAST(cuRewrite, linkedModel);
		}

		@Override
		public String getAdditionalInfo() {
			return delegate.getAdditionalInfo();
		}
	}
}
