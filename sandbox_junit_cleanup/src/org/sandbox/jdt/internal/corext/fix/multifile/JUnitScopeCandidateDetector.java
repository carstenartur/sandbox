/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_CLASS_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RULES_EXTERNAL_RESOURCE;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

/** Lightweight pre-scan used before requesting complete-project JUnit scope. */
public final class JUnitScopeCandidateDetector {

	private JUnitScopeCandidateDetector() {
		// utility class
	}

	/**
	 * Returns whether the current selection contains either side of the coordinated
	 * named {@code ExternalResource} migration: a resource declaration or a
	 * {@code @Rule}/{@code @ClassRule} field.
	 * <p>
	 * A positive result only enables the existing conservative complete-project
	 * fallback. The semantic planner remains responsible for proving that a closed,
	 * lifecycle-compatible migration exists.
	 * </p>
	 *
	 * @param project Java project owning the cleanup scope
	 * @param currentScope currently selected compilation units
	 * @param monitor progress monitor, may be {@code null}
	 * @return {@code true} when coordinated fallback analysis may be required
	 */
	public static boolean containsCandidate(IJavaProject project, Collection<ICompilationUnit> currentScope,
			IProgressMonitor monitor) {
		if (currentScope == null || currentScope.isEmpty()) {
			return false;
		}
		checkCanceled(monitor);
		Set<ICompilationUnit> units= new LinkedHashSet<>();
		for (ICompilationUnit unit : currentScope) {
			if (unit != null && unit.exists() && project.equals(unit.getJavaProject())) {
				units.add(unit.getPrimary());
			}
		}
		if (units.isEmpty()) {
			return false;
		}

		AtomicBoolean candidate= new AtomicBoolean();
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(project);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(project));
		parser.createASTs(units.toArray(ICompilationUnit[]::new), new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				if (candidate.get()) {
					return;
				}
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration node) {
						ITypeBinding binding= node.resolveBinding();
						if (binding != null && binding.getSuperclass() != null
								&& ORG_JUNIT_RULES_EXTERNAL_RESOURCE.equals(
										binding.getSuperclass().getErasure().getQualifiedName())) {
							candidate.set(true);
							return false;
						}
						return !candidate.get();
					}

					@Override
					public boolean visit(FieldDeclaration node) {
						for (Object modifier : node.modifiers()) {
							if (!(modifier instanceof Annotation annotation)) {
								continue;
							}
							ITypeBinding binding= annotation.resolveTypeBinding();
							if (binding == null) {
								continue;
							}
							String qualifiedName= binding.getQualifiedName();
							if (ORG_JUNIT_RULE.equals(qualifiedName) || ORG_JUNIT_CLASS_RULE.equals(qualifiedName)) {
								candidate.set(true);
								return false;
							}
						}
						return !candidate.get();
					}
				});
			}
		}, monitor);
		checkCanceled(monitor);
		return candidate.get();
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
	}
}
